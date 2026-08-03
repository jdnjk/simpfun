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
