package cn.jdnjk.simpfun.ui.ins.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.TransferListener;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.ui.setting.SftpTransferSettingsManager;
import cn.jdnjk.simpfun.utils.FilePathUtils;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;

class SftpTransferCoordinator {
    private final Activity activity;
    private final int deviceId;
    private final SftpTransferSettingsManager settingsManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private androidx.appcompat.app.AlertDialog progressDialog;
    private TextView fileNameText;
    private TextView statusText;
    private LinearProgressIndicator progressIndicator;
    private ExecutorService coordinatorExecutor;

    SftpTransferCoordinator(Activity activity, int deviceId) {
        this.activity = activity;
        this.deviceId = deviceId;
        this.settingsManager = new SftpTransferSettingsManager(activity);
    }

    void shutdown() {
        cancelled.set(true);
        if (coordinatorExecutor != null) {
            coordinatorExecutor.shutdownNow();
        }
        dismissProgressDialog();
    }

    void copyServerToLocal(FilePaneFragment serverPane, LocalFilePaneFragment localPane, FileItem item, boolean move) {
        copyServerToLocal(serverPane, localPane, java.util.Collections.singletonList(item), move);
    }

    void copyServerToLocal(FilePaneFragment serverPane, LocalFilePaneFragment localPane, List<FileItem> items, boolean move) {
        if (items == null || items.isEmpty()) {
            toast("没有可传输的文件");
            return;
        }
        fetchCredentials(credentials -> startServerToLocalTransfer(credentials, serverPane, localPane, items, move, true));
    }

    void copyLocalToServer(LocalFilePaneFragment localPane, FilePaneFragment serverPane, FileItem item, boolean move) {
        copyLocalToServer(localPane, serverPane, java.util.Collections.singletonList(item), move);
    }

    void copyLocalToServer(LocalFilePaneFragment localPane, FilePaneFragment serverPane, List<FileItem> items, boolean move) {
        if (items == null || items.isEmpty()) {
            toast("没有可传输的文件");
            return;
        }
        fetchCredentials(credentials -> startLocalToServerTransfer(credentials, localPane, serverPane, items, move, true));
    }

    void copyServerToServer(FilePaneFragment sourcePane, FilePaneFragment targetPane, List<FileItem> items, boolean move) {
        if (items == null || items.isEmpty()) {
            toast("没有可传输的文件");
            return;
        }
        fetchCredentials(credentials -> startServerToServerTransfer(credentials, sourcePane, targetPane, items, move, true));
    }

    private void startServerToLocalTransfer(SftpCredentials credentials, FilePaneFragment serverPane, LocalFilePaneFragment localPane, List<FileItem> items, boolean move, boolean allowRetry) {
        try {
            startProgressDialog((move ? "移动 " : "复制 ") + describeItems(items));
            runTransfer(() -> transferServerToLocal(credentials, serverPane, localPane, items, move, () -> {
                serverPane.reloadForHost();
                localPane.reloadForHost();
            }), allowRetry ? () -> retryWithFreshCredentials(fresh -> startServerToLocalTransfer(fresh, serverPane, localPane, items, move, false)) : null);
        } catch (Exception e) {
            toast(e.getMessage() == null ? "准备传输失败" : e.getMessage());
        }
    }

    private void startLocalToServerTransfer(SftpCredentials credentials, LocalFilePaneFragment localPane, FilePaneFragment serverPane, List<FileItem> items, boolean move, boolean allowRetry) {
        try {
            startProgressDialog((move ? "移动 " : "复制 ") + describeItems(items));
            runTransfer(() -> transferLocalToServer(credentials, localPane, serverPane, items, move, () -> {
                serverPane.reloadForHost();
                localPane.reloadForHost();
            }), allowRetry ? () -> retryWithFreshCredentials(fresh -> startLocalToServerTransfer(fresh, localPane, serverPane, items, move, false)) : null);
        } catch (Exception e) {
            toast(e.getMessage() == null ? "准备传输失败" : e.getMessage());
        }
    }

    private void startServerToServerTransfer(SftpCredentials credentials, FilePaneFragment sourcePane, FilePaneFragment targetPane, List<FileItem> items, boolean move, boolean allowRetry) {
        try {
            startProgressDialog((move ? "移动 " : "复制 ") + describeItems(items));
            runTransfer(() -> transferServerToServer(credentials, sourcePane, targetPane, items, move, () -> {
                sourcePane.reloadForHost();
                targetPane.reloadForHost();
            }), allowRetry ? () -> retryWithFreshCredentials(fresh -> startServerToServerTransfer(fresh, sourcePane, targetPane, items, move, false)) : null);
        } catch (Exception e) {
            toast(e.getMessage() == null ? "准备传输失败" : e.getMessage());
        }
    }

    private void runTransfer(TransferRunnable runnable) {
        runTransfer(runnable, null);
    }

