package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;

public class FilePaneModeManager {
    private static final String SP_NAME = "setting_sp";
    private static final String KEY_DUAL_FILE_PANE = "dual_file_pane_enabled";

    private final SharedPreferences preferences;

    public FilePaneModeManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public boolean isDualFilePaneEnabled() {
        return preferences.getBoolean(KEY_DUAL_FILE_PANE, false);
    }

    public void setDualFilePaneEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DUAL_FILE_PANE, enabled).apply();
    }
}
