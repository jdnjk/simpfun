package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class TasksApi {
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    public void getTasks(Context context, int serverId, Callback callback) {
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

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/tasks");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                String body;
                try {
                    body = Objects.requireNonNull(response.body()).string();
                    JSONObject json = new JSONObject(body);
                    int code = json.optInt("code", response.code());
                    if (code == 200) {
                        JSONObject data = new JSONObject();
                        for (Iterator<String> it = json.keys(); it.hasNext();) {
                            String key = it.next();
                            if (!"code".equals(key)) {
                                data.put(key, json.get(key));
                            }
                        }
                        new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, data, true, null));
                    } else if (code == 429) {
                        new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "请求过于频繁，请稍后再试"));
                    } else if (code == 500) {
                        new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "服务器繁忙，请稍后再试"));
                    } else {
                        String msg = json.optString("msg", "获取任务失败");
                        new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, msg));
                    }
                } catch (JSONException ex) {
                    new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "数据解析错误"));
                } catch (Exception ex) {
                    new Handler(Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "未知错误"));
                }
            }
        });
    }

    private void invokeCallback(Callback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback == null) return;
        if (success) callback.onSuccess(data); else callback.onFailure(errorMsg);
    }
}
