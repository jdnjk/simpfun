package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

public class SftpTransferSettingsManager {
    private static final String KEY_SFTP_THREAD_COUNT = "sftp_thread_count";
    public static final int DEFAULT_THREAD_COUNT = 6;
    private static final int MIN_THREAD_COUNT = 1;
    private static final int MAX_THREAD_COUNT = 32;

    private final SettingsSaveManager saveManager;

    public SftpTransferSettingsManager(Context context) {
        saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    public int getThreadCount() {
        return clamp(saveManager.getInt(KEY_SFTP_THREAD_COUNT, DEFAULT_THREAD_COUNT));
    }

    public void setThreadCount(int threadCount) {
        saveManager.putInt(KEY_SFTP_THREAD_COUNT, clamp(threadCount));
    }

    public static int clamp(int value) {
        return Math.max(MIN_THREAD_COUNT, Math.min(MAX_THREAD_COUNT, value));
    }
}
