package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.widget.TextView;
import android.view.View;

public class TerminalColorUtils {
    public static void applyTerminalColors(Context context, TextView textView) {
        TerminalThemeManager terminalThemeManager = TerminalThemeManager.getInstance(context);
        TerminalThemeManager.TerminalColors colors = terminalThemeManager.getTerminalColors();

        textView.setBackgroundColor(colors.backgroundColor);
        textView.setTextColor(colors.textColor);
    }

    /**
     * 为View应用终端背景色
     * @param context 上下文
     * @param view 要应用背景色的View
     */
    public static void applyTerminalBackgroundColor(Context context, View view) {
        TerminalThemeManager terminalThemeManager = TerminalThemeManager.getInstance(context);
        int backgroundColor = terminalThemeManager.getTerminalBackgroundColor();
        view.setBackgroundColor(backgroundColor);
    }

    /**
     * 获取终端文字颜色
     * @param context 上下文
     * @return 文字颜色值
     */
    public static int getTerminalTextColor(Context context) {
        TerminalThemeManager terminalThemeManager = TerminalThemeManager.getInstance(context);
        return terminalThemeManager.getTerminalTextColor();
    }

    /**
     * 获取终端背景颜色
     * @param context 上下文
     * @return 背景颜色值
     */
    public static int getTerminalBackgroundColor(Context context) {
        TerminalThemeManager terminalThemeManager = TerminalThemeManager.getInstance(context);
        return terminalThemeManager.getTerminalBackgroundColor();
    }

    /**
     * 检查当前是否为深色终端主题
     * @param context 上下文
     * @return 是否为深色主题
     */
    public static boolean isDarkTerminalTheme(Context context) {
        TerminalThemeManager terminalThemeManager = TerminalThemeManager.getInstance(context);
        int themeMode = terminalThemeManager.getTerminalThemeMode();

        if (themeMode == TerminalThemeManager.TERMINAL_THEME_FORCE_DARK) {
            return true;
        } else if (themeMode == TerminalThemeManager.TERMINAL_THEME_FORCE_LIGHT) {
            return false;
        } else {
            // 跟随系统主题
            int currentNightMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
    }
}
