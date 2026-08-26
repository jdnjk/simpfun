package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

public class ServerCardStyleManager {
    private static final String KEY_MODERN_SERVER_CARD = "modern_server_card";
    private static final String KEY_CPU_100_PERCENT = "cpu_100_percent_mode";

    private final SettingsSaveManager saveManager;

    public ServerCardStyleManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public boolean isModernServerCardEnabled() {
        return saveManager.getBoolean(KEY_MODERN_SERVER_CARD, false);
    }

    public void setModernServerCardEnabled(boolean enabled) {
        saveManager.putBoolean(KEY_MODERN_SERVER_CARD, enabled);
    }

    /**
     * 新版卡片 CPU 是否以 100% 方式显示。
     * 开启（默认）：将 CPU 占用率归一化到 0-100%，满 1 核即 100%。
     * 关闭：直接使用原始 cpu_absolute（1 核 = 100%，满载多核会 >100%）。
     */
    public boolean isCpu100PercentEnabled() {
        return saveManager.getBoolean(KEY_CPU_100_PERCENT, true);
    }

    public void setCpu100PercentEnabled(boolean enabled) {
        saveManager.putBoolean(KEY_CPU_100_PERCENT, enabled);
    }
}
