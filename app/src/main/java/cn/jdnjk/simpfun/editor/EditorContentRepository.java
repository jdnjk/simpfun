package cn.jdnjk.simpfun.editor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.api.ins.file.FileTransferApi;
import cn.jdnjk.simpfun.download.DownloadTarget;
import cn.jdnjk.simpfun.download.FileDownloader;
import cn.jdnjk.simpfun.download.MemoryDownloadTarget;

/**
 * 编辑器的文件内容读写入口，负责在三条通道之间选路与回退。
 *
 * <p>读取默认走 {@code /file/fetch}：一次请求拿到文本，最省事。它对非文本文件会返回 500，
 * 也可能被服务端拒绝，这时换下载直链把字节取回内存。开启双页文件管理器时顺序相反，优先
 * 用 SFTP——那套界面本来就在用 SFTP 列目录，凭据已在手上，也不受 fetch 的限制。
 *
 * <p>保存跟着读取走：内容从哪条通道来，就先从哪条通道回去，失败再换另一条，避免出现
 * 打得开、存不回的文件。下载直链是只读的，它读来的内容按 fetch 那条链保存。
 *
 * <p>回调一律在主线程执行。
 */
public final class EditorContentRepository {

    /** 编辑器只处理文本，超过这个大小的文件不往内存里读。文件列表的入口限制取的也是这个值。 */
    public static final long MAX_CONTENT_BYTES = 5L * 1024L * 1024L;

    /** 内容实际走的通道。出错时用它的名字说明是哪一条失败了。 */
    public enum Channel {
        FETCH_API("在线编辑接口"),
        DOWNLOAD_LINK("下载通道"),
        SFTP("SFTP");

        private final String label;

        Channel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public interface LoadCallback {
        void onContent(String content, Channel channel);

        void onFailure(String errorMsg);
    }

    public interface SaveCallback {
        void onSaved(Channel channel);

        void onFailure(String errorMsg);
    }

    private final Context appContext;
    private final int serverId;
    private final String remotePath;
    private final boolean preferSftp;
    private final FileApi fileApi = new FileApi();

    @Nullable
    private EditorSftpGateway sftpGateway;
    @Nullable
    private FileDownloader.Handle downloadHandle;
    /** 内容来自哪条通道，决定保存先试哪一条。 */
    @Nullable
    private Channel loadedChannel;
    private boolean closed;

    public EditorContentRepository(@NonNull Context context, int serverId, @NonNull String remotePath, boolean preferSftp) {
        this.appContext = context.getApplicationContext();
        this.serverId = serverId;
        this.remotePath = remotePath;
        this.preferSftp = preferSftp;
    }

    /** 界面销毁时调用：作废在途回调、断开下载、关掉 SFTP 线程。 */
    public void close() {
        closed = true;
        if (downloadHandle != null) {
            downloadHandle.cancel();
            downloadHandle = null;
        }
        if (sftpGateway != null) {
            sftpGateway.shutdown();
            sftpGateway = null;
        }
    }

    public void load(@NonNull LoadCallback callback) {
        if (preferSftp) {
            loadViaSftp(callback);
        } else {
            loadViaFetch(callback);
        }
    }

    public void save(@NonNull String content, @NonNull SaveCallback callback) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (loadedChannel == Channel.SFTP || (loadedChannel == null && preferSftp)) {
            saveViaSftp(bytes, callback, sftpError ->
                    saveViaApi(content, callback, apiError -> reportBothFailed(callback, sftpError, apiError)));
        } else {
            saveViaApi(content, callback, apiError ->
                    saveViaSftp(bytes, callback, sftpError -> reportBothFailed(callback, apiError, sftpError)));
        }
    }

    // ---------- 读取 ----------

