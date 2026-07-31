package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

public final class ManageScreenshotProtection {
    private static final String KEY_ENABLED = "manage_screenshot_protection_enabled";

    private ManageScreenshotProtection() {}

    public static boolean isEnabled(Context context) {
        if (context == null) return true;
        return SettingsSaveManager.getInstance(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SettingsSaveManager.getInstance(context).putBoolean(KEY_ENABLED, enabled);
    }
}
