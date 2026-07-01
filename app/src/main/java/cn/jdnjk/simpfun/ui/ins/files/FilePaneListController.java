package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;

class FilePaneListController {
    interface Host {
        Context getContextOrNull();
        boolean isActive();
        int getDeviceId(Context context);
        void showLoading(boolean show);
        void showError(String message);
        void stopRefreshing();
        void onFileListChanged();
        default boolean useSftpFileList() { return false; }
    }

    private static final String TAG = "FilePaneListController";
    private final FilePaneState state;
    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sftpExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    FilePaneListController(FilePaneState state, Host host) {
        this.state = state;
        this.host = host;
    }

    void loadFileList() {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        if (deviceId <= 0) {
            host.showError("设备ID无效");
            host.stopRefreshing();
            return;
        }

        String requestPath = state.getCurrentPath();
        if (host.useSftpFileList()) {
            loadSftpFileList(context, deviceId, requestPath);
            return;
        }

        host.showLoading(true);
        new FileApi().getFileList(context, deviceId, requestPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isCurrentRequest(requestPath)) {
                    return;
                }
                host.stopRefreshing();
                host.showLoading(false);
                try {
                    updateFileList(data.getJSONArray("list"), requestPath);
                } catch (Exception e) {
                    host.showError("解析失败:" + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentRequest(requestPath)) {
                    return;
                }
                host.stopRefreshing();
                host.showLoading(false);
                host.showError(errorMsg);
            }
        });
    }

    void shutdown() {
        sftpExecutor.shutdownNow();
    }

    private void loadSftpFileList(Context context, int deviceId, String requestPath) {
        host.showLoading(true);
        String instanceId = String.valueOf(deviceId);
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(context).getValid(instanceId);
        if (cached != null) {
            loadSftpFileList(context, instanceId, requestPath, cached, true);
            return;
        }
        fetchFreshCredentialsAndLoad(context, instanceId, requestPath, "获取 SFTP 信息失败: ");
    }

    private void loadSftpFileList(Context context, String instanceId, String requestPath, SftpCredentialStore.Credential credential, boolean allowRetry) {
        sftpExecutor.execute(() -> {
            try {
                List<FileItem> items = readSftpDirectory(credential, requestPath);
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
                if (allowRetry && isAuthFailure(e)) {
                    mainHandler.post(() -> fetchFreshCredentialsAndLoad(context, instanceId, requestPath, "缓存密码失效，重新获取 SFTP 信息失败: "));
                    return;
                }
                mainHandler.post(() -> {
                    if (!isCurrentRequest(requestPath)) {
                        return;
                    }
                    host.stopRefreshing();
                    host.showLoading(false);
                    host.showError(e.getMessage() == null ? "读取 SFTP 目录失败" : e.getMessage());
                });
            }
        });
    }

    private void fetchFreshCredentialsAndLoad(Context context, String instanceId, String requestPath, String errorPrefix) {
        String token = context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) {
            host.stopRefreshing();
            host.showLoading(false);
            host.showError("未登录");
            return;
        }
        new MainApi(context).getSftp(token, instanceId, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                SftpCredentialStore.Credential credential = SftpCredentialStore.Credential.fromApiJson(instanceId, data);
                if (credential == null) {
                    host.stopRefreshing();
                    host.showLoading(false);
                    host.showError("SFTP 信息无效");
                    return;
                }
                loadSftpFileList(context, instanceId, requestPath, credential, false);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentRequest(requestPath)) {
                    return;
                }
                host.stopRefreshing();
                host.showLoading(false);
                host.showError(errorPrefix + errorMsg);
            }
        });
    }

    private List<FileItem> readSftpDirectory(SftpCredentialStore.Credential credential, String requestPath) throws Exception {
        SftpTransferCoordinator.ensureBouncyCastleRegistered();
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.connect(credential.host, credential.port);
            ssh.authPassword(credential.username, credential.password);
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                List<RemoteResourceInfo> remoteItems = new ArrayList<>(sftp.ls(requestPath));
                remoteItems.sort(Comparator
                        .comparing((RemoteResourceInfo info) -> !info.isDirectory())
                        .thenComparing(info -> info.getName().toLowerCase(Locale.ROOT)));

                List<FileItem> items = new ArrayList<>();
                if (!state.getRootPath().equals(requestPath)) {
                    items.add(new FileItem(FileItem.PARENT_DIR_NAME, false, 0, "", ""));
                }
                for (RemoteResourceInfo info : remoteItems) {
                    String name = info.getName();
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    boolean file = !info.isDirectory();
                    long size = file ? Math.max(0L, info.getAttributes().getSize()) : 0L;
                    items.add(new FileItem(name, file, size, file ? guessMime(name) : "", formatRemoteTime(info.getAttributes().getMtime())));
                }
                return items;
            }
        }
    }

    private void updateFileList(JSONArray list, String requestPath) {
        List<FileItem> items = new ArrayList<>();
        if (!state.isAtRoot()) {
            items.add(new FileItem(FileItem.PARENT_DIR_NAME, false, 0, "", ""));
        }

        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject obj = list.getJSONObject(i);
                String name = obj.getString("name");
                if ("..".equals(name) || ".".equals(name)) {
                    continue;
                }
                items.add(new FileItem(
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

        state.replaceFileList(items);
        state.clearSelection();
        host.onFileListChanged();
    }

    private boolean isCurrentRequest(String requestPath) {
        return host.isActive() && requestPath.equals(state.getCurrentPath());
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

    private String formatRemoteTime(long seconds) {
        return seconds <= 0L ? "" : dateFormat.format(new Date(seconds * 1000L));
    }

    private String guessMime(String name) {
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime == null ? "" : mime;
    }
}
