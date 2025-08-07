// File: FileApi.java
package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class FileApi {
    //TODO: 添加文件上传、下载、删除等功能
    private static final String TAG = "FileApi";
    private static final String BASE_URL = "https://api.simpfun.cn/api/ins/";
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    /**
     * 获取指定目录下的文件列表
     * @param context Context
     * @param serverId 服务器ID
     * @param path 要列出的目录路径，例如 "/" 或 "/plugins/"
     * @param callback 回调
     */
    public void getFileList(Context context, int serverId, String path, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (path == null) {
            path = "/";
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/list");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        url = url.newBuilder()
                .addQueryParameter("path", path)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 在指定目录下创建文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param mode "file" 或 "folder"
     * @param root 目标目录，例如 "/plugins/"
     * @param name 要创建的文件或文件夹的名称
     * @param callback 回调
     */
    public void createFileOrFolder(Context context, int serverId, String mode, String root, String name, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (mode == null || (!"file".equals(mode) && !"folder".equals(mode))) {
            invokeCallback(callback, null, false, "无效的 mode，必须是 'file' 或 'folder'");
            return;
        }
        if (root == null || root.isEmpty()) {
            invokeCallback(callback, null, false, "目标目录 (root) 不能为空");
            return;
        }
        if (name == null || name.isEmpty()) {
            invokeCallback(callback, null, false, "名称 (name) 不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/create");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        // 构建表单数据
        RequestBody formBody = new FormBody.Builder()
                .add("mode", mode)
                .add("root", root)
                .add("name", name)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(context, request, callback);
    }
    private void sendRequest(Context context, Request request, Callback callback) {
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
                    if (!response.isSuccessful()) {
                        invokeCallback(callback, null, false, "HTTP 错误: " + response.code());
                        return;
                    }

                    String responseBody = null;
                    try {
                        responseBody = response.body() != null ? response.body().string() : "";
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
    private void invokeCallback(@Nullable Callback callback, @Nullable JSONObject data, boolean success, @Nullable String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(data);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }
}