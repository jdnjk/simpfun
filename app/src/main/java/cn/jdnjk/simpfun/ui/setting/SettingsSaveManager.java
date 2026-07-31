package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

/**
 * 设置延迟保存管理器。
 * 收集各 Manager 的待写入值，0.9s 无新变更后统一写入 SP；
 * 返回键或页面销毁时立即 flush。
 */
public class SettingsSaveManager {

    private static final String SP_NAME = "setting_sp";
    private static final long DEBOUNCE_MS = 900L;

    private static SettingsSaveManager instance;

    private final SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Object> pending = new HashMap<>();
    private Runnable flushRunnable;

    private SettingsSaveManager(Context context) {
        sp = context.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public static SettingsSaveManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsSaveManager(context);
        }
        return instance;
    }

    // ---------- 暂存接口（各 Manager 调用） ----------

    public void putInt(String key, int value) {
        pending.put(key, value);
        scheduleFlush();
    }

    public void putFloat(String key, float value) {
        pending.put(key, value);
        scheduleFlush();
    }

    public void putBoolean(String key, boolean value) {
        pending.put(key, value);
        scheduleFlush();
    }

    public void putString(String key, String value) {
        pending.put(key, value);
        scheduleFlush();
    }

    // ---------- 读取接口（优先读 pending，否则读 SP） ----------

    public int getInt(String key, int defValue) {
        Object v = pending.get(key);
        return v instanceof Integer ? (int) v : sp.getInt(key, defValue);
    }

    public float getFloat(String key, float defValue) {
        Object v = pending.get(key);
        return v instanceof Float ? (float) v : sp.getFloat(key, defValue);
    }

    public boolean getBoolean(String key, boolean defValue) {
        Object v = pending.get(key);
        return v instanceof Boolean ? (boolean) v : sp.getBoolean(key, defValue);
    }

    public String getString(String key, String defValue) {
        Object v = pending.get(key);
        return v instanceof String ? (String) v : sp.getString(key, defValue);
    }

    // ---------- Debounce 逻辑 ----------

    private void scheduleFlush() {
        if (flushRunnable != null) {
            handler.removeCallbacks(flushRunnable);
        }
        flushRunnable = this::flush;
        handler.postDelayed(flushRunnable, DEBOUNCE_MS);
    }

    /** 立即写入所有待保存项，用于返回键 / 页面销毁 */
    public void flush() {
        if (flushRunnable != null) {
            handler.removeCallbacks(flushRunnable);
            flushRunnable = null;
        }
        if (pending.isEmpty()) return;

        SharedPreferences.Editor editor = sp.edit();
        for (Map.Entry<String, Object> e : pending.entrySet()) {
            Object v = e.getValue();
            String k = e.getKey();
            if (v instanceof Integer)      editor.putInt(k, (int) v);
            else if (v instanceof Float)   editor.putFloat(k, (float) v);
            else if (v instanceof Boolean) editor.putBoolean(k, (boolean) v);
            else if (v instanceof String)  editor.putString(k, (String) v);
        }
        editor.apply();
        pending.clear();
    }
}
