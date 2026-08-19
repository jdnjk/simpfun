package cn.jdnjk.simpfun.ui.ins.files;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.provider.Settings;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.StoragePermissionHelper;

class LocalFileListController {
    static final int REQUEST_CODE_ALL_FILES_ACCESS = 4001;
    static final int REQUEST_CODE_STORAGE = 4002;

    interface Host {
        Context getContextOrNull();
        boolean isActive();
        void showLoading(boolean show);
        void showError(String message);
        void stopRefreshing();
        void onFileListChanged();
        void startActivityForResult(Intent intent, int requestCode);
        void requestPermissions(String[] permissions, int requestCode);
        void showMessage(String message);
    }

    private final FilePaneState state;
    private final Host host;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    LocalFileListController(FilePaneState state, Host host) {
        this.state = state;
        this.host = host;
    }

    void loadFileList() {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        if (!StoragePermissionHelper.hasLocalStorageAccess(context)) {
            host.stopRefreshing();
            host.showLoading(false);
            host.showError("需要文件访问权限才能浏览本地文件");
            requestStorageAccess();
            return;
        }
        String requestPath = state.getCurrentPath();
        host.showLoading(true);
        executor.execute(() -> {
            try {
                List<FileItem> items = readDirectory(requestPath);
                mainHandler.post(() -> {
                    if (!isCurrentRequest(requestPath)) {
                        return;
                    }
                    host.stopRefreshing();
                    host.showLoading(false);
                    state.replaceFileList(items);
                    state.clearSelection();
                    host.onFileListChanged();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isCurrentRequest(requestPath)) {
                        return;
                    }
                    host.stopRefreshing();
                    host.showLoading(false);
                    host.showError(e.getMessage() == null ? "读取本地目录失败" : e.getMessage());
                });
            }
        });
    }

    /** 从页面上重新发起系统授权弹窗，不跳转系统设置 */
    void requestStorageAccess() {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：系统弹窗只用于 READ_MEDIA_*，完整文件访问需去设置页
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            host.startActivityForResult(intent, REQUEST_CODE_ALL_FILES_ACCESS);
        } else {
            host.requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE
            );
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private List<FileItem> readDirectory(String virtualPath) throws Exception {
        File directory = resolveLocalFile(virtualPath);
        if (!directory.exists()) {
            throw new IllegalArgumentException("目录不存在");
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("不是目录");
        }
        File[] files = directory.listFiles();
        List<File> fileList = new ArrayList<>();
        if (files != null) {
            Collections.addAll(fileList, files);
        }

        List<FileItem> items = new ArrayList<>();
        if (!state.isAtRoot()) {
            items.add(new FileItem(FileItem.PARENT_DIR_NAME, false, 0, "", ""));
        }
        for (File file : fileList) {
            items.add(new FileItem(
                    file.getName(),
                    file.isFile(),
                    file.isFile() ? file.length() : 0L,
                    file.isFile() ? guessMime(file.getName()) : "",
                    dateFormat.format(new Date(file.lastModified()))
            ));
        }
        return items;
    }

    File resolveLocalFile(String virtualPath) throws Exception {
        File root = Environment.getExternalStorageDirectory();
        String rootCanonical = root.getCanonicalPath();
        String safePath = state.getRootPath().equals(virtualPath) ? "" : virtualPath.substring(state.getRootPath().length());
        if (safePath.startsWith("/")) {
            safePath = safePath.substring(1);
        }
        File target = safePath.isEmpty() ? root : new File(root, safePath);
        String targetCanonical = target.getCanonicalPath();
        if (!targetCanonical.equals(rootCanonical) && !targetCanonical.startsWith(rootCanonical + File.separator)) {
            throw new SecurityException("不能访问 /sdcard 之外的路径");
        }
        return target;
    }

    private boolean isCurrentRequest(String requestPath) {
        return host.isActive() && requestPath.equals(state.getCurrentPath());
    }

    private String guessMime(String name) {
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime == null ? "" : mime;
    }
}
