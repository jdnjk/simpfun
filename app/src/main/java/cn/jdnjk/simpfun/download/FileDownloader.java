package cn.jdnjk.simpfun.download;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 流式下载引擎：把 HTTP 响应体写进 {@link DownloadTarget}，支持取消。
 *
 * <p>刻意不复用 {@link cn.jdnjk.simpfun.api.ApiClient} 的共享客户端：那个设了
 * callTimeout(120s)，会把大文件下载直接掐断。这里只设连接/读写超时，不限总时长。
 */
public final class FileDownloader {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long PROGRESS_INTERVAL_MILLIS = 200L;

    /** 备份下载节点：该 host 的 TLS 证书已过期，仅对它放行证书校验。 */
    private static final String TRUST_ALL_HOST = "sfe4-connect.simpfun.cn";

    private static volatile OkHttpClient client;
    private static volatile OkHttpClient trustAllClient;

    private FileDownloader() {
    }

    public interface Callback {
        /** totalBytes <= 0 表示服务端没给 Content-Length，调用方应显示不确定进度。 */
        void onProgress(long downloadedBytes, long totalBytes);

        void onSuccess(DownloadTarget target);

        void onFailure(String errorMsg);
    }

    /**
     * 决定落盘文件名的回调。用于「下载响应头里下发了真实文件名时，按它存盘」的场景
     * （如备份下载的 zip）。在 OkHttp worker 线程回调。
     *
     * @param response    已成功、body 非空的响应，可读 Content-Disposition。
     * @param fallbackName 调用方在下载前已定的文件名。
     * @return 要使用的最终文件名；返回 null 表示沿用 fallbackName。
     */
    public interface FilenameResolver {
        @Nullable
        String resolveFrom(@NonNull Response response, @NonNull String fallbackName);
    }

    /** 取消句柄，语义与 {@code FileTransferApi.UploadHandle} 一致。 */
    public static final class Handle {
        private final AtomicBoolean canceled = new AtomicBoolean(false);
        private volatile Call call;

        public void cancel() {
            canceled.set(true);
            Call current = call;
            if (current != null) {
                current.cancel();
            }
        }

        public boolean isCanceled() {
            return canceled.get();
        }

        private void setCall(Call newCall) {
            call = newCall;
            if (canceled.get() && newCall != null) {
                newCall.cancel();
            }
        }
    }

    /** 发起下载。回调一律在主线程执行。 */
    public static Handle download(@NonNull String url, @NonNull DownloadTarget target, @NonNull Callback callback) {
        return download(url, target, callback, null);
    }

