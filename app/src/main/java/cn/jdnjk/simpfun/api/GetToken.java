package cn.jdnjk.simpfun.api;

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
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_URL;

public class GetToken {

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";

    public interface Callback {
        void onSuccess(String token);
        void onFailure(String errorMsg);
    }

    public GetToken(Context context) {
        this.context = context;
    }

    /**
     * 用户登录
     */
    public void login(String username, String password, Callback callback) {
        if (username == null || username.trim().isEmpty()) {
            invokeCallback(callback, false, "请输入用户名");
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            invokeCallback(callback, false, "请输入密码");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("passwd", password)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/login")
                .post(formBody)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 用户注册
     */
    public void register(String username, String password, @Nullable String inviteCode, Callback callback) {
        if (username == null || username.trim().isEmpty()) {
            invokeCallback(callback, false, "请输入用户名");
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            invokeCallback(callback, false, "请输入密码");
            return;
        }

        FormBody.Builder builder = new FormBody.Builder()
                .add("username", username)
                .add("passwd", password);

        if (inviteCode != null && !inviteCode.trim().isEmpty()) {
            builder.add("invite_code", inviteCode);
        }

        RequestBody formBody = builder.build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/register")
                .post(formBody)
                .build();

        sendRequest(request, callback);
    }

    private void sendRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                mainHandler.post(() -> {
                    String responseBody = null;
                    try {
                        responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");

                        if (code == 200) {
                            String token = json.getString("token");
                            saveToken(token);
                            invokeCallback(callback, true, null, token);
                        } else if (code == 429){
                            String msg = json.optString("msg", "频率超过限制，请稍后再试");
                            invokeCallback(callback, false, msg);
                        } else if (code == 401){
                            String msg = json.optString("msg", "请登录小程序");
                            invokeCallback(callback, false, msg);
                        } else {
                            String msg = json.optString("msg", "未知错误");
                            invokeCallback(callback, false, msg);
                        }
                    } catch (JSONException e) {
                        invokeCallback(callback, false, "数据解析错误");
                    } catch (IOException e) {
                        invokeCallback(callback, false, "读取响应失败");
                    } catch (Exception e) {
                        invokeCallback(callback, false, "未知异常");
                    }
                });
            }
        });
    }

    private void saveToken(String token) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(TOKEN_KEY, token).apply();
    }

    private void invokeCallback(Callback callback, boolean success, String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(null);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }

    private void invokeCallback(Callback callback, boolean success, String errorMsg, String token) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(token);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }
}