package cn.jdnjk.simpfun.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.model.AnnouncementItem;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_URL;

public class AnnouncementApi {

    private static final String TAG = "AnnouncementApi";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(List<AnnouncementItem> list);
        void onFailure(String errorMsg);
    }

    public void getAnnouncements(String token, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + "/announcement");
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", token);
        }
        Request request = builder.build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure("网络请求失败: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                JSONObject json = null;
                String parseError = null;
                int httpCode = response.code();
                try {
                    if (response.body() != null) {
                        String body = response.body().string();
                        if (!body.isEmpty()) {
                            json = new JSONObject(body);
                        }
                    }
                } catch (Exception e) {
                    parseError = "数据解析错误";
                    Log.e(TAG, "onResponse parse: " + e.getClass().getName() + " " + e.getMessage());
                } finally {
                    try {
                        response.close();
                    } catch (Exception ignored) {
                    }
                }

                if (json == null) {
                    final String err = parseError != null ? parseError : "未知错误";
                    mainHandler.post(() -> {
                        if (callback != null) callback.onFailure(err);
                    });
                    return;
                }

                int code = json.optInt("code", httpCode);
                if (code == 200) {
                    List<AnnouncementItem> list = new ArrayList<>();
                    try {
                        JSONArray arr = json.getJSONArray("list");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            AnnouncementItem item = new AnnouncementItem(
                                    obj.optInt("id", -1),
                                    obj.optString("title", ""),
                                    obj.optString("text", ""),
                                    obj.optString("create_time", ""),
                                    obj.optInt("read_times", 0)
                            );
                            list.add(item);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "parse list error: " + e.getMessage());
                    }
                    final List<AnnouncementItem> result = list;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(result);
                    });
                } else {
                    final String msg = json.optString("msg", "请求失败");
                    mainHandler.post(() -> {
                        if (callback != null) callback.onFailure(msg);
                    });
                }
            }
        });
    }
}
