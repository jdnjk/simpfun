package cn.jdnjk.simpfun.api.ins;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class PlanAPI {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }

    /**
     * 列出计划
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void listPlans(String token, int serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/delaysend");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 创建计划
     * @param token 用户Token
     * @param serverId 实例ID
     * @param command 命令
     * @param time 时间（格式：yyyy-MM-dd HH:mm）
     * @param interval 间隔时间（秒）
     * @param callback 回调
     */
    public void createPlan(String token, int serverId, String command, String time, @Nullable Integer interval, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (command == null || command.trim().isEmpty()) {
            invokeCallback(callback, null, false, "命令不能为空");
            return;
        }
        if (time == null || time.trim().isEmpty()) {
            invokeCallback(callback, null, false, "时间不能为空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/delaysend");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("command", command)
                .add("time", time);

        if (interval != null) {
            formBuilder.add("interval", String.valueOf(interval));
        }

        Request request = new Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 删除计划
     * @param token 用户Token
     * @param serverId 实例ID
     * @param schedulerId 计划ID
     * @param callback 回调
     */
    public void deletePlan(String token, int serverId, int schedulerId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (schedulerId <= 0) {
            invokeCallback(callback, null, false, "无效的计划ID");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/delaysend");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("scheduler_id", String.valueOf(schedulerId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .delete(formBody)
                .header("Authorization", token)
                .build();

        executeRequest(request, callback);
    }

    private void executeRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String responseBody;
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        mainHandler.post(() -> invokeCallback(callback, null, false, "响应为空"));
                        return;
                    }
                    responseBody = body.string();
                    JSONObject json = new JSONObject(responseBody);
                    int code = json.getInt("code");

                    if (code == 200) {
                        mainHandler.post(() -> invokeCallback(callback, json, true, null));
                    } else {
                        String msg = json.optString("msg", "操作失败");
                        mainHandler.post(() -> invokeCallback(callback, null, false, msg));
                    }
                } catch (JSONException e) {
                    mainHandler.post(() -> invokeCallback(callback, null, false, "数据解析错误"));
                } catch (Exception e) {
                    mainHandler.post(() -> invokeCallback(callback, null, false, "未知错误"));
                }
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
