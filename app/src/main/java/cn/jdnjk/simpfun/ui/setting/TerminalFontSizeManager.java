package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

public class TerminalFontSizeManager {
    private static final String TERMINAL_FONT_SIZE_KEY = "terminal_font_size";
    public static final float DEFAULT_FONT_SIZE = 14f;
    public static final float MIN_FONT_SIZE = 10f;
    public static final float MAX_FONT_SIZE = 24f;

    private static TerminalFontSizeManager instance;
    private final SettingsSaveManager saveManager;

    private TerminalFontSizeManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public static TerminalFontSizeManager getInstance(Context context) {
        if (instance == null) {
            instance = new TerminalFontSizeManager(context);
        }
        return instance;
    }

    public float getFontSize() {
        return saveManager.getFloat(TERMINAL_FONT_SIZE_KEY, DEFAULT_FONT_SIZE);
    }

    public void setFontSize(float size) {
        saveManager.putFloat(TERMINAL_FONT_SIZE_KEY, size);
    }
}
