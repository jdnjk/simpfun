package cn.jdnjk.simpfun.download;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import cn.jdnjk.simpfun.utils.StoragePermissionHelper;

/**
 * 把「用户选的保存位置 + 当前权限状态」解析成一个可写的 {@link DownloadTarget}。
 *
 * <p>这是唯一知道回落规则的地方。回落一律静默——用户点的是「下载」，
 * 不该在这一步被权限弹窗打断，所以拿不到目标位置时改写应用私有目录并告知结果。
 */
public final class DownloadTargetResolver {
    private static final String TAG = "DownloadTargetResolver";
    private static final String PRIVATE_DIR_NAME = "download";
    private static final String DEFAULT_MIME = "application/octet-stream";

    private DownloadTargetResolver() {
    }

    public static final class Result {
        public final DownloadTarget target;
        /** 非 null 表示发生了回落，调用方应提示用户，并把设置改回应用私有目录。 */
        @Nullable
        public final String fallbackReason;

        Result(DownloadTarget target, @Nullable String fallbackReason) {
            this.target = target;
            this.fallbackReason = fallbackReason;
        }

        public boolean didFallback() {
            return fallbackReason != null;
        }
    }

    public static Result resolve(@NonNull Context context, @NonNull String fileName) throws IOException {
        DownloadLocationManager locationManager = new DownloadLocationManager(context);
        int mode = locationManager.getMode();
        String mimeType = guessMimeType(fileName);

        if (mode == DownloadLocationManager.MODE_PUBLIC_DOWNLOADS) {
            if (!StoragePermissionHelper.canWritePublicDownloads(context)) {
                return fallback(context, fileName, "未获得存储权限，已保存到应用专属目录");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return new Result(new MediaStoreDownloadTarget(context, fileName, mimeType), null);
            }
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            return new Result(new FileDownloadTarget(makeUnique(new File(publicDir, fileName))), null);
        }

        if (mode == DownloadLocationManager.MODE_CUSTOM_TREE) {
            String saved = locationManager.getCustomTreeUri();
            if (saved.isEmpty()) {
                return fallback(context, fileName, "未选择自定义目录，已保存到应用专属目录");
            }
            if (!hasPersistedAccess(context, saved)) {
                return fallback(context, fileName, "自定义目录的授权已失效，已保存到应用专属目录");
            }
            return new Result(new SafTreeDownloadTarget(context, Uri.parse(saved), fileName, mimeType), null);
        }

        return new Result(privateTarget(context, fileName), null);
    }

    private static Result fallback(Context context, String fileName, String reason) throws IOException {
        return new Result(privateTarget(context, fileName), reason);
    }

    private static DownloadTarget privateTarget(Context context, String fileName) throws IOException {
        File root = context.getExternalFilesDir(PRIVATE_DIR_NAME);
        if (root == null) {
            root = new File(context.getCacheDir(), PRIVATE_DIR_NAME);
        }
        return new FileDownloadTarget(makeUnique(new File(root, fileName)));
    }

    /**
     * 检查 SAF 目录授权是否还在。用户可能在系统设置里撤销，或应用被清数据。
     * MediaStore 与 createDocument 只有在实际写入时才会报错，提前查一下能给出更准确的提示。
     */
    private static boolean hasPersistedAccess(Context context, String treeUri) {
        try {
            for (android.content.UriPermission permission
                    : context.getContentResolver().getPersistedUriPermissions()) {
                if (permission.isWritePermission() && treeUri.equals(permission.getUri().toString())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "检查目录授权失败", e);
        }
        return false;
    }

    /**
     * 文件已存在时追加 "(1)"、"(2)"……与 MediaStore 的自动改名行为保持一致。
     * 仅用于 File 分支；MediaStore 和 SAF 由系统自己处理重名。
     */
    private static File makeUnique(File target) {
        if (!target.exists()) {
            return target;
        }
        File parent = target.getParentFile();
        String name = target.getName();
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(parent, base + "(" + i + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return target;
    }

    private static String guessMimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return DEFAULT_MIME;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? DEFAULT_MIME : mime;
    }
}
