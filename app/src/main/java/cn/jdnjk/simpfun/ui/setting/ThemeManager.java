package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

import cn.jdnjk.simpfun.utils.ThemeUtils;

public class ThemeManager {
    public static final int THEME_SYSTEM = ThemeUtils.THEME_SYSTEM;
    public static final int THEME_LIGHT = ThemeUtils.THEME_LIGHT;
    public static final int THEME_DARK = ThemeUtils.THEME_DARK;
    public static final int THEME_APPLE = ThemeUtils.THEME_APPLE;

    private static ThemeManager instance;
    private final Context appContext;

    private ThemeManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context.getApplicationContext());
        }
        return instance;
    }

    public void setThemeMode(int themeMode) {
        ThemeUtils.setThemeMode(appContext, themeMode);
    }

    public int getThemeMode() {
        return ThemeUtils.getThemeMode(appContext);
    }

    public void applyTheme(int themeMode) {
        ThemeUtils.applyTheme(themeMode);
    }

    public String getThemeName(int themeMode) {
        return ThemeUtils.getThemeName(themeMode);
    }

    public void initializeTheme() {
        ThemeUtils.applySavedTheme(appContext);
    }
}
