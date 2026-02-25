package cn.jdnjk.simpfun.api;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.jspecify.annotations.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

public class PointApi {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String BASE_URL = "https://api.simpfun.cn/api/";

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }

    /**
     * 获取积分历史
     */
    public void getPointHistory(@NonNull String token, @NonNull Callback callback) {
        requestApi(token, "pointhistory", callback);
    }

    /**
     * 获取钻石历史
     */
    public void getDiamondHistory(@NonNull String token, @NonNull Callback callback) {
        requestApi(token, "diamondhistory", callback);
    }

    /**
     * 通用请求方法
     */
    private void requestApi(@NonNull String token, @NonNull String endpoint, @NonNull Callback callback) {
        if (token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (endpoint.trim().isEmpty()) {
            invokeCallback(callback, null, false, "接口地址不能为空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + endpoint);
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                mainHandler.post(() -> {
                    try {
                        String body = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(body);
                        int code = json.optInt("code", 0);
                        if (code == 200) {
                            invokeCallback(callback, json, true, null);
                        } else {
                            String msg = json.optString("msg", "请求失败");
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
