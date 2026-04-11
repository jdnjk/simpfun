package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;

public class MainApi {
    private static final String TAG = "MainApi";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MainApi(Context context) {
    }

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
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

        sendRequest(request, callback, parseDeviceId(serverId), null);
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

        sendRequest(request, callback, parseDeviceId(serverId), newName);
    }

    /**
     * 删除实例
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void deleteInstance(String token, String serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, false, "Token 不能为空");
            return;
        }
        if (serverId == null || serverId.trim().isEmpty()) {
            invokeCallback(callback, false, "Server ID 不能为空");
            return;
        }

        RequestBody emptyBody = new FormBody.Builder().build();
        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/delete")
                .post(emptyBody)
                .header("Authorization", token)
                .build();

        Integer deviceId = parseDeviceId(serverId);
        sendRequest(request, new Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (deviceId != null) {
                    InstanceDetailStore.getInstance().clear(deviceId);
                }
                if (callback != null) {
                    callback.onSuccess(data);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (callback != null) {
                    callback.onFailure(errorMsg);
                }
            }
        });
    }

    /**
     * 获取 SFTP 信息
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void getSftp(String token, String serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, false, "Token 不能为空");
            return;
        }
        if (serverId == null || serverId.trim().isEmpty()) {
            invokeCallback(callback, false, "Server ID 不能为空");
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/sftp")
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    private void sendRequest(Request request, Callback callback) {
        sendRequest(request, callback, null, null);
    }

    private void sendRequest(Request request, Callback callback, Integer cacheDeviceId, String renamedName) {
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

                            if (cacheDeviceId != null) {
                                if (renamedName != null) {
                                    InstanceDetailStore.getInstance().updateName(cacheDeviceId, renamedName);
                                } else {
                                    InstanceDetailStore.getInstance().put(cacheDeviceId, data);
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

    private Integer parseDeviceId(String serverId) {
        try {
            return Integer.parseInt(serverId);
        } catch (NumberFormatException e) {
            return null;
        }
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