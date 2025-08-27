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
    private static final String TAG = "FileApi";
    private static final String BASE_URL = "https://api.simpfun.cn/api/ins/";
    private static final String SP_NAME = "token";
    private static final String TOKEN_KEY = "token";

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    public interface DownloadCallback {
        void onSuccess(java.io.File file);
        void onFailure(String errorMsg);
        default void onProgress(int progress) {} // 默认实现，避免需要强制实现进度回调
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

    /**
     * 上传文件到指定目录
     * @param context Context
     * @param serverId 服务器ID
     * @param path 目标目录路径，例如 "/plugins/"
     * @param file 要上传的文件
     * @param callback 回调
     */
    public void uploadFile(Context context, int serverId, String path, java.io.File file, Callback callback) {
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
        if (file == null || !file.exists()) {
            invokeCallback(callback, null, false, "文件不存在");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/upload");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        url = url.newBuilder()
                .addQueryParameter("path", path)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(MediaType.parse("application/octet-stream"), file))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(requestBody)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 下载指定文件并保存到本地
     * @param context Context
     * @param serverId 服务器ID
     * @param path 文件路径，例如 "/plugins/config.yml"
     * @param localFile 本地保存文件的File对象
     * @param downloadCallback 下载进度和结果回调
     */
    public void downloadFileToLocal(Context context, int serverId, String path, java.io.File localFile, DownloadCallback downloadCallback) {
        if (context == null) {
            if (downloadCallback != null) downloadCallback.onFailure("Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            if (downloadCallback != null) downloadCallback.onFailure("无效的服务器ID");
            return;
        }
        if (path == null || path.isEmpty()) {
            if (downloadCallback != null) downloadCallback.onFailure("文件路径不能为空");
            return;
        }
        if (localFile == null) {
            if (downloadCallback != null) downloadCallback.onFailure("本地文件路径不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            if (downloadCallback != null) downloadCallback.onFailure("未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/download");
        if (url == null) {
            if (downloadCallback != null) downloadCallback.onFailure("URL 解析错误");
            return;
        }

        url = url.newBuilder()
                .addQueryParameter("path", path)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (downloadCallback != null) downloadCallback.onFailure("网络请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (downloadCallback != null) downloadCallback.onFailure("HTTP 错误: " + response.code());
                    });
                    return;
                }

                try {
                    if (response.body() != null) {
                        String responseString = response.body().string();

                        JSONObject jsonResponse = new JSONObject(responseString);
                        int code = jsonResponse.optInt("code", -1);

                        if (code == 200) {
                            String downloadLink = jsonResponse.optString("link", null);
                            if (downloadLink != null && !downloadLink.isEmpty()) {
                                downloadFromDirectLink(downloadLink, localFile, downloadCallback);
                            } else {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    if (downloadCallback != null) downloadCallback.onFailure("响应中没有下载链接");
                                });
                            }
                        } else {
                            String errorMsg = jsonResponse.optString("message", "未知错误");
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (downloadCallback != null) downloadCallback.onFailure("服务器错误: " + errorMsg);
                            });
                        }
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (downloadCallback != null) downloadCallback.onFailure("空的响应体");
                        });
                    }
                } catch (JSONException e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (downloadCallback != null) downloadCallback.onFailure("响应解析失败: " + e.getMessage());
                    });
                } catch (Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (downloadCallback != null) downloadCallback.onFailure("下载失败: " + e.getMessage());
                    });
                }
            }
        });
    }

    private void downloadFromDirectLink(String downloadUrl, java.io.File localFile, DownloadCallback downloadCallback) {
        OkHttpClient client = ApiClient.getInstance().getClient();

        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (downloadCallback != null) downloadCallback.onFailure("文件下载失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (downloadCallback != null) downloadCallback.onFailure("文件下载HTTP错误: " + response.code());
                    });
                    return;
                }

                try {
                    java.io.File parentDir = localFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    if (response.body() != null) {
                        long totalBytes = response.body().contentLength();
                        long downloadedBytes = 0;

                        java.io.InputStream inputStream = response.body().byteStream();
                        java.io.BufferedOutputStream outputStream = new java.io.BufferedOutputStream(
                                new java.io.FileOutputStream(localFile));

                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            downloadedBytes += bytesRead;

                            if (totalBytes > 0) {
                                int progress = (int) ((downloadedBytes * 100) / totalBytes);
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    if (downloadCallback != null) downloadCallback.onProgress(progress);
                                });
                            }
                        }

                        outputStream.flush();
                        outputStream.close();
                        inputStream.close();

                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (downloadCallback != null) downloadCallback.onSuccess(localFile);
                        });
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (downloadCallback != null) downloadCallback.onFailure("文件内容为空");
                        });
                    }
                } catch (Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (downloadCallback != null) downloadCallback.onFailure("文件保存失败: " + e.getMessage());
                    });
                }
            }
        });
    }

    /**
     * 删除指定文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param path 要删除的文件或文件夹路径，例如 "/plugins/config.yml" 或 "/plugins/"
     * @param callback 回调
     */
    public void deleteFileOrFolder(Context context, int serverId, String path, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (path == null || path.isEmpty()) {
            invokeCallback(callback, null, false, "文件路径不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/delete");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("path", path)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 执行工具箱操作
     * @param context Context
     * @param serverId 服务器ID
     * @param action 工具箱操作名称，如"fix_permission_and_charset"修复文件权限和中文名
     * @param callback 回调
     */
    public void toolboxOperation(Context context, int serverId, String action, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (action == null || action.isEmpty()) {
            invokeCallback(callback, null, false, "操作名称不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/toolbox");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        // 构建表单数据
        RequestBody formBody = new FormBody.Builder()
                .add("action", action)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 创建文件副本
     * @param context Context
     * @param serverId 服务器ID
     * @param location 源文件路径，例如 "/plugins/A"
     * @param callback 回调
     */
    public void copyFileOrFolder(Context context, int serverId, String location, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (location == null || location.isEmpty()) {
            invokeCallback(callback, null, false, "文件路径不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/copy");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        // 构建表单数据
        RequestBody formBody = new FormBody.Builder()
                .add("location", location)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 剪贴文件
     * @param context Context
     * @param serverId 服务器ID
     * @param fileList 要剪贴的文件列表，例如 ["plugins/111.jar"]
     * @param target 目标目录，例如 "/plugins"
     * @param callback 回调
     */
    public void moveFileOrFolder(Context context, int serverId, String fileList, String target, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (fileList == null || fileList.isEmpty()) {
            invokeCallback(callback, null, false, "文件列表不能为空");
            return;
        }
        if (target == null) {
            target = "/";
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/paste");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("list", fileList)
                .add("target", target)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 压缩文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param root 目录路径
     * @param files 要压缩的文件列表，例如 ["union-1.0-SNAPSHOT-all.jar","union-1.0-SNAPSHOT-all.jar"]
     * @param format 压缩格式，7z或zip
     * @param callback 回调
     */
    public void zipFileOrFolder(Context context, int serverId, String root, String files, String format, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (files == null || files.isEmpty()) {
            invokeCallback(callback, null, false, "文件列表不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/archive");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        FormBody.Builder formBuilder = new FormBody.Builder();
        
        if (root != null && !root.isEmpty()) {
            formBuilder.add("root", root);
        }
        
        formBuilder.add("files", files);
        
        if (format != null && !format.isEmpty()) {
            formBuilder.add("format", format);
        }

        RequestBody formBody = formBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(context, request, callback);
    }

    /**
     * 解压缩文件
     * @param context Context
     * @param serverId 服务器ID
     * @param root 解压目标目录，例如 "/plugins"
     * @param file 要解压的文件名，例如 "compressed2025.7z"
     * @param callback 回调
     */
    public void unzipFile(Context context, int serverId, String root, String file, Callback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (file == null || file.isEmpty()) {
            invokeCallback(callback, null, false, "文件名不能为空");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String token = sp.getString(TOKEN_KEY, null);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/unarchive");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        FormBody.Builder formBuilder = new FormBody.Builder();
        
        if (root != null && !root.isEmpty()) {
            formBuilder.add("root", root);
        }
        
        formBuilder.add("file", file);

        RequestBody formBody = formBuilder.build();

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