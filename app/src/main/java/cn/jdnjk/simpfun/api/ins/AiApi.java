package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;

import org.json.JSONObject;

import java.io.IOException;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class AiApi {

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    public void getAiHistory(Context context, int serverId, Callback callback) {
        String token = getToken(context);
        if (token == null) {
            callback.onFailure("未登录");
            return;
        }

        String url = BASE_INS_URL + serverId + "/ai_analyze";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get()
                .build();

        executeRequest(request, callback);
    }

    public void postAiAction(Context context, int serverId, String type, String supplement, Callback callback) {
        String token = getToken(context);
        if (token == null) {
            callback.onFailure("未登录");
            return;
        }

        String url = BASE_INS_URL + serverId + "/ai_analyze";
        FormBody.Builder formBody = new FormBody.Builder()
                .add("type", type);
        if (supplement != null) {
            formBody.add("supplement", supplement);
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody.build())
                .build();

        executeRequest(request, callback);
    }

    private String getToken(Context context) {
        SharedPreferences sp = context.getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", null);
    }

    private void executeRequest(Request request, Callback callback) {
        ApiClient.getInstance().getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    int code = json.optInt("code", response.code());
                    if (code == 200) {
                        callback.onSuccess(json);
                    } else {
                        callback.onFailure(json.optString("msg", "错误代码: " + code));
                    }
                } catch (Exception e) {
                    callback.onFailure("解析失败: " + e.getMessage());
                }
            }
        });
    }
}
