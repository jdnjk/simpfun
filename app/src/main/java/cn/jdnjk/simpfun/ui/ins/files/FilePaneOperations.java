package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.FilePathUtils;

class FilePaneOperations {
    interface Host {
        Context getContextOrNull();
        boolean isActive();
        int getDeviceId(Context context);
        void clearSelectionAndRender();
        void clearPendingMoveAndRender();
        void reloadFileList();
        void toast(String message, int length);
    }

    private static final String TOOLBOX_FIX_ACTION = "fix_permission_and_charset";
    private final FilePaneState state;
    private final Host host;
    private String inFlightOperation;

    FilePaneOperations(FilePaneState state, Host host) {
        this.state = state;
        this.host = host;
    }

    void createEntry(String mode, String name) {
        Context context = getReadyContext();
        if (context == null || !ensureDeviceId(context) || !beginFileOperation("创建")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        new FileApi().createFileOrFolder(context, deviceId, mode, state.getCurrentPath(), name, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.clearSelectionAndRender();
                host.toast("创建成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("创建失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void deletePaths(List<String> paths) {
        Context context = getReadyContext();
        if (context == null || paths.isEmpty() || !ensureDeviceId(context) || !beginFileOperation("删除")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        new FileApi().deleteFileOrFolderBatch(context, deviceId, paths, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.clearSelectionAndRender();
                host.toast("删除成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("删除失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void renameFile(FileItem item, String newName) {
        Context context = getReadyContext();
        if (context == null || !ensureDeviceId(context) || !beginFileOperation("重命名")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        String origin = FilePathUtils.appendPath(state.getCurrentPath(), item.getName());
        String target = FilePathUtils.appendPath(state.getCurrentPath(), newName);
        new FileApi().renameFile(context, deviceId, origin, target, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.clearSelectionAndRender();
                host.toast("重命名成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("重命名失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void copyFileOrFolder(FileItem item) {
        Context context = getReadyContext();
        if (context == null || !ensureDeviceId(context) || !beginFileOperation("创建副本")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        new FileApi().copyFileOrFolder(context, deviceId, state.getItemPath(item), new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("副本创建成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("创建副本失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void movePendingToCurrentPath() {
        Context context = getReadyContext();
        List<String> pendingMovePaths = state.copyPendingMovePaths();
        if (context == null || pendingMovePaths.isEmpty()) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        if (deviceId <= 0) {
            host.toast(context.getString(R.string.invalid_device_id), Toast.LENGTH_SHORT);
            return;
        }
        String validationError = state.validateMoveTarget(state.getCurrentPath());
        if (validationError != null) {
            host.toast(validationError, Toast.LENGTH_SHORT);
            return;
        }
        if (!beginFileOperation("移动")) {
            return;
        }

        String fileListJson = new JSONArray(pendingMovePaths).toString();
        new FileApi().moveFileOrFolder(context, deviceId, fileListJson, state.getCurrentPath(), new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.clearPendingMoveAndRender();
                host.toast("移动成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("移动失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void archivePaths(List<String> paths, String format) {
        Context context = getReadyContext();
        if (context == null || paths.isEmpty()) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        if (deviceId <= 0) {
            host.toast(context.getString(R.string.invalid_device_id), Toast.LENGTH_SHORT);
            return;
        }
        List<String> names = state.toCurrentDirectoryNames(paths);
        if (names.isEmpty()) {
            host.toast("没有可压缩的文件", Toast.LENGTH_SHORT);
            return;
        }
        if (!beginFileOperation("压缩")) {
            return;
        }

        new FileApi().zipFileOrFolder(context, deviceId, state.getCurrentPath(), new JSONArray(names).toString(), format, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.clearSelectionAndRender();
                host.toast("压缩成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("压缩失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void unarchiveFile(FileItem item) {
        Context context = getReadyContext();
        if (context == null || !ensureDeviceId(context) || !beginFileOperation("解压")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        new FileApi().unzipFile(context, deviceId, state.getCurrentPath(), item.getName(), new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("解压成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("解压失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void runToolboxFix() {
        Context context = getReadyContext();
        if (context == null || !ensureDeviceId(context) || !beginFileOperation("工具箱操作")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        new FileApi().toolboxOperation(context, deviceId, TOOLBOX_FIX_ACTION, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("修复成功", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("修复失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    private Context getReadyContext() {
        return host.getContextOrNull();
    }

    private boolean ensureDeviceId(Context context) {
        if (host.getDeviceId(context) > 0) {
            return true;
        }
        host.toast(context.getString(R.string.invalid_device_id), Toast.LENGTH_SHORT);
        return false;
    }

    private boolean beginFileOperation(String operation) {
        if (inFlightOperation != null) {
            host.toast(inFlightOperation + "正在进行", Toast.LENGTH_SHORT);
            return false;
        }
        inFlightOperation = operation;
        return true;
    }

    private void finishFileOperation() {
        inFlightOperation = null;
    }
}
