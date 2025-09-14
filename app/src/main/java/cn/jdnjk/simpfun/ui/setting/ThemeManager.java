package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String THEME_PREFS = "theme_preferences";
    private static final String THEME_MODE_KEY = "theme_mode";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static ThemeManager instance;
    private SharedPreferences preferences;

    private ThemeManager(Context context) {
        preferences = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE);
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context.getApplicationContext());
        }
        return instance;
    }

    public void setThemeMode(int themeMode) {
        preferences.edit().putInt(THEME_MODE_KEY, themeMode).apply();
        applyTheme(themeMode);
    }

    public int getThemeMode() {
        return preferences.getInt(THEME_MODE_KEY, THEME_SYSTEM);
    }

    public void applyTheme(int themeMode) {
        switch (themeMode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public String getThemeName(int themeMode) {
        return switch (themeMode) {
            case THEME_LIGHT -> "浅色模式";
            case THEME_DARK -> "深色模式";
            default -> "跟随系统";
        };
    }

    public void initializeTheme() {
        applyTheme(getThemeMode());
    }
}
