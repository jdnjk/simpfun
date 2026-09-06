package cn.jdnjk.simpfun.download;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import cn.jdnjk.simpfun.BuildConfig;

/** 写入普通文件：应用私有目录，以及 API 23-28 上的公共下载目录。 */
class FileDownloadTarget implements DownloadTarget {
    private static final String TAG = "FileDownloadTarget";

    private File file;

    FileDownloadTarget(@NonNull File file) {
        this.file = file;
    }

    @Override
    public boolean renameTo(@NonNull String newName) {
        File parent = file.getParentFile();
        if (parent == null || newName == null || newName.trim().isEmpty()) {
            return false;
        }
        file = new File(parent, newName);
        return true;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("无法创建目录：" + parent.getAbsolutePath());
        }
        return new FileOutputStream(file);
    }

    @Override
    public void finish() {
        // 普通文件写完即可见，无需收尾。
    }

    @Override
    public void deletePartial() {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "删除半成品失败: " + file.getAbsolutePath());
        }
    }

    @Override
    public String getDisplayPath() {
        return file.getAbsolutePath();
    }

    @Nullable
    @Override
    public Uri getViewableUri(Context context) {
        try {
            return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        } catch (Exception e) {
            // file_paths.xml 未覆盖该路径时会抛 IllegalArgumentException，降级为不可预览。
            Log.w(TAG, "无法为文件生成 FileProvider Uri: " + file.getAbsolutePath(), e);
            return null;
        }
    }
}