    private void runTransfer(TransferRunnable runnable, Runnable onAuthFailure) {
        cancelled.set(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        coordinatorExecutor = executor;
        executor.execute(() -> {
            boolean retry = false;
            try {
                runnable.run();
            } catch (Exception e) {
                retry = onAuthFailure != null && isAuthFailure(e);
                if (!retry) {
                    mainHandler.post(() -> toast(e.getMessage() == null ? "SFTP 传输失败" : e.getMessage()));
                }
            } finally {
                boolean shouldRetry = retry;
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    if (shouldRetry) {
                        onAuthFailure.run();
                    }
                });
                executor.shutdown();
            }
        });
    }

    private void transferServerToLocal(SftpCredentials credentials, FilePaneFragment serverPane, LocalFilePaneFragment localPane, List<FileItem> items, boolean move, Runnable onSuccess) throws Exception {
        List<TransferTask> tasks = new ArrayList<>();
        FileConflictDialog.ConflictAction cachedAction = null;
        try (SftpSession session = openSession(credentials)) {
            for (FileItem item : items) {
                File localTarget = localPane.resolveChildInCurrentPathForHost(item.getName());
                if (localTarget.exists()) {
                    FileConflictDialog.ResolvedAction resolved = resolveConflict(
                            item.getName(), item.getSize(), item.getModifiedAt(),
                            localTarget.length(), FileConflictDialog.formatFileTime(localTarget.lastModified()),
                            items.size() > 1, cachedAction);
                    if (resolved.cachedAction != null) {
                        cachedAction = resolved.cachedAction;
                    }
                    if (resolved.action == FileConflictDialog.ConflictAction.SKIP) {
                        continue;
                    } else if (resolved.action == FileConflictDialog.ConflictAction.KEEP_BOTH) {
                        localTarget = buildUniqueLocalTarget(localTarget);
                    }
                    // REPLACE: 继续使用原 target，后面下载会覆盖
                }
                String remoteSource = serverPane.getItemPathForHost(item);
                if (item.isFile()) {
                    tasks.add(TransferTask.download(remoteSource, localTarget, safeRemoteSize(session.sftp, remoteSource)));
                } else {
                    if (!localTarget.mkdirs() && !localTarget.isDirectory()) {
                        throw new IOException("无法创建目录：" + localTarget.getName());
                    }
                    collectRemoteDownloads(session.sftp, remoteSource, localTarget, tasks);
                }
            }
        }
        runFileTasks(credentials, tasks);
        if (cancelled.get()) {
            return;
        }
        if (move) {
            try (SftpSession session = openSession(credentials)) {
                for (FileItem item : items) {
                    deleteRemoteRecursively(session.sftp, serverPane.getItemPathForHost(item), item.isFile());
                }
            }
        }
        mainHandler.post(onSuccess);
    }

    private void transferLocalToServer(SftpCredentials credentials, LocalFilePaneFragment localPane, FilePaneFragment serverPane, List<FileItem> items, boolean move, Runnable onSuccess) throws Exception {
        List<TransferTask> tasks = new ArrayList<>();
        List<String> remoteDirectories = new ArrayList<>();
        List<File> localSources = new ArrayList<>();
        FileConflictDialog.ConflictAction cachedAction = null;
        try (SftpSession session = openSession(credentials)) {
            for (FileItem item : items) {
                File source = localPane.resolveLocalPathForHost(localPane.getItemPathForHost(item));
                String remoteTarget = FilePathUtils.appendPath(serverPane.getCurrentPathForHost(), item.getName());
                if (remoteExists(session.sftp, remoteTarget)) {
                    FileConflictDialog.ResolvedAction resolved = resolveConflict(
                            item.getName(), item.getSize(), item.getModifiedAt(),
                            -1, "-",
                            items.size() > 1, cachedAction);
                    if (resolved.cachedAction != null) {
                        cachedAction = resolved.cachedAction;
                    }
                    if (resolved.action == FileConflictDialog.ConflictAction.SKIP) {
                        continue;
                    } else if (resolved.action == FileConflictDialog.ConflictAction.KEEP_BOTH) {
                        remoteTarget = buildUniqueRemoteTarget(session.sftp, remoteTarget);
                    }
                    // REPLACE: 上传会覆盖
                }
                localSources.add(source);
                collectLocalUploads(source, remoteTarget, tasks, remoteDirectories);
            }
            for (String directory : remoteDirectories) {
                ensureRemoteDirectory(session.sftp, directory);
            }
        }
        runFileTasks(credentials, tasks);
        if (cancelled.get()) {
            return;
        }
        if (move) {
            for (File source : localSources) {
                deleteLocalRecursively(source);
            }
        }
        mainHandler.post(onSuccess);
        mainHandler.post(() -> toast(move ? "移动完成" : "复制完成"));
    }

    private void transferServerToServer(SftpCredentials credentials, FilePaneFragment sourcePane, FilePaneFragment targetPane, List<FileItem> items, boolean move, Runnable onSuccess) throws Exception {
        android.util.Log.d("SftpTransfer", "transferServerToServer: items=" + items.size() + ", move=" + move);
        FileConflictDialog.ConflictAction cachedAction = null;
        List<ServerTransferAction> actions = new ArrayList<>();

        // 预扫描冲突并构建操作列表
        try (SftpSession session = openSession(credentials)) {
            for (FileItem item : items) {
                String remoteSource = sourcePane.getItemPathForHost(item);
                String remoteTarget = FilePathUtils.appendPath(targetPane.getCurrentPathForHost(), item.getName());
                boolean conflict = remoteExists(session.sftp, remoteTarget);
                android.util.Log.d("SftpTransfer", "检查: " + item.getName() + " -> " + remoteTarget + ", 冲突=" + conflict);

                if (conflict) {
                    FileConflictDialog.ResolvedAction resolved = resolveConflict(
                            item.getName(), item.getSize(), item.getModifiedAt(),
                            -1, "-",
                            items.size() > 1, cachedAction);
                    if (resolved.cachedAction != null) {
                        cachedAction = resolved.cachedAction;
                    }
                    if (resolved.action == FileConflictDialog.ConflictAction.SKIP) {
                        android.util.Log.d("SftpTransfer", "跳过: " + item.getName());
                        continue;
                    }
                    actions.add(new ServerTransferAction(remoteSource, remoteTarget, item.isFile(),
                            resolved.action == FileConflictDialog.ConflictAction.REPLACE));
                } else {
                    actions.add(new ServerTransferAction(remoteSource, remoteTarget, item.isFile(), false));
                }
            }
        }

        android.util.Log.d("SftpTransfer", "操作数量: " + actions.size() + ", cancelled=" + cancelled.get());
        if (actions.isEmpty() || cancelled.get()) {
            return;
        }

        Context context = activity;
        int deviceId = sourcePane.getDeviceIdForHost(context);
        int threadCount = Math.min(settingsManager.getThreadCount(), actions.size());
        android.util.Log.d("SftpTransfer", "deviceId=" + deviceId + ", 操作数=" + actions.size() + ", 线程数=" + threadCount);

        // 预统计总文件数（用于进度显示）
        int totalFileCount = 0;
        try (SftpSession session = openSession(credentials)) {
            for (ServerTransferAction action : actions) {
                if (action.isFile) {
                    totalFileCount++;
                } else {
                    totalFileCount += countRemoteFiles(session.sftp, action.source);
                }
            }
        }
        final int totalFiles = Math.max(totalFileCount, 1);
        updateProgress(0, totalFiles, 0, 0, 0);

        // 分离文件操作和文件夹操作
        List<ServerTransferAction> fileActions = new ArrayList<>();
        List<ServerTransferAction> folderActions = new ArrayList<>();
        for (ServerTransferAction action : actions) {
            if (action.isFile || move) {
                fileActions.add(action);
            } else {
                folderActions.add(action);
            }
        }

        final AtomicInteger completedFiles = new AtomicInteger(0);
        final long startedAt = System.currentTimeMillis();
        final AtomicReference<String> firstError = new AtomicReference<>();
        final AtomicBoolean hasError = new AtomicBoolean(false);

        // 文件操作：多线程并发
        if (!fileActions.isEmpty() && !cancelled.get()) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            for (ServerTransferAction action : fileActions) {
                if (cancelled.get()) break;
                futures.add(executor.submit(() -> {
                    if (cancelled.get() || hasError.get()) return;
                    try {
                        String itemName = action.source.substring(action.source.lastIndexOf('/') + 1);
                        android.util.Log.d("SftpTransfer", "执行: " + (move ? "移动" : "复制") + " " + itemName);

                        if (move) {
                            if (action.replace) apiDeleteSync(context, deviceId, action.target);
                            apiMoveSync(context, deviceId, action.source, action.target);
                        } else {
                            if (action.replace) apiDeleteSync(context, deviceId, action.target);
                            apiCopyFileSync(context, deviceId, action.source, action.target);
                        }
                        int completed = completedFiles.incrementAndGet();
                        updateProgress(completed, totalFiles, completed, totalFiles,
                                System.currentTimeMillis() - startedAt);
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e.getMessage());
                        hasError.set(true);
                        cancelled.set(true);
                    }
                }));
            }
            executor.shutdown();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {}
            }
        }

        // 文件夹操作：逐个执行（内部已有多线程文件复制）
        if (!folderActions.isEmpty() && !cancelled.get() && !hasError.get()) {
            try (SftpSession session = openSession(credentials)) {
                for (ServerTransferAction action : folderActions) {
                    if (cancelled.get() || hasError.get()) break;
                    try {
                        String itemName = action.source.substring(action.source.lastIndexOf('/') + 1);
                        android.util.Log.d("SftpTransfer", "执行文件夹复制: " + itemName);
                        if (action.replace) apiDeleteSync(context, deviceId, action.target);
                        apiCopyFolderWithProgress(context, deviceId, session.sftp, action.source, action.target,
                                totalFiles, completedFiles, startedAt);
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e.getMessage());
                        hasError.set(true);
                    }
                }
            }
        }

        String error = firstError.get();
        if (error != null) throw new IOException(error);
        if (cancelled.get()) return;
        mainHandler.post(onSuccess);
        mainHandler.post(() -> toast(move ? "移动完成" : "复制完成"));
    }

    private void apiMove(Context context, int deviceId, ServerTransferAction action,
                         CountDownLatch latch, AtomicReference<String> firstError) {
        new FileApi().moveFileOrFolder(context, deviceId,
                new org.json.JSONArray(java.util.Collections.singletonList(action.source)).toString(),
                parentPath(action.target),
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        firstError.compareAndSet(null, "移动失败: " + errorMsg);
                        latch.countDown();
                    }
                });
    }

    private void apiDeleteThenMove(Context context, int deviceId, ServerTransferAction action,
                                   CountDownLatch latch, AtomicReference<String> firstError) {
        new FileApi().deleteFileOrFolderBatch(context, deviceId,
                java.util.Collections.singletonList(action.target),
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        apiMove(context, deviceId, action, latch, firstError);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        firstError.compareAndSet(null, "删除目标失败: " + errorMsg);
                        latch.countDown();
                    }
                });
    }

    private void apiCopy(Context context, int deviceId, ServerTransferAction action,
                         CountDownLatch latch, AtomicReference<String> firstError) {
        // 1. 创建副本 (start.sh → start copy.sh)
        new FileApi().copyFileOrFolder(context, deviceId, action.source,
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        // 副本名称：文件 → "name copy.ext"，隐藏文件 → "copy.name"
                        String sourceName = action.source.substring(action.source.lastIndexOf('/') + 1);
                        String copyName = buildApiCopyName(sourceName);
                        String copyPath = parentPath(action.source) + "/" + copyName;

                        if (action.target.equals(copyPath)) {
                            // 目标就是副本名称（KEEP_BOTH），完成
                            latch.countDown();
                            return;
                        }

                        if (action.replace) {
                            // REPLACE: 删除目标，然后移动副本到目标
                            apiDeleteThenRename(context, deviceId, copyPath, action.target, latch, firstError);
                        } else {
                            // 普通复制：移动副本到目标目录并重命名
                            apiRename(context, deviceId, copyPath, action.target, latch, firstError);
                        }
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        firstError.compareAndSet(null, "复制失败: " + errorMsg);
                        latch.countDown();
                    }
                });
    }

    private void apiDeleteThenRename(Context context, int deviceId, String source, String target,
                                     CountDownLatch latch, AtomicReference<String> firstError) {
        new FileApi().deleteFileOrFolderBatch(context, deviceId,
                java.util.Collections.singletonList(target),
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        apiRename(context, deviceId, source, target, latch, firstError);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        firstError.compareAndSet(null, "删除目标失败: " + errorMsg);
                        latch.countDown();
                    }
                });
    }

    private void apiRename(Context context, int deviceId, String origin, String target,
                           CountDownLatch latch, AtomicReference<String> firstError) {
        new FileApi().renameFile(context, deviceId, origin, target,
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        firstError.compareAndSet(null, "重命名失败: " + errorMsg);
                        latch.countDown();
                    }
                });
    }

    // ===== 同步版本的 API 方法（逐个等待完成） =====

    private static final long API_TIMEOUT_SECONDS = 60;

    /**
     * 等待 CountDownLatch，超时后弹出 MD3 对话框询问跳过还是继续等待。
     * @return true 如果正常完成，false 如果用户选择跳过
     */
    private boolean awaitLatchWithTimeoutDialog(CountDownLatch latch, String operationName) throws InterruptedException {
        while (true) {
            boolean completed = latch.await(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (completed) {
                return true; // 正常完成
            }
            // 超时，弹出对话框询问用户
            android.util.Log.w("SftpTransfer", "API 超时: " + operationName);
            AtomicReference<Boolean> userChoice = new AtomicReference<>(null);
            CountDownLatch dialogLatch = new CountDownLatch(1);
            mainHandler.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    userChoice.set(false);
                    dialogLatch.countDown();
                    return;
                }
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("请求超时")
                        .setMessage(operationName + " 已超过 " + API_TIMEOUT_SECONDS + " 秒未响应")
                        .setPositiveButton("继续等待", (d, w) -> {
                            userChoice.set(true);
                            dialogLatch.countDown();
                        })
                        .setNegativeButton("跳过", (d, w) -> {
                            userChoice.set(false);
                            dialogLatch.countDown();
                        })
                        .setCancelable(false)
                        .show();
            });
            dialogLatch.await();
            if (!userChoice.get()) {
                return false; // 用户选择跳过
            }
            // 用户选择继续等待，循环再等 60 秒
        }
    }

    private void apiDeleteSync(Context context, int deviceId, String target) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        new FileApi().deleteFileOrFolderBatch(context, deviceId,
                java.util.Collections.singletonList(target),
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        android.util.Log.d("SftpTransfer", "删除成功: " + target);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        android.util.Log.e("SftpTransfer", "删除失败: " + target + " - " + errorMsg);
                        error.compareAndSet(null, "删除失败: " + errorMsg);
                        latch.countDown();
                    }
                });
        if (!awaitLatchWithTimeoutDialog(latch, "删除 " + target)) {
            return; // 用户跳过
        }
        if (error.get() != null) throw new IOException(error.get());
    }

    private void apiMoveSync(Context context, int deviceId, String source, String target) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        new FileApi().moveFileOrFolder(context, deviceId,
                new org.json.JSONArray(java.util.Collections.singletonList(source)).toString(),
                parentPath(target),
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        android.util.Log.d("SftpTransfer", "移动成功: " + source + " -> " + target);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        android.util.Log.e("SftpTransfer", "移动失败: " + source + " - " + errorMsg);
                        error.compareAndSet(null, "移动失败: " + errorMsg);
                        latch.countDown();
                    }
                });
        if (!awaitLatchWithTimeoutDialog(latch, "移动 " + source)) {
            return;
        }
        if (error.get() != null) throw new IOException(error.get());
    }

    private void apiCopyFileSync(Context context, int deviceId, String source, String target) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        new FileApi().copyFileOrFolder(context, deviceId, source,
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        String sourceName = source.substring(source.lastIndexOf('/') + 1);
                        String copyName = buildApiCopyName(sourceName);
                        String copyPath = parentPath(source) + "/" + copyName;
                        android.util.Log.d("SftpTransfer", "副本创建成功: " + copyPath);

                        // rename 副本到目标位置
                        new FileApi().renameFile(context, deviceId, copyPath, target,
                                new FileApi.Callback() {
                                    @Override
                                    public void onSuccess(JSONObject data) {
                                        android.util.Log.d("SftpTransfer", "重命名成功: " + copyPath + " -> " + target);
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onFailure(String errorMsg) {
                                        android.util.Log.e("SftpTransfer", "重命名失败: " + copyPath + " - " + errorMsg);
                                        error.compareAndSet(null, "重命名失败: " + errorMsg);
                                        latch.countDown();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        android.util.Log.e("SftpTransfer", "复制失败: " + source + " - " + errorMsg);
                        error.compareAndSet(null, "复制失败: " + errorMsg);
                        latch.countDown();
                    }
                });

        if (!awaitLatchWithTimeoutDialog(latch, "复制 " + source)) {
            return;
        }
        if (error.get() != null) throw new IOException(error.get());
    }

    /**
     * 通过 API 复制文件夹：遍历文件，逐个 copyFileOrFolder + rename。
     * 使用 CountDownLatch 逐个同步，避免并发问题。
     */
    private void apiCopyFolder(Context context, int deviceId, SFTPClient sftp,
                               String sourceDir, String targetDir) throws Exception {
        // 1. 收集所有文件和目录
        List<String> directories = new ArrayList<>();
        List<String[]> files = new ArrayList<>(); // [sourcePath, targetPath]
        collectFolderStructure(sftp, sourceDir, targetDir, directories, files);

        // 2. 创建目标目录结构（同步等待）
        for (String dir : directories) {
            if (cancelled.get()) return;
            String dirName = dir.substring(dir.lastIndexOf('/') + 1);
            String dirParent = parentPath(dir);
            CountDownLatch dirLatch = new CountDownLatch(1);
            new FileApi().createFileOrFolder(context, deviceId, "folder", dirParent, dirName,
                    new FileApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            dirLatch.countDown();
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            // 目录可能已存在，忽略
                            dirLatch.countDown();
                        }
                    });
            if (!awaitLatchWithTimeoutDialog(dirLatch, "创建目录 " + dirName)) {
                return; // 用户跳过
            }
        }

        // 3. 逐个复制文件
        for (String[] filePair : files) {
            if (cancelled.get()) return;
            String sourcePath = filePair[0];
            String targetPath = filePair[1];
            apiCopyFileToTarget(context, deviceId, sourcePath, targetPath);
        }
    }

    /**
     * 带进度回调的文件夹复制。
     */
    private void apiCopyFolderWithProgress(Context context, int deviceId, SFTPClient sftp,
                                           String sourceDir, String targetDir,
                                           int totalFiles, AtomicInteger completedFiles, long startedAt) throws Exception {
        List<String> directories = new ArrayList<>();
        List<String[]> files = new ArrayList<>();
        collectFolderStructure(sftp, sourceDir, targetDir, directories, files);

        // 创建目标目录结构
        for (String dir : directories) {
            if (cancelled.get()) return;
            String dirName = dir.substring(dir.lastIndexOf('/') + 1);
            String dirParent = parentPath(dir);
            CountDownLatch dirLatch = new CountDownLatch(1);
            new FileApi().createFileOrFolder(context, deviceId, "folder", dirParent, dirName,
                    new FileApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            dirLatch.countDown();
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            dirLatch.countDown();
                        }
                    });
            if (!awaitLatchWithTimeoutDialog(dirLatch, "创建目录 " + dirName)) {
                return; // 用户跳过
            }
        }

        // 逐个复制文件，更新进度
        for (String[] filePair : files) {
            if (cancelled.get()) return;
            apiCopyFileToTarget(context, deviceId, filePair[0], filePair[1]);
            int completed = completedFiles.incrementAndGet();
            updateProgress(completed, totalFiles, completed, totalFiles,
                    System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * 统计远程目录中的文件数量（递归）。
     */
    private int countRemoteFiles(SFTPClient sftp, String remoteDir) throws IOException {
        int count = 0;
        for (RemoteResourceInfo info : sftp.ls(remoteDir)) {
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            if (info.isDirectory()) {
                count += countRemoteFiles(sftp, FilePathUtils.appendPath(remoteDir, name));
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * 通过 API 复制单个文件到目标位置：copyFileOrFolder 产生副本 → rename 到目标。
     */
    private void apiCopyFileToTarget(Context context, int deviceId,
                                     String sourcePath, String targetPath) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        new FileApi().copyFileOrFolder(context, deviceId, sourcePath,
                new FileApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        String sourceName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
                        String copyName = buildApiCopyName(sourceName);
                        String copyPath = parentPath(sourcePath) + "/" + copyName;

                        // rename 副本到目标位置
                        new FileApi().renameFile(context, deviceId, copyPath, targetPath,
                                new FileApi.Callback() {
                                    @Override
                                    public void onSuccess(JSONObject data) {
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onFailure(String errorMsg) {
                                        error.compareAndSet(null, "重命名失败: " + errorMsg);
                                        latch.countDown();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        error.compareAndSet(null, "复制失败: " + errorMsg);
                        latch.countDown();
                    }
                });

        if (!awaitLatchWithTimeoutDialog(latch, "复制 " + sourcePath.substring(sourcePath.lastIndexOf('/') + 1))) {
            return;
        }
        String err = error.get();
        if (err != null) throw new IOException(err);
    }

    /**
     * 递归收集文件夹结构。
     */
    private void collectFolderStructure(SFTPClient sftp, String sourceDir, String targetDir,
                                        List<String> directories, List<String[]> files) throws IOException {
        if (cancelled.get()) return;
        directories.add(targetDir);
        for (RemoteResourceInfo info : sftp.ls(sourceDir)) {
            if (cancelled.get()) return;
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            String sourceChild = FilePathUtils.appendPath(sourceDir, name);
            String targetChild = FilePathUtils.appendPath(targetDir, name);
            if (info.isDirectory()) {
                collectFolderStructure(sftp, sourceChild, targetChild, directories, files);
            } else {
                files.add(new String[]{sourceChild, targetChild});
            }
        }
    }

    /**
     * 根据 API 副本命名规则生成副本文件名。
     * 文件: start.sh → start copy.sh, .bashrc → " copy.bashrc"（前导空格）
     */
    private static String buildApiCopyName(String name) {
        if (name.startsWith(".")) {
            // 隐藏文件: .bashrc → " copy.bashrc"（前导空格）
            return " copy." + name.substring(1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot) + " copy" + name.substring(dot);
        }
        return name + " copy";
    }

    private static String parentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash > 0 ? path.substring(0, lastSlash) : "/";
    }

    private static class ServerTransferAction {
        final String source;
        final String target;
        final boolean isFile;
        final boolean replace;

        ServerTransferAction(String source, String target, boolean isFile, boolean replace) {
            this.source = source;
            this.target = target;
            this.isFile = isFile;
            this.replace = replace;
        }
    }

    private String describeItems(List<FileItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.size() == 1 ? items.get(0).getName() : items.size() + " 项";
    }

    /**
     * 解析文件冲突，使用阻塞对话框等待用户选择。
     * @param cachedAction 如果非 null，直接使用此决策而不弹窗
     * @return 解析结果，包含动作和（可能更新的）缓存动作
     */
    private FileConflictDialog.ResolvedAction resolveConflict(String fileName, long incomingSize, String incomingTime,
                                                               long existingSize, String existingTime,
                                                               boolean showApplyAll,
                                                               FileConflictDialog.ConflictAction cachedAction) {
        if (cachedAction != null) {
            return new FileConflictDialog.ResolvedAction(cachedAction, cachedAction);
        }
        FileConflictDialog dialog = new FileConflictDialog(activity);
        FileConflictDialog.ConflictResult result = dialog.showBlocking(
                fileName, incomingSize, incomingTime, existingSize, existingTime, showApplyAll);
        FileConflictDialog.ConflictAction newCache = result.applyToAll ? result.action : null;
        return new FileConflictDialog.ResolvedAction(result.action, newCache);
    }

    private File buildUniqueLocalTarget(File target) {
        String name = target.getName();
        String base = name;
        String extension = "";
        if (target.isFile()) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                base = name.substring(0, dot);
                extension = name.substring(dot);
            }
        }
        File parent = target.getParentFile();
        for (int i = 1; i < 1000; i++) {
            String suffix = i == 1 ? " - 副本" : " - 副本 (" + i + ")";
            File candidate = new File(parent, base + suffix + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return target;
    }

    private String buildUniqueRemoteTarget(SFTPClient sftp, String remoteTarget) throws IOException {
        int lastSlash = remoteTarget.lastIndexOf('/');
        String parent = lastSlash > 0 ? remoteTarget.substring(0, lastSlash) : "/";
        String name = lastSlash >= 0 ? remoteTarget.substring(lastSlash + 1) : remoteTarget;
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            String suffix = i == 1 ? " - 副本" : " - 副本 (" + i + ")";
            String candidate = parent + "/" + base + suffix + extension;
            if (!remoteExists(sftp, candidate)) {
                return candidate;
            }
        }
        return remoteTarget;
    }

    private void runFileTasks(SftpCredentials credentials, List<TransferTask> tasks) throws Exception {
        if (tasks.isEmpty()) {
            updateProgress(0, 0, 0, 0, 0);
            return;
        }
        int threadCount = Math.min(settingsManager.getThreadCount(), tasks.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            AtomicInteger nextTaskIndex = new AtomicInteger(0);
            AtomicInteger completedFiles = new AtomicInteger(0);
            AtomicLong transferredBytes = new AtomicLong(0L);
            AtomicLong lastProgressUpdateAt = new AtomicLong(0L);
            AtomicReference<Exception> firstError = new AtomicReference<>();
            long totalBytes = totalTaskSize(tasks);
            long startedAt = System.currentTimeMillis();
            TransferProgress progress = new TransferProgress(tasks.size(), totalBytes, startedAt, completedFiles, transferredBytes, lastProgressUpdateAt);
            List<Future<?>> futures = new ArrayList<>();
            updateProgress(0, tasks.size(), 0, totalBytes, 0);
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try (SftpSession session = openSession(credentials)) {
                        while (!cancelled.get()) {
                            int taskIndex = nextTaskIndex.getAndIncrement();
                            if (taskIndex >= tasks.size()) {
                                return;
                            }
                            TransferTask task = tasks.get(taskIndex);
                            transferSingleFile(session, task, progress);
                            progress.finishFile(task);
                        }
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e);
                        cancelled.set(true);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            Exception error = firstError.get();
            if (error != null) {
                throw error;
            }
        }
    }

    private long totalTaskSize(List<TransferTask> tasks) {
        long total = 0L;
        for (TransferTask task : tasks) {
            total += Math.max(0L, task.size);
        }
        return total;
    }

    private long totalRemoteCopySize(List<RemoteCopyTask> tasks) {
        long total = 0L;
        for (RemoteCopyTask task : tasks) {
            total += Math.max(0L, task.size);
        }
        return total;
    }

    private void transferSingleFile(SftpSession session, TransferTask task, TransferProgress progress) throws IOException {
        TransferListener previousListener = session.sftp.getFileTransfer().getTransferListener();
        try {
            session.sftp.getFileTransfer().setTransferListener(progress.createListener(task));
            if (task.download) {
                File parent = task.localFile.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("无法创建目录：" + parent.getName());
                }
                session.sftp.get(task.remotePath, task.localFile.getAbsolutePath());
                return;
            }
            ensureRemoteDirectory(session.sftp, remoteParent(task.remotePath));
            session.sftp.put(task.localFile.getAbsolutePath(), task.remotePath);
        } finally {
            session.sftp.getFileTransfer().setTransferListener(previousListener);
        }
    }

    private void runRemoteCopyTasks(SftpCredentials credentials, List<RemoteCopyTask> tasks) throws Exception {
        if (tasks.isEmpty()) {
            updateProgress(0, 0, 0, 0, 0);
            return;
        }
        int threadCount = Math.min(settingsManager.getThreadCount(), tasks.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            AtomicInteger nextTaskIndex = new AtomicInteger(0);
            AtomicInteger completedFiles = new AtomicInteger(0);
            AtomicLong transferredBytes = new AtomicLong(0L);
            AtomicLong lastProgressUpdateAt = new AtomicLong(0L);
            AtomicReference<Exception> firstError = new AtomicReference<>();
            long totalBytes = totalRemoteCopySize(tasks);
            long startedAt = System.currentTimeMillis();
            TransferProgress progress = new TransferProgress(tasks.size(), totalBytes, startedAt, completedFiles, transferredBytes, lastProgressUpdateAt);
            List<Future<?>> futures = new ArrayList<>();
            updateProgress(0, tasks.size(), 0, totalBytes, 0);
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try (SftpSession session = openSession(credentials)) {
                        while (!cancelled.get()) {
                            int taskIndex = nextTaskIndex.getAndIncrement();
                            if (taskIndex >= tasks.size()) {
                                return;
                            }
                            RemoteCopyTask task = tasks.get(taskIndex);
                            copyRemoteFile(session.sftp, task, progress);
                            progress.finishRemoteFile(task);
                        }
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e);
                        cancelled.set(true);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            Exception error = firstError.get();
            if (error != null) {
                throw error;
            }
        }
    }

    private void copyRemoteFile(SFTPClient sftp, RemoteCopyTask task, TransferProgress progress) throws IOException {
        ensureRemoteDirectory(sftp, remoteParent(task.targetPath));
        byte[] buffer = new byte[64 * 1024];
        long offset = 0L;
        try (RemoteFile source = sftp.open(task.sourcePath, EnumSet.of(OpenMode.READ));
             RemoteFile target = sftp.open(task.targetPath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))) {
            while (!cancelled.get()) {
                int read = source.read(offset, buffer, 0, buffer.length);
                if (read <= 0) {
                    return;
                }
                target.write(offset, buffer, 0, read);
                offset += read;
                if (progress != null) {
                    progress.reportRemoteFileProgress(task, offset);
                }
            }
        }
    }

    private void collectRemoteDownloads(SFTPClient sftp, String remoteDirectory, File localDirectory, List<TransferTask> tasks) throws IOException {
        for (RemoteResourceInfo info : sftp.ls(remoteDirectory)) {
            if (cancelled.get()) {
                return;
            }
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String remoteChild = FilePathUtils.appendPath(remoteDirectory, name);
            File localChild = new File(localDirectory, name);
            if (info.isDirectory()) {
                if (!localChild.mkdirs() && !localChild.isDirectory()) {
                    throw new IOException("无法创建目录：" + localChild.getName());
                }
                collectRemoteDownloads(sftp, remoteChild, localChild, tasks);
            } else {
                tasks.add(TransferTask.download(remoteChild, localChild, info.getAttributes().getSize()));
            }
        }
    }

    private void collectRemoteCopies(SFTPClient sftp, String sourceDirectory, String targetDirectory, List<RemoteCopyTask> tasks) throws IOException {
        for (RemoteResourceInfo info : sftp.ls(sourceDirectory)) {
            if (cancelled.get()) {
                return;
            }
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String sourceChild = FilePathUtils.appendPath(sourceDirectory, name);
            String targetChild = FilePathUtils.appendPath(targetDirectory, name);
            if (info.isDirectory()) {
                ensureRemoteDirectory(sftp, targetChild);
                collectRemoteCopies(sftp, sourceChild, targetChild, tasks);
            } else {
                tasks.add(new RemoteCopyTask(sourceChild, targetChild, info.getAttributes().getSize()));
            }
        }
    }

    private void collectLocalUploads(File localSource, String remoteTarget, List<TransferTask> tasks, List<String> remoteDirectories) {
        if (cancelled.get()) {
            return;
        }
        if (localSource.isDirectory()) {
            remoteDirectories.add(remoteTarget);
            File[] children = localSource.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectLocalUploads(child, FilePathUtils.appendPath(remoteTarget, child.getName()), tasks, remoteDirectories);
                }
            }
        } else {
            tasks.add(TransferTask.upload(localSource, remoteTarget, localSource.length()));
        }
    }

    private void deleteRemoteRecursively(SFTPClient sftp, String remotePath, boolean file) throws IOException {
        if (file) {
            sftp.rm(remotePath);
            return;
        }
        for (RemoteResourceInfo info : sftp.ls(remotePath)) {
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String child = FilePathUtils.appendPath(remotePath, name);
            if (info.isDirectory()) {
                deleteRemoteRecursively(sftp, child, false);
            } else {
                sftp.rm(child);
            }
        }
        sftp.rmdir(remotePath);
    }

    private void deleteLocalRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteLocalRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("无法删除：" + file.getName());
        }
    }

    private long safeRemoteSize(SFTPClient sftp, String remotePath) {
        try {
            return sftp.stat(remotePath).getSize();
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean remoteExists(SFTPClient sftp, String remotePath) {
        try {
            return sftp.statExistence(remotePath) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureRemoteDirectory(SFTPClient sftp, String path) {
        String safePath = FilePathUtils.sanitizePath(path);
        if ("/".equals(safePath)) {
            return;
        }
        String[] parts = safePath.split("/");
        String current = "";
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            current += "/" + part;
            try {
                sftp.mkdir(current);
            } catch (IOException ignored) {
            }
        }
    }

    private String remoteParent(String path) {
        return FilePathUtils.getParentPath(path);
    }

    private SftpSession openSession(SftpCredentials credentials) throws IOException {
        ensureBouncyCastleRegistered();
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(credentials.host, credentials.port);
        ssh.authPassword(credentials.username, credentials.password);
        return new SftpSession(ssh, ssh.newSFTPClient());
    }

    private boolean isAuthFailure(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof UserAuthException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("auth")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void fetchCredentials(CredentialsCallback callback) {
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(activity).getValid(String.valueOf(deviceId));
        if (cached != null) {
            callback.onCredentials(SftpCredentials.fromCached(cached));
            return;
        }
        fetchFreshCredentials(callback, "获取 SFTP 信息失败: ");
    }

    private void fetchFreshCredentials(CredentialsCallback callback, String errorPrefix) {
        String token = token();
        if (token.isEmpty()) {
            toast("未登录");
            return;
        }
        new MainApi(activity).getSftp(token, String.valueOf(deviceId), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                SftpCredentials credentials = SftpCredentials.fromApiJson(data);
                if (credentials == null) {
                    toast("SFTP 信息无效");
                    return;
                }
                callback.onCredentials(credentials);
            }

            @Override
            public void onFailure(String errorMsg) {
                toast(errorPrefix + errorMsg);
            }
        });
    }

    private void retryWithFreshCredentials(CredentialsCallback callback) {
        fetchFreshCredentials(callback, "缓存密码失效，重新获取 SFTP 信息失败: ");
    }

    static void ensureBouncyCastleRegistered() {
        try {
            java.security.Provider bc = java.security.Security.getProvider("BC");
            if (bc == null || !bc.getClass().getName().equals("org.bouncycastle.jce.provider.BouncyCastleProvider")) {
                java.security.Security.removeProvider("BC");
                java.security.Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);
            }
        } catch (Exception ignored) {
        }
    }

    private String token() {
        SharedPreferences sp = activity.getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", "");
    }

    private void startProgressDialog(String title) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_upload_progress, null, false);
        fileNameText = view.findViewById(R.id.text_upload_file_name);
        statusText = view.findViewById(R.id.text_upload_status);
        progressIndicator = view.findViewById(R.id.progress_upload);
        View backgroundButton = view.findViewById(R.id.button_upload_background);
        View cancelButton = view.findViewById(R.id.button_upload_cancel);
        if (backgroundButton != null) {
            backgroundButton.setVisibility(View.GONE);
        }
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                cancelled.set(true);
                dismissProgressDialog();
            });
        }
        fileNameText.setText(title);
        statusText.setText("准备传输");
        progressIndicator.setIndeterminate(true);
        progressDialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void updateProgress(int completed, int total, long bytes, long totalBytes, long elapsedMillis) {
        mainHandler.post(() -> {
            if (statusText == null || progressIndicator == null) {
                return;
            }
            if (total <= 0) {
                progressIndicator.setIndeterminate(true);
                statusText.setText("正在统计文件...");
                return;
            }
            progressIndicator.setIndeterminate(false);
            int progress = totalBytes > 0L
                    ? (int) Math.min(100L, Math.max(0L, bytes) * 100L / totalBytes)
                    : Math.min(100, completed * 100 / total);
            progressIndicator.setProgress(progress);
            String speed = elapsedMillis <= 0 ? "0 B/s" : formatSize(bytes * 1000L / elapsedMillis) + "/s";
            String status = String.format(Locale.getDefault(), "已完成 %d/%d  %s", completed, total, speed);
            String eta = formatRemainingTime(completed, total, bytes, totalBytes, elapsedMillis);
            if (eta != null) {
                status += "  剩余 " + eta;
            }
            statusText.setText(status);
        });
    }

    private String formatRemainingTime(int completed, int total, long bytes, long totalBytes, long elapsedMillis) {
        if (elapsedMillis <= 0L || completed >= total) {
            return null;
        }
        long remainingMillis;
        if (totalBytes > 0L && bytes > 0L && bytes < totalBytes) {
            remainingMillis = (long) ((totalBytes - bytes) * (double) elapsedMillis / bytes);
        } else if (completed > 0 && completed < total) {
            remainingMillis = (long) ((total - completed) * (double) elapsedMillis / completed);
        } else {
            return null;
        }
        return formatDuration(remainingMillis);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = Math.min(99L, seconds / 3600L);
        long minutes = Math.min(60L, (seconds % 3600L) / 60L);
        long remainingSeconds = Math.min(60L, seconds % 60L);
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
        fileNameText = null;
        statusText = null;
        progressIndicator = null;
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    static String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int group = (int) (Math.log10(size) / Math.log10(1024));
        group = Math.min(group, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, group), units[group]);
    }

    private interface TransferRunnable {
        void run() throws Exception;
    }

    private interface CredentialsCallback {
        void onCredentials(SftpCredentials credentials);
    }

    private static class SftpCredentials {
        final String host;
        final int port;
        final String username;
        final String password;

        SftpCredentials(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        static SftpCredentials fromCached(SftpCredentialStore.Credential credential) {
            return new SftpCredentials(credential.host, credential.port, credential.username, credential.password);
        }

        static SftpCredentials fromApiJson(JSONObject data) {
            JSONObject sftp = data == null ? null : data.optJSONObject("data");
            if (sftp == null) {
                sftp = data;
            }
            if (sftp == null) {
                return null;
            }
            String host = sftp.optString("ip", "").trim();
            String username = sftp.optString("user_name", "").trim();
            String password = sftp.optString("password", "");
            if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
                return null;
            }
            try {
                return new SftpCredentials(host, Integer.parseInt(sftp.optString("port", "22")), username, password);
            } catch (NumberFormatException e) {
                return new SftpCredentials(host, 22, username, password);
            }
        }
    }

    private static class SftpSession implements AutoCloseable {
        final SSHClient ssh;
        final SFTPClient sftp;

        SftpSession(SSHClient ssh, SFTPClient sftp) {
            this.ssh = ssh;
            this.sftp = sftp;
        }

        @Override
        public void close() throws IOException {
            sftp.close();
            ssh.disconnect();
        }
    }

    private class TransferProgress {
        private final int totalFiles;
        private final long totalBytes;
        private final long startedAt;
        private final AtomicInteger completedFiles;
        private final AtomicLong transferredBytes;
        private final AtomicLong lastProgressUpdateAt;

        TransferProgress(int totalFiles, long totalBytes, long startedAt, AtomicInteger completedFiles,
                AtomicLong transferredBytes, AtomicLong lastProgressUpdateAt) {
            this.totalFiles = totalFiles;
            this.totalBytes = totalBytes;
            this.startedAt = startedAt;
            this.completedFiles = completedFiles;
            this.transferredBytes = transferredBytes;
            this.lastProgressUpdateAt = lastProgressUpdateAt;
        }

        TransferListener createListener(TransferTask task) {
            return new TransferListener() {
                @Override
                public TransferListener directory(String name) {
                    return this;
                }

                @Override
                public StreamCopier.Listener file(String name, long size) {
                    return transferred -> reportFileProgress(task, transferred);
                }
            };
        }

        void finishFile(TransferTask task) {
            long knownSize = Math.max(0L, task.size);
            long reported = task.reportedBytes.get();
            if (knownSize > reported && task.reportedBytes.compareAndSet(reported, knownSize)) {
                transferredBytes.addAndGet(knownSize - reported);
            }
            completedFiles.incrementAndGet();
            publishProgress(true);
        }

        private void reportFileProgress(TransferTask task, long transferred) {
            reportProgress(task.size, task.reportedBytes, transferred, false);
        }

        void reportRemoteFileProgress(RemoteCopyTask task, long transferred) {
            reportProgress(task.size, task.reportedBytes, transferred, false);
        }

        void finishRemoteFile(RemoteCopyTask task) {
            reportProgress(task.size, task.reportedBytes, task.size, true);
            completedFiles.incrementAndGet();
            publishProgress(true);
        }

        private void reportProgress(long size, AtomicLong reportedBytesForTask, long transferred, boolean force) {
            long normalized = Math.max(0L, transferred);
            if (size > 0L) {
                normalized = Math.min(normalized, size);
            }
            while (true) {
                long previous = reportedBytesForTask.get();
                if (normalized <= previous) {
                    if (force) publishProgress(true);
                    return;
                }
                if (reportedBytesForTask.compareAndSet(previous, normalized)) {
                    transferredBytes.addAndGet(normalized - previous);
                    publishProgress(force);
                    return;
                }
            }
        }

        private void publishProgress(boolean force) {
            long now = System.currentTimeMillis();
            long last = lastProgressUpdateAt.get();
            if (!force && now - last < 200L) {
                return;
            }
            if (force || lastProgressUpdateAt.compareAndSet(last, now)) {
                updateProgress(completedFiles.get(), totalFiles, transferredBytes.get(), totalBytes, Math.max(1L, now - startedAt));
            }
        }
    }

    private static class TransferTask {
        final boolean download;
        final String remotePath;
        final File localFile;
        final long size;
        final AtomicLong reportedBytes = new AtomicLong(0L);

        private TransferTask(boolean download, String remotePath, File localFile, long size) {
            this.download = download;
            this.remotePath = remotePath;
            this.localFile = localFile;
            this.size = size;
        }

        static TransferTask download(String remotePath, File localFile, long size) {
            return new TransferTask(true, remotePath, localFile, size);
        }

        static TransferTask upload(File localFile, String remotePath, long size) {
            return new TransferTask(false, remotePath, localFile, size);
        }
    }

    private static class RemoteCopyTask {
        final String sourcePath;
        final String targetPath;
        final long size;
        final AtomicLong reportedBytes = new AtomicLong(0L);

        RemoteCopyTask(String sourcePath, String targetPath, long size) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.size = size;
        }
    }
}
