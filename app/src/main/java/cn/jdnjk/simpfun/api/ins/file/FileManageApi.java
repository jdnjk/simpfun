package cn.jdnjk.simpfun.api.ins.file;

import android.content.Context;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

import java.util.List;

public class FileManageApi extends FileBaseApi {
    
    /**
     * 在指定目录下创建文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param mode "file" 或 "folder"
     * @param root 目标目录，例如 "/plugins/"
     * @param name 要创建的文件或文件夹的名称
     * @param callback 回调
     */
    public void createFileOrFolder(Context context, int serverId, String mode, String root, String name, FileCallback callback) {
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

        String token = getToken(context);
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
     * 重命名文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param origin 源文件路径，例如 "/plugins/114514.jar"
     * @param target 新文件路径，例如 "/plugins/114.jar"
     * @param callback 回调
     */
    public void renameFile(Context context, int serverId, String origin, String target, FileCallback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (origin == null || origin.isEmpty()) {
            invokeCallback(callback, null, false, "源文件路径不能为空");
            return;
        }
        if (target == null || target.isEmpty()) {
            invokeCallback(callback, null, false, "目标文件路径不能为空");
            return;
        }

        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/rename");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("origin", origin)
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
     * 批量删除指定的文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param filePaths 要删除的文件路径列表，例如 ["/plugins/file1.jar", "/plugins/file2.jar"]
     * @param callback 回调
     */
    public void deleteFileOrFolderBatch(Context context, int serverId, List<String> filePaths, FileCallback callback) {
        if (context == null) {
            invokeCallback(callback, null, false, "Context 不能为空");
            return;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return;
        }
        if (filePaths == null || filePaths.isEmpty()) {
            invokeCallback(callback, null, false, "文件路径列表不能为空");
            return;
        }

        String token = getToken(context);
        if (token == null || token.isEmpty()) {
            invokeCallback(callback, null, false, "未登录，请先登录");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL + serverId + "/file/delete");
        if (url == null) {
            invokeCallback(callback, null, false, "URL 解析错误");
            return;
        }

        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(filePaths);
            String listString = jsonArray.toString();

            RequestBody formBody = new FormBody.Builder()
                    .add("list", listString)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", token)
                    .post(formBody)
                    .build();

            sendRequest(context, request, callback);
        } catch (Exception e) {
            invokeCallback(callback, null, false, "构建请求数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建文件副本
     * @param context Context
     * @param serverId 服务器ID
     * @param location 源文件路径，例如 "/plugins/A"
     * @param callback 回调
     */
    public void copyFileOrFolder(Context context, int serverId, String location, FileCallback callback) {
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

        String token = getToken(context);
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
    public void moveFileOrFolder(Context context, int serverId, String fileList, String target, FileCallback callback) {
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

        String token = getToken(context);
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
}