package cn.jdnjk.simpfun.editor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;
import cn.jdnjk.simpfun.utils.SftpSupport;

/**
 * 编辑器用 SFTP 直接读写单个文件。
 *
 * <p>与 {@code SftpTransferCoordinator} 的区别：这里只处理一个小文件、不需要进度与并发，
 * 也刻意保留 SSHJ 的默认超时——连不上要尽快失败，好让上层回退到 HTTP 那条路。
 *
 * <p>回调一律在主线程执行。
 */
final class EditorSftpGateway {
    /** 一次维持多个未确认请求，压掉「读一块→等一个 RTT→再读」的延迟。与传输模块取值一致。 */
    private static final int UNCONFIRMED_REQUESTS = 16;
    private static final int BUFFER_SIZE = 256 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    interface FailureListener {
        void onFailure(String errorMsg);
    }

    interface BytesCallback extends FailureListener {
        void onBytes(byte[] content);
    }

    interface DoneCallback extends FailureListener {
        void onDone();
    }

    private interface CredentialListener {
        void onCredential(SftpCredentialStore.Credential credential);
    }

    private final Context appContext;
    private final String instanceId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    EditorSftpGateway(@NonNull Context context, int serverId) {
        this.appContext = context.getApplicationContext();
        this.instanceId = String.valueOf(serverId);
    }

    void shutdown() {
        executor.shutdownNow();
    }

    void read(String remotePath, long maxBytes, @NonNull BytesCallback callback) {
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(appContext).getValid(instanceId);
        if (cached != null) {
            read(cached, true, remotePath, maxBytes, callback);
            return;
        }
        fetchFreshCredential(credential -> read(credential, false, remotePath, maxBytes, callback),
                callback, "获取 SFTP 信息失败: ");
    }

    void write(String remotePath, byte[] content, @NonNull DoneCallback callback) {
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(appContext).getValid(instanceId);
        if (cached != null) {
            write(cached, true, remotePath, content, callback);
            return;
        }
        fetchFreshCredential(credential -> write(credential, false, remotePath, content, callback),
                callback, "获取 SFTP 信息失败: ");
    }

    private void read(SftpCredentialStore.Credential credential, boolean allowRetry,
                      String remotePath, long maxBytes, BytesCallback callback) {
        execute(() -> {
            byte[] content = readBytes(credential, remotePath, maxBytes);
            mainHandler.post(() -> callback.onBytes(content));
        }, allowRetry, callback, "SFTP 读取失败",
                fresh -> read(fresh, false, remotePath, maxBytes, callback));
    }

    private void write(SftpCredentialStore.Credential credential, boolean allowRetry,
                       String remotePath, byte[] content, DoneCallback callback) {
        execute(() -> {
            writeBytes(credential, remotePath, content);
            mainHandler.post(callback::onDone);
        }, allowRetry, callback, "SFTP 写入失败",
                fresh -> write(fresh, false, remotePath, content, callback));
    }

    /**
     * 在工作线程跑一段 SFTP 操作。鉴权失败且还有重试机会时换一份新密码重来，
     * 其余错误直接汇报给调用方。
     */
    private void execute(SftpAction action, boolean allowRetry, FailureListener callback,
                         String failurePrefix, CredentialListener retry) {
        executor.execute(() -> {
            try {
                action.run();
            } catch (Exception e) {
                if (allowRetry && SftpSupport.isAuthFailure(e)) {
                    mainHandler.post(() -> fetchFreshCredential(retry, callback,
                            "缓存密码失效，重新获取 SFTP 信息失败: "));
                    return;
                }
                String message = describe(e, failurePrefix);
                mainHandler.post(() -> callback.onFailure(message));
            }
        });
    }

    private void fetchFreshCredential(CredentialListener onCredential, FailureListener onFailure, String errorPrefix) {
        String token = appContext.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token == null || token.isEmpty()) {
            onFailure.onFailure("未登录");
            return;
        }
        new MainApi(appContext).getSftp(token, instanceId, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                SftpCredentialStore.Credential credential = SftpCredentialStore.Credential.fromApiJson(instanceId, data);
                if (credential == null) {
                    onFailure.onFailure("SFTP 信息无效");
                    return;
                }
                onCredential.onCredential(credential);
            }

