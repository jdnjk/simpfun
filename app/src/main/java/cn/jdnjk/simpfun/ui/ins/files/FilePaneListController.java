package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.model.FileItem;

class FilePaneListController {
    interface Host {
        Context getContextOrNull();
        boolean isActive();
        int getDeviceId(Context context);
        void showLoading(boolean show);
        void showError(String message);
        void stopRefreshing();
        void onFileListChanged();
    }

    private static final String TAG = "FilePaneListController";
    private final FilePaneState state;
    private final Host host;

    FilePaneListController(FilePaneState state, Host host) {
        this.state = state;
        this.host = host;
    }

    void loadFileList() {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        int deviceId = host.getDeviceId(context);
        if (deviceId <= 0) {
            host.showError("设备ID无效");
            host.stopRefreshing();
            return;
        }

        String requestPath = state.getCurrentPath();
        host.showLoading(true);
        new FileApi().getFileList(context, deviceId, requestPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isCurrentRequest(requestPath)) {
                    return;
                }
                host.stopRefreshing();
                host.showLoading(false);
                try {
                    updateFileList(data.getJSONArray("list"), requestPath);
                } catch (Exception e) {
                    host.showError("解析失败:" + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentRequest(requestPath)) {
                    return;
                }
                host.stopRefreshing();
                host.showLoading(false);
                host.showError(errorMsg);
            }
        });
    }

    private void updateFileList(JSONArray list, String requestPath) {
        List<FileItem> items = new ArrayList<>();
        if (!state.isAtRoot()) {
            items.add(new FileItem(FileItem.PARENT_DIR_NAME, false, 0, "", ""));
        }

        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject obj = list.getJSONObject(i);
                String name = obj.getString("name");
                if ("..".equals(name) || ".".equals(name)) {
                    continue;
                }
                items.add(new FileItem(
                        name,
                        obj.getBoolean("file"),
                        obj.optLong("size", 0L),
                        obj.optString("mime", ""),
                        obj.optString("modified_at", "")
                ));
            } catch (Exception e) {
                Log.e(TAG, "文件解析失败", e);
            }
        }

        state.replaceFileList(items);
        state.clearSelection();
        host.onFileListChanged();
    }

    private boolean isCurrentRequest(String requestPath) {
        return host.isActive() && requestPath.equals(state.getCurrentPath());
    }
}
