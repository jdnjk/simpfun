package cn.jdnjk.simpfun.api.dev;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.jspecify.annotations.NonNull;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DevApi {
    private static final String TAG = "DevApi";
    private static final String BASE_DEV_URL = ApiClient.BASE_URL + "/dev/";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
        default void onForbidden() {
            onFailure("没有权限使用开发者功能");
        }
    }

    public void getMirrorList(String token, Callback callback) {
        get(token, "list", callback);
    }

    public void getMirrorDetail(String token, int mirrorId, Callback callback) {
        get(token, mirrorId + "/detail", callback);
    }

    public void updateMirrorDetail(String token, int mirrorId, boolean state, String name,
                                   String description, long supportGroup, String link, Callback callback) {
        FormBody body = new FormBody.Builder()
                .add("state", String.valueOf(state))
                .add("name", safe(name))
                .add("description", safe(description))
                .add("support_group", String.valueOf(supportGroup))
                .add("link", safe(link))
                .build();
        post(token, mirrorId + "/detail", body, callback);
    }

    public void deleteMirror(String token, int mirrorId, Callback callback) {
        post(token, mirrorId + "/delete", new FormBody.Builder().build(), callback);
    }

    public void createMirror(String token, long backupId, String name, String description,
                             String version, String versionDescription, String versionRecommendSetting,
                             long supportGroup, String link, String oneKey, String properties,
                             long gameId, Callback callback) {
        FormBody body = new FormBody.Builder()
                .add("backup_id", String.valueOf(backupId))
                .add("name", safe(name))
                .add("description", safe(description))
                .add("version", safe(version))
                .add("version_description", safe(versionDescription))
                .add("version_recommend_setting", safe(versionRecommendSetting))
                .add("support_group", String.valueOf(supportGroup))
                .add("link", safe(link))
                .add("one_key", safe(oneKey))
                .add("properties", safe(properties))
                .add("game_id", String.valueOf(gameId))
                .build();
        post(token, "create", body, callback);
    }

    public void getVersions(String token, int mirrorId, Callback callback) {
        get(token, mirrorId + "/version", callback);
    }

    public void updateVersion(String token, int mirrorId, long versionId, boolean state, String name,
                              String description, String recommendSetting, String oneKey,
                              String properties, Callback callback) {
        FormBody body = new FormBody.Builder()
                .add("version_id", String.valueOf(versionId))
                .add("state", String.valueOf(state))
                .add("name", safe(name))
                .add("description", safe(description))
                .add("recommend_setting", safe(recommendSetting))
                .add("one_key", safe(oneKey))
                .add("properties", safe(properties))
                .build();
        patch(token, mirrorId + "/version", body, callback);
    }

    public void createVersion(String token, int mirrorId, long backupId, String version,
                              String description, String recommendSetting, String oneKey,
                              String properties, Callback callback) {
        FormBody body = new FormBody.Builder()
                .add("backup_id", String.valueOf(backupId))
                .add("version", safe(version))
                .add("description", safe(description))
                .add("recommend_setting", safe(recommendSetting))
                .add("one_key", safe(oneKey))
                .add("properties", safe(properties))
                .build();
        post(token, mirrorId + "/version", body, callback);
    }

    public void deleteVersion(String token, int mirrorId, long versionId, Callback callback) {
        FormBody body = new FormBody.Builder()
                .add("version_id", String.valueOf(versionId))
                .build();
        delete(token, mirrorId + "/version", body, callback);
    }

    public void getFeedback(String token, int mirrorId, Callback callback) {
        get(token, mirrorId + "/feedback", callback);
    }

    public void getAllBackups(String token, Callback callback) {
        HttpUrl url = HttpUrl.parse(BASE_DEV_URL + "backup");
        if (url == null) {
            invoke(callback, null, false, "无效的URL", false);
            return;
        }
        HttpUrl withQuery = url.newBuilder()
                .addQueryParameter("all", "true")
                .addQueryParameter("is_windows", "true")
                .build();
        Request request = newRequest(token, withQuery, null, "GET");
        execute(request, callback);
    }

    private void get(String token, String path, Callback callback) {
        HttpUrl url = HttpUrl.parse(BASE_DEV_URL + path);
        if (url == null) {
            invoke(callback, null, false, "无效的URL", false);
            return;
        }
        execute(newRequest(token, url, null, "GET"), callback);
    }

    private void post(String token, String path, RequestBody body, Callback callback) {
        HttpUrl url = HttpUrl.parse(BASE_DEV_URL + path);
        if (url == null) {
            invoke(callback, null, false, "无效的URL", false);
            return;
        }
        execute(newRequest(token, url, body, "POST"), callback);
    }

    private void patch(String token, String path, RequestBody body, Callback callback) {
        HttpUrl url = HttpUrl.parse(BASE_DEV_URL + path);
        if (url == null) {
            invoke(callback, null, false, "无效的URL", false);
            return;
        }
        execute(newRequest(token, url, body, "PATCH"), callback);
    }

    private void delete(String token, String path, RequestBody body, Callback callback) {
        HttpUrl url = HttpUrl.parse(BASE_DEV_URL + path);
        if (url == null) {
            invoke(callback, null, false, "无效的URL", false);
            return;
        }
        execute(newRequest(token, url, body, "DELETE"), callback);
    }

    private Request newRequest(String token, HttpUrl url, @Nullable RequestBody body, String method) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", token);
        switch (method) {
            case "POST":
                builder.post(body != null ? body : new FormBody.Builder().build());
                break;
            case "PATCH":
                builder.patch(body != null ? body : new FormBody.Builder().build());
                break;
            case "DELETE":
                builder.delete(body != null ? body : new FormBody.Builder().build());
                break;
            default:
                builder.get();
                break;
        }
        return builder.build();
    }

    private void execute(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> invoke(callback, null, false, "网络请求失败: " + e.getMessage(), false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                int httpCode = response.code();
                JSONObject json = null;
                String parseError = null;

                ResponseBody body = response.body();
                if (body == null) {
                    parseError = "响应体为空";
                    Log.e(TAG, "onResponse: body is null, httpCode=" + httpCode);
                } else {
                    try {
                        String responseBody = body.string();
                        if (responseBody.isEmpty()) {
                            parseError = "响应体为空";
                            Log.e(TAG, "onResponse: body string is empty, httpCode=" + httpCode);
                        } else {
                            String preview = responseBody.substring(0, Math.min(200, responseBody.length()));
                            Log.d(TAG, "onResponse body length=" + responseBody.length()
                                    + " bytes/chars, preview=" + preview);
                            json = new JSONObject(responseBody);
                        }
                    } catch (Exception e) {
                        parseError = "数据解析错误";
                        Log.e(TAG, "onResponse parse: " + e.getClass().getName()
                                + " | " + e.getMessage() + " | httpCode=" + httpCode, e);
                    }
                }

                try {
                    response.close();
                } catch (Exception ignored) {
                }

                final int finalHttpCode = httpCode;
                final JSONObject finalJson = json;
                final String finalParseError = parseError;

                mainHandler.post(() -> {
                    if (finalHttpCode == 403) {
                        invoke(callback, null, false, "没有权限使用开发者功能", true);
                        return;
                    }
                    if (finalJson == null) {
                        invoke(callback, null, false,
                                finalParseError != null ? finalParseError : "未知错误", false);
                        return;
                    }
                    int code = finalJson.optInt("code", finalHttpCode);
                    if (code == 403) {
                        invoke(callback, null, false, "没有权限使用开发者功能", true);
                        return;
                    }
                    if (code == 200) {
                        invoke(callback, finalJson, true, null, false);
                    } else {
                        String msg = finalJson.optString("msg", "操作失败");
                        invoke(callback, null, false, msg, false);
                    }
                });
            }
        });
    }

    private void invoke(Callback callback, @Nullable JSONObject response, boolean success,
                        @Nullable String errorMsg, boolean forbidden) {
        if (callback == null) return;
        try {
            if (success) {
                callback.onSuccess(response);
            } else if (forbidden) {
                callback.onForbidden();
            } else {
                callback.onFailure(errorMsg);
            }
        } catch (Exception e) {
            Log.e(TAG, "callback error", e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
