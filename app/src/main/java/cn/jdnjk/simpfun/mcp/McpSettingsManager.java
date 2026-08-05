package cn.jdnjk.simpfun.mcp;

import android.content.Context;

import cn.jdnjk.simpfun.ui.setting.SettingsSaveManager;

/**
 * MCP 服务器设置管理器（debounced，复用 SettingsSaveManager）。
 */
public class McpSettingsManager {
    private static final String KEY_ENABLED = "mcp_server_enabled";
    private static final String KEY_PORT = "mcp_server_port";

    private final SettingsSaveManager saveManager;

    public McpSettingsManager(Context context) {
        this.saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public boolean isEnabled() {
        return saveManager.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        saveManager.putBoolean(KEY_ENABLED, enabled);
    }

    public int getPort() {
        return saveManager.getInt(KEY_PORT, McpConstants.DEFAULT_PORT);
    }

    public void setPort(int port) {
        saveManager.putInt(KEY_PORT, port);
    }
}
