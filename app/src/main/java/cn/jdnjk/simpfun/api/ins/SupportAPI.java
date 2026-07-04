package cn.jdnjk.simpfun.api.ins;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class SupportAPI {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }

    /**
     * 创建技术支持
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void CreateSupport(String token, int serverId, String comment, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/support");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        RequestBody body = new FormBody.Builder()
                .add("comment", comment)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 技术支持联系
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void GetSupport(String token, int serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/support");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 结束技术支持
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void StopSupport(String token, int serverId, String comment, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (comment == null) {
            invokeCallback(callback, null, false, "不能放空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/support");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        RequestBody body = new FormBody.Builder()
                .add("feedback", comment)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .delete(body)
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 评价
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void Rating(String token, int serverId, boolean like, String comment, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (comment == null) {
            invokeCallback(callback, null, false, "不要放空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/rating");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }
        RequestBody body = new FormBody.Builder()
                .add("like", String.valueOf(like))
                .add("dislike", String.valueOf(!like))
                .add("comment", comment)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    private void executeRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String responseBody;
                try (ResponseBody body = response.body()) {
                    responseBody = body.string();
                    JSONObject json = new JSONObject(responseBody);
                    int code = json.getInt("code");

                    if (code == 200) {
                        mainHandler.post(() -> invokeCallback(callback, json, true, null));
                    } else {
                        String msg = json.optString("msg", "操作失败");
                        mainHandler.post(() -> invokeCallback(callback, null, false, msg));
                    }
                } catch (JSONException e) {
                    mainHandler.post(() -> invokeCallback(callback, null, false, "数据解析错误"));
                } catch (Exception e) {
                    mainHandler.post(() -> invokeCallback(callback, null, false, "未知错误"));
                }
            }
        });
    }

    private void invokeCallback(Callback callback, @Nullable JSONObject response, boolean success, @Nullable String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(response);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }
}
