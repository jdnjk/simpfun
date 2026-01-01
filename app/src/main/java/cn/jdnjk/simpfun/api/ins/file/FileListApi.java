package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import okhttp3.HttpUrl;
import okhttp3.Request;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class FileListApi extends FileBaseApi {
    
    /**
     * 获取指定目录下的文件列表
     * @param context Context
     * @param serverId 服务器ID
     * @param path 要列出的目录路径，例如 "/" 或 "/plugins/"
     * @param callback 回调
     */
    public void getFileList(Context context, int serverId, String path, FileCallback callback) {
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

        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/file/list");
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
}