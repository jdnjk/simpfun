package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

public class ServerCardStyleManager {
    private static final String KEY_MODERN_SERVER_CARD = "modern_server_card";

    private final SettingsSaveManager saveManager;

    public ServerCardStyleManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public boolean isModernServerCardEnabled() {
        return saveManager.getBoolean(KEY_MODERN_SERVER_CARD, false);
    }

    public void setModernServerCardEnabled(boolean enabled) {
        saveManager.putBoolean(KEY_MODERN_SERVER_CARD, enabled);
    }
}
