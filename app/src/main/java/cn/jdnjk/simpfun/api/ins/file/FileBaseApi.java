package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class FileBaseApi {
    protected static final String TAG = "FileApi";
    protected static final String BASE_URL = "https://api.simpfun.cn/api/ins/";
    protected static final String SP_NAME = "token";
    protected static final String TOKEN_KEY = "token";

    protected void sendRequest(Context context, Request request, FileCallback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    String responseBody = null;
                    try {
                        responseBody = response.body() != null ? response.body().string() : "";

                        if (!response.isSuccessful()) {
                            // 处理HTTP错误状态码
                            if (response.code() == 500) {
                                invokeCallback(callback, null, false, "HTTP 错误: 500");
                            } else {
                                invokeCallback(callback, null, false, "HTTP 错误: " + response.code());
                            }
                            return;
                        }

                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");

                        if (code == 200) {
                            // 这里直接返回整个 json，由调用者解析 "list" 数组
                            invokeCallback(callback, json, true, null);
                        } else {
                            String msg = json.optString("msg", "操作失败");
                            invokeCallback(callback, null, false, msg);
                        }
                    } catch (JSONException e) {
                        invokeCallback(callback, null, false, "数据解析错误: " + e.getMessage());
                    } catch (Exception e) {
                        invokeCallback(callback, null, false, "未知错误: " + e.getMessage());
                    }
                });
            }
        });
    }

    protected String getToken(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString(TOKEN_KEY, null);
    }

    protected void invokeCallback(@Nullable FileCallback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(data);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }

    protected String getMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";

        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1).toLowerCase();
        }

        switch (extension) {
            case "txt":
            case "log":
                return "text/plain";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "yml":
            case "yaml":
                return "text/yaml";
            case "properties":
                return "text/plain";
            case "jar":
                return "application/java-archive";
            case "zip":
                return "application/zip";
            case "7z":
                return "application/x-7z-compressed";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "pdf":
                return "application/pdf";
            default:
                return "application/octet-stream";
        }
    }
}