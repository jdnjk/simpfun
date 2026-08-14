package cn.jdnjk.simpfun.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SplashActivity;

public final class ThemeUtils {
    private static final String SETTINGS_PREFS = "setting_sp";
    private static final String THEME_MODE_KEY = "theme_mode";
    private static final String DYNAMIC_COLOR_KEY = "dynamic_color";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    public static final int THEME_APPLE = 3;

    private ThemeUtils() {
    }

    public static void applySavedTheme(Context context) {
        int themeMode = getThemeMode(context);
        if (context instanceof Activity) {
            ((Activity) context).setTheme(getThemeResId(context, themeMode));
        }
        applyTheme(themeMode);
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
            case THEME_APPLE:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
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
            case THEME_APPLE -> "Apple 冰霜";
            default -> "跟随系统";
        };
    }

    private static int getThemeResId(Context context, int themeMode) {
        if (themeMode == THEME_APPLE) {
            return context instanceof SplashActivity
                    ? R.style.Theme_Simpfun_Apple_Splash
                    : R.style.Theme_Simpfun_Apple;
        }
        return context instanceof SplashActivity
                ? R.style.Theme_Simpfun_Splash
                : R.style.Theme_Simpfun;
    }

    // ---------------------------------------------------------------- 动态取色

    public static boolean isDynamicColorEnabled(Context context) {
        return getPreferences(context).getBoolean(DYNAMIC_COLOR_KEY, true);
    }

    public static void setDynamicColorEnabled(Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(DYNAMIC_COLOR_KEY, enabled).apply();
    }

    // ---------------------------------------------------------------- 系统栏

    /**
     * 当前生效的是否为深色外观。
     *
     * <p>优先看 AppCompat 的夜间模式设置：用户在应用内选了「浅色」时，即使系统处于深色，
     * 界面也是浅色的，此时系统栏图标必须按浅色底来配。只有「跟随系统」才去读系统配置。
     */
    public static boolean isEffectiveDarkMode(Context context) {
        switch (AppCompatDelegate.getDefaultNightMode()) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                return true;
            case AppCompatDelegate.MODE_NIGHT_NO:
                return false;
            default:
                int mode = context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                return mode == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /**
     * 按当前主题设置状态栏/导航栏图标的明暗。
     *
     * <p>改造前四个 Activity 各自硬编码了这个值且互相矛盾（MainActivity 传 false、
     * SettingsActivity 传 true），浅色主题下 MainActivity 的状态栏图标是白的、看不见。
     */
    public static void applySystemBarAppearance(Activity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        boolean light = !isEffectiveDarkMode(activity);
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    /**
     * 开启 edge-to-edge 并配好系统栏图标配色。
     *
     * <p>targetSdk 35 起 Android 15 会强制 edge-to-edge，与其被动接受不如显式声明，
     * 这样各页面的 inset 处理才有统一前提。
     */
    public static void applyEdgeToEdge(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 系统栏改为透明，由内容自己延伸到其下方。
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 刘海屏下也铺满，避免横屏出现黑边。
            window.getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            // API 26 以下无法把导航栏图标改成深色，给个半透明底保证可见。
            window.setNavigationBarColor(0x40000000);
        }
        applySystemBarAppearance(activity);
    }

    /**
     * 把系统栏 inset 转成指定 View 的 padding。
     *
     * @param top    是否消费状态栏高度（页面顶部有自己的 toolbar 时传 true）
     * @param bottom 是否消费导航栏高度（页面底部没有 BottomNavigation 时传 true）
     */
    public static void applyInsetsAsPadding(View view, boolean top, boolean bottom) {
        final int pl = view.getPaddingLeft();
        final int pt = view.getPaddingTop();
        final int pr = view.getPaddingRight();
        final int pb = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    pl + bars.left,
                    top ? pt + bars.top : pt,
                    pr + bars.right,
                    bottom ? pb + bars.bottom : pb);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * 给 Activity 的内容根布局套上系统栏 inset。
     *
     * <p>{@code android.R.id.content} 是系统的 FrameLayout 容器，给它加 padding 会连
     * 窗口背景一起缩进；真正要缩进的是 setContentView 塞进去的那个子 View，所以这里
     * 优先取它的第一个子节点。
     *
     * <p>必须在 {@link #applyEdgeToEdge(Activity)} 与 setContentView 之后调用。
     */
    public static void applyRootInsets(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        View root = content;
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            root = ((ViewGroup) content).getChildAt(0);
        }
        applyInsetsAsPadding(root, true, true);
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
    }
}
