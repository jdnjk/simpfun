package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;

public final class ManageScreenshotProtection {
    private static final String SP_DEBUG = "setting_sp";
    private static final String KEY_ENABLED = "manage_screenshot_protection_enabled";

    private ManageScreenshotProtection() {}

    public static boolean isEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences sp = context.getSharedPreferences(SP_DEBUG, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(SP_DEBUG, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
