package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;

import cn.jdnjk.simpfun.api.ApiClient;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

/**
 * 实例流量模式变更（PATCH /api/ins/{serverid}/traffic）。
 * <p>
 * 支持的 action：
 * <ul>
 *   <li>auto_reset — 开启自动补充</li>
 *   <li>manual_reset — 关闭自动补充（手动补充）</li>
 *   <li>set_reset_cd — 设置自动补充间隔（分钟），需配合 cd 参数</li>
 *   <li>enable_more_traffic — 切换到普通线路</li>
 *   <li>disable_more_traffic — 切换到精品线路</li>
 * </ul>
 * <p>
 * 注意：本接口切换线路时后端会在 400 中返回可读的 msg（例如
 * 「72小时内仅可调整一次，剩余约71小时58分钟」），因此无论 HTTP
 * 状态码如何都必须读取响应体，把 msg 透传给调用方，不能像
 * MainApi.sendRequest 那样在非 2xx 时直接丢弃 body。
 */
public class TrafficApi {
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject data);

        void onFailure(String errorMsg);
    }

    /**
     * 变更实例流量模式。
     *
     * @param context  上下文（用于读取登录 token）
     * @param serverId 实例 ID
     * @param action   操作类型，见类注释
     * @param callback 回调
     */
    public void changeTrafficMode(Context context, int serverId, String action, Callback callback) {
        changeTrafficMode(context, serverId, action, null, callback);
    }

    /**
     * 变更实例流量模式（带间隔参数）。
     *
     * @param context  上下文（用于读取登录 token）
     * @param serverId 实例 ID
     * @param action   操作类型，见类注释
     * @param cd       自动补充间隔（分钟），action 为 {@code set_reset_cd} 时必填，其余可为 null
     * @param callback 回调
     */
    public void changeTrafficMode(Context context, int serverId, String action, @Nullable Integer cd, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (action == null || action.isEmpty()) {
            invokeCallback(callback, null, false, "操作类型不能为空");
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/traffic");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        FormBody.Builder bodyBuilder = new FormBody.Builder()
                .add("action", action);
        if (cd != null && cd > 0) {
            bodyBuilder.add("cd", String.valueOf(cd));
        }
        RequestBody body = bodyBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .patch(body)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
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
            if (rb == null) {
                postCallback(callback, null, false, "读取响应失败");
                return;
            }
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
