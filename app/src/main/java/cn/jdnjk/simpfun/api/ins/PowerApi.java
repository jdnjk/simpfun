package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

public class PowerApi {

    private static final String TAG = "PowerApi";
    private static final String BASE_URL = "https://api.simpfun.cn/api/ins/";
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 电源操作常量
    public static class Action {
        public static final String START = "start";
        public static final String STOP = "stop";
        public static final String KILL = "kill";
        public static final String RESTART = "restart";
    }

    public PowerApi(Context context) {
        this.context = context;
    }
    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }
    public void powerControl(String token, int serverId, String action, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (action == null || action.trim().isEmpty()) {
            invokeCallback(callback, null, false, "电源操作不能为空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/power")
                .newBuilder()
                .addQueryParameter("action", action)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", null))
                .header("Authorization", token)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (!response.isSuccessful()) {
                        invokeCallback(callback, null, false, "HTTP 错误: " + response.code());
                        return;
                    }

                    // 读取响应体
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