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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import cn.jdnjk.simpfun.R;
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

    void copyServerToServer(FilePaneFragment sourcePane, FilePaneFragment targetPane, List<FileItem> items) {
        if (items == null || items.isEmpty()) {
            toast("没有可传输的文件");
            return;
        }
        fetchCredentials(credentials -> startServerToServerTransfer(credentials, sourcePane, targetPane, items, true));
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

    private void startServerToServerTransfer(SftpCredentials credentials, FilePaneFragment sourcePane, FilePaneFragment targetPane, List<FileItem> items, boolean allowRetry) {
        try {
            startProgressDialog("复制 " + describeItems(items));
            runTransfer(() -> transferServerToServer(credentials, sourcePane, targetPane, items, () -> {
                sourcePane.reloadForHost();
                targetPane.reloadForHost();
            }), allowRetry ? () -> retryWithFreshCredentials(fresh -> startServerToServerTransfer(fresh, sourcePane, targetPane, items, false)) : null);
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
        try (SftpSession session = openSession(credentials)) {
            for (FileItem item : items) {
                File localTarget = localPane.resolveChildInCurrentPathForHost(item.getName());
                if (localTarget.exists()) {
                    throw new IOException("目标已存在：" + item.getName());
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
        try (SftpSession session = openSession(credentials)) {
            for (FileItem item : items) {
                File source = localPane.resolveLocalPathForHost(localPane.getItemPathForHost(item));
                String remoteTarget = FilePathUtils.appendPath(serverPane.getCurrentPathForHost(), item.getName());
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

    private void transferServerToServer(SftpCredentials credentials, FilePaneFragment sourcePane, FilePaneFragment targetPane, List<FileItem> items, Runnable onSuccess) throws Exception {
        List<RemoteCopyTask> tasks = new ArrayList<>();
        try (SftpSession session = openSession(credentials)) {
            for (FileItem item : items) {
                String remoteSource = sourcePane.getItemPathForHost(item);
                String remoteTarget = FilePathUtils.appendPath(targetPane.getCurrentPathForHost(), item.getName());
                if (remoteExists(session.sftp, remoteTarget)) {
                    throw new IOException("目标已存在：" + item.getName());
                }
                if (item.isFile()) {
                    tasks.add(new RemoteCopyTask(remoteSource, remoteTarget, safeRemoteSize(session.sftp, remoteSource)));
                } else {
                    ensureRemoteDirectory(session.sftp, remoteTarget);
                    collectRemoteCopies(session.sftp, remoteSource, remoteTarget, tasks);
                }
            }
        }
        runRemoteCopyTasks(credentials, tasks);
        if (cancelled.get()) {
            return;
        }
        mainHandler.post(onSuccess);
        mainHandler.post(() -> toast("复制完成"));
    }

    private String describeItems(List<FileItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.size() == 1 ? items.get(0).getName() : items.size() + " 项";
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
                progress.reportRemoteFileProgress(task, offset);
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