            @Override
            public void onFailure(String errorMsg) {
                onFailure.onFailure(errorPrefix + errorMsg);
            }
        });
    }

    private byte[] readBytes(SftpCredentialStore.Credential credential, String remotePath, long maxBytes) throws IOException {
        try (SftpSession session = openSession(credential)) {
            long remoteSize = Math.max(0L, session.sftp.stat(remotePath).getSize());
            if (remoteSize > maxBytes) {
                throw new IOException("文件过大，暂不支持编辑");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(remoteSize > 0L ? (int) remoteSize : BUFFER_SIZE);
            byte[] buffer = new byte[BUFFER_SIZE];
            try (RemoteFile source = session.sftp.open(remotePath, EnumSet.of(OpenMode.READ));
                 RemoteFile.ReadAheadRemoteFileInputStream remote =
                         source.new ReadAheadRemoteFileInputStream(UNCONFIRMED_REQUESTS, 0L)) {
                int read;
                while ((read = remote.read(buffer, 0, buffer.length)) > 0) {
                    if (out.size() + (long) read > maxBytes) {
                        throw new IOException("文件过大，暂不支持编辑");
                    }
                    out.write(buffer, 0, read);
                }
            }
            return out.toByteArray();
        }
    }

    /**
     * 写入远端文件。先写同目录的 ".part" 再改名：保存中途断线时原文件仍是完整的旧内容，
     * 不会留下被截断的半截配置。
     */
    private void writeBytes(SftpCredentialStore.Credential credential, String remotePath, byte[] content) throws IOException {
        try (SftpSession session = openSession(credential)) {
            SFTPClient sftp = session.sftp;
            String partPath = remotePath + ".part";
            try (RemoteFile target = sftp.open(partPath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC));
                 RemoteFile.RemoteFileOutputStream remote =
                         target.new RemoteFileOutputStream(0L, UNCONFIRMED_REQUESTS)) {
                // 单次写请求不得超过服务器公告的最大包长度。
                int chunkSize = sftp.getSFTPEngine().getSubsystem().getRemoteMaxPacketSize()
                        - target.getOutgoingPacketOverhead();
                if (chunkSize <= 0) {
                    chunkSize = BUFFER_SIZE;
                }
                for (int offset = 0; offset < content.length; offset += chunkSize) {
                    remote.write(content, offset, Math.min(chunkSize, content.length - offset));
                }
                remote.flush();
            }
            finalizeWrite(sftp, partPath, remotePath);
        }
    }

    /** 部分服务器不支持覆盖式重命名，失败时先删目标再重试。 */
    private void finalizeWrite(SFTPClient sftp, String partPath, String remotePath) throws IOException {
        try {
            sftp.rename(partPath, remotePath);
        } catch (IOException e) {
            if (!exists(sftp, remotePath)) {
                throw e;
            }
            sftp.rm(remotePath);
            sftp.rename(partPath, remotePath);
        }
    }

    private boolean exists(SFTPClient sftp, String remotePath) {
        try {
            return sftp.statExistence(remotePath) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private SftpSession openSession(SftpCredentialStore.Credential credential) throws IOException {
        SftpSupport.ensureBouncyCastleRegistered();
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        // 卡住不如尽快失败：这两条通道之上还有 HTTP 回退，连不上就该让它接手。
        // SSHJ 默认连接超时为 0（跟随系统，可能拖上一两分钟），必须显式设小。
        ssh.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        ssh.setTimeout(READ_TIMEOUT_MILLIS);
        ssh.connect(credential.host, credential.port);
        ssh.authPassword(credential.username, credential.password);
        return new SftpSession(ssh, ssh.newSFTPClient());
    }

    private static String describe(Exception e, String prefix) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return prefix;
        }
        return prefix + ": " + message;
    }

    private interface SftpAction {
        void run() throws Exception;
    }

    private static final class SftpSession implements AutoCloseable {
        private final SSHClient ssh;
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
}
