package cn.jdnjk.simpfun.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeUtils {
    private static final String SETTINGS_PREFS = "setting_sp";
    private static final String THEME_MODE_KEY = "theme_mode";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private ThemeUtils() {
    }

    public static void applySavedTheme(Context context) {
        applyTheme(getThemeMode(context));
    }

    public static void setThemeMode(Context context, int themeMode) {
        getPreferences(context).edit().putInt(THEME_MODE_KEY, themeMode).apply();
        applyTheme(themeMode);
    }

    public static int getThemeMode(Context context) {
        return getPreferences(context).getInt(THEME_MODE_KEY, THEME_SYSTEM);
    }

    public static void applyTheme(int themeMode) {
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

    public static String getThemeName(int themeMode) {
        return switch (themeMode) {
            case THEME_LIGHT -> "浅色模式";
            case THEME_DARK -> "深色模式";
            default -> "跟随系统";
        };
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
    }
}
