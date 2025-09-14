package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

public class FileCompressApi extends FileBaseApi {
    
    /**
     * 压缩文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param root 目录路径
     * @param files 要压缩的文件列表，例如 ["union-1.0-SNAPSHOT-all.jar","union-1.0-SNAPSHOT-all.jar"]
     * @param format 压缩格式，7z或zip
     * @param callback 回调
     */
    public void zipFileOrFolder(Context context, int serverId, String root, String files, String format, FileCallback callback) {
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

        String token = getToken(context);
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
    public void unzipFile(Context context, int serverId, String root, String file, FileCallback callback) {
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

        String token = getToken(context);
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
}