package cn.jdnjk.simpfun.api.ins;

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

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

/**
 * 拉取服务器配置文件的可视化编辑定义。
 *
 * <p>接口：GET /api/ins/{serverId}/properties，返回配置文件定义数组，
 * 每个元素含 file / name / notice / exps（字段定义）。
 */
public class PropertiesApi {
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    /**
     * 获取配置定义。
     *
     * @param context Context
     * @param serverId 服务器ID
     * @param callback 回调，成功时 data 的 "list" 字段为配置定义数组
     */
    public void getConfigDefinitions(Context context, int serverId, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/properties");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    private void executeRequest(Request request, Callback callback) {
        ApiClient.getInstance().getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String body = response.body().string();
                if (!response.isSuccessful()) {
                    invokeCallback(callback, null, false, "HTTP 错误: " + response.code());
                    return;
                }
                try {
                    // 兼容多种形态：
                    // 1) 裸 JSONArray
                    // 2) {code, data/list/content} 扁平包，data 可能是 JSONArray 或字符串化的 JSONArray
                    JSONArray array = null;
                    try {
                        JSONObject obj = new JSONObject(body);
                        int code = obj.optInt("code", 200);
                        if (code != 200) {
                            invokeCallback(callback, null, false, obj.optString("msg", "操作失败"));
                            return;
                        }
                        array = extractArray(obj, "data");
                        if (array == null) array = extractArray(obj, "list");
                        if (array == null) array = extractArray(obj, "content");
                    } catch (JSONException e) {
                        // 不是对象 → 尝试裸数组
                        try {
                            array = new JSONArray(body);
                        } catch (JSONException ignored) {
                        }
                    }
                    if (array == null) {
                        // 没有可解析的数组（可能 data 为空字符串 / null）
                        array = new JSONArray(); // 视为空列表，调用方据此隐藏入口
                    }
                    JSONObject data = new JSONObject();
                    data.put("list", array);
                    invokeCallback(callback, data, true, null);
                } catch (JSONException e) {
                    invokeCallback(callback, null, false, "数据解析错误");
                } catch (Exception e) {
                    invokeCallback(callback, null, false, "未知错误: " + e.getMessage());
                }
            }
        });
    }

    /** 从 obj 中按 key 取数组；兼容 value 本身是数组、或 value 是字符串化的数组。 */
    @Nullable
    private JSONArray extractArray(JSONObject obj, String key) {
        if (obj.isNull(key)) return null;
        Object v = obj.opt(key);
        if (v instanceof JSONArray) {
            return (JSONArray) v;
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
            try {
                return new JSONArray(s);
            } catch (JSONException ignored) {
                return null;
            }
        }
        return null;
    }

    private void invokeCallback(@Nullable Callback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback == null) return;
        Handler main = new Handler(Looper.getMainLooper());
        if (success) {
            JSONObject finalData = data;
            main.post(() -> callback.onSuccess(finalData));
        } else {
            String msg = errorMsg == null || errorMsg.trim().isEmpty() ? "操作失败" : errorMsg;
            main.post(() -> callback.onFailure(msg));
        }
    }

    private String getToken(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString(TOKEN_KEY, null);
    }
}
