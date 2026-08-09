package cn.jdnjk.simpfun.api;

import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import cn.jdnjk.simpfun.BuildConfig;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.jdnjk.simpfun.model.FirewallCheckResult;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FirewallApi {
    private static final String BASE_URL = "http://simpfun.cn:88/unblock/";
    private static final String CHECK_IP_URL = BASE_URL + "api/check-ip/";
    private static final String UNBLOCK_IP_URL = BASE_URL + "api/unblock-ip/";
    private static final String COOKIE_HEADER = "Cookie";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("name=\"csrfmiddlewaretoken\"\\s+value=\"([^\"]+)\"");
    private static final Pattern DEFAULT_IP_PATTERN =
            Pattern.compile("id=\"ipAddress\"[^>]*value=\"([^\"]*)\"");
    private static final Pattern QUERY_LIMIT_PATTERN =
            Pattern.compile("dailyIpQueriesLimit\\s*=\\s*parseInt\\(\"(\\d+)\"\\)");
    private static final Pattern UNBLOCK_LIMIT_PATTERN =
            Pattern.compile("dailyIpUnblocksLimit\\s*=\\s*parseInt\\(\"(\\d+)\"\\)");

    private static final String BROWSER_FINGERPRINT = buildBrowserFingerprint();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String csrfToken;
    private String csrfCookie;

    private static String buildBrowserFingerprint() {
        try {
            JSONObject fp = new JSONObject();
            fp.put("ua", "SimpfunAPP/"+ BuildConfig.VERSION_NAME);
            fp.put("lang", Locale.getDefault().toLanguageTag());
            fp.put("platform", "Android " + Build.VERSION.RELEASE);
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            fp.put("screen", metrics.widthPixels + "x" + metrics.heightPixels);
            return Base64.encodeToString(fp.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        } catch (JSONException e) {
            return "";
        }
    }

    public static class FirewallSession {
        public final String defaultIp;
        public final int dailyQueriesLimit;
        public final int dailyUnblocksLimit;

        FirewallSession(String defaultIp, int dailyQueriesLimit, int dailyUnblocksLimit) {
            this.defaultIp = defaultIp;
            this.dailyQueriesLimit = dailyQueriesLimit;
            this.dailyUnblocksLimit = dailyUnblocksLimit;
        }
    }

    public interface SessionCallback {
        void onSuccess(FirewallSession session);

        void onFailure(String errorMsg);
    }

    public interface CheckCallback {
        void onSuccess(FirewallCheckResult result);

        void onFailure(String errorMsg);
    }

    public interface UnblockCallback {
        void onSuccess(int dailyUnblocksRemaining);

        void onFailure(String errorMsg);
    }

    private OkHttpClient newClient() {
        return ApiClient.getInstance().getClient().newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public boolean isSessionReady() {
        return csrfToken != null && !csrfToken.isEmpty();
    }

    @Nullable
    public Call fetchSession(SessionCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL)
                .get()
                .build();

        OkHttpClient client = newClient();
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (call.isCanceled()) return;
                postFailure(callback, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try {
                    String body = Objects.requireNonNull(response.body()).string();
                    if (call.isCanceled()) return;
                    if (!response.isSuccessful()) {
                        postFailure(callback, "HTTP " + response.code());
                        return;
                    }
                    csrfToken = findGroup(body, CSRF_TOKEN_PATTERN);
                    String defaultIp = findGroup(body, DEFAULT_IP_PATTERN);
                    csrfCookie = extractCsrfCookie(response.headers("Set-Cookie"));
                    int queryLimit = parseLimit(body, QUERY_LIMIT_PATTERN, 15);
                    int unblockLimit = parseLimit(body, UNBLOCK_LIMIT_PATTERN, 3);
                    if (csrfToken == null || csrfToken.isEmpty()) {
                        postFailure(callback, "无法获取防火墙会话，请稍后再试");
                        return;
                    }
                    FirewallSession session = new FirewallSession(
                            defaultIp == null ? "" : defaultIp, queryLimit, unblockLimit);
                    postSuccess(callback, session);
                } catch (Exception e) {
                    if (!call.isCanceled()) postFailure(callback, "解析防火墙页面失败");
                }
            }
        });
        return call;
    }

    /** 查询 IP 封禁状态。 */
    @Nullable
    public Call checkIp(String ip, CheckCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("ip", ip);
        } catch (JSONException ignored) {
        }
        Request request = new Request.Builder()
                .url(CHECK_IP_URL)
                .post(RequestBody.create(JSON, body.toString()))
                .header("X-CSRFToken", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Browser-Fingerprint", BROWSER_FINGERPRINT)
                .header(COOKIE_HEADER, csrfCookie == null ? "" : csrfCookie)
                .build();

        OkHttpClient client = newClient();
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (call.isCanceled()) return;
                postFailure(callback, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleJsonResponse(call, response, new JsonHandler() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        FirewallCheckResult result = parseCheckResult(json);
                        if (result == null) {
                            postFailure(callback, "查询结果解析失败");
                            return;
                        }
                        mainHandler.post(() -> callback.onSuccess(result));
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        postFailure(callback, errorMsg);
                    }
                });
            }
        });
        return call;
    }

    @Nullable
    public Call unblockIp(String ip, String blockType, UnblockCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("ip", ip);
            body.put("block_type", blockType);
        } catch (JSONException ignored) {
        }
        Request request = new Request.Builder()
                .url(UNBLOCK_IP_URL)
                .post(RequestBody.create(JSON, body.toString()))
                .header("X-CSRFToken", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Browser-Fingerprint", BROWSER_FINGERPRINT)
                .header(COOKIE_HEADER, csrfCookie == null ? "" : csrfCookie)
                .build();

        OkHttpClient client = newClient();
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (call.isCanceled()) return;
                postFailure(callback, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleJsonResponse(call, response, new JsonHandler() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        int remaining = json.optInt("daily_unblocks_remaining", -1);
                        mainHandler.post(() -> callback.onSuccess(remaining));
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        postFailure(callback, errorMsg);
                    }
                });
            }
        });
        return call;
    }

    private interface JsonHandler {
        void onSuccess(JSONObject json);

        void onFailure(String errorMsg);
    }

    private void handleJsonResponse(@NotNull Call call, @NotNull Response response, JsonHandler handler) {
        try {
            String bodyString = Objects.requireNonNull(response.body()).string();
            if (call.isCanceled()) return;
            if (!response.isSuccessful()) {
                handler.onFailure("HTTP " + response.code());
                return;
            }
            JSONObject json = new JSONObject(bodyString);
            if (json.optString("error", "").length() > 0) {
                handler.onFailure(json.optString("error"));
                return;
            }
            handler.onSuccess(json);
        } catch (JSONException ex) {
            if (!call.isCanceled()) handler.onFailure("服务器响应格式错误");
        } catch (Exception ex) {
            if (!call.isCanceled()) handler.onFailure("未知错误");
        }
    }

    private static FirewallCheckResult parseCheckResult(JSONObject json) {
        try {
            return new FirewallCheckResult(
                    json.optString("ip", ""),
                    json.optBoolean("is_blocked", false),
                    json.optString("block_type", ""),
                    json.optString("block_time", ""),
                    json.optString("timeout", ""),
                    json.optInt("daily_queries_remaining", -1),
                    json.optInt("daily_unblocks_remaining", -1)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static String findGroup(String text, Pattern pattern) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int parseLimit(String text, Pattern pattern, int fallback) {
        String value = findGroup(text, pattern);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String extractCsrfCookie(List<String> setCookies) {
        if (setCookies == null) return "";
        for (String header : setCookies) {
            String trimmed = header.trim();
            if (trimmed.startsWith("csrftoken=")) {
                int end = trimmed.indexOf(';');
                return end < 0 ? trimmed : trimmed.substring(0, end);
            }
        }
        return "";
    }

    private void postSuccess(SessionCallback callback, FirewallSession session) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(session));
    }

    private void postFailure(SessionCallback callback, String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }

    private void postFailure(CheckCallback callback, String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }

    private void postFailure(UnblockCallback callback, String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }
}
