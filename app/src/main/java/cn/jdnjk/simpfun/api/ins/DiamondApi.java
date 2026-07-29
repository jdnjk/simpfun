package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;

import cn.jdnjk.simpfun.api.ApiClient;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class DiamondApi {
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject data);

        void onFailure(String errorMsg);
    }

    public void getDiamondPlan(Context context, int serverId, Callback callback) {
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

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/diamond_plan");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get()
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                postCallback(callback, null, false, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleResponse(response, callback);
            }
        });
    }

    public void applyDiamondPlan(Context context, int serverId, int planId, Callback callback) {
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

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/diamond_plan");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        RequestBody body = new FormBody.Builder()
                .add("plan", String.valueOf(planId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(body)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleResponse(response, callback);
            }
        });
    }

    private void handleResponse(@NotNull Response response, Callback callback) {
        try (Response r = response) {
            ResponseBody rb = r.body();
            String bodyString = rb.string();
            JSONObject json = new JSONObject(bodyString);
            int code = json.optInt("code", r.code());
            if (code == 200) {
                JSONObject data = new JSONObject();
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (!"code".equals(key)) {
                        data.put(key, json.get(key));
                    }
                }
                postCallback(callback, data, true, null);
            } else if (code == 429) {
                postCallback(callback, null, false, "请求过于频繁，请稍后再试");
            } else if (code == 500) {
                postCallback(callback, null, false, "服务器繁忙，请稍后再试");
            } else {
                String msg = json.optString("msg", "请求失败");
                postCallback(callback, null, false, msg);
            }
        } catch (JSONException ex) {
            postCallback(callback, null, false, "数据解析错误");
        } catch (IOException ex) {
            postCallback(callback, null, false, "读取响应失败");
        }
    }

    private void postCallback(Callback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> invokeCallback(callback, data, success, errorMsg));
    }

    private void invokeCallback(Callback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback == null) return;
        if (success) callback.onSuccess(data);
        else callback.onFailure(errorMsg);
    }
}
