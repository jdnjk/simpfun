package cn.jdnjk.simpfun.ui.setting;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import cn.jdnjk.simpfun.utils.BottomNavScrollHelper;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;
import cn.jdnjk.simpfun.utils.PageDataStore;
import cn.jdnjk.simpfun.utils.StoragePermissionHelper;

public class SettingsFragment extends Fragment {
    private static final String SP_TOKEN = "token";
    private static final String SP_USER_INFO = "user_info";
    private static final String SP_SERVER_DATA = "server_data";
    private static final String SP_DEVICE_ID = "deviceid";
    private static final String KEY_TOKEN = "token";

    private SharedPreferences sp;
    private SharedPreferences userInfo;
    private ThemeManager themeManager;
    private TerminalThemeManager terminalThemeManager;
    private ServerCardStyleManager serverCardStyleManager;
    private FilePaneModeManager filePaneModeManager;
    private SftpTransferSettingsManager sftpTransferSettingsManager;
    private TextView tvThemeCurrent;
    private TextView tvTerminalThemeCurrent;
    private TextView tvQqCurrent;
    private EditText etSftpThreadCount;
    private Slider sliderSftpThreadCount;
    private MaterialSwitch switchServerCardStyle;
    private MaterialSwitch switchFileDualPane;
    private NestedScrollView scrollView;
    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private ActivityResultLauncher<String> readStoragePermissionLauncher;
    private boolean pendingEnableDualPane;
    private boolean suppressDualPaneSwitchChange;
    private final BottomNavScrollHelper.Binding bottomNavBinding = new BottomNavScrollHelper.Binding();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manageAllFilesLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> finishEnableDualPaneIfAllowed());
        readStoragePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (Boolean.TRUE.equals(granted)) {
                enableDualPaneSetting();
            } else {
                pendingEnableDualPane = false;
                Toast.makeText(requireContext(), "未获得本地存储访问权限", Toast.LENGTH_SHORT).show();
            }
        });
        sp = requireContext().getSharedPreferences(SP_TOKEN, 0);
        userInfo = requireContext().getSharedPreferences(SP_USER_INFO, 0);
        themeManager = ThemeManager.getInstance(requireContext());
        terminalThemeManager = TerminalThemeManager.getInstance(requireContext());
        serverCardStyleManager = new ServerCardStyleManager(requireContext());
        filePaneModeManager = new FilePaneModeManager(requireContext());
        sftpTransferSettingsManager = new SftpTransferSettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        scrollView = root.findViewById(R.id.scroll_settings);
        if (getActivity() instanceof cn.jdnjk.simpfun.MainActivity mainActivity) {
            bottomNavBinding.attach(scrollView, mainActivity::onPrimaryScroll);
        }

        initViews(root);
        setupClickListeners(root);
        updateThemeDisplay();
        loadUserInfo();
        updateSftpThreadCountDisplay();
        bindSwitches();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingEnableDualPane && StoragePermissionHelper.hasLocalStorageAccess(requireContext())) {
            enableDualPaneSetting();
        }
    }

    @Override
    public void onDestroyView() {
        bottomNavBinding.detach(scrollView);
        scrollView = null;
        super.onDestroyView();
    }

    private void initViews(View root) {
        tvThemeCurrent = root.findViewById(R.id.tv_theme_current);
        tvTerminalThemeCurrent = root.findViewById(R.id.tv_terminal_theme_current);
        tvQqCurrent = root.findViewById(R.id.tv_qq_current);
        etSftpThreadCount = root.findViewById(R.id.et_sftp_thread_count);
        sliderSftpThreadCount = root.findViewById(R.id.slider_sftp_thread_count);
        switchServerCardStyle = root.findViewById(R.id.switch_server_card_style);
        switchFileDualPane = root.findViewById(R.id.switch_file_dual_pane);

        TextView tvVersion = root.findViewById(R.id.tv_version);
        String currentVersion = BuildConfig.VERSION_NAME;
        tvVersion.setText(String.format("当前版本：%s", currentVersion));
    }

    private void bindSwitches() {
        if (switchServerCardStyle != null) {
            switchServerCardStyle.setChecked(serverCardStyleManager.isModernServerCardEnabled());
            switchServerCardStyle.setOnCheckedChangeListener((buttonView, isChecked) ->
                    serverCardStyleManager.setModernServerCardEnabled(isChecked));
        }
        if (switchFileDualPane != null) {
            setDualPaneSwitchChecked(filePaneModeManager.isDualFilePaneEnabled());
            switchFileDualPane.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressDualPaneSwitchChange) {
                    return;
                }
                if (isChecked) {
                    requestEnableDualPane();
                } else {
                    pendingEnableDualPane = false;
                    filePaneModeManager.setDualFilePaneEnabled(false);
                }
            });
        }
    }

    private void setDualPaneSwitchChecked(boolean checked) {
        if (switchFileDualPane == null) {
            return;
        }
        suppressDualPaneSwitchChange = true;
        switchFileDualPane.setChecked(checked);
        suppressDualPaneSwitchChange = false;
    }

    private void requestEnableDualPane() {
        if (StoragePermissionHelper.hasLocalStorageAccess(requireContext())) {
            enableDualPaneSetting();
            return;
        }
        setDualPaneSwitchChecked(false);
        showLocalStoragePermissionDialog();
    }

    private void showLocalStoragePermissionDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("需要本地存储权限")
                .setMessage("双排模式需要访问 /sdcard，用于显示本地存储文件。请在系统设置中允许访问所有文件。")
                .setPositiveButton("去授权", (dialog, which) -> launchStoragePermissionRequest())
                .setNegativeButton(R.string.cancel, (dialog, which) -> pendingEnableDualPane = false)
                .show();
    }

    private void launchStoragePermissionRequest() {
        pendingEnableDualPane = true;
        if (!StoragePermissionHelper.requiresManageAllFiles()) {
            readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            return;
        }
        try {
            manageAllFilesLauncher.launch(StoragePermissionHelper.createManageAllFilesIntent(requireContext()));
        } catch (ActivityNotFoundException e) {
            manageAllFilesLauncher.launch(StoragePermissionHelper.createManageAllFilesFallbackIntent());
        }
    }

    private void finishEnableDualPaneIfAllowed() {
        if (!pendingEnableDualPane) {
            return;
        }
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

    private void setupClickListeners(View root) {
        root.findViewById(R.id.option_theme).setOnClickListener(v -> showThemeDialog());
        root.findViewById(R.id.option_terminal_theme).setOnClickListener(v -> showTerminalThemeDialog());
        root.findViewById(R.id.option_login_browser).setOnClickListener(v -> openBrowserLogin());
        root.findViewById(R.id.option_logout).setOnClickListener(v -> showLogoutDialog());
        root.findViewById(R.id.option_bind_qq).setOnClickListener(v -> showBindQQDialog());
    }

    private void updateSftpThreadCountDisplay() {
        int count = sftpTransferSettingsManager.getThreadCount();
        if (etSftpThreadCount != null) {
            etSftpThreadCount.setText(String.valueOf(count));
        }
        if (sliderSftpThreadCount != null) {
            sliderSftpThreadCount.setValue(count);
        }
        setupSftpThreadCountListeners();
    }

    private boolean isUpdatingSftpThreadCount = false;

    private void setupSftpThreadCountListeners() {
        if (sliderSftpThreadCount == null || etSftpThreadCount == null) return;

        sliderSftpThreadCount.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isUpdatingSftpThreadCount) {
                isUpdatingSftpThreadCount = true;
                int count = (int) value;
                sftpTransferSettingsManager.setThreadCount(count);
                etSftpThreadCount.setText(String.valueOf(count));
                isUpdatingSftpThreadCount = false;
            }
        });

        etSftpThreadCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingSftpThreadCount) {
                    try {
                        String input = s.toString().trim();
                        if (input.isEmpty()) return;
                        int count = Integer.parseInt(input);
                        if (count < 1) count = 1;
                        if (count > 32) count = 32;

                        isUpdatingSftpThreadCount = true;
                        sftpTransferSettingsManager.setThreadCount(count);
                        sliderSftpThreadCount.setValue(count);
                        isUpdatingSftpThreadCount = false;
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }

    private void updateThemeDisplay() {
        int currentTheme = themeManager.getThemeMode();
        tvThemeCurrent.setText(themeManager.getThemeName(currentTheme));

        int currentTerminalTheme = terminalThemeManager.getTerminalThemeMode();
        tvTerminalThemeCurrent.setText(terminalThemeManager.getTerminalThemeName(currentTerminalTheme));
    }

    private void showThemeDialog() {
        String[] themeOptions = {"跟随系统", "浅色模式", "深色模式"};
        int currentTheme = themeManager.getThemeMode();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择主题")
                .setSingleChoiceItems(themeOptions, currentTheme, (dialog, which) -> {
                    themeManager.setThemeMode(which);
                    updateThemeDisplay();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTerminalThemeDialog() {
        String[] terminalThemeOptions = {"跟随主题", "强制浅色", "强制深色"};
        int currentTerminalTheme = terminalThemeManager.getTerminalThemeMode();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择终端颜色")
                .setSingleChoiceItems(terminalThemeOptions, currentTerminalTheme, (dialog, which) -> {
                    terminalThemeManager.setTerminalThemeMode(which);
                    updateThemeDisplay();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openBrowserLogin() {
        String token = sp.getString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = "https://simpfun.cn/auth?autologin=" + token;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账户吗？")
                .setPositiveButton("退出", (dialog, which) -> {
                    clearAccountData();
                    Intent intent = new Intent(requireContext(), AuthActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearAccountData() {
        requireContext().getSharedPreferences(SP_TOKEN, 0).edit().remove(KEY_TOKEN).apply();
        requireContext().getSharedPreferences(SP_USER_INFO, 0).edit().clear().apply();
        requireContext().getSharedPreferences(SP_SERVER_DATA, 0).edit().clear().apply();
        requireContext().getSharedPreferences(SP_DEVICE_ID, 0).edit().clear().apply();
        PageDataStore.getInstance().clearAll();
        InstanceDetailStore.getInstance().clearAll();
    }

    private void loadUserInfo() {
        if (userInfo != null && tvQqCurrent != null) {
            long qq = userInfo.getLong("qq", 0);
            tvQqCurrent.setText(qq == 0 ? "未绑定" : String.valueOf(qq));
        }
    }

    private void showBindQQDialog() {
        final EditText editText = new EditText(requireContext());
        long currentQq = userInfo.getLong("qq", 0);
        if (currentQq != 0) {
            editText.setText(String.valueOf(currentQq));
        }
        editText.setHint("请输入 QQ 号码");

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(padding * 2, padding, padding * 2, padding);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("绑定 QQ 号")
                .setView(editText)
                .setPositiveButton("确定", (dialog, which) -> {
                    String qqStr = editText.getText().toString().trim();
                    if (qqStr.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入 QQ 号码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        long qq = Long.parseLong(qqStr);
                        bindQQ(qq);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "请输入有效的 QQ 号码", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void bindQQ(long qq) {
        String token = sp.getString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        new UserApi(requireContext()).bindQQ(token, qq, new UserApi.InstanceCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), "绑定成功", Toast.LENGTH_SHORT).show();
                userInfo.edit().putLong("qq", qq).apply();
                loadUserInfo();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "绑定失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
