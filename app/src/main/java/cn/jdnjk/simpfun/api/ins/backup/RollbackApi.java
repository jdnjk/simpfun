package cn.jdnjk.simpfun.api.ins.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class RollbackApi {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RollbackApi(Context context) {
    }

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }

    /**
     * 获取回档
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void getRollback(String token, int serverId, Callback callback) {
        if (!validateBaseParams(token, serverId, callback)) {
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/rollback");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    /**
     * 执行回档
     * @param token 用户Token
     * @param serverId 实例ID
     * @param rollbackTime 回档时间
     * @param callback 回调
     */
    public void executeRollback(String token, int serverId, String rollbackTime, Callback callback) {
        if (!validateBaseParams(token, serverId, callback)) {
            return;
        }
        if (rollbackTime == null || rollbackTime.trim().isEmpty()) {
            invokeCallback(callback, null, false, "回档时间不能为空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/rollback");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("rollback_time", rollbackTime)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    private boolean validateBaseParams(String token, int serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return false;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return false;
        }
        return true;
    }

    private void executeJsonRequest(Request request, Callback callback) {
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
                        String responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.optInt("code", response.code());

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
