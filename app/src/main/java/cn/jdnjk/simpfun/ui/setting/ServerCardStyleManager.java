package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;

public class ServerCardStyleManager {
    private static final String SP_NAME = "ui_preferences";
    private static final String KEY_MODERN_SERVER_CARD = "modern_server_card";

    private final SharedPreferences preferences;

    public ServerCardStyleManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public boolean isModernServerCardEnabled() {
        return preferences.getBoolean(KEY_MODERN_SERVER_CARD, false);
    }

    public void setModernServerCardEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MODERN_SERVER_CARD, enabled).apply();
    }
}
