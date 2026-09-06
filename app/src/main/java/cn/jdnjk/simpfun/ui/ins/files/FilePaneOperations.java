package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
        /** 批量操作部分失败时展示可滚动的失败清单，而不是塞进一条 toast。 */
        void showFailureReport(String title, int succeeded, List<String> failures);
    }

    private static final String TOOLBOX_FIX_ACTION = "fix_permission_and_charset";
    /** 批量请求的并发上限，避免一次选中几十项时把服务器和连接池打满。 */
    private static final int MAX_CONCURRENT_REQUESTS = 16;
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

    void copyPaths(List<String> paths) {
        Context context = getReadyContext();
        if (context == null || paths == null || paths.isEmpty() || !ensureDeviceId(context) || !beginFileOperation("创建副本")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        List<BatchTask> tasks = new ArrayList<>(paths.size());
        for (String path : paths) {
            tasks.add(new BatchTask(FilePaneState.getFileNameFromPath(path),
                    callback -> new FileApi().copyFileOrFolder(context, deviceId, path, callback)));
        }
        new BatchRunner("创建副本", tasks).start();
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
                host.toast("已发送压缩任务", Toast.LENGTH_SHORT);
                host.reloadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                finishFileOperation();
                if (!host.isActive()) return;
                host.toast("发送压缩任务失败: " + errorMsg, Toast.LENGTH_SHORT);
            }
        });
    }

    void unarchiveItems(List<FileItem> items) {
        Context context = getReadyContext();
        if (context == null || items == null || items.isEmpty() || !ensureDeviceId(context) || !beginFileOperation("解压")) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        String root = state.getCurrentPath();
        List<BatchTask> tasks = new ArrayList<>(items.size());
        for (FileItem item : items) {
            String name = item.getName();
            tasks.add(new BatchTask(name, callback -> new FileApi().unzipFile(context, deviceId, root, name, callback)));
        }
        new BatchRunner("解压", tasks).start();
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

    private interface BatchStep {
        void run(FileApi.Callback callback);
    }

    /** 批量中的一项：label 用于失败清单，step 发起该项的单次请求。 */
    private static final class BatchTask {
        final String label;
        final BatchStep step;

        BatchTask(String label, BatchStep step) {
            this.label = label;
            this.step = step;
        }
    }

    /**
     * 批量执行只支持单项的接口：最多 {@link #MAX_CONCURRENT_REQUESTS} 个请求同时在飞，完成一个补一个，
     * 所以选中多少项都会全部处理，只是并发被限制。单项失败不中断整批，结束后统一汇报失败清单。
     * <p>
     * FileBaseApi 把网络回调都投递到主线程，因此计数字段无需加锁；但参数校验失败会同步回调，
     * 使 {@link #pump()} 重入，故用 pumping 标志把补位交回最外层循环，避免 finish 执行两次。
     */
    private final class BatchRunner {
        private final String operation;
        private final List<BatchTask> tasks;
        private final List<String> failures = new ArrayList<>();
        private int nextIndex;
        private int inFlight;
        private int succeeded;
        private boolean pumping;

        BatchRunner(String operation, List<BatchTask> tasks) {
            this.operation = operation;
            this.tasks = tasks;
        }

        void start() {
            pump();
        }

        private void pump() {
            if (pumping) {
                return;
            }
            pumping = true;
            try {
                while (inFlight < MAX_CONCURRENT_REQUESTS && nextIndex < tasks.size()) {
                    dispatch(tasks.get(nextIndex++));
                }
            } finally {
                pumping = false;
            }
            if (inFlight == 0) {
                finish();
            }
        }

        private void dispatch(BatchTask task) {
            inFlight++;
            task.step.run(new FileApi.Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    succeeded++;
                    onStepDone();
                }

                @Override
                public void onFailure(String errorMsg) {
                    failures.add(task.label + "：" + errorMsg);
                    onStepDone();
                }
            });
        }

        private void onStepDone() {
            inFlight--;
            pump();
        }

        private void finish() {
            finishFileOperation();
            if (!host.isActive()) {
                return;
            }
            if (succeeded > 0) {
                host.clearSelectionAndRender();
                host.reloadFileList();
            }
            if (failures.isEmpty()) {
                host.toast(succeeded > 1 ? "已" + operation + " " + succeeded + " 项" : operation + "成功", Toast.LENGTH_SHORT);
            } else {
                host.showFailureReport(operation + "结果", succeeded, failures);
            }
        }
    }
}
