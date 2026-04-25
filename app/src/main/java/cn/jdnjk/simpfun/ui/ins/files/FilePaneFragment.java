package cn.jdnjk.simpfun.ui.ins.files;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.FileEditorActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.adapter.FileAdapter;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.api.ins.file.FileManageApi;
import cn.jdnjk.simpfun.api.ins.file.FileTransferApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.notification.TaskQueueNotificationHelper;
import cn.jdnjk.simpfun.utils.FilePathUtils;

public class FilePaneFragment extends Fragment {
    private static final String ARG_INITIAL_PATH = "initial_path";
    private static final String TAG = "FilePaneFragment";
    private static final long MAX_UPLOAD_SIZE_BYTES = 1000L * 1024L * 1024L;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout pathContainer;
    private HorizontalScrollView pathScrollView;

    private final List<FileItem> fileList = new ArrayList<>();
    private FileAdapter adapter;
    private String currentPath = "/";

    private ActivityResultLauncher<android.content.Intent> editorLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private Runnable pendingNotificationAction;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    private androidx.appcompat.app.AlertDialog uploadDialog;
    private androidx.appcompat.app.AlertDialog downloadDialog;
    private LinearProgressIndicator uploadProgressIndicator;
    private TextView uploadStatusText;
    private TextView uploadFileNameText;
    private FileTransferApi.UploadHandle currentUploadHandle;
    private File currentUploadTempFile;
    private boolean currentUploadDeleteWhenDone;
    private boolean currentUploadBackgrounded;
    private boolean currentUploadCancelled;
    private boolean preparingUpload;
    private int currentUploadNotificationId;
    private int currentUploadDeviceId;
    private int currentUploadLastProgress = -1;
    private long currentUploadStartMillis;
    private long uploadGeneration;
    private String currentUploadFileName;
    private String currentUploadSpeedText = "正在上传";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            Runnable action = pendingNotificationAction;
            pendingNotificationAction = null;
            if (isGranted && isAdded()) {
                if (action != null) {
                    action.run();
                }
            } else if (!isGranted && isAdded()) {
                Toast.makeText(requireContext(), "通知权限未授予，上传继续在前台", Toast.LENGTH_LONG).show();
            }
        });

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handleSelectedFile(uri);
            }
        });

        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleEditorResult(result.getData());
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_file_pane, container, false);

        FloatingActionButton fabAdd = root.findViewById(R.id.fab_add);
        recyclerView = root.findViewById(R.id.recycler_view_files);
        progressBar = root.findViewById(R.id.progress_bar);
        emptyView = root.findViewById(R.id.empty_view);
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        pathContainer = root.findViewById(R.id.layout_path_container);
        pathScrollView = root.findViewById(R.id.scroll_view_path);

        if (getArguments() != null) {
            String init = getArguments().getString(ARG_INITIAL_PATH, "/");
            if (init != null && !init.trim().isEmpty()) {
                currentPath = FilePathUtils.sanitizePath(init.trim());
            }
        }

        adapter = new FileAdapter(fileList, this::onFileItemClick, this::onFileItemLongClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showCreateOptionsDialog());
        }

        updatePathView();
        loadFileList();
        return root;
    }

    @Override
    public void onDestroyView() {
        pendingNotificationAction = null;
        dismissDownloadDialog();
        if (currentUploadHandle != null && !currentUploadBackgrounded) {
            currentUploadCancelled = true;
            currentUploadHandle.cancel();
            cleanupCurrentUpload(true);
        }
        dismissUploadDialog();
        recyclerView = null;
        progressBar = null;
        emptyView = null;
        swipeRefreshLayout = null;
        pathContainer = null;
        pathScrollView = null;
        uploadProgressIndicator = null;
        uploadStatusText = null;
        uploadFileNameText = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        fileExecutor.shutdownNow();
        super.onDestroy();
    }

    private void showCreateOptionsDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String[] options = {getString(R.string.new_file), getString(R.string.new_folder), "上传文件"};
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.create_file_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCreateEntryDialog(true);
                    } else if (which == 1) {
                        showCreateEntryDialog(false);
                    } else {
                        filePickerLauncher.launch("*/*");
                    }
                })
                .show();
    }

    private void showCreateEntryDialog(boolean createFile) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle(createFile ? getString(R.string.create_file_title) : getString(R.string.create_folder_title));
        final EditText input = new EditText(context);
        input.setHint(createFile ? getString(R.string.create_file_hint) : getString(R.string.create_folder_hint));
        input.setSingleLine(true);
        builder.setView(input);

        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!FilePathUtils.isSafeEntryName(name)) {
                Toast.makeText(context, "名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT).show();
                return;
            }
            createEntryOnServer(createFile ? "file" : "folder", name);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void createEntryOnServer(String mode, String name) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int deviceId = getDeviceId(context);
        if (deviceId <= 0) {
            Toast.makeText(context, "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileManageApi().createFileOrFolder(context, deviceId, mode, currentPath, name, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "创建成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleSelectedFile(Uri uri) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        int serverId = getDeviceId(context);
        if (serverId <= 0) {
            Toast.makeText(context, "未找到服务器ID", Toast.LENGTH_SHORT).show();
            return;
        }
        if (preparingUpload || currentUploadHandle != null) {
            Toast.makeText(context, "已有上传任务正在进行", Toast.LENGTH_SHORT).show();
            return;
        }

        Long size = getContentSize(context, uri);
        if (size != null && size > MAX_UPLOAD_SIZE_BYTES) {
            Toast.makeText(context, "文件超过1000MB，请使用SFTP上传较大文件", Toast.LENGTH_LONG).show();
            return;
        }

        String fileName = FilePathUtils.sanitizeFileName(getFileName(context, uri), "uploaded_file");
        String targetPath = currentPath;
        preparingUpload = true;
        Toast.makeText(context, "正在准备上传", Toast.LENGTH_SHORT).show();

        fileExecutor.execute(() -> {
            File tempFile = null;
            Exception copyError = null;
            try {
                File uploadDir = new File(appContext.getCacheDir(), "uploads");
                if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                    throw new IOException("无法创建上传缓存目录");
                }
                tempFile = new File(uploadDir, fileName);
                if (tempFile.exists() && !tempFile.delete()) {
                    throw new IOException("无法清理旧缓存文件");
                }
                try (InputStream is = appContext.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    if (is == null) {
                        throw new IOException("无法打开输入流");
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    long copied = 0L;
                    while ((read = is.read(buffer)) != -1) {
                        copied += read;
                        if (copied > MAX_UPLOAD_SIZE_BYTES) {
                            throw new IOException("文件超过1000MB，请使用SFTP上传较大文件");
                        }
                        fos.write(buffer, 0, read);
                    }
                }
            } catch (Exception e) {
                copyError = e;
            }

            File finalTempFile = tempFile;
            Exception finalCopyError = copyError;
            mainHandler.post(() -> {
                preparingUpload = false;
                if (!isAdded()) {
                    deleteFileQuietly(finalTempFile);
                    return;
                }
                if (currentUploadHandle != null) {
                    deleteFileQuietly(finalTempFile);
                    Toast.makeText(requireContext(), "已有上传任务正在进行", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (finalCopyError != null) {
                    deleteFileQuietly(finalTempFile);
                    Toast.makeText(requireContext(), "准备上传失败: " + finalCopyError.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (finalTempFile == null || !finalTempFile.exists()) {
                    Toast.makeText(requireContext(), "准备上传失败: 文件不存在", Toast.LENGTH_SHORT).show();
                    return;
                }
                startUpload(requireContext().getApplicationContext(), serverId, targetPath, finalTempFile, true, fileName);
            });
        });
    }

    private void handleEditorResult(@NonNull android.content.Intent data) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String localPath = data.getStringExtra("local_path");
        String remotePath = data.getStringExtra("remote_path");
        int serverId = data.getIntExtra("server_id", -1);
        if (localPath == null || remotePath == null || serverId <= 0) {
            Toast.makeText(context, "保存结果无上传配置", Toast.LENGTH_SHORT).show();
            return;
        }
        if (preparingUpload || currentUploadHandle != null) {
            Toast.makeText(context, "已有上传任务正在进行", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(localPath);
        if (!file.exists()) {
            Toast.makeText(context, "本地文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        String remoteDir = FilePathUtils.getParentPath(remotePath);
        startUpload(context.getApplicationContext(), serverId, remoteDir, file, false,
                FilePathUtils.sanitizeFileName(data.getStringExtra("file_name"), file.getName()));
    }

    private void startUpload(Context appContext, int serverId, String remoteDir, File file,
            boolean deleteWhenDone, String displayName) {
        if (currentUploadHandle != null) {
            Toast.makeText(appContext, "已有上传任务正在进行", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUploadTempFile = file;
        currentUploadDeleteWhenDone = deleteWhenDone;
        currentUploadBackgrounded = false;
        currentUploadCancelled = false;
        currentUploadDeviceId = serverId;
        currentUploadNotificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        currentUploadLastProgress = -1;
        currentUploadStartMillis = System.currentTimeMillis();
        long uploadId = ++uploadGeneration;
        currentUploadFileName = FilePathUtils.sanitizeFileName(displayName, file.getName());
        currentUploadSpeedText = "正在上传";

        if (isAdded()) {
            showUploadDialog(currentUploadFileName);
        }

        FileTransferApi api = new FileTransferApi();
        currentUploadHandle = api.uploadFileWithProgress(appContext, serverId, remoteDir, file, new FileTransferApi.UploadCallback() {
            @Override
            public void onProgress(long uploadedBytes, long totalBytes) {
                if (!isCurrentUpload(uploadId) || currentUploadCancelled) {
                    return;
                }
                int progress = calculateProgress(uploadedBytes, totalBytes);
                String speedText = formatSpeed(uploadedBytes);
                currentUploadLastProgress = progress;
                currentUploadSpeedText = speedText;
                if (currentUploadBackgrounded) {
                    TaskQueueNotificationHelper.showUploadProgress(appContext, currentUploadNotificationId,
                            currentUploadDeviceId, currentUploadFileName, Math.max(progress, 0), speedText, progress < 0);
                } else if (isAdded()) {
                    updateUploadDialog(progress, speedText);
                }
            }

            @Override
            public void onSuccess(JSONObject data) {
                if (!isCurrentUpload(uploadId) || currentUploadCancelled) {
                    return;
                }
                if (currentUploadBackgrounded) {
                    TaskQueueNotificationHelper.cancel(appContext, currentUploadNotificationId);
                } else if (isAdded()) {
                    dismissUploadDialog();
                    Toast.makeText(requireContext(), "上传成功", Toast.LENGTH_SHORT).show();
                }
                cleanupCurrentUpload(currentUploadDeleteWhenDone);
                if (isAdded()) {
                    loadFileList();
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentUpload(uploadId) || currentUploadCancelled) {
                    return;
                }
                if (currentUploadBackgrounded) {
                    TaskQueueNotificationHelper.showUploadFailed(appContext, currentUploadNotificationId,
                            currentUploadDeviceId, currentUploadFileName, errorMsg);
                } else if (isAdded()) {
                    dismissUploadDialog();
                    Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                }
                cleanupCurrentUpload(currentUploadDeleteWhenDone);
            }
        });
    }

    private void showUploadDialog(String fileName) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_upload_progress, null);
        uploadFileNameText = dialogView.findViewById(R.id.text_upload_file_name);
        uploadStatusText = dialogView.findViewById(R.id.text_upload_status);
        uploadProgressIndicator = dialogView.findViewById(R.id.progress_upload);
        View buttonBackground = dialogView.findViewById(R.id.button_upload_background);
        View buttonCancel = dialogView.findViewById(R.id.button_upload_cancel);

        uploadFileNameText.setText(fileName);
        uploadStatusText.setText("准备上传");
        uploadProgressIndicator.setIndeterminate(true);

        buttonBackground.setOnClickListener(v -> moveCurrentUploadToBackground());
        buttonCancel.setOnClickListener(v -> cancelCurrentUpload());

        uploadDialog = new MaterialAlertDialogBuilder(context)
                .setTitle("正在上传文件...")
                .setView(dialogView)
                .setCancelable(false)
                .create();
        uploadDialog.show();
    }

    private void updateUploadDialog(int progress, String speedText) {
        if (uploadProgressIndicator == null || uploadStatusText == null) {
            return;
        }
        if (progress < 0) {
            uploadProgressIndicator.setIndeterminate(true);
            uploadStatusText.setText(speedText);
            return;
        }
        uploadProgressIndicator.setIndeterminate(false);
        try {
            uploadProgressIndicator.setProgressCompat(progress, true);
        } catch (Throwable t) {
            uploadProgressIndicator.setProgress(progress);
        }
        uploadStatusText.setText(String.format(Locale.getDefault(), "%d%% · %s", progress, speedText));
    }

    private void moveCurrentUploadToBackground() {
        if (currentUploadHandle == null) {
            dismissUploadDialog();
            return;
        }
        withNotificationPermission(() -> {
            if (currentUploadHandle == null) {
                dismissUploadDialog();
                return;
            }
            currentUploadBackgrounded = true;
            dismissUploadDialog();
            TaskQueueNotificationHelper.showUploadProgress(requireContext().getApplicationContext(), currentUploadNotificationId,
                    currentUploadDeviceId, currentUploadFileName, Math.max(currentUploadLastProgress, 0),
                    currentUploadSpeedText, currentUploadLastProgress < 0);
            Toast.makeText(requireContext(), "上传已切入后台", Toast.LENGTH_SHORT).show();
        });
    }

    private void cancelCurrentUpload() {
        if (currentUploadHandle == null) {
            dismissUploadDialog();
            return;
        }
        Context context = getContext();
        if (context == null) {
            currentUploadCancelled = true;
            currentUploadHandle.cancel();
            cleanupCurrentUpload(true);
            return;
        }
        currentUploadCancelled = true;
        currentUploadHandle.cancel();
        TaskQueueNotificationHelper.cancel(context.getApplicationContext(), currentUploadNotificationId);
        dismissUploadDialog();
        cleanupCurrentUpload(true);
        if (isAdded()) {
            Toast.makeText(requireContext(), "已取消上传", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isCurrentUpload(long uploadId) {
        return uploadId == uploadGeneration && currentUploadHandle != null;
    }

    private void cleanupCurrentUpload(boolean deleteFile) {
        if (deleteFile) {
            deleteFileQuietly(currentUploadTempFile);
        }
        currentUploadHandle = null;
        currentUploadTempFile = null;
        currentUploadDeleteWhenDone = false;
        currentUploadBackgrounded = false;
        currentUploadCancelled = false;
        currentUploadDeviceId = -1;
        currentUploadNotificationId = 0;
        currentUploadLastProgress = -1;
        currentUploadStartMillis = 0L;
        currentUploadFileName = null;
        currentUploadSpeedText = "正在上传";
    }

    private void dismissUploadDialog() {
        if (uploadDialog != null) {
            uploadDialog.dismiss();
            uploadDialog = null;
        }
        uploadProgressIndicator = null;
        uploadStatusText = null;
        uploadFileNameText = null;
    }

    private void withNotificationPermission(@NonNull Runnable action) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingNotificationAction = action;
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private int calculateProgress(long uploadedBytes, long totalBytes) {
        if (totalBytes <= 0) {
            return -1;
        }
        long value = uploadedBytes * 100L / totalBytes;
        return (int) Math.max(0, Math.min(value, 100));
    }

    private String formatSpeed(long uploadedBytes) {
        long elapsedMillis = Math.max(1L, System.currentTimeMillis() - currentUploadStartMillis);
        long bytesPerSecond = uploadedBytes * 1000L / elapsedMillis;
        return formatSize(bytesPerSecond) + "/s";
    }

    private Long getContentSize(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (index != -1 && !cursor.isNull(index)) {
                        long size = cursor.getLong(index);
                        if (size >= 0) {
                            return size;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }

        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = Math.max(result.lastIndexOf('/'), result.lastIndexOf('\\'));
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }

    private void onFileItemClick(FileItem item) {
        if (item.isParentEntry()) {
            currentPath = FilePathUtils.getParentPath(currentPath);
            updatePathView();
            loadFileList();
        } else if (item.isFile()) {
            downloadAndOpenFile(item);
        } else {
            currentPath = FilePathUtils.appendPath(currentPath, item.getName());
            updatePathView();
            loadFileList();
        }
    }

    private void onFileItemLongClick(FileItem item) {
        if (item.isParentEntry() || !isAdded()) {
            return;
        }
        showFileActionDialog(item);
    }

    private void showFileActionDialog(FileItem item) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_Simpfun_BottomSheet);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_file_actions, null, false);

        TextView title = view.findViewById(R.id.text_view_title);
        if (title != null) {
            title.setText(item.getName());
        }

        View deleteAction = view.findViewById(R.id.action_delete);
        if (deleteAction != null) {
            deleteAction.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteConfirmDialog(item);
            });
        }

        View renameAction = view.findViewById(R.id.action_rename);
        if (renameAction != null) {
            renameAction.setOnClickListener(v -> {
                dialog.dismiss();
                showRenameDialog(item);
            });
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private void showDeleteConfirmDialog(FileItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("删除确认")
                .setMessage("确定要删除 " + item.getName() + " 吗？")
                .setPositiveButton("删除", (d, w) -> deleteFile(item))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteFile(FileItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int deviceId = getDeviceId(context);
        if (deviceId <= 0) {
            Toast.makeText(context, "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> paths = new ArrayList<>();
        paths.add(FilePathUtils.appendPath(currentPath, item.getName()));

        new FileManageApi().deleteFileOrFolderBatch(context, deviceId, paths, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "删除失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRenameDialog(FileItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText input = new EditText(context);
        input.setText(item.getName());
        new MaterialAlertDialogBuilder(context)
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.equals(item.getName())) {
                        if (!FilePathUtils.isSafeEntryName(newName)) {
                            Toast.makeText(context, "名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        renameFile(item, newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameFile(FileItem item, String newName) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int deviceId = getDeviceId(context);
        if (deviceId <= 0) {
            Toast.makeText(context, "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        String origin = FilePathUtils.appendPath(currentPath, item.getName());
        String target = FilePathUtils.appendPath(currentPath, newName);

        new FileManageApi().renameFile(context, deviceId, origin, target, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "重命名成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "重命名失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFileList() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int deviceId = getDeviceId(context);
        if (deviceId <= 0) {
            showError("设备ID无效");
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        String requestPath = currentPath;
        showLoading(true);
        new FileApi().getFileList(context, deviceId, requestPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded() || !requestPath.equals(currentPath)) {
                    return;
                }
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                showLoading(false);
                try {
                    JSONArray list = data.getJSONArray("list");
                    updateFileList(list, requestPath);
                } catch (Exception e) {
                    showError("解析失败:" + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded() || !requestPath.equals(currentPath)) {
                    return;
                }
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                showLoading(false);
                showError(errorMsg);
            }
        });
    }

    private void updateFileList(JSONArray list, String requestPath) {
        fileList.clear();
        if (!"/".equals(requestPath)) {
            fileList.add(new FileItem(FileItem.PARENT_DIR_NAME, false, 0, "", ""));
        }

        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject obj = list.getJSONObject(i);
                String name = obj.getString("name");
                if ("..".equals(name) || ".".equals(name)) {
                    continue;
                }
                fileList.add(new FileItem(
                        name,
                        obj.getBoolean("file"),
                        obj.optLong("size", 0L),
                        obj.optString("mime", ""),
                        obj.optString("modified_at", "")
                ));
            } catch (Exception e) {
                Log.e(TAG, "文件解析失败", e);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmptyView();
    }

    private void downloadAndOpenFile(FileItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int deviceId = getDeviceId(context);
        if (deviceId <= 0) {
            Toast.makeText(context, "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.getSize() > 5 * 1024 * 1024) {
            Toast.makeText(context, "文件过大，暂不支持", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null);
        final LinearProgressIndicator progressIndicator = dialogView.findViewById(R.id.progress_download);
        final TextView textPercent = dialogView.findViewById(R.id.text_download_percent);
        final androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        downloadDialog = progressDialog;
        progressDialog.show();

        String remotePath = FilePathUtils.appendPath(currentPath, item.getName());
        File downloadsRoot = context.getExternalFilesDir("downloads");
        File remoteDir = new File(downloadsRoot == null ? context.getCacheDir() : downloadsRoot,
                Integer.toHexString(remotePath.hashCode()));
        File local = new File(remoteDir, FilePathUtils.sanitizeFileName(item.getName(), "downloaded_file"));
        if (local.getParentFile() != null && !local.getParentFile().exists()) {
            boolean mk = local.getParentFile().mkdirs();
            if (!mk && !local.getParentFile().exists()) {
                Toast.makeText(context, getString(R.string.download_failed_format, "无法创建本地目录"), Toast.LENGTH_SHORT).show();
                dismissDownloadDialog(progressDialog);
                return;
            }
        }

        new FileApi().downloadFileToLocal(context, deviceId, remotePath, local, new FileApi.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                if (!isAdded()) {
                    return;
                }
                progressIndicator.setIndeterminate(false);
                try {
                    progressIndicator.setProgressCompat(progress, true);
                } catch (Throwable t) {
                    progressIndicator.setProgress(progress);
                }
                textPercent.setText(getString(R.string.percent_format, progress));
            }

            @Override
            public void onSuccess(File file) {
                dismissDownloadDialog(progressDialog);
                if (!isAdded()) {
                    return;
                }
                openInternalEditor(file, remotePath, deviceId, item.getName());
            }

            @Override
            public void onFailure(String errorMsg) {
                dismissDownloadDialog(progressDialog);
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), getString(R.string.download_failed_format, errorMsg), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void dismissDownloadDialog() {
        if (downloadDialog != null) {
            downloadDialog.dismiss();
            downloadDialog = null;
        }
    }

    private void dismissDownloadDialog(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog != null) {
            dialog.dismiss();
        }
        if (downloadDialog == dialog) {
            downloadDialog = null;
        }
    }

    private void openInternalEditor(File file, String remotePath, int deviceId, String displayName) {
        try {
            Context context = getContext();
            if (context == null) {
                return;
            }
            android.content.Intent intent = new android.content.Intent(context, FileEditorActivity.class);
            intent.putExtra("local_path", file.getAbsolutePath());
            intent.putExtra("remote_path", remotePath);
            intent.putExtra("file_name", displayName);
            intent.putExtra("server_id", deviceId);
            editorLauncher.launch(intent);
        } catch (Exception e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), getString(R.string.open_editor_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLoading(boolean show) {
        if (progressBar == null || recyclerView == null || emptyView == null) {
            return;
        }
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        if (progressBar == null || recyclerView == null || emptyView == null) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setText(getString(R.string.error_format, msg));
        emptyView.setVisibility(View.VISIBLE);
    }

    private void updateEmptyView() {
        if (emptyView == null || recyclerView == null) {
            return;
        }
        if (fileList.isEmpty()) {
            emptyView.setText(getString(R.string.empty_directory));
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private int getDeviceId() {
        Context context = getContext();
        return context == null ? -1 : getDeviceId(context);
    }

    private int getDeviceId(Context context) {
        if (getActivity() instanceof ServerManages activity && activity.getDeviceId() > 0) {
            return activity.getDeviceId();
        }
        SharedPreferences sp = context.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    private void showEditPathDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.jump_path_title));
        final EditText input = new EditText(context);
        input.setText(currentPath);
        input.setSingleLine(true);
        builder.setView(input);
        builder.setPositiveButton(R.string.confirm, (d, w) -> {
            String p = input.getText().toString().trim();
            if (p.isEmpty()) {
                p = "/";
            }
            currentPath = FilePathUtils.sanitizePath(p);
            updatePathView();
            loadFileList();
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void updatePathView() {
        if (pathContainer == null) {
            return;
        }
        pathContainer.removeAllViews();

        TextView rootNode = new TextView(requireContext());
        rootNode.setText("/");
        rootNode.setTextColor(Color.WHITE);
        rootNode.setTextSize(16);
        rootNode.setPadding(16, 8, 16, 8);
        rootNode.setGravity(Gravity.CENTER);
        rootNode.setBackgroundResource(android.R.color.transparent);
        rootNode.setOnClickListener(v -> {
            if (!"/".equals(currentPath)) {
                currentPath = "/";
                updatePathView();
                loadFileList();
            }
        });
        rootNode.setOnLongClickListener(v -> {
            showEditPathDialog();
            return true;
        });
        pathContainer.addView(rootNode);

        if ("/".equals(currentPath)) {
            return;
        }

        String[] parts = currentPath.split("/");
        StringBuilder builtPath = new StringBuilder("/");

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            TextView divider = new TextView(requireContext());
            divider.setText(">");
            divider.setTextColor(Color.LTGRAY);
            divider.setTextSize(14);
            pathContainer.addView(divider);

            builtPath.append(part);
            String thisPath = builtPath.toString();
            builtPath.append("/");

            TextView node = new TextView(requireContext());
            node.setText(part);
            node.setTextColor(Color.WHITE);
            node.setTextSize(16);
            node.setPadding(16, 8, 16, 8);
            node.setGravity(Gravity.CENTER);

            if (thisPath.equals(currentPath)) {
                node.setTypeface(null, android.graphics.Typeface.BOLD);
                node.setOnLongClickListener(v -> {
                    showEditPathDialog();
                    return true;
                });
            }

            node.setOnClickListener(v -> {
                if (!thisPath.equals(currentPath)) {
                    currentPath = thisPath;
                    updatePathView();
                    loadFileList();
                }
            });

            pathContainer.addView(node);
        }

        if (pathScrollView != null) {
            pathScrollView.post(() -> pathScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
        }
    }

    private String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int group = (int) (Math.log10(size) / Math.log10(1024));
        group = Math.min(group, units.length - 1);
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, group)) + " " + units[group];
    }

    private void deleteFileQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete file: " + file.getAbsolutePath());
        }
    }
}
