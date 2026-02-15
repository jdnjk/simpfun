package cn.jdnjk.simpfun.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import cn.jdnjk.simpfun.model.InviteData;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_URL;

public class UserApi {
    private final Context context;
    private final Handler mainHandler;
    private final SharedPreferences UserInfo;

    public interface AuthCallback {
        void onSuccess();
        void onFailure();
    }

    public interface InviteCallback {
        void onSuccess(InviteData data);
        void onFailure(String message);
    }

    public UserApi(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.UserInfo = context.getSharedPreferences("user_info", Context.MODE_PRIVATE);
    }

    public void UserInfo(String authorizationToken) {
        UserInfo(authorizationToken, null);
    }

    public void UserInfo(String authorizationToken, AuthCallback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        Request request = new Request.Builder()
                .url(BASE_URL+"/auth/info")
                .header("Authorization", authorizationToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "请求失败，请稍后再试", Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onFailure();
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        int code = jsonObject.getInt("code");

                        mainHandler.post(() -> {
                            if (code == 200) {
                                try {
                                    saveUserInfo(jsonObject.getJSONObject("info"));
                                    if (callback != null) callback.onSuccess();
                                } catch (JSONException e) {
                                    Toast.makeText(context, "用户信息解析失败", Toast.LENGTH_SHORT).show();
                                    if (callback != null) callback.onFailure();
                                }
                            } else if (code == 403) {
                                Toast.makeText(context, "账号验证失败，请重新登录", Toast.LENGTH_SHORT).show();
                                if (callback != null) callback.onFailure();
                            } else {
                                String msg = jsonObject.optString("msg", "未知错误");
                                Toast.makeText(context, "错误: " + msg, Toast.LENGTH_SHORT).show();
                                if (callback != null) callback.onFailure();
                            }
                        });
                    } catch (JSONException e) {
                        mainHandler.post(() -> {
                            Toast.makeText(context, "数据解析错误", Toast.LENGTH_SHORT).show();
                            if (callback != null) callback.onFailure();
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(context, "网络错误，请稍后再试", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onFailure();
                    });
                }
            }
        });
    }

    private void saveUserInfo(JSONObject userInfo) {
        SharedPreferences.Editor editor = UserInfo.edit();
        try {
            editor.putInt("uid", userInfo.getInt("id"));
            editor.putString("username", userInfo.getString("username"));
            editor.putInt("point", userInfo.getInt("point"));
            editor.putInt("diamond", userInfo.getInt("diamond"));
            editor.putLong("queue_time", userInfo.getLong("queue_time"));
            editor.putBoolean("verified", userInfo.getBoolean("verified"));
            editor.putBoolean("dev", userInfo.getBoolean("is_dev"));
            editor.putLong("qq", userInfo.getLong("qq"));
            editor.putBoolean("pro", userInfo.getBoolean("is_pro"));
            editor.putBoolean("pro_valid", userInfo.getBoolean("pro_valid"));
            JSONObject announcement = userInfo.getJSONObject("announcement");
            editor.putString("announcement_title", announcement.getString("title"));
            editor.putString("announcement_text", announcement.getString("text"));
            editor.putBoolean("announcement_show", announcement.optBoolean("show", false));

            editor.apply();
        } catch (JSONException e) {
            Log.e("UserApi", "保存用户信息时出错: " + e.getMessage());
            Toast.makeText(context, "保存用户信息时出错", Toast.LENGTH_SHORT).show();
        }
    }

    public void getInviteData(String token, InviteCallback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        Request request = new Request.Builder()
                .url(BASE_URL + "/invite")
                .header("Authorization", token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> callback.onFailure("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onFailure("服务器返回错误: " + response.code()));
                    return;
                }
                try {
                    String jsonResponse = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    int code = jsonObject.getInt("code");
                    if (code == 200) {
                        JSONObject dataObj = jsonObject.getJSONObject("data");
                        InviteData data = new InviteData(
                                dataObj.getInt("register_times"),
                                dataObj.getInt("register_verify_times"),
                                dataObj.getInt("register_total_income"),
                                dataObj.getInt("register_total_income_from_pro"),
                                String.valueOf(dataObj.get("invite_code"))
                        );
                        mainHandler.post(() -> callback.onSuccess(data));
                    } else {
                        String msg = jsonObject.optString("msg", "未知错误");
                        mainHandler.post(() -> callback.onFailure(msg));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onFailure("数据处理失败: " + e.getMessage()));
                }
            }
        });
    }

    public void readAnnouncement(String token) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        Request request = new Request.Builder()
                .url(BASE_URL + "/announcement_read")
                .header("Authorization", token)
                .post(RequestBody.create(new byte[0], null))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e("UserApi", "Failed to mark announcement as read: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d("UserApi", "Announcement marked as read");
                }
            }
        });
    }
}