package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class TerminalThemeManager {
    private static final String TERMINAL_THEME_PREFS = "terminal_theme_preferences";
    private static final String TERMINAL_THEME_MODE_KEY = "terminal_theme_mode";
    public static final int TERMINAL_THEME_FOLLOW_SYSTEM = 0;
    public static final int TERMINAL_THEME_FORCE_LIGHT = 1;
    public static final int TERMINAL_THEME_FORCE_DARK = 2;
    public static final int LIGHT_BACKGROUND_COLOR = Color.WHITE;
    public static final int LIGHT_TEXT_COLOR = Color.BLACK;
    public static final int DARK_BACKGROUND_COLOR = Color.parseColor("#000000");
    public static final int DARK_TEXT_COLOR = Color.parseColor("#FFFFFF");
    private static TerminalThemeManager instance;
    private SharedPreferences preferences;
    private Context context;

    private TerminalThemeManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(TERMINAL_THEME_PREFS, Context.MODE_PRIVATE);
    }

    public static TerminalThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new TerminalThemeManager(context);
        }
        return instance;
    }

    public void setTerminalThemeMode(int themeMode) {
        preferences.edit().putInt(TERMINAL_THEME_MODE_KEY, themeMode).apply();
    }

    public int getTerminalThemeMode() {
        return preferences.getInt(TERMINAL_THEME_MODE_KEY, TERMINAL_THEME_FOLLOW_SYSTEM);
    }

    public String getTerminalThemeName(int themeMode) {
        switch (themeMode) {
            case TERMINAL_THEME_FORCE_LIGHT:
                return "强制浅色";
            case TERMINAL_THEME_FORCE_DARK:
                return "强制深色";
            case TERMINAL_THEME_FOLLOW_SYSTEM:
            default:
                return "跟随主题";
        }
    }

    public int getTerminalBackgroundColor() {
        int themeMode = getTerminalThemeMode();
        switch (themeMode) {
            case TERMINAL_THEME_FORCE_LIGHT:
                return LIGHT_BACKGROUND_COLOR;
            case TERMINAL_THEME_FORCE_DARK:
                return DARK_BACKGROUND_COLOR;
            case TERMINAL_THEME_FOLLOW_SYSTEM:
            default:
                return isSystemInDarkMode() ? DARK_BACKGROUND_COLOR : LIGHT_BACKGROUND_COLOR;
        }
    }

    public int getTerminalTextColor() {
        int themeMode = getTerminalThemeMode();
        switch (themeMode) {
            case TERMINAL_THEME_FORCE_LIGHT:
                return LIGHT_TEXT_COLOR;
            case TERMINAL_THEME_FORCE_DARK:
                return DARK_TEXT_COLOR;
            case TERMINAL_THEME_FOLLOW_SYSTEM:
            default:
                return isSystemInDarkMode() ? DARK_TEXT_COLOR : LIGHT_TEXT_COLOR;
        }
    }

    private boolean isSystemInDarkMode() {
        int currentNightMode = context.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public TerminalColors getTerminalColors() {
        return new TerminalColors(getTerminalBackgroundColor(), getTerminalTextColor());
    }

    public static class TerminalColors {
        public final int backgroundColor;
        public final int textColor;

        public TerminalColors(int backgroundColor, int textColor) {
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }
    }
}
