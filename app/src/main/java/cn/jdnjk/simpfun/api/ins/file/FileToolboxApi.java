package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

public class FileToolboxApi extends FileBaseApi {
    
    /**
     * 执行工具箱操作
     * @param context Context
     * @param serverId 服务器ID
     * @param action 工具箱操作名称，如"fix_permission_and_charset"修复文件权限和中文名
     * @param callback 回调
     */
    public void toolboxOperation(Context context, int serverId, String action, FileCallback callback) {
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

        String token = getToken(context);
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
}