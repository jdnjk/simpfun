package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import cn.jdnjk.simpfun.api.ApiClient;

import org.jspecify.annotations.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class PortApi {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PortApi(Context context) {
    }

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }

    /**
     * 购买端口
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void buyPort(String token, int serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }

        RequestBody emptyBody = new FormBody.Builder().build();
        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/allocation")
                .post(emptyBody)
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 设置主端口
     * @param token 用户Token
     * @param serverId 实例ID
     * @param portId 端口内部ID
     * @param callback 回调
     */
    public void setMainPort(String token, int serverId, int portId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (portId <= 0) {
            invokeCallback(callback, null, false, "无效的端口ID");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("port_id", String.valueOf(portId))
                .build();

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/allocation")
                .put(formBody)
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    private void sendRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                mainHandler.post(() -> {
                    String responseBody = null;
                    try {
                        responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");

                        if (code == 200) {
                            invokeCallback(callback, json, true, null);
                        } else {
                            String msg = json.optString("msg", "操作失败");
                            invokeCallback(callback, null, false, msg);
                        }
                    } catch (JSONException e) {
                        invokeCallback(callback, null, false, "数据解析错误");
                    } catch (Exception e) {
                        invokeCallback(callback, null, false, "未知错误");
                    }
                });
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
