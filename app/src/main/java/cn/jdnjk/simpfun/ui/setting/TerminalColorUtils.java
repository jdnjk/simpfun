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
}
