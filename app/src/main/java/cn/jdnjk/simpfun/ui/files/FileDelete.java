package cn.jdnjk.simpfun.ui.files;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import cn.jdnjk.simpfun.api.ins.FileApi;
import org.json.JSONObject;

import java.util.List;

public class FileDelete {
    private final FileListFragment fragment;
    
    public FileDelete(FileListFragment fragment) {
        this.fragment = fragment;
    }
    
    public void deleteFiles(List<FileListFragment.FileItem> items) {
        String message = items.size() == 1 ?
                "确定要删除 " + items.get(0).getName() + " 吗？" :
                "确定要删除选中的 " + items.size() + " 个项目吗？";

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext());
        builder.setTitle("删除确认");
        builder.setMessage(message);
        builder.setPositiveButton("删除", (dialog, which) -> executeDelete(items));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeDelete(List<FileListFragment.FileItem> items) {
        SharedPreferences sp = fragment.requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(fragment.requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.List<String> filePaths = new java.util.ArrayList<>();
        for (FileListFragment.FileItem item : items) {
            String filePath = fragment.appendPath(fragment.currentPath, item.getName());
            filePaths.add(filePath);
        }

        new FileApi().deleteFileOrFolderBatch(fragment.requireContext(), deviceId, filePaths, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                fragment.mainHandler.post(() -> {
                    Toast.makeText(fragment.requireContext(), "删除完成", Toast.LENGTH_SHORT).show();
                    fragment.clearAllSelections();
                    fragment.loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                fragment.mainHandler.post(() -> {
                    Toast.makeText(fragment.requireContext(), "删除失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                    fragment.clearAllSelections();
                    fragment.loadFileList();
                });
            }
        });
    }
}