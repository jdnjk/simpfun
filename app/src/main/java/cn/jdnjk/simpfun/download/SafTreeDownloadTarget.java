package cn.jdnjk.simpfun.download;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 写入用户通过 SAF 授权的自定义目录。
 *
 * <p>只用 {@link DocumentsContract}，不引入 androidx.documentfile —— 需要的只有
 * createDocument / deleteDocument / 查显示名三件事。
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class SafTreeDownloadTarget implements DownloadTarget {
    private static final String TAG = "SafTreeTarget";

    private final ContentResolver resolver;
    private final Uri treeUri;
    private String fileName;
    private final String mimeType;
    private Uri documentUri;

    SafTreeDownloadTarget(@NonNull Context context, @NonNull Uri treeUri,
                          @NonNull String fileName, @NonNull String mimeType) {
        this.resolver = context.getApplicationContext().getContentResolver();
        this.treeUri = treeUri;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    @Override
    public boolean renameTo(@NonNull String newName) {
        if (documentUri != null) {
            return false; // 已建文档，改名已来不及
        }
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        fileName = newName;
        return true;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        Uri created;
        try {
            created = DocumentsContract.createDocument(resolver, parentDocumentUri, mimeType, fileName);
        } catch (Exception e) {
            // 目录授权被系统回收后会抛 SecurityException / FileNotFoundException。
            throw new IOException("无法在所选目录创建文件：" + e.getMessage(), e);
        }
        if (created == null) {
            throw new IOException("无法在所选目录创建文件");
        }
        documentUri = created;
        OutputStream stream = resolver.openOutputStream(created);
        if (stream == null) {
            deletePartial();
            throw new IOException("无法打开所选目录的写入流");
        }
        return stream;
    }

    @Override
    public void finish() {
        // SAF 文档写完即可见。
    }

    @Override
    public void deletePartial() {
        if (documentUri == null) {
            return;
        }
        try {
            DocumentsContract.deleteDocument(resolver, documentUri);
        } catch (Exception e) {
            Log.w(TAG, "删除半成品失败: " + documentUri, e);
        } finally {
            documentUri = null;
        }
    }

    @Override
    public String getDisplayPath() {
        String dirName = queryTreeDisplayName();
        return dirName == null ? fileName : dirName + "/" + fileName;
    }

    @Nullable
    @Override
    public Uri getViewableUri(Context context) {
        return documentUri;
    }

    /** 查授权目录的显示名，用于给用户展示保存位置。查不到返回 null。 */
    @Nullable
    private String queryTreeDisplayName() {
        Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        try (Cursor cursor = resolver.query(parentDocumentUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "查询目录名失败: " + treeUri, e);
        }
        return null;
    }
}