    /**
     * 发起下载；回调一律在主线程执行。
     *
     * @param resolver 非 null 时，在拿到响应后、开流前用其解析服务器下发的文件名并按此存盘
     *                 （仅当解析结果与 fallback 不同才生效）。
     */
    public static Handle download(@NonNull String url, @NonNull DownloadTarget target, @NonNull Callback callback,
                                  @Nullable FilenameResolver resolver) {
        Handle handle = new Handle();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        Request request;
        try {
            request = new Request.Builder().url(url).get().build();
        } catch (IllegalArgumentException e) {
            mainHandler.post(() -> callback.onFailure("下载地址无效"));
            return handle;
        }
        final String fallbackName = lastPathSegment(target.getDisplayPath());

        // 仅备份下载节点放行过期证书，其余 host 一律严格校验。
        Call call = usesTrustAllClient(request) ? getTrustAllClient().newCall(request) : getClient().newCall(request);
        handle.setCall(call);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                target.deletePartial();
                if (handle.isCanceled()) {
                    return;
                }
                mainHandler.post(() -> callback.onFailure("网络请求失败: " + describe(e)));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeable = response) {
                    if (!closeable.isSuccessful()) {
                        target.deletePartial();
                        if (!handle.isCanceled()) {
                            mainHandler.post(() -> callback.onFailure("HTTP 错误: " + closeable.code()));
                        }
                        return;
                    }
                    ResponseBody body = closeable.body();
                    if (body == null) {
                        target.deletePartial();
                        if (!handle.isCanceled()) {
                            mainHandler.post(() -> callback.onFailure("响应内容为空"));
                        }
                        return;
                    }
                    // 开流前，按服务器下发的文件名改写落盘目标。
                    if (resolver != null) {
                        String real = resolver.resolveFrom(closeable, fallbackName);
                        if (real != null && !real.isEmpty() && !real.equals(fallbackName)) {
                            target.renameTo(sanitizeFileName(real));
                        }
                    }
                    writeBody(body, target, handle, mainHandler, callback);
                } catch (IOException e) {
                    target.deletePartial();
                    if (!handle.isCanceled()) {
                        mainHandler.post(() -> callback.onFailure("下载失败: " + describe(e)));
                    }
                }
            }
        });
        return handle;
    }

    private static void writeBody(ResponseBody body, DownloadTarget target, Handle handle,
                                  Handler mainHandler, Callback callback) throws IOException {
        long totalBytes = body.contentLength();
        long downloadedBytes = 0L;
        long lastPublishedAt = 0L;

        try (InputStream input = body.byteStream(); OutputStream output = target.openOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (handle.isCanceled()) {
                    throw new InterruptedIOException("已取消");
                }
                output.write(buffer, 0, read);
                downloadedBytes += read;

                // 节流：不加限制的话每读一次就 post 一条主线程消息，大文件会有上千条。
                long now = System.currentTimeMillis();
                if (now - lastPublishedAt >= PROGRESS_INTERVAL_MILLIS) {
                    lastPublishedAt = now;
                    long snapshot = downloadedBytes;
                    mainHandler.post(() -> callback.onProgress(snapshot, totalBytes));
                }
            }
            output.flush();
        } catch (IOException e) {
            target.deletePartial();
            if (!handle.isCanceled()) {
                mainHandler.post(() -> callback.onFailure("写入失败: " + describe(e)));
            }
            return;
        }

        if (handle.isCanceled()) {
            target.deletePartial();
            return;
        }

        try {
            target.finish();
        } catch (IOException e) {
            target.deletePartial();
            mainHandler.post(() -> callback.onFailure("保存失败: " + describe(e)));
            return;
        }

        long finalBytes = downloadedBytes;
        mainHandler.post(() -> {
            callback.onProgress(finalBytes, totalBytes);
            callback.onSuccess(target);
        });
    }

    private static String describe(IOException e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static String lastPathSegment(String path) {
        if (path == null) {
            return "download";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    /** 去掉路径分隔符等危险字符，避免服务器下发名写成子路径。 */
    private static String sanitizeFileName(String name) {
        String cleaned = name.replace('/', '_').replace('\\', '_').trim();
        return cleaned.isEmpty() ? "download" : cleaned;
    }

    /**
     * 从 Content-Disposition 解析服务器下发的文件名。
     * 优先 filename*=UTF-8''（RFC 5987），否则 filename="…"。取不到返回 null。
     */
    @Nullable
    public static String parseContentDispositionFilename(@NonNull Response response) {
        String disposition = response.header("Content-Disposition");
        if (disposition == null || disposition.trim().isEmpty()) {
            return null;
        }
        String value = disposition.trim();
        try {
            // filename*=UTF-8''<url-encoded>
            int starIdx = value.toLowerCase(java.util.Locale.ROOT).indexOf("filename*=");
            if (starIdx >= 0) {
                String after = value.substring(starIdx + "filename*=".length()).trim();
                int semi = after.indexOf(';');
                if (semi >= 0) {
                    after = after.substring(0, semi);
                }
                int quote = after.indexOf('\'');
                int quote2 = quote >= 0 ? after.indexOf('\'', quote + 1) : -1;
                if (quote >= 0 && quote2 >= 0) {
                    String encoded = after.substring(quote2 + 1);
                    String decoded = java.net.URLDecoder.decode(encoded, "UTF-8");
                    if (!decoded.trim().isEmpty()) {
                        return decoded;
                    }
                }
            }
            // filename="…" 或 filename=…
            int idx = value.toLowerCase(java.util.Locale.ROOT).indexOf("filename=");
            if (idx < 0) {
                return null;
            }
            String after = value.substring(idx + "filename=".length()).trim();
            int semi = after.indexOf(';');
            if (semi >= 0) {
                after = after.substring(0, semi);
            }
            after = after.trim();
            if (after.length() >= 2 && after.startsWith("\"") && after.endsWith("\"")) {
                after = after.substring(1, after.length() - 1);
            }
            if (!after.isEmpty()) {
                return after;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean usesTrustAllClient(Request request) {
        try {
            String host = request.url().host();
            return host != null && TRUST_ALL_HOST.equalsIgnoreCase(host);
        } catch (Exception e) {
            return false;
        }
    }

    private static OkHttpClient getTrustAllClient() {
        OkHttpClient existing = trustAllClient;
        if (existing != null) {
            return existing;
        }
        synchronized (FileDownloader.class) {
            if (trustAllClient == null) {
                trustAllClient = baseBuilder()
                        .sslSocketFactory(trustAllSslContext().getSocketFactory(), trustAllManager())
                        .hostnameVerifier((hostname, session) -> true)
                        .build();
            }
            return trustAllClient;
        }
    }

    private static OkHttpClient getClient() {
        OkHttpClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (FileDownloader.class) {
            if (client == null) {
                client = baseBuilder().build();
            }
            return client;
        }
    }

    private static OkHttpClient.Builder baseBuilder() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // 下载地址是一次性的，同一条链接不能发两次请求。OkHttp 默认会在连接
                // 失败时换条路由透明重发，若服务端已消耗掉 uuid，重发只会拿到「链接已失效」。
                // 关掉透明重试，失败交给调用方重新换取地址后再来。
                .retryOnConnectionFailure(false);
    }

    private static javax.net.ssl.X509TrustManager trustAllManager() {
        return new javax.net.ssl.X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        };
    }

    private static javax.net.ssl.SSLContext trustAllSslContext() {
        try {
            javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS");
            context.init(null, new javax.net.ssl.TrustManager[]{trustAllManager()},
                    new java.security.SecureRandom());
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建信任全部证书的 SSLContext", e);
        }
    }
}
