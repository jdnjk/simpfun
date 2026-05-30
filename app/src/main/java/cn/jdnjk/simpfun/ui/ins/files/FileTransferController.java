package cn.jdnjk.simpfun.ui.ins.files;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.FileEditorActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileTransferApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.notification.TaskQueueNotificationHelper;
import cn.jdnjk.simpfun.utils.FilePathUtils;

class FileTransferController {
    interface Host {
        Context getContextOrNull();
        boolean isActive();
        int getDeviceId(Context context);
        void clearSelectionAndRender();
        void reloadFileList();
        void toast(String message, int length);
    }

    private static final String TAG = "FileTransferController";
    private static final long MAX_UPLOAD_SIZE_BYTES = 1000L * 1024L * 1024L;
    private static final long MAX_EDITOR_SIZE_BYTES = 5L * 1024L * 1024L;

    private final Fragment fragment;
    private final FilePaneState state;
    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<Intent> editorLauncher;
    private final ActivityResultLauncher<String> filePickerLauncher;
    private final ActivityResultLauncher<String> notificationPermissionLauncher;

    private Runnable pendingNotificationAction;
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

    FileTransferController(@NonNull Fragment fragment, @NonNull FilePaneState state, @NonNull Host host) {
        this.fragment = fragment;
        this.state = state;
        this.host = host;
        notificationPermissionLauncher = fragment.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            Runnable action = pendingNotificationAction;
            pendingNotificationAction = null;
            if (isGranted && host.isActive()) {
                if (action != null) {
                    action.run();
                }
            } else if (!isGranted && host.isActive()) {
                host.toast("通知权限未授予，上传继续在前台", Toast.LENGTH_LONG);
            }
        });
        filePickerLauncher = fragment.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handleSelectedFile(uri);
            }
        });
        editorLauncher = fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleEditorResult(result.getData());
            }
        });
    }

    void pickFile() {
        filePickerLauncher.launch("*/*");
    }

    void downloadAndOpenFile(FileItem item) {
        if (item.getSize() > MAX_EDITOR_SIZE_BYTES) {
            Context context = host.getContextOrNull();
            if (context != null) {
                host.toast(context.getString(R.string.file_too_large), Toast.LENGTH_SHORT);
            }
            return;
        }
        downloadFile(item, true);
    }

    void downloadFileOnly(FileItem item) {
        downloadFile(item, false);
    }

    void onDestroyView() {
        pendingNotificationAction = null;
        dismissDownloadDialog();
        if (currentUploadHandle != null && !currentUploadBackgrounded) {
            currentUploadCancelled = true;
            currentUploadHandle.cancel();
            cleanupCurrentUpload(true);
        }
        dismissUploadDialog();
    }

    void onDestroy() {
        fileExecutor.shutdownNow();
    }

    private void handleSelectedFile(Uri uri) {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        int serverId = host.getDeviceId(context);
        if (serverId <= 0) {
            host.toast("未找到服务器ID", Toast.LENGTH_SHORT);
            return;
        }
        if (preparingUpload || currentUploadHandle != null) {
            host.toast("已有上传任务正在进行", Toast.LENGTH_SHORT);
            return;
        }

        Long size = getContentSize(context, uri);
        if (size != null && size > MAX_UPLOAD_SIZE_BYTES) {
            host.toast("文件超过1000MB，请使用SFTP上传较大文件", Toast.LENGTH_LONG);
            return;
        }

        String fileName = FilePathUtils.sanitizeFileName(getFileName(context, uri), "uploaded_file");
        String targetPath = state.getCurrentPath();
        preparingUpload = true;
        host.toast("正在准备上传", Toast.LENGTH_SHORT);

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
                if (!host.isActive()) {
                    deleteFileQuietly(finalTempFile);
                    return;
                }
                if (currentUploadHandle != null) {
                    deleteFileQuietly(finalTempFile);
                    host.toast("已有上传任务正在进行", Toast.LENGTH_SHORT);
                    return;
                }
                if (finalCopyError != null) {
                    deleteFileQuietly(finalTempFile);
                    host.toast("准备上传失败: " + finalCopyError.getMessage(), Toast.LENGTH_SHORT);
                    return;
                }
                if (finalTempFile == null || !finalTempFile.exists()) {
                    host.toast("准备上传失败: 文件不存在", Toast.LENGTH_SHORT);
                    return;
                }
                startUpload(appContext, serverId, targetPath, finalTempFile, true, fileName);
            });
        });
    }

    private void handleEditorResult(@NonNull Intent data) {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        String localPath = data.getStringExtra("local_path");
        String remotePath = data.getStringExtra("remote_path");
        int serverId = data.getIntExtra("server_id", -1);
        if (localPath == null || remotePath == null || serverId <= 0) {
            host.toast("保存结果无上传配置", Toast.LENGTH_SHORT);
            return;
        }
        if (preparingUpload || currentUploadHandle != null) {
            host.toast("已有上传任务正在进行", Toast.LENGTH_SHORT);
            return;
        }
        File file = new File(localPath);
        if (!file.exists()) {
            host.toast("本地文件不存在", Toast.LENGTH_SHORT);
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

        if (host.isActive()) {
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
                } else if (host.isActive()) {
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
                } else if (host.isActive()) {
                    dismissUploadDialog();
                    host.toast("上传成功", Toast.LENGTH_SHORT);
                }
                cleanupCurrentUpload(currentUploadDeleteWhenDone);
                if (host.isActive()) {
                    host.clearSelectionAndRender();
                    host.reloadFileList();
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
                } else if (host.isActive()) {
                    dismissUploadDialog();
                    host.toast("上传失败: " + errorMsg, Toast.LENGTH_LONG);
                }
                cleanupCurrentUpload(currentUploadDeleteWhenDone);
            }
        });
    }

    private void showUploadDialog(String fileName) {
        Context context = host.getContextOrNull();
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
            Context context = host.getContextOrNull();
            if (context == null) {
                return;
            }
            if (currentUploadHandle == null) {
                dismissUploadDialog();
                return;
            }
            currentUploadBackgrounded = true;
            dismissUploadDialog();
            TaskQueueNotificationHelper.showUploadProgress(context.getApplicationContext(), currentUploadNotificationId,
                    currentUploadDeviceId, currentUploadFileName, Math.max(currentUploadLastProgress, 0),
                    currentUploadSpeedText, currentUploadLastProgress < 0);
            host.toast("上传已切入后台", Toast.LENGTH_SHORT);
        });
    }

    private void cancelCurrentUpload() {
        if (currentUploadHandle == null) {
            dismissUploadDialog();
            return;
        }
        Context context = host.getContextOrNull();
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
        host.toast("已取消上传", Toast.LENGTH_SHORT);
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
        Context context = host.getContextOrNull();
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

    private void downloadFile(FileItem item, boolean openEditor) {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        if (deviceId <= 0) {
            host.toast(context.getString(R.string.invalid_device_id), Toast.LENGTH_SHORT);
            return;
        }
        if (downloadDialog != null) {
            host.toast("已有下载任务正在进行", Toast.LENGTH_SHORT);
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null);
        final LinearProgressIndicator progressIndicator = dialogView.findViewById(R.id.progress_download);
        final TextView textPercent = dialogView.findViewById(R.id.text_download_percent);
        final androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(openEditor ? R.string.file_action_open : R.string.file_action_download)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        downloadDialog = progressDialog;
        progressDialog.show();

        String remotePath = FilePathUtils.appendPath(state.getCurrentPath(), item.getName());
        File downloadsRoot = context.getExternalFilesDir("downloads");
        File remoteDir = new File(downloadsRoot == null ? context.getCacheDir() : downloadsRoot,
                Integer.toHexString(remotePath.hashCode()));
        File local = new File(remoteDir, FilePathUtils.sanitizeFileName(item.getName(), "downloaded_file"));
        if (local.getParentFile() != null && !local.getParentFile().exists()) {
            boolean mk = local.getParentFile().mkdirs();
            if (!mk && !local.getParentFile().exists()) {
                host.toast(context.getString(R.string.download_failed_format, "无法创建本地目录"), Toast.LENGTH_SHORT);
                dismissDownloadDialog(progressDialog);
                return;
            }
        }

        new FileApi().downloadFileToLocal(context, deviceId, remotePath, local, new FileApi.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                if (!host.isActive()) {
                    return;
                }
                progressIndicator.setIndeterminate(false);
                try {
                    progressIndicator.setProgressCompat(progress, true);
                } catch (Throwable t) {
                    progressIndicator.setProgress(progress);
                }
                textPercent.setText(textPercent.getContext().getString(R.string.percent_format, progress));
            }

            @Override
            public void onSuccess(File file) {
                dismissDownloadDialog(progressDialog);
                if (!host.isActive()) {
                    return;
                }
                if (openEditor) {
                    openInternalEditor(file, remotePath, deviceId, item.getName());
                } else {
                    host.toast("下载完成: " + file.getAbsolutePath(), Toast.LENGTH_LONG);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                dismissDownloadDialog(progressDialog);
                if (!host.isActive()) {
                    return;
                }
                Context activeContext = host.getContextOrNull();
                if (activeContext != null) {
                    host.toast(activeContext.getString(R.string.download_failed_format, errorMsg), Toast.LENGTH_SHORT);
                }
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
            Context context = host.getContextOrNull();
            if (context == null) {
                return;
            }
            Intent intent = new Intent(context, FileEditorActivity.class);
            intent.putExtra("local_path", file.getAbsolutePath());
            intent.putExtra("remote_path", remotePath);
            intent.putExtra("file_name", displayName);
            intent.putExtra("server_id", deviceId);
            editorLauncher.launch(intent);
        } catch (Exception e) {
            Context context = host.getContextOrNull();
            if (context != null && host.isActive()) {
                host.toast(context.getString(R.string.open_editor_failed_format, e.getMessage()), Toast.LENGTH_SHORT);
            }
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
