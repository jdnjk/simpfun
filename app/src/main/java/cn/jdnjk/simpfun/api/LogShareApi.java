package cn.jdnjk.simpfun.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import cn.jdnjk.simpfun.BuildConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 独立客户端：禁止日志拦截器记录日志正文/删除 Token，不携带简幻欢 Authorization。 */
public final class LogShareApi {
    public enum Site {
        MCLO_GS("api.mclo.gs", "https://api.mclo.gs/1/log", 25000, false),
        LOGSHARE_CN("api.logshare.cn", "https://api.logshare.cn/v1/log", 50000, true);

        public final String displayName;
        public final int maxLines;
        final String endpoint;
        final boolean gzip;

        Site(String name, String endpoint, int maxLines, boolean gzip) {
            this.displayName = name;
            this.endpoint = endpoint;
            this.maxLines = maxLines;
            this.gzip = gzip;
        }
    }

    public static final class SharedLog {
        public final Site site;
        public final String id;
        public final String url;
        public final String raw;
        private final String token;

        SharedLog(Site site, String id, String url, String raw, String token) {
            this.site = site;
            this.id = id;
            this.url = url;
            this.raw = raw;
            this.token = token;
        }
    }

    public interface ResultCallback<T> {
        void onSuccess(T result);
        void onFailure(String message);
    }

    private final OkHttpClient client;

    public LogShareApi() {
        this(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .build());
    }

    LogShareApi(OkHttpClient client) {
        this.client = client;
    }

    /** JSON 构建/压缩应在工作线程调用；回调也在 OkHttp 工作线程。 */
    public Call create(Site site, LogSharePayload.PreparedLog log, JSONArray metadata,
                       ResultCallback<SharedLog> callback) throws IOException, JSONException {
        Call call = client.newCall(createRequest(site, log, metadata));
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("分享请求失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    JSONObject json = readResult(closeable);
                    callback.onSuccess(parseSharedLog(site, json));
                } catch (Exception e) {
                    callback.onFailure(e.getMessage() == null ? "分享失败" : e.getMessage());
                }
            }
        });
        return call;
    }

    public Call delete(SharedLog log, ResultCallback<Void> callback) {
        Call call = client.newCall(deleteRequest(log));
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("取消分享失败：" + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    JSONObject json = readResult(closeable);
                    JSONArray failed = json.optJSONArray("failed");
                    if ((failed != null && failed.length() > 0) || json.optInt("deletedCount", 1) == 0) {
                        throw new IOException(ApiError.getMessage(json, "站点未能删除日志，请重试"));
                    }
                    callback.onSuccess(null);
                } catch (Exception e) {
                    callback.onFailure(e.getMessage() == null ? "取消分享失败" : e.getMessage());
                }
            }
        });
        return call;
    }

    static Request createRequest(Site site, LogSharePayload.PreparedLog log, JSONArray metadata)
            throws JSONException, IOException {
        if (log.content.trim().isEmpty()) throw new IOException("终端暂无日志可分享");
        JSONObject payload = new JSONObject().put("content", log.content)
                .put("source", "SimpfunAPP/" + BuildConfig.VERSION_NAME);
        if (metadata != null && metadata.length() > 0) payload.put("metadata", metadata);
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        Request.Builder request = new Request.Builder().url(site.endpoint)
                .header("User-Agent", "SimpfunAPP/" + BuildConfig.VERSION_NAME);
        if (site.gzip) {
            if (bytes.length > 20 * 1024 * 1024) throw new IOException("编码后的日志超过站点 20 MiB 请求上限");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(bytes);
            }
            bytes = output.toByteArray();
            request.header("Content-Encoding", "gzip");
        }
        // 不声明 br：OkHttp 默认协商并透明解压 gzip，无需引入 Brotli 依赖。
        return request.post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), bytes)).build();
    }

    static Request deleteRequest(SharedLog log) {
        HttpUrl url = HttpUrl.get(log.site.endpoint).newBuilder().addPathSegment(log.id).build();
        return new Request.Builder().url(url).delete()
                .header("User-Agent", "SimpfunAPP/" + BuildConfig.VERSION_NAME)
                .header("Authorization", "Bearer " + log.token).build();
    }

    static JSONObject readResult(Response response) throws IOException, JSONException {
        String body = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
            throw new IOException(ApiError.getMessage(body, "分享站点 HTTP 错误：" + response.code()));
        }
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("分享站点返回了无效的 JSON 响应");
        }
        if (!json.optBoolean("success", false)) {
            throw new IOException(ApiError.getMessage(json, "分享站点处理失败"));
        }
        return json;
    }

    static SharedLog parseSharedLog(Site site, JSONObject json) throws IOException {
        String id = requiredString(json, "id");
        String token = requiredString(json, "token");
        String url = requiredString(json, "url");
        String raw = requiredString(json, "raw");
        if (!isHttpsUrl(url) || !isHttpsUrl(raw)) throw new IOException("分享站点返回的链接无效");
        return new SharedLog(site, id, url, raw, token);
    }

    private static String requiredString(JSONObject json, String key) throws IOException {
        Object value = json.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IOException("分享站点响应缺少 " + key + "，请勿重复提交日志");
        }
        return ((String) value).trim();
    }

    private static boolean isHttpsUrl(String value) {
        HttpUrl url = HttpUrl.parse(value);
        return url != null && "https".equals(url.scheme()) && url.username().isEmpty() && url.password().isEmpty();
    }
}
