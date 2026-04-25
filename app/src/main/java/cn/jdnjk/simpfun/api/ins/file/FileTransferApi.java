package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.*;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class FileTransferApi extends FileBaseApi {
    
    public interface DownloadCallback {
        void onSuccess(java.io.File file);
        void onFailure(String errorMsg);
        default void onProgress(int progress) {} // 默认实现，避免需要强制实现进度回调
    }

    public interface UploadCallback extends FileCallback {
        void onProgress(long uploadedBytes, long totalBytes);
    }

    public static class UploadHandle {
        private final AtomicBoolean canceled = new AtomicBoolean(false);
        private Call linkCall;
        private Call uploadCall;

        public void cancel() {
            canceled.set(true);
            if (linkCall != null) {
                linkCall.cancel();
            }
            if (uploadCall != null) {
                uploadCall.cancel();
            }
        }

        public boolean isCanceled() {
            return canceled.get();
        }

        private void setLinkCall(Call call) {
            linkCall = call;
            if (canceled.get() && call != null) {
                call.cancel();
            }
        }

        private void setUploadCall(Call call) {
            uploadCall = call;
            if (canceled.get() && call != null) {
                call.cancel();
            }
        }
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
        uploadFileWithProgress(context, serverId, path, file, callback instanceof UploadCallback
                ? (UploadCallback) callback
                : new UploadCallback() {
                    @Override
                    public void onProgress(long uploadedBytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(JSONObject data) {
                        if (callback != null) {
                            callback.onSuccess(data);
                        }
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        if (callback != null) {
                            callback.onFailure(errorMsg);
                        }
                    }
                });
    }

    public UploadHandle uploadFileWithProgress(Context context, int serverId, String path, java.io.File file, UploadCallback callback) {
        UploadHandle handle = new UploadHandle();
        if (context == null) {
            postUploadFailure(callback, "Context 不能为空");
            return handle;
        }
        if (serverId <= 0) {
            postUploadFailure(callback, "无效的服务器ID");
            return handle;
        }
        if (path == null) {
            path = "/";
        }
        if (file == null || !file.exists()) {
            postUploadFailure(callback, "文件不存在");
            return handle;
        }

        final String finalPath = path;

        getUploadLink(context, serverId, handle, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (handle.isCanceled()) {
                    return;
                }
                try {
                    String uploadLink = data.getString("link");
                    uploadFileToLink(uploadLink, finalPath, file, callback, handle);
                } catch (Exception e) {
                    invokeCallback(callback, null, false, "解析上传链接失败: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!handle.isCanceled()) {
                    invokeCallback(callback, null, false, "获取上传地址失败: " + errorMsg);
                }
            }
        });
        return handle;
    }
    
    /**
     * 获取上传地址
     * @param context Context
     * @param serverId 服务器ID
     * @param callback 回调
     */
    private void getUploadLink(Context context, int serverId, UploadHandle handle, FileCallback callback) {
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

        OkHttpClient client = ApiClient.getInstance().getClient();
        Call call = client.newCall(request);
        handle.setLinkCall(call);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (handle.isCanceled()) {
                    return;
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, buildMsg("网络请求失败", e)));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (handle.isCanceled()) {
                    response.close();
                    return;
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        String responseBody = response.body() == null ? "" : response.body().string();

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
    
    /**
     * 使用获取到的链接上传文件
     * @param uploadLink 上传链接
     * @param path 目标目录路径
     * @param file 要上传的文件
     * @param callback 回调
     */
    private void uploadFileToLink(String uploadLink, String path, java.io.File file, UploadCallback callback, UploadHandle handle) {
        try {
            HttpUrl url = HttpUrl.parse(uploadLink);
            if (url == null) {
                invokeCallback(callback, null, false, "上传链接解析错误");
                return;
            }

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

            RequestBody multipartBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("files", file.getName(),
                            RequestBody.create(MediaType.parse(mimeType), file))
                    .build();
            RequestBody requestBody = new ProgressRequestBody(multipartBody, callback, handle);

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            OkHttpClient client = ApiClient.getInstance().getClient();
            Call uploadCall = client.newCall(request);
            handle.setUploadCall(uploadCall);

            uploadCall.enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    if (handle.isCanceled()) {
                        return;
                    }
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, "文件上传失败: " + e.getMessage()));
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    if (handle.isCanceled()) {
                        response.close();
                        return;
                    }
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
    
    private void postUploadFailure(UploadCallback callback, String errorMsg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> invokeCallback(callback, null, false, errorMsg));
    }

    private class ProgressRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final UploadCallback callback;
        private final UploadHandle handle;
        private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        ProgressRequestBody(RequestBody delegate, UploadCallback callback, UploadHandle handle) {
            this.delegate = delegate;
            this.callback = callback;
            this.handle = handle;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return delegate.contentLength();
        }

        @Override
        public void writeTo(@NotNull BufferedSink sink) throws IOException {
            long totalBytes = contentLength();
            Sink countingSink = new ForwardingSink(sink) {
                private long uploadedBytes;

                @Override
                public void write(@NotNull okio.Buffer source, long byteCount) throws IOException {
                    if (handle.isCanceled()) {
                        throw new IOException("上传已取消");
                    }
                    super.write(source, byteCount);
                    uploadedBytes += byteCount;
                    mainHandler.post(() -> {
                        if (!handle.isCanceled()) {
                            callback.onProgress(uploadedBytes, totalBytes);
                        }
                    });
                }
            };
            BufferedSink bufferedSink = Okio.buffer(countingSink);
            delegate.writeTo(bufferedSink);
            bufferedSink.flush();
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