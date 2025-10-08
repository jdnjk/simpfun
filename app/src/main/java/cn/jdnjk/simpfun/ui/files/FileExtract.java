package cn.jdnjk.simpfun.ui.files;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FileExtract {
    private final FileListFragment fragment;
    
    public FileExtract(FileListFragment fragment) {
        this.fragment = fragment;
    }
    
    public void compressFiles(List<FileListFragment.FileItem> items) {
        String[] formats = {"ZIP", "7Z", "TAR.GZ"};
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext());
        builder.setTitle("选择压缩格式");
        builder.setItems(formats, (dialog, which) -> {
            String format = formats[which].toLowerCase().replace(".", "");
            showCompressNameDialog(items, format);
        });
        builder.show();
    }

    private void showCompressNameDialog(List<FileListFragment.FileItem> items, String format) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext());
        builder.setTitle("压缩文件");

        EditText input = new EditText(fragment.requireContext());
        input.setHint("请输入压缩包名称");
        if (items.size() == 1) {
            String baseName = items.get(0).getName();
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            input.setText(baseName);
        } else {
            input.setText("压缩文件");
        }
        builder.setView(input);

        builder.setPositiveButton("压缩", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                executeCompress(items, fileName, format);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeCompress(List<FileListFragment.FileItem> items, String fileName, String format) {
        Toast.makeText(fragment.requireContext(), "正在压缩文件...", Toast.LENGTH_SHORT).show();

        List<String> fileNames = new ArrayList<>();
        for (FileListFragment.FileItem item : items) {
            fileNames.add(item.getName());
        }

        SharedPreferences sp = fragment.requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(fragment.requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder fileListJson = new StringBuilder("[");
        for (int i = 0; i < fileNames.size(); i++) {
            if (i > 0) fileListJson.append(",");
            fileListJson.append("\"").append(fileNames.get(i)).append("\"");
        }
        fileListJson.append("]");

        new FileApi().zipFileOrFolder(fragment.requireContext(), deviceId, fragment.currentPath, fileListJson.toString(), format, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                fragment.mainHandler.post(() -> {
                    Toast.makeText(fragment.requireContext(), "压缩完成: " + fileName + "." + format, Toast.LENGTH_SHORT).show();
                    fragment.clearAllSelections();
                    fragment.loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                fragment.mainHandler.post(() -> Toast.makeText(fragment.requireContext(), "压缩失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    public void extractFile(FileListFragment.FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.requireContext());
        builder.setTitle("解压文件");
        builder.setMessage("确定要解压 " + item.getName() + " 吗？");
        builder.setPositiveButton("解压", (dialog, which) -> executeExtract(item));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeExtract(FileListFragment.FileItem item) {
        SharedPreferences sp = fragment.requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(fragment.requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileApi().unzipFile(fragment.requireContext(), deviceId, fragment.currentPath, item.getName(), new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                fragment.mainHandler.post(() -> {
                    fragment.clearAllSelections();
                    fragment.loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                fragment.mainHandler.post(() -> Toast.makeText(fragment.requireContext(), "解压失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }
}