package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

/**
 * 终端日志最大行数（RecyclerView 中保留的行数上限）。
 * 超出后丢弃最旧的行；默认 5000。
 */
public class TerminalLineLimitManager {
    private static final String TERMINAL_LINE_LIMIT_KEY = "terminal_line_limit";
    public static final int DEFAULT_LINE_LIMIT = 5000;
    /** 允许的自定义范围 */
    public static final int MIN_LINE_LIMIT = 100;
    public static final int MAX_LINE_LIMIT = 50000;
    /** 输入框的快捷档位建议 */
    public static final int[] OPTIONS = {1000, 2000, 5000, 10000, 20000};

    private static TerminalLineLimitManager instance;
    private final SettingsSaveManager saveManager;

    private TerminalLineLimitManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public static TerminalLineLimitManager getInstance(Context context) {
        if (instance == null) {
            instance = new TerminalLineLimitManager(context.getApplicationContext());
        }
        return instance;
    }

    public int getLineLimit() {
        return saveManager.getInt(TERMINAL_LINE_LIMIT_KEY, DEFAULT_LINE_LIMIT);
    }

    public void setLineLimit(int limit) {
        saveManager.putInt(TERMINAL_LINE_LIMIT_KEY, clamp(limit));
    }

    /** 限制在允许范围内，防止 0/负数或夸张值导致内存问题 */
    public static int clamp(int value) {
        return Math.max(MIN_LINE_LIMIT, Math.min(MAX_LINE_LIMIT, value));
    }
}