    private void loadViaSftp(LoadCallback callback) {
        gateway().read(remotePath, MAX_CONTENT_BYTES, new EditorSftpGateway.BytesCallback() {
            @Override
            public void onBytes(byte[] content) {
                deliver(content, Channel.SFTP, callback);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!closed) {
                    loadViaFetch(callback);
                }
            }
        });
    }

    private void loadViaFetch(LoadCallback callback) {
        fileApi.fetchFileContent(appContext, serverId, remotePath, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (closed) {
                    return;
                }
                loadedChannel = Channel.FETCH_API;
                callback.onContent(data.optString("content", ""), Channel.FETCH_API);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!closed) {
                    loadViaDownload(callback, errorMsg);
                }
            }
        });
    }

    /**
     * 换取下载直链，把字节读进内存。
     *
     * @param fetchError fetch 通道的失败原因；两条都失败时一并报出，便于分辨是文件本身
     *                   不可编辑，还是网络出了问题。
     */
    private void loadViaDownload(LoadCallback callback, String fetchError) {
        fileApi.resolveDownloadLink(appContext, serverId, remotePath, new FileTransferApi.LinkCallback() {
            @Override
            public void onLink(String url) {
                if (closed) {
                    return;
                }
                MemoryDownloadTarget target = new MemoryDownloadTarget(remotePath, MAX_CONTENT_BYTES);
                downloadHandle = FileDownloader.download(url, target, new FileDownloader.Callback() {
                    @Override
                    public void onProgress(long downloadedBytes, long totalBytes) {
                        // 编辑器只开小文件，不显示进度。
                    }

                    @Override
                    public void onSuccess(DownloadTarget finished) {
                        downloadHandle = null;
                        byte[] content = target.getContent();
                        deliver(content == null ? new byte[0] : content, Channel.DOWNLOAD_LINK, callback);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        downloadHandle = null;
                        reportLoadFailed(callback, fetchError, errorMsg);
                    }
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                reportLoadFailed(callback, fetchError, errorMsg);
            }
        });
    }

    /**
     * 字节转文本。含 NUL 字节的一律拒绝：那是二进制文件，按 UTF-8 解出来全是替换字符，
     * 一保存就把原文件毁了。
     */
    private void deliver(byte[] content, Channel channel, LoadCallback callback) {
        if (closed) {
            return;
        }
        for (byte b : content) {
            if (b == 0) {
                callback.onFailure("该文件不是文本文件，无法编辑");
                return;
            }
        }
        loadedChannel = channel;
        callback.onContent(new String(content, StandardCharsets.UTF_8), channel);
    }

    private void reportLoadFailed(LoadCallback callback, String fetchError, String downloadError) {
        if (!closed) {
            callback.onFailure(Channel.FETCH_API.getLabel() + ": " + fetchError
                    + "\n" + Channel.DOWNLOAD_LINK.getLabel() + ": " + downloadError);
        }
    }

    // ---------- 保存 ----------

    private void saveViaSftp(byte[] bytes, SaveCallback callback, ChannelFailure fallback) {
        gateway().write(remotePath, bytes, new EditorSftpGateway.DoneCallback() {
            @Override
            public void onDone() {
                if (!closed) {
                    callback.onSaved(Channel.SFTP);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!closed) {
                    fallback.onFailure(Channel.SFTP.getLabel() + ": " + errorMsg);
                }
            }
        });
    }

    private void saveViaApi(String content, SaveCallback callback, ChannelFailure fallback) {
        fileApi.saveFileContent(appContext, serverId, remotePath, content, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!closed) {
                    callback.onSaved(Channel.FETCH_API);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!closed) {
                    fallback.onFailure(Channel.FETCH_API.getLabel() + ": " + errorMsg);
                }
            }
        });
    }

    private void reportBothFailed(SaveCallback callback, String firstError, String secondError) {
        if (!closed) {
            callback.onFailure(firstError + "\n" + secondError);
        }
    }

    private EditorSftpGateway gateway() {
        if (sftpGateway == null) {
            sftpGateway = new EditorSftpGateway(appContext, serverId);
        }
        return sftpGateway;
    }

    /** 带上失败通道名的回退入口，让最终错误里两条通道都留有痕迹。 */
    private interface ChannelFailure {
        void onFailure(String describedError);
    }
}
