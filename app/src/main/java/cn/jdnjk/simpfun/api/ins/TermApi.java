package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class TermApi {

    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";
    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    /**
     * 获取 WebSocket 连接信息
     */
    public void getWebSocketInfo(Context context, int serverId, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/ws");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .build();

        sendRequest(request, callback);
    }

    private void sendRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    String responseBody = null;
                    try {
                        responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");

                        if (code == 200) {
                            JSONObject data = json.getJSONObject("data");
                            invokeCallback(callback, data, true, null);
                        } else if (code == 500){
                            String msg = ("由于当前节点负载已到达预设上限，无剩余可用资源，已拒绝了本次的连接，请稍后再试。");
                            invokeCallback(callback, null, false, msg);
                        } else if (code == 429) {
                            String msg = ("请求过于频繁，请稍后再试。");
                            invokeCallback(callback, null, false, msg);
                        } else {
                            String msg = json.optString("msg", "操作失败");
                            invokeCallback(callback, null, false, msg);
                        }
                    } catch (JSONException e) {
                        Log.e("TermApi", "解析错误: " + e.getMessage() + ", Response: " + responseBody);
                        invokeCallback(callback, null, false, "数据解析错误");
                    } catch (Exception e) {
                        Log.e("TermApi", "未知错误: " + e.getMessage() + ", Response: " + responseBody);
                        invokeCallback(callback, null, false, "未知错误");
                    }

                });
            }
        });
    }
    private void invokeCallback(Callback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(data);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }
}