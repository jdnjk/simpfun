package cn.jdnjk.simpfun.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ClipboardUtils {
    private static final String EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE";

    private ClipboardUtils() {
    }

    public static boolean copyPlainText(
            @Nullable Context context,
            @NonNull String label,
            @Nullable String text,
            @Nullable String successToast
    ) {
        return copyText(context, label, text, false, successToast);
    }

    public static boolean copySensitiveText(
            @Nullable Context context,
            @NonNull String label,
            @Nullable String text,
            @Nullable String successToast
    ) {
        return copyText(context, label, text, true, successToast);
    }

    private static boolean copyText(
            @Nullable Context context,
            @NonNull String label,
            @Nullable String text,
            boolean sensitive,
            @Nullable String successToast
    ) {
        if (context == null) return false;
        String value = text == null ? "" : text;
        if (value.trim().isEmpty()) {
            Toast.makeText(context, "暂无可复制内容", Toast.LENGTH_SHORT).show();
            return false;
        }

        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
            return false;
        }

        ClipData clip = ClipData.newPlainText(label, value);
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PersistableBundle extras = new PersistableBundle();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            } else {
                extras.putBoolean(EXTRA_IS_SENSITIVE_COMPAT, true);
            }
            clip.getDescription().setExtras(extras);
        }

        try {
            clipboard.setPrimaryClip(clip);
            if (successToast != null && !successToast.isEmpty()) {
                Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (RuntimeException e) {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
