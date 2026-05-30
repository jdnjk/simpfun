package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;

public class SftpTransferSettingsManager {
    private static final String SP_NAME = "setting_sp";
    private static final String KEY_SFTP_THREAD_COUNT = "sftp_thread_count";
    public static final int DEFAULT_THREAD_COUNT = 6;
    private static final int MIN_THREAD_COUNT = 1;
    private static final int MAX_THREAD_COUNT = 32;

    private final SharedPreferences preferences;

    public SftpTransferSettingsManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public int getThreadCount() {
        return clamp(preferences.getInt(KEY_SFTP_THREAD_COUNT, DEFAULT_THREAD_COUNT));
    }

    public void setThreadCount(int threadCount) {
        preferences.edit().putInt(KEY_SFTP_THREAD_COUNT, clamp(threadCount)).apply();
    }

    public static int clamp(int value) {
        return Math.max(MIN_THREAD_COUNT, Math.min(MAX_THREAD_COUNT, value));
    }
}
