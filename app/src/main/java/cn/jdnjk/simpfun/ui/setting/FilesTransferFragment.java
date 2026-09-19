package cn.jdnjk.simpfun.ui.setting;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.download.DownloadLocationManager;
import cn.jdnjk.simpfun.utils.StoragePermissionHelper;

/**
 * 文件与传输：文件浏览模式、下载保存位置与 SFTP 传输并发数。
 */
public class FilesTransferFragment extends Fragment {

    private static final String[] DOWNLOAD_LOCATION_OPTIONS =
            {"应用专属目录", "系统下载目录", "自定义文件夹"};

    private FilePaneModeManager filePaneModeManager;
    private SftpTransferSettingsManager sftpTransferSettingsManager;
    private DownloadLocationManager downloadLocationManager;

    private MaterialSwitch switchDualPane;
    private boolean suppressDualPaneSwitchChange;
    private boolean pendingEnableDualPane;
    private MaterialAutoCompleteTextView actvDownloadLocation;

    private TextInputEditText etSftpThreadCount;
    private TextInputLayout inputSftpThreadCount;
    private boolean updatingSftpThreadCount;

    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private ActivityResultLauncher<String> readStoragePermissionLauncher;
    private ActivityResultLauncher<String> downloadWritePermissionLauncher;
    private ActivityResultLauncher<Uri> downloadTreeLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filePaneModeManager = new FilePaneModeManager(requireContext());
        sftpTransferSettingsManager = new SftpTransferSettingsManager(requireContext());
        downloadLocationManager = new DownloadLocationManager(requireContext());

        manageAllFilesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> finishEnableDualPaneIfAllowed());
        readStoragePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        enableDualPaneSetting();
                    } else {
                        pendingEnableDualPane = false;
                        Toast.makeText(requireContext(), "未获得本地存储访问权限",
                                Toast.LENGTH_SHORT).show();
                    }
                });
        downloadWritePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> onDownloadWritePermissionResult(Boolean.TRUE.equals(granted)));
        downloadTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                this::onCustomTreePicked);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_files_transfer, container, false);

        setupDualPaneToggle(root);
        setupDownloadLocationDropdown(root);
        setupSftpThreadControls(root);

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("文件与传输");
            activity.setHelpEnabled(false);
            activity.bindToolbarScroll(root.findViewById(R.id.scroll_files));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("文件与传输");
            activity.setHelpEnabled(false);
        }
        if (pendingEnableDualPane && StoragePermissionHelper.hasLocalStorageAccess(requireContext())) {
            enableDualPaneSetting();
        }
        // SAF 目录授权可能在离开期间被撤销，回到页面时重新渲染一次
        updateDownloadLocationDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        SettingsSaveManager.getInstance(requireContext()).flush();
    }

    @Override
    public void onDestroyView() {
        SettingsSaveManager.getInstance(requireContext()).flush();
        actvDownloadLocation = null;
        switchDualPane = null;
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    private void setupDualPaneToggle(View root) {
        View row = root.findViewById(R.id.row_dual_pane);
        switchDualPane = row.findViewById(R.id.switch_entry);
        bindToggleRow(row, R.drawable.ic_view_column_2,
                "文件双排模式", "同时浏览本地文件与远程目录");
        setDualPaneSwitchChecked(filePaneModeManager.isDualFilePaneEnabled());
        // 首次启用时请求必要的本地文件访问权限
        switchDualPane.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressDualPaneSwitchChange) return;
            if (isChecked) {
                requestEnableDualPane();
            } else {
                pendingEnableDualPane = false;
                filePaneModeManager.setDualFilePaneEnabled(false);
            }
        });
    }

    private void setDualPaneSwitchChecked(boolean checked) {
        suppressDualPaneSwitchChange = true;
        switchDualPane.setChecked(checked);
        suppressDualPaneSwitchChange = false;
    }

    private void requestEnableDualPane() {
        if (StoragePermissionHelper.hasLocalStorageAccess(requireContext())) {
            enableDualPaneSetting();
            return;
        }
        setDualPaneSwitchChecked(false);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("需要本地存储权限")
                .setMessage("双排模式需要访问本地存储，用于显示本地文件。请在系统设置中允许访问。")
                .setPositiveButton("去授权", (dialog, which) -> launchStoragePermissionRequest())
                .setNegativeButton(cn.jdnjk.simpfun.R.string.cancel,
                        (dialog, which) -> pendingEnableDualPane = false)
                .show();
    }

    private void launchStoragePermissionRequest() {
        pendingEnableDualPane = true;
        if (!StoragePermissionHelper.requiresManageAllFiles()) {
            readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            return;
        }
        try {
            manageAllFilesLauncher.launch(
                    StoragePermissionHelper.createManageAllFilesIntent(requireContext()));
        } catch (ActivityNotFoundException e) {
            manageAllFilesLauncher.launch(
                    StoragePermissionHelper.createManageAllFilesFallbackIntent());
        }
    }

    private void finishEnableDualPaneIfAllowed() {
        if (!pendingEnableDualPane) return;
        if (StoragePermissionHelper.hasLocalStorageAccess(requireContext())) {
            enableDualPaneSetting();
        } else {
            pendingEnableDualPane = false;
            setDualPaneSwitchChecked(false);
            Toast.makeText(requireContext(), "未获得本地存储访问权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableDualPaneSetting() {
        pendingEnableDualPane = false;
        filePaneModeManager.setDualFilePaneEnabled(true);
        setDualPaneSwitchChecked(true);
    }

    private void setupDownloadLocationDropdown(View root) {
        actvDownloadLocation = root.findViewById(R.id.actv_download_location);
        actvDownloadLocation.setSimpleItems(DOWNLOAD_LOCATION_OPTIONS);
        updateDownloadLocationDisplay();
        actvDownloadLocation.setOnItemClickListener((parent, view, position, id) ->
                applyDownloadMode(position + DownloadLocationManager.MODE_APP_PRIVATE));
    }    /** 选择自定义文件夹时使用系统目录选择器，授权成功后再落库。 */
    private void applyDownloadMode(int mode) {
        if (mode == DownloadLocationManager.MODE_PUBLIC_DOWNLOADS) {
            // API 30+ 走 MediaStore 不需要权限；更低版本才需要 WRITE_EXTERNAL_STORAGE
            if (!StoragePermissionHelper.canWritePublicDownloads(requireContext())) {
                downloadWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        } else if (mode == DownloadLocationManager.MODE_CUSTOM_TREE) {
            downloadTreeLauncher.launch(null);
            return;
        }
        downloadLocationManager.setMode(mode);
        updateDownloadLocationDisplay();
    }

    private void onDownloadWritePermissionResult(boolean granted) {
        if (granted) {
            downloadLocationManager.setMode(DownloadLocationManager.MODE_PUBLIC_DOWNLOADS);
        } else {
            downloadLocationManager.setMode(DownloadLocationManager.MODE_APP_PRIVATE);
            Toast.makeText(requireContext(), R.string.download_location_permission_denied,
                    Toast.LENGTH_LONG).show();
        }
        updateDownloadLocationDisplay();
    }

    private void onCustomTreePicked(@Nullable Uri treeUri) {
        if (treeUri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(requireContext(), R.string.download_tree_not_writable,
                    Toast.LENGTH_LONG).show();
            return;
        }
        releasePreviousDownloadTree(treeUri.toString());
        downloadLocationManager.setCustomTreeUri(treeUri.toString());
        downloadLocationManager.setMode(DownloadLocationManager.MODE_CUSTOM_TREE);
        updateDownloadLocationDisplay();
    }

    /** 释放上一个目录的持久授权，避免永久授权记录累积。 */
    private void releasePreviousDownloadTree(String newUri) {
        String previous = downloadLocationManager.getCustomTreeUri();
        if (previous.isEmpty() || previous.equals(newUri)) return;
        try {
            requireContext().getContentResolver().releasePersistableUriPermission(
                    Uri.parse(previous),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
            // 授权可能已经失效，忽略即可
        }
    }

    private void updateDownloadLocationDisplay() {
        // onCreateView 完成前（requireView 不可用时）也会被调用，缓存引用避免崩溃
        MaterialAutoCompleteTextView actv = actvDownloadLocation;
        if (actv == null) return;
        int mode = downloadLocationManager.getMode();
        String text;
        if (mode == DownloadLocationManager.MODE_CUSTOM_TREE) {
            text = "自定义文件夹（" + describeCustomTree() + "）";
        } else if (mode == DownloadLocationManager.MODE_PUBLIC_DOWNLOADS) {
            text = "系统下载目录";
        } else {
            text = "应用专属目录";
        }
        actv.setText(text, false);
    }

    private String describeCustomTree() {
        String saved = downloadLocationManager.getCustomTreeUri();
        if (saved.isEmpty()) return "应用专属目录";
        try {
            String documentId = DocumentsContract.getTreeDocumentId(Uri.parse(saved));
            int colon = documentId.indexOf(':');
            String path = colon >= 0 ? documentId.substring(colon + 1) : documentId;
            return path.isEmpty() ? documentId : path;
        } catch (Exception e) {
            return saved;
        }
    }

    private void setupSftpThreadControls(View root) {
        Slider slider = root.findViewById(R.id.slider_sftp_thread_count);
        etSftpThreadCount = root.findViewById(R.id.et_sftp_thread_count);
        inputSftpThreadCount = root.findViewById(R.id.input_sftp_thread_count);

        int count = sftpTransferSettingsManager.getThreadCount();
        slider.setValue(Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), count)));
        etSftpThreadCount.setText(String.valueOf(count));

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser && !updatingSftpThreadCount) {
                updatingSftpThreadCount = true;
                int threadCount = (int) value;
                sftpTransferSettingsManager.setThreadCount(threadCount);
                etSftpThreadCount.setText(String.valueOf(threadCount));
                updatingSftpThreadCount = false;
            }
        });

        etSftpThreadCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingSftpThreadCount) return;
                String input = s.toString().trim();
                if (input.isEmpty()) return;
                try {
                    int threadCount = Integer.parseInt(input);
                    if (threadCount < 1 || threadCount > 32) {
                        inputSftpThreadCount.setError("需在 1–32 之间");
                        return;
                    }
                    inputSftpThreadCount.setError(null);
                    updatingSftpThreadCount = true;
                    sftpTransferSettingsManager.setThreadCount(threadCount);
                    slider.setValue(threadCount);
                    updatingSftpThreadCount = false;
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    private void bindToggleRow(View row, int iconRes, String title, String subtitle) {
        SettingsRowUtils.hideLeadingIcon(row);
        ImageView icon = row.findViewById(R.id.iv_entry_icon);
        icon.setImageResource(iconRes);
        TextView tvTitle = row.findViewById(R.id.tv_entry_title);
        tvTitle.setText(title);
        TextView tvSubtitle = row.findViewById(R.id.tv_entry_subtitle);
        tvSubtitle.setText(subtitle);
    }
}
