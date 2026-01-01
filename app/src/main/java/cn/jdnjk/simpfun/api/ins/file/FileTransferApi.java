package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class FileTransferApi extends FileBaseApi {
    
    public interface DownloadCallback {
        void onSuccess(java.io.File file);
        void onFailure(String errorMsg);
        default void onProgress(int progress) {} // 默认实现，避免需要强制实现进度回调
    }
    
    /**
     * 上传文件到指定目录
     * @param context Context
     * @param serverId 服务器ID
     * @param path 目标目录路径，例如 "/plugins/"
     * @param file 要上传的文件
     * @param callback 回调
     */
    public void uploadFile(Context context, int serverId, String path, java.io.File file, FileCallback callback) {
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

        final String finalPath = path;

        getUploadLink(context, serverId, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    String uploadLink = data.getString("link");
                    uploadFileToLink(uploadLink, finalPath, file, callback);
                } catch (Exception e) {
                    invokeCallback(callback, null, false, "解析上传链接失败: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                invokeCallback(callback, null, false, "获取上传地址失败: " + errorMsg);
            }
        });
    }
    
    /**
     * 获取上传地址
     * @param context Context
     * @param serverId 服务器ID
     * @param callback 回调
     */
    private void getUploadLink(Context context, int serverId, FileCallback callback) {
        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/file/upload");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get() // 使用GET请求获取上传地址
                .build();

        sendRequest(request, callback);
    }
    
    /**
     * 使用获取到的链接上传文件
     * @param uploadLink 上传链接
     * @param path 目标目录路径
     * @param file 要上传的文件
     * @param callback 回调
     */
    private void uploadFileToLink(String uploadLink, String path, java.io.File file, FileCallback callback) {
        try {
            HttpUrl url = HttpUrl.parse(uploadLink);
            if (url == null) {
                invokeCallback(callback, null, false, "上传链接解析错误");
                return;
            }

            // 确保path格式正确
            String formattedPath = path;
            if (formattedPath == null || formattedPath.isEmpty()) {
                formattedPath = "/";
            }
            if (!formattedPath.startsWith("/")) {
                formattedPath = "/" + formattedPath;
            }
            if (!formattedPath.endsWith("/") && !formattedPath.equals("/")) {
                formattedPath = formattedPath + "/";
            }

            url = url.newBuilder()
                    .addQueryParameter("directory", formattedPath)
                    .build();

            String mimeType = getMimeType(file.getName());

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("files", file.getName(),
                            RequestBody.create(MediaType.parse(mimeType), file))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            OkHttpClient client = ApiClient.getInstance().getClient();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "文件上传失败: " + e.getMessage()));
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (!response.isSuccessful()) {
                            try {
                                String errorBody = response.body() != null ? response.body().string() : "";
                                invokeCallback(callback, null, false, "文件上传HTTP错误: " + response.code() + " - " + errorBody);
                            } catch (Exception e) {
                                invokeCallback(callback, null, false, "文件上传HTTP错误: " + response.code());
                            }
                            return;
                        }

                        try {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            if (responseBody.isEmpty()) {
                                JSONObject successResponse = new JSONObject();
                                successResponse.put("code", 200);
                                successResponse.put("message", "文件上传成功");
                                invokeCallback(callback, successResponse, true, null);
                            } else {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                int code = jsonResponse.optInt("code", 200);
                                if (code == 200) {
                                    invokeCallback(callback, jsonResponse, true, null);
                                } else {
                                    String errorMsg = jsonResponse.optString("message", "上传失败");
                                    invokeCallback(callback, null, false, errorMsg);
                                }
                            }
                        } catch (Exception e) {
                            // 如果JSON解析失败，但HTTP状态码成功，认为上传成功
                            try {
                                JSONObject successResponse = new JSONObject();
                                successResponse.put("code", 200);
                                successResponse.put("message", "文件上传成功");
                                invokeCallback(callback, successResponse, true, null);
                            } catch (Exception jsonException) {
                                invokeCallback(callback, null, false, "响应解析失败: " + e.getMessage());
                            }
                        }
                    });
                }
            });

        } catch (Exception e) {
            invokeCallback(callback, null, false, "准备上传请求失败: " + e.getMessage());
        }
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

        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            if (downloadCallback != null) downloadCallback.onFailure("未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/file/download");
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
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (downloadCallback != null) downloadCallback.onFailure("网络请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
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
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (downloadCallback != null) downloadCallback.onFailure("文件下载失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
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
}