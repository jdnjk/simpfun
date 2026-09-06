package cn.jdnjk.simpfun.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 通过 MediaStore 写入公共下载目录（API 29+）。
 *
 * <p>相比直写 /sdcard/Download，API 30+ 完全不需要存储权限，同名文件也由系统自动加
 * "(1)" 后缀。写入期间 IS_PENDING=1，只有本应用可见，{@link #finish()} 清零后才正式发布。
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
class MediaStoreDownloadTarget implements DownloadTarget {
    private static final String TAG = "MediaStoreTarget";

    private final ContentResolver resolver;
    private String fileName;
    private final String mimeType;
    private Uri itemUri;

    MediaStoreDownloadTarget(@NonNull Context context, @NonNull String fileName, @NonNull String mimeType) {
        this.resolver = context.getApplicationContext().getContentResolver();
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    @Override
    public boolean renameTo(@NonNull String newName) {
        if (itemUri != null) {
            return false; // 已开流/已建条目，改名已来不及
        }
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        fileName = newName;
        return true;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("无法在下载目录创建文件");
        }
        itemUri = uri;
        OutputStream stream = resolver.openOutputStream(uri);
        if (stream == null) {
            deletePartial();
            throw new IOException("无法打开下载目录的写入流");
        }
        return stream;
    }

    @Override
    public void finish() throws IOException {
        if (itemUri == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        if (resolver.update(itemUri, values, null, null) <= 0) {
            throw new IOException("无法发布下载文件");
        }
    }

    @Override
    public void deletePartial() {
        if (itemUri == null) {
            return;
        }
        try {
            resolver.delete(itemUri, null, null);
        } catch (Exception e) {
            Log.w(TAG, "删除半成品失败: " + itemUri, e);
        } finally {
            itemUri = null;
        }
    }

    @Override
    public String getDisplayPath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
    }

    @Nullable
    @Override
    public Uri getViewableUri(Context context) {
        return itemUri;
    }
}
