package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import okhttp3.*;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class FilesEditorApi extends FileBaseApi {

    /**
     * 获取文件内容
     * @param context Context
     * @param serverId 服务器ID
     * @param path 文件路径，例如 "/start.sh"
     * @param callback 回调，成功时 data 包含 "content" 字段
     */
    public void fetchFileContent(Context context, int serverId, String path, FileCallback callback) {
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

        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/file/fetch");
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

        sendRequest(request, callback);
    }

    /**
     * 保存文件内容
     * @param context Context
     * @param serverId 服务器ID
     * @param path 文件路径，例如 "/start.sh"
     * @param content 文件内容
     * @param callback 回调
     */
    public void saveFileContent(Context context, int serverId, String path, String content, FileCallback callback) {
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

        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        String url = BASE_INS_URL + serverId + "/file/save";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("path", path)
                .addFormDataPart("content", content != null ? content : "")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(requestBody)
                .build();

        sendRequest(request, callback);
    }
}
