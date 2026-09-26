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
            // 写后读回校验：部分国产 ROM（MIUI/澎湃、ColorOS、HarmonyOS 等）会静默拦截写入，
            // 应用前台时读自己刚写入的剪贴板不受 Android 10 后台限制，可用来确认真正写成功
            CharSequence written = null;
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0) {
                written = clipboard.getPrimaryClip().getItemAt(0).getText();
            }
            if (written == null || written.length() == 0) {
                Toast.makeText(context, "复制失败：请在系统设置中允许本应用使用剪贴板", Toast.LENGTH_LONG).show();
                return false;
            }
            if (successToast != null && !successToast.isEmpty()) {
                Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (RuntimeException e) {
            Toast.makeText(context, "复制失败：请在系统设置中允许本应用使用剪贴板", Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
