package cn.jdnjk.simpfun.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import okhttp3.*;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;
import static cn.jdnjk.simpfun.api.ApiClient.BASE_URL;

public class MainApi {

    private static final String TAG = "MainApi";
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MainApi(Context context) {
        this.context = context;
    }

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    /**
     * 获取实例列表
     */
    public void getInstanceList(String token, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, false, "Token 不能为空");
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_INS_URL + "list")
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 获取实例信息
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void getInstanceDetail(String token, String serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, false, "Token 不能为空");
            return;
        }
        if (serverId == null || serverId.trim().isEmpty()) {
            invokeCallback(callback, false, "Server ID 不能为空");
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/detail")
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 重命名实例
     * @param token 用户Token
     * @param serverId 实例ID
     * @param newName 新名称
     * @param callback 回调
     */
    public void renameInstance(String token, String serverId, String newName, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, false, "Token 不能为空");
            return;
        }
        if (serverId == null || serverId.trim().isEmpty()) {
            invokeCallback(callback, false, "Server ID 不能为空");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("name", newName == null ? "" : newName)
                .build();

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/rename")
                .post(formBody)
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 绑定 QQ 号
     */
    public void bindQQ(String token, long qqNumber, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, false, "Token 不能为空");
            return;
        }

        if (qqNumber <= 0) {
            invokeCallback(callback, false, "QQ 号码无效");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("qq", String.valueOf(qqNumber))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/bindqq")
                .post(formBody)
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }
    private void sendRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "Request failed", e);
                mainHandler.post(() -> invokeCallback(callback, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                mainHandler.post(() -> {
                    if (!response.isSuccessful()) {
                        invokeCallback(callback, false, "HTTP 错误: " + response.code());
                        return;
                    }

                    String responseBody = null;
                    try {
                        responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");

                        if (code == 200) {
                            JSONObject data = new JSONObject();
                            Iterator<String> keys = json.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                if (!"code".equals(key)) {
                                    data.put(key, json.get(key));
                                }
                            }
                            invokeCallback(callback, true, null, data);
                        } else {
                            String msg = json.optString("msg", "操作失败");
                            invokeCallback(callback, false, msg);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parse error: " + responseBody, e);
                        invokeCallback(callback, false, "数据解析错误");
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error", e);
                        invokeCallback(callback, false, "未知错误");
                    }
                });
            }
        });
    }

    private void invokeCallback(Callback callback, boolean success, String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(new JSONObject());
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }

    private void invokeCallback(Callback callback, boolean success, String errorMsg, JSONObject data) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(data);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }
}