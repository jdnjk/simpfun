package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

/**
 * 总览页显示模式管理器。
 * 关闭（默认）：卡片 + 进度条展示实时 CPU / 内存 / 网速。
 * 开启：切换为实时折线统计图。
 */
public class OverviewDisplayManager {
    private static final String KEY_LINE_CHART = "overview_line_chart";

    private final SettingsSaveManager saveManager;

    public OverviewDisplayManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public boolean isLineChartEnabled() {
        return saveManager.getBoolean(KEY_LINE_CHART, false);
    }

    public void setLineChartEnabled(boolean enabled) {
        saveManager.putBoolean(KEY_LINE_CHART, enabled);
    }
}
