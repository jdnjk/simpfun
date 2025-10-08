package cn.jdnjk.simpfun.ui.files;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;
import cn.jdnjk.simpfun.api.ins.FileApi;
import org.json.JSONObject;

import java.util.List;

public class FileCopy {
    private final FileListFragment fragment;
    
    public FileCopy(FileListFragment fragment) {
        this.fragment = fragment;
    }

    public void cutFiles(List<FileListFragment.FileItem> items) {
        fragment.cutFilesPaths.clear();
        for (FileListFragment.FileItem item : items) {
            String filePath = fragment.appendPath(fragment.currentPath, item.getName());
            fragment.cutFilesPaths.add(filePath);
        }
        fragment.cutSourcePath = fragment.currentPath;

        Toast.makeText(fragment.requireContext(), "已剪切 " + items.size() + " 个项目，请到目标文件夹粘贴", Toast.LENGTH_SHORT).show();
        fragment.clearAllSelections();
    }

    public void copyFile(FileListFragment.FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext());
        builder.setTitle("创建副本");

        EditText input = new EditText(fragment.requireContext());
        String baseName = item.getName();
        int dotIndex = baseName.lastIndexOf('.');
        String newName = dotIndex > 0 ?
                baseName.substring(0, dotIndex) + "_副本" + baseName.substring(dotIndex) :
                baseName + "_副本";
        input.setText(newName);
        builder.setView(input);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                executeCopyFile(item, fileName);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeCopyFile(FileListFragment.FileItem item, String newName) {
        SharedPreferences sp = fragment.requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(fragment.requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        String sourceFilePath = fragment.appendPath(fragment.currentPath, item.getName());

        new FileApi().copyFileOrFolder(fragment.requireContext(), deviceId, sourceFilePath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                fragment.mainHandler.post(() -> {
                    Toast.makeText(fragment.requireContext(), "创建副本成功", Toast.LENGTH_SHORT).show();
                    fragment.clearAllSelections();
                    fragment.loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                fragment.mainHandler.post(() -> Toast.makeText(fragment.requireContext(), "创建副本失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    public void pasteFiles() {
        if (fragment.cutFilesPaths.isEmpty()) {
            Toast.makeText(fragment.requireContext(), "没有可粘贴的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fragment.currentPath.equals(fragment.cutSourcePath)) {
            Toast.makeText(fragment.requireContext(), "不能粘贴到同一文件夹", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sp = fragment.requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(fragment.requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建JSON数组字符串
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(fragment.cutFilesPaths);
            String listString = jsonArray.toString();

            new FileApi().moveFileOrFolder(fragment.requireContext(), deviceId, listString, fragment.currentPath, new FileApi.Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    fragment.mainHandler.post(() -> {
                        Toast.makeText(fragment.requireContext(), "粘贴完成", Toast.LENGTH_SHORT).show();
                        fragment.cutFilesPaths.clear();
                        fragment.cutSourcePath = "";
                        fragment.loadFileList();
                    });
                }

                @Override
                public void onFailure(String errorMsg) {
                    fragment.mainHandler.post(() -> {
                        if (errorMsg.contains("500")) {
                            Toast.makeText(fragment.requireContext(), "不能粘贴到同一文件夹", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(fragment.requireContext(), "粘贴失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(fragment.requireContext(), "构建请求数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}