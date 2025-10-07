package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
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
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, buildMsg("网络请求失败", e)));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";

                        if (!response.isSuccessful()) {
                            invokeCallback(callback, null, false, "HTTP 错误: " + response.code());
                            return;
                        }

                        JSONObject json = new JSONObject(responseBody);
                        int code = json.getInt("code");

                        if (code == 200) {
                            invokeCallback(callback, json, true, null);
                        } else {
                            String msg = json.optString("msg", "操作失败");
                            if (msg == null || msg.trim().isEmpty() || "null".equalsIgnoreCase(msg.trim())) {
                                msg = "操作失败";
                            }
                            invokeCallback(callback, null, false, msg);
                        }
                    } catch (JSONException e) {
                        invokeCallback(callback, null, false, buildMsg("数据解析错误", e));
                    } catch (Exception e) {
                        invokeCallback(callback, null, false, buildMsg("未知错误", e));
                    }
                });
            }
        });
    }

    private String buildMsg(String prefix, Exception e) {
        String m = (e == null ? null : e.getMessage());
        if (m == null || m.trim().isEmpty() || "null".equalsIgnoreCase(m.trim())) {
            return prefix;
        }
        return prefix + ": " + m;
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
                if (errorMsg == null || errorMsg.trim().isEmpty() || "null".equalsIgnoreCase(errorMsg.trim()) || errorMsg.endsWith(": null")) {
                    errorMsg = "操作失败";
                }
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

        return switch (extension) {
            case "txt", "log" -> "text/plain";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "yml", "yaml" -> "text/yaml";
            case "properties" -> "text/plain";
            case "jar" -> "application/java-archive";
            case "zip" -> "application/zip";
            case "7z" -> "application/x-7z-compressed";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}