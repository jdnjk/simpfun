package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import cn.jdnjk.simpfun.api.ins.file.*;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class FileApi {

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    public interface DownloadCallback {
        void onSuccess(File file);
        void onFailure(String errorMsg);
        default void onProgress(int progress) {}
    }

    private final FileListApi fileListApi = new FileListApi();
    private final FileManageApi fileManageApi = new FileManageApi();
    private final FileTransferApi fileTransferApi = new FileTransferApi();
    private final FileCompressApi fileCompressApi = new FileCompressApi();
    private final FileToolboxApi fileToolboxApi = new FileToolboxApi();
    
    /**
     * 获取指定目录下的文件列表
     * @param context Context
     * @param serverId 服务器ID
     * @param path 要列出的目录路径，例如 "/" 或 "/plugins/"
     * @param callback 回调
     */
    public void getFileList(Context context, int serverId, String path, Callback callback) {
        fileListApi.getFileList(context, serverId, path, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
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
        fileManageApi.createFileOrFolder(context, serverId, mode, root, name, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
    }

    /**
     * 上传文件到指定目录
     * @param context Context
     * @param serverId 服务器ID
     * @param path 目标目录路径，例如 "/plugins/"
     * @param file 要上传的文件
     * @param callback 回调
     */
    public void uploadFile(Context context, int serverId, String path, File file, Callback callback) {
        fileTransferApi.uploadFile(context, serverId, path, file, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
    }

    /**
     * 下载指定文件并保存到本地
     * @param context Context
     * @param serverId 服务器ID
     * @param path 文件路径，例如 "/plugins/config.yml"
     * @param localFile 本地保存文件的File对象
     * @param downloadCallback 下载进度和结果回调
     */
    public void downloadFileToLocal(Context context, int serverId, String path, File localFile, DownloadCallback downloadCallback) {
        fileTransferApi.downloadFileToLocal(context, serverId, path, localFile, new FileTransferApi.DownloadCallback() {
            @Override
            public void onSuccess(File file) {
                downloadCallback.onSuccess(file);
            }

            @Override
            public void onFailure(String errorMsg) {
                downloadCallback.onFailure(errorMsg);
            }

            @Override
            public void onProgress(int progress) {
                downloadCallback.onProgress(progress);
            }
        });
    }

    /**
     * 批量删除指定的文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param filePaths 要删除的文件路径列表，例如 ["/plugins/file1.jar", "/plugins/file2.jar"]
     * @param callback 回调
     */
    public void deleteFileOrFolderBatch(Context context, int serverId, List<String> filePaths, Callback callback) {
        fileManageApi.deleteFileOrFolderBatch(context, serverId, filePaths, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
    }

    /**
     * 执行工具箱操作
     * @param context Context
     * @param serverId 服务器ID
     * @param action 工具箱操作名称，如"fix_permission_and_charset"修复文件权限和中文名
     * @param callback 回调
     */
    public void toolboxOperation(Context context, int serverId, String action, Callback callback) {
        fileToolboxApi.toolboxOperation(context, serverId, action, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
    }

    /**
     * 创建文件副本
     * @param context Context
     * @param serverId 服务器ID
     * @param location 源文件路径，例如 "/plugins/A"
     * @param callback 回调
     */
    public void copyFileOrFolder(Context context, int serverId, String location, Callback callback) {
        fileManageApi.copyFileOrFolder(context, serverId, location, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
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
        fileManageApi.moveFileOrFolder(context, serverId, fileList, target, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
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
        fileCompressApi.zipFileOrFolder(context, serverId, root, files, format, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
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
        fileCompressApi.unzipFile(context, serverId, root, file, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
    }

    /**
     * 重命名文件或文件夹
     * @param context Context
     * @param serverId 服务器ID
     * @param origin 源文件路径，例如 "/plugins/114514.jar"
     * @param target 新文件路径，例如 "/plugins/114.jar"
     * @param callback 回调
     */
    public void renameFile(Context context, int serverId, String origin, String target, Callback callback) {
        fileManageApi.renameFile(context, serverId, origin, target, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                callback.onFailure(errorMsg);
            }
        });
    }
}
