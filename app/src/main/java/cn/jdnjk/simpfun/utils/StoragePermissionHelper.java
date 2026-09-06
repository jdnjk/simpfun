package cn.jdnjk.simpfun.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

public final class StoragePermissionHelper {
    private StoragePermissionHelper() {
    }

    public static boolean hasLocalStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean requiresManageAllFiles() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * 能否把文件写入公共下载目录（/sdcard/Download）。
     *
     * <p>刻意比 {@link #hasLocalStorageAccess} 窄：那个方法要求「所有文件访问」，
     * 用于文件管理器浏览整个 /sdcard；而写入下载目录在 Android 11+ 通过
     * MediaStore 完全不需要权限。若复用前者，API 30+ 上会把本可直接写入的情况
     * 误判为未授权。
     *
     * <p>Android 10 (API 29) 例外：manifest 声明了 requestLegacyExternalStorage，
     * 应用处于 legacy 模式，MediaStore 写入仍会检查 WRITE_EXTERNAL_STORAGE。
     */
    public static boolean canWritePublicDownloads(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 公共下载目录是否需要运行时申请 WRITE_EXTERNAL_STORAGE。 */
    public static boolean requiresWritePermissionForDownloads() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R;
    }

    public static Intent createManageAllFilesIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent appIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            appIntent.setData(Uri.parse("package:" + context.getPackageName()));
            return appIntent;
        }
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
    }

    public static Intent createManageAllFilesFallbackIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        }
        return new Intent(Settings.ACTION_SETTINGS);
    }
}
