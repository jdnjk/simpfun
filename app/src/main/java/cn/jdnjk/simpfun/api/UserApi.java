package cn.jdnjk.simpfun.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class UserApi {
    private Context context;
    private Handler mainHandler;
    private SharedPreferences UserInfo;

    public UserApi(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.UserInfo = context.getSharedPreferences("user_info", Context.MODE_PRIVATE);
    }

    public void UserInfo(String authorizationToken) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        Request request = new Request.Builder()
                .url("https://api.simpfun.cn/api/auth/info")
                .header("Authorization", authorizationToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "请求失败，请稍后再试", Toast.LENGTH_SHORT).show();
                    navigateToLogin();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        int code = jsonObject.getInt("code");

                        mainHandler.post(() -> {
                            if (code == 200) {
                                try {
                                    saveUserInfo(jsonObject.getJSONObject("info"));
                                    navigateToHomePage();
                                } catch (JSONException e) {
                                    Toast.makeText(context, "用户信息解析失败", Toast.LENGTH_SHORT).show();
                                    navigateToLogin();
                                }
                            } else if (code == 403) {
                                Toast.makeText(context, "账号验证失败，请重新登录", Toast.LENGTH_SHORT).show();
                                navigateToLogin();
                            } else {
                                String msg = jsonObject.optString("msg", "未知错误");
                                Toast.makeText(context, "错误: " + msg, Toast.LENGTH_SHORT).show();
                                navigateToLogin();
                            }
                        });
                    } catch (JSONException e) {
                        mainHandler.post(() -> {
                            Toast.makeText(context, "数据解析错误", Toast.LENGTH_SHORT).show();
                            navigateToLogin();
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(context, "网络错误，请稍后再试", Toast.LENGTH_SHORT).show();
                        navigateToLogin();
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
            editor.putLong("create_time", userInfo.getLong("create_time"));
            editor.putLong("qq", userInfo.getLong("qq"));
            editor.putBoolean("pro", userInfo.getBoolean("is_pro"));
            editor.putBoolean("pro_valid", userInfo.getBoolean("pro_valid"));

            JSONObject announcement = userInfo.getJSONObject("announcement");
            editor.putString("announcement_title", announcement.getString("title"));
            editor.putString("announcement_text", announcement.getString("text"));

            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, "保存用户信息时出错", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(context, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    private void navigateToHomePage() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}