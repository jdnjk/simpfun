package cn.jdnjk.simpfun.api.ins;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import cn.jdnjk.simpfun.api.ApiClient;
import cn.jdnjk.simpfun.model.InstanceStatPoint;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StatsApi {
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(List<InstanceStatPoint> points);

        void onFailure(String errorMsg);
    }

    @Nullable
    public Call getStats(Context context, int serverId, Callback callback) {
        if (context == null) {
            postFailure(callback, "Context 不能为空");
            return null;
        }
        if (serverId <= 0) {
            postFailure(callback, "无效的服务器ID");
            return null;
        }
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            postFailure(callback, "未登录，请先登录");
            return null;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/stat");
        if (url == null) {
            postFailure(callback, "URL 解析错误");
            return null;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get()
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (call.isCanceled()) return;
                postFailure(callback, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleResponse(call, response, callback);
            }
        });
        return call;
    }

    private void handleResponse(@NotNull Call call, @NotNull Response response, Callback callback) {
        try {
            String bodyString = Objects.requireNonNull(response.body()).string();
            if (call.isCanceled()) return;

            JSONObject json = new JSONObject(bodyString);
            int code = json.optInt("code", response.code());
            if (code == 200) {
                JSONArray list = json.optJSONArray("list");
                int count = list == null ? 0 : list.length();
                List<InstanceStatPoint> points = new ArrayList<>(count);
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) continue;
                        int cpuPercent = item.optInt("cpu_percent", 0);
                        cpuPercent = Math.max(0, Math.min(100, cpuPercent));
                        points.add(new InstanceStatPoint(
                                item.optLong("uptime", 0L),
                                item.optLong("in_bytes", 0L),
                                item.optLong("out_bytes", 0L),
                                item.optLong("out_remain_bytes", 0L),
                                cpuPercent,
                                item.optLong("mem_used_bytes", 0L),
                                item.optLong("create_time_timestamp", 0L)
                        ));
                    }
                }
                points.sort(Comparator.comparingLong(InstanceStatPoint::getCreateTimeTimestampSeconds));
                if (!call.isCanceled()) {
                    postSuccess(callback, points);
                }
            } else if (code == 429) {
                postFailure(callback, "请求过于频繁，请稍后再试");
            } else if (code == 500) {
                postFailure(callback, "服务器繁忙，请稍后再试");
            } else {
                postFailure(callback, json.optString("msg", "获取统计数据失败"));
            }
        } catch (JSONException ex) {
            if (!call.isCanceled()) postFailure(callback, "数据解析错误");
        } catch (Exception ex) {
            if (!call.isCanceled()) postFailure(callback, "未知错误");
        }
    }

    private void postSuccess(Callback callback, List<InstanceStatPoint> points) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(points));
    }

    private void postFailure(Callback callback, String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }
}
