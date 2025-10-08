package cn.jdnjk.simpfun.ui.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;
import cn.jdnjk.simpfun.api.ins.FileApi;
import org.json.JSONObject;

import java.io.File;

public class FileUpdate {
    private static final String TAG = "FileUpdate";
    
    private final FileListFragment fragment;
    
    public FileUpdate(FileListFragment fragment) {
        this.fragment = fragment;
    }
    
    public void uploadModifiedFile(File localFile, String remotePath, String fileName) {
        SharedPreferences sp = fragment.requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Log.e(TAG, "设备ID无效，无法上传文件");
            return;
        }

        if (!localFile.exists()) {
            Log.w(TAG, "本地文件不存在，跳过上传: " + localFile.getAbsolutePath());
            return;
        }

        Log.d(TAG, "开始上传修改后的文件: " + fileName);

        Toast.makeText(fragment.requireContext(), "正在上传文件: " + fileName, Toast.LENGTH_SHORT).show();

        new FileApi().uploadFile(fragment.requireContext(), deviceId, fragment.getParentPath(remotePath), localFile, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                fragment.mainHandler.post(() -> {
                    Toast.makeText(fragment.requireContext(), "文件 " + fileName + " 上传成功", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "文件上传成功: " + fileName);
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                fragment.mainHandler.post(() -> {
                    Toast.makeText(fragment.requireContext(), "文件上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "文件上传失败: " + fileName + ", 错误: " + errorMsg);
                });
            }
        });
    }
    
    public void startFileWatcher(String localFilePath, String remoteFilePath, String fileName) {
        if (fragment.fileWatchers.containsKey(localFilePath)) {
            FileListFragment.FileWatcher existingWatcher = fragment.fileWatchers.get(localFilePath);
            if (existingWatcher != null) {
                existingWatcher.stopWatching();
            }
        }
        FileListFragment.FileWatcher watcher = new FileListFragment.FileWatcher(fragment, localFilePath, remoteFilePath, fileName);
        watcher.startWatching();
        fragment.fileWatchers.put(localFilePath, watcher);
    }
}