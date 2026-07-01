package cn.jdnjk.simpfun.ui.ins.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.FilePathUtils;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;

class FilePropertiesDialog {
    private final Activity activity;
    private final LinearLayout content;
    private final TextView sizeValue;
    private final TextView fileCountValue;
    private final TextView folderCountValue;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long totalSize;
    private long fileCount;
    private long folderCount;

    private FilePropertiesDialog(Activity activity, FileItem item, String directory) {
        this.activity = activity;
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, dp(8), padding, dp(8));
        addRow("名称", item.getName());
        addRow("目录", directory);
        addRow("类型", item.isFile() ? "文件" : "文件夹");
        sizeValue = addRow("大小", item.isFile() ? formatSizeWithRaw(item.getSize()) : "统计中...");
        addRow("修改时间", item.getModifiedAt() == null || item.getModifiedAt().isEmpty() ? "-" : item.getModifiedAt());
        fileCountValue = addRow("文件数", item.isFile() ? "-" : "0");
        folderCountValue = addRow("文件夹数", item.isFile() ? "-" : "0");
    }

    static void showLocal(Activity activity, LocalFilePaneFragment pane, FileItem item) {
        FilePropertiesDialog dialog = new FilePropertiesDialog(activity, item, pane.getCurrentPathForHost());
        dialog.show();
        if (!item.isFile()) {
            try {
                File root = pane.resolveLocalPathForHost(pane.getItemPathForHost(item));
                dialog.executor.execute(() -> dialog.scanLocal(root));
            } catch (Exception e) {
                dialog.updateError(e.getMessage());
            }
        }
    }

    static void showServer(Activity activity, FilePaneFragment pane, int deviceId, FileItem item) {
        FilePropertiesDialog dialog = new FilePropertiesDialog(activity, item, pane.getCurrentPathForHost());
        dialog.show();
        if (!item.isFile()) {
            dialog.fetchSftpAndScan(deviceId, pane.getItemPathForHost(item));
        }
    }

    private void show() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle("属性")
                .setView(content)
                .setPositiveButton("关闭", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            cancelled.set(true);
            executor.shutdownNow();
        });
        dialog.show();
    }

    private TextView addRow(String label, String value) {
        TextView textView = new TextView(activity);
        textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setText(label + "  " + value);
        textView.setTextSize(14);
        textView.setPadding(0, dp(5), 0, dp(5));
        content.addView(textView);
        return textView;
    }

    private void scanLocal(File file) {
        if (cancelled.get()) {
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            postStats();
            return;
        }
        for (File child : children) {
            if (cancelled.get()) {
                return;
            }
            if (child.isDirectory()) {
                folderCount++;
                postStats();
                scanLocal(child);
            } else {
                fileCount++;
                totalSize += Math.max(0L, child.length());
                postStats();
            }
        }
        postStats();
    }

    private void fetchSftpAndScan(int deviceId, String remotePath) {
        String instanceId = String.valueOf(deviceId);
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(activity).getValid(instanceId);
        if (cached != null) {
            executor.execute(() -> {
                if (scanRemoteNeedsFreshCredentials(cached.host, cached.port, cached.username, cached.password, remotePath) && !cancelled.get()) {
                    handler.post(() -> fetchFreshSftpAndScan(instanceId, remotePath, "缓存密码失效，重新获取 SFTP 信息失败: "));
                }
            });
            return;
        }
        fetchFreshSftpAndScan(instanceId, remotePath, "获取 SFTP 信息失败: ");
    }

    private void fetchFreshSftpAndScan(String instanceId, String remotePath, String errorPrefix) {
        String token = token();
        if (token.isEmpty()) {
            updateError("未登录");
            return;
        }
        new MainApi(activity).getSftp(token, instanceId, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                SftpCredentialStore.Credential credential = SftpCredentialStore.Credential.fromApiJson(instanceId, data);
                if (credential == null) {
                    updateError("SFTP 信息无效");
                    return;
                }
                executor.execute(() -> scanRemote(credential.host, credential.port, credential.username, credential.password, remotePath));
            }

            @Override
            public void onFailure(String errorMsg) {
                updateError(errorPrefix + errorMsg);
            }
        });
    }

    private boolean scanRemoteNeedsFreshCredentials(String host, int port, String user, String password, String remotePath) {
        return scanRemote(host, port, user, password, remotePath, true);
    }

    private boolean scanRemote(String host, int port, String user, String password, String remotePath) {
        return scanRemote(host, port, user, password, remotePath, false);
    }

    private boolean scanRemote(String host, int port, String user, String password, String remotePath, boolean returnAuthFailure) {
        SftpTransferCoordinator.ensureBouncyCastleRegistered();
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.connect(host, port);
            ssh.authPassword(user, password);
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                scanRemoteDirectory(sftp, remotePath);
            }
            ssh.disconnect();
            return false;
        } catch (Exception e) {
            Log.w("FilePropertiesDialog", "扫描远程文件属性失败或用户取消操作", e);
            return returnAuthFailure && isAuthFailure(e);
        }
    }

    private boolean isAuthFailure(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof UserAuthException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("auth")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void scanRemoteDirectory(SFTPClient sftp, String path) throws IOException {
        if (cancelled.get()) {
            return;
        }
        for (RemoteResourceInfo info : sftp.ls(path)) {
            if (cancelled.get()) {
                return;
            }
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            if (info.isDirectory()) {
                folderCount++;
                postStats();
                scanRemoteDirectory(sftp, FilePathUtils.appendPath(path, name));
            } else {
                fileCount++;
                totalSize += Math.max(0L, info.getAttributes().getSize());
                postStats();
            }
        }
        postStats();
    }

    private void postStats() {
        handler.post(() -> {
            if (cancelled.get()) {
                return;
            }
            sizeValue.setText("大小  " + formatSizeWithRaw(totalSize));
            fileCountValue.setText("文件数  " + fileCount);
            folderCountValue.setText("文件夹数  " + folderCount);
        });
    }

    private void updateError(String message) {
        handler.post(() -> Toast.makeText(activity, message == null ? "属性统计失败" : message, Toast.LENGTH_SHORT).show());
    }

    private String token() {
        SharedPreferences sp = activity.getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", "");
    }

    private String formatSizeWithRaw(long size) {
        return SftpTransferCoordinator.formatSize(size) + " (" + size + " B)";
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
