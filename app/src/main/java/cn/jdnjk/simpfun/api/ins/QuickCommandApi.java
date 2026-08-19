package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class QuickCommandApi {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONArray commands);
        void onFailure(String errorMsg);
    }

    public void getOneKeyCommands(Context context, int serverId, Callback callback) {
        SharedPreferences sp = context.getSharedPreferences("token", Context.MODE_PRIVATE);
        String token = sp.getString("token", null);
        if (token == null) {
            mainHandler.post(() -> callback.onFailure("未登录"));
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/onekeycommand")
                .newBuilder()
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get()
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> callback.onFailure("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                mainHandler.post(() -> {
                    try {
                        String responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");
                        if (code == 200) {
                            String dataStr = json.optString("data", "[]").trim();
                            JSONArray commands = dataStr.isEmpty() ? new JSONArray() : new JSONArray(dataStr);
                            callback.onSuccess(commands);
                        } else {
                            String msg = json.optString("msg", "获取指令失败");
                            callback.onFailure(msg);
                        }
                    } catch (Exception e) {
                        callback.onFailure("解析失败: " + e.getMessage());
                    }
                });
            }
        });
    }
}