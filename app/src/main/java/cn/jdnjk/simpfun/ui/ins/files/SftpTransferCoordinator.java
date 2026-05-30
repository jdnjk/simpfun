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
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
        fetchCredentials(credentials -> {
            try {
                File target = localPane.resolveChildInCurrentPathForHost(item.getName());
                if (target.exists()) {
                    toast("目标已存在：" + item.getName());
                    return;
                }
                startProgressDialog((move ? "移动 " : "复制 ") + item.getName());
                runTransfer(() -> transferServerToLocal(credentials, serverPane.getItemPathForHost(item), target, item.isFile(), move, () -> {
                    serverPane.reloadForHost();
                    localPane.reloadForHost();
                }));
            } catch (Exception e) {
                toast(e.getMessage() == null ? "准备传输失败" : e.getMessage());
            }
        });
    }

    void copyLocalToServer(LocalFilePaneFragment localPane, FilePaneFragment serverPane, FileItem item, boolean move) {
        fetchCredentials(credentials -> {
            try {
                File source = localPane.resolveLocalPathForHost(localPane.getItemPathForHost(item));
                String remoteTarget = FilePathUtils.appendPath(serverPane.getCurrentPathForHost(), item.getName());
                startProgressDialog((move ? "移动 " : "复制 ") + item.getName());
                runTransfer(() -> transferLocalToServer(credentials, source, remoteTarget, move, () -> {
                    serverPane.reloadForHost();
                    localPane.reloadForHost();
                }));
            } catch (Exception e) {
                toast(e.getMessage() == null ? "准备传输失败" : e.getMessage());
            }
        });
    }

    private void runTransfer(TransferRunnable runnable) {
        cancelled.set(false);
        coordinatorExecutor = Executors.newSingleThreadExecutor();
        coordinatorExecutor.execute(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                mainHandler.post(() -> toast(e.getMessage() == null ? "SFTP 传输失败" : e.getMessage()));
            } finally {
                mainHandler.post(this::dismissProgressDialog);
                coordinatorExecutor.shutdown();
            }
        });
    }

    private void transferServerToLocal(SftpCredentials credentials, String remoteSource, File localTarget, boolean sourceIsFile, boolean move, Runnable onSuccess) throws Exception {
        List<TransferTask> tasks = new ArrayList<>();
        try (SftpSession session = openSession(credentials)) {
            if (sourceIsFile) {
                tasks.add(TransferTask.download(remoteSource, localTarget, safeRemoteSize(session.sftp, remoteSource)));
            } else {
                if (!localTarget.mkdirs() && !localTarget.isDirectory()) {
                    throw new IOException("无法创建目录：" + localTarget.getName());
                }
                collectRemoteDownloads(session.sftp, remoteSource, localTarget, tasks);
            }
        }
        runFileTasks(credentials, tasks);
        if (cancelled.get()) {
            return;
        }
        if (move) {
            try (SftpSession session = openSession(credentials)) {
                deleteRemoteRecursively(session.sftp, remoteSource, sourceIsFile);
            }
        }
        mainHandler.post(onSuccess);
    }

    private void transferLocalToServer(SftpCredentials credentials, File localSource, String remoteTarget, boolean move, Runnable onSuccess) throws Exception {
        List<TransferTask> tasks = new ArrayList<>();
        List<String> remoteDirectories = new ArrayList<>();
        collectLocalUploads(localSource, remoteTarget, tasks, remoteDirectories);
        try (SftpSession session = openSession(credentials)) {
            for (String directory : remoteDirectories) {
                ensureRemoteDirectory(session.sftp, directory);
            }
        }
        runFileTasks(credentials, tasks);
        if (cancelled.get()) {
            return;
        }
        if (move) {
            deleteLocalRecursively(localSource);
        }
        mainHandler.post(onSuccess);
        mainHandler.post(() -> toast(move ? "移动完成" : "复制完成"));
    }

    private void runFileTasks(SftpCredentials credentials, List<TransferTask> tasks) throws Exception {
        if (tasks.isEmpty()) {
            updateProgress(0, 0, 0, 0);
            return;
        }
        int threadCount = Math.min(settingsManager.getThreadCount(), tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger completedFiles = new AtomicInteger(0);
        AtomicLong completedBytes = new AtomicLong(0L);
        AtomicReference<Exception> firstError = new AtomicReference<>();
        long startedAt = System.currentTimeMillis();
        List<Future<?>> futures = new ArrayList<>();
        updateProgress(0, tasks.size(), 0, 0);
        for (TransferTask task : tasks) {
            futures.add(executor.submit(() -> {
                if (cancelled.get()) {
                    return;
                }
                try (SftpSession session = openSession(credentials)) {
                    if (task.download) {
                        File parent = task.localFile.getParentFile();
                        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                            throw new IOException("无法创建目录：" + parent.getName());
                        }
                        session.sftp.get(task.remotePath, task.localFile.getAbsolutePath());
                    } else {
                        ensureRemoteDirectory(session.sftp, remoteParent(task.remotePath));
                        session.sftp.put(task.localFile.getAbsolutePath(), task.remotePath);
                    }
                    completedBytes.addAndGet(Math.max(0L, task.size));
                    int done = completedFiles.incrementAndGet();
                    long elapsed = Math.max(1L, System.currentTimeMillis() - startedAt);
                    updateProgress(done, tasks.size(), completedBytes.get(), elapsed);
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                    cancelled.set(true);
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdownNow();
        Exception error = firstError.get();
        if (error != null) {
            throw error;
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

    private void collectLocalUploads(File localSource, String remoteTarget, List<TransferTask> tasks, List<String> remoteDirectories) throws IOException {
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

    private void ensureRemoteDirectory(SFTPClient sftp, String path) throws IOException {
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

    private void fetchCredentials(CredentialsCallback callback) {
        String token = token();
        if (token.isEmpty()) {
            toast("未登录");
            return;
        }
        new MainApi(activity).getSftp(token, String.valueOf(deviceId), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    JSONObject sftp = data.optJSONObject("data");
                    if (sftp == null) {
                        sftp = data;
                    }
                    callback.onCredentials(new SftpCredentials(
                            sftp.optString("ip"),
                            Integer.parseInt(sftp.optString("port", "22")),
                            sftp.optString("user_name"),
                            sftp.optString("password")
                    ));
                } catch (Exception e) {
                    toast("SFTP 信息无效");
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                toast("获取 SFTP 信息失败: " + errorMsg);
            }
        });
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

    private void updateProgress(int completed, int total, long bytes, long elapsedMillis) {
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
            progressIndicator.setProgress(Math.min(100, completed * 100 / total));
            String speed = elapsedMillis <= 0 ? "0 B/s" : formatSize(bytes * 1000L / elapsedMillis) + "/s";
            statusText.setText(String.format(Locale.getDefault(), "已完成 %d/%d  %s", completed, total, speed));
        });
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

    private static class TransferTask {
        final boolean download;
        final String remotePath;
        final File localFile;
        final long size;

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
}
