package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

public class FilePaneModeManager {
    private static final String KEY_DUAL_FILE_PANE = "dual_file_pane_enabled";

    private final SettingsSaveManager saveManager;

    public FilePaneModeManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public boolean isDualFilePaneEnabled() {
        return saveManager.getBoolean(KEY_DUAL_FILE_PANE, false);
    }

    public void setDualFilePaneEnabled(boolean enabled) {
        saveManager.putBoolean(KEY_DUAL_FILE_PANE, enabled);
    }
}
