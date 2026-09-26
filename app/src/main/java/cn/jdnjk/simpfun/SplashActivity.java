package cn.jdnjk.simpfun;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import com.tencent.bugly.crashreport.CrashReport;
import com.tencent.shiply.processor.DiffPkgHandler;
import com.tencent.shiply.processor.OriginBasePkgFile;
import com.tencent.upgrade.bean.UpgradeConfig;
import com.tencent.upgrade.core.UpgradeManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static cn.jdnjk.simpfun.BuildConfig.*;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final String SETTINGS_SP = "setting_sp";
    private static final String LEGACY_DEBUG_SP = "debug_settings";
    private static final String LEGACY_UI_SP = "ui_preferences";
    private static final String LEGACY_TERMINAL_THEME_SP = "terminal_theme_preferences";
    private static final String LEGACY_THEME_SP = "theme_preferences";
    private int deepLinkDeviceId = -1; // 深链指定的服务器ID
    private boolean deepLinkError = false; // 深链是否错误
    private String deepLinkRaw = null; // 原始深链内容
    private static final String SP_DEBUG = SETTINGS_SP;
    private static final String KEY_BUGLY_ENABLED = "bugly_enabled";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_TERMINAL_THEME_MODE = "terminal_theme_mode";
    private static final String KEY_MODERN_SERVER_CARD = "modern_server_card";

    public static final String EXTRA_DEEP_SERVER_ID = "extra_deep_server_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        migrateLegacySettingPreferences();
        ThemeUtils.applySavedTheme(this);
        setContentView(R.layout.activity_splash);
        final SharedPreferences spDebug = getSharedPreferences(SP_DEBUG, Context.MODE_PRIVATE);

        parseDeepLink();
        if (deepLinkError) {
            showLinkErrorDialog();
            return;
        }

        SharedPreferences sp = getSharedPreferences("token", MODE_PRIVATE);
        String token = sp.getString("token", null);

        if (token != null && !token.isEmpty()) {
            new UserApi(this).UserInfo(token, new UserApi.AuthCallback() {
                @Override
                public void onSuccess() {
                    boolean buglyEnabled = spDebug.getBoolean(KEY_BUGLY_ENABLED, true);
                    if (buglyEnabled) {
                        initBugly();
                    }
                    navigateAfterAuth();
                }

                @Override
                public void onFailure() {
                    Intent auth = new Intent(SplashActivity.this, AuthActivity.class);
                    if (deepLinkDeviceId != -1) auth.putExtra(EXTRA_DEEP_SERVER_ID, deepLinkDeviceId);
                    startActivity(auth);
                    finish();
                }
            });
        } else {
            Intent auth = new Intent(SplashActivity.this, AuthActivity.class);
            if (deepLinkDeviceId != -1) auth.putExtra(EXTRA_DEEP_SERVER_ID, deepLinkDeviceId);
            startActivity(auth);
            finish();
        }
    }

    private void migrateLegacySettingPreferences() {
        SharedPreferences targetPrefs = getSharedPreferences(SETTINGS_SP, Context.MODE_PRIVATE);
        SharedPreferences.Editor targetEditor = targetPrefs.edit();
        boolean migrated = false;

        migrated |= migrateBooleanKey(LEGACY_DEBUG_SP, KEY_BUGLY_ENABLED, targetPrefs, targetEditor);
        migrated |= migrateBooleanKey(LEGACY_UI_SP, KEY_MODERN_SERVER_CARD, targetPrefs, targetEditor);
        migrated |= migrateIntKey(LEGACY_TERMINAL_THEME_SP, KEY_TERMINAL_THEME_MODE, targetPrefs, targetEditor);
        migrated |= migrateIntKey(LEGACY_THEME_SP, KEY_THEME_MODE, targetPrefs, targetEditor);

        if (migrated && !targetEditor.commit()) {
            Log.w("SplashActivity", "Failed to migrate legacy settings");
            return;
        }

        cleanupLegacyPrefs(LEGACY_DEBUG_SP);
        cleanupLegacyPrefs(LEGACY_UI_SP);
        cleanupLegacyPrefs(LEGACY_TERMINAL_THEME_SP);
        cleanupLegacyPrefs(LEGACY_THEME_SP);
    }

    private boolean migrateBooleanKey(String legacyPrefsName, String key, SharedPreferences targetPrefs,
            SharedPreferences.Editor targetEditor) {
        SharedPreferences legacyPrefs = getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE);
        if (!legacyPrefs.contains(key) || targetPrefs.contains(key)) {
            return false;
        }
        targetEditor.putBoolean(key, legacyPrefs.getBoolean(key, false));
        return true;
    }

    private boolean migrateIntKey(String legacyPrefsName, String key, SharedPreferences targetPrefs,
            SharedPreferences.Editor targetEditor) {
        SharedPreferences legacyPrefs = getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE);
        if (!legacyPrefs.contains(key) || targetPrefs.contains(key)) {
            return false;
        }
        targetEditor.putInt(key, legacyPrefs.getInt(key, 0));
        return true;
    }

    private void cleanupLegacyPrefs(String prefsName) {
        SharedPreferences legacyPrefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        if (!legacyPrefs.getAll().isEmpty()) {
            legacyPrefs.edit().clear().apply();
        }

        File sharedPrefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
        File prefsFile = new File(sharedPrefsDir, prefsName + ".xml");
        if (prefsFile.exists() && !prefsFile.delete()) {
            Log.w("SplashActivity", "Failed to delete legacy prefs file: " + prefsName);
        }
    }

    private void parseDeepLink() {
        Intent intent = getIntent();
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        if (!"simpfun".equalsIgnoreCase(data.getScheme())) return;
        deepLinkRaw = data.toString();
        String host = data.getHost(); // simpfun://server/xxx -> host=server
        if (host == null || host.isEmpty()) {
            return;
        }
        if ("server".equalsIgnoreCase(host)) {
            java.util.List<String> pathSegments = data.getPathSegments();
            if (pathSegments.isEmpty()) {
                deepLinkError = true; // 缺少ID
                return;
            }
            try {
                deepLinkDeviceId = Integer.parseInt(pathSegments.get(0));
                Log.d("SplashActivity", "服务器ID=" + deepLinkDeviceId);
            } catch (NumberFormatException e) {
                deepLinkError = true; // ID格式错误
            }
            return;
        }
        deepLinkError = true;
    }

    private void showLinkErrorDialog() {
        String msg = "应用链接错误: " + (deepLinkRaw == null ? "(空)" : deepLinkRaw);
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    finishAffinity();
                })
                .show();
    }

    private void navigateAfterAuth() {
        if (deepLinkDeviceId != -1) {
            SharedPreferences sp = getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            sp.edit().putInt("device_id", deepLinkDeviceId).apply();
            Intent sm = new Intent(this, ServerManages.class);
            sm.putExtra(ServerManages.EXTRA_DEVICE_ID, deepLinkDeviceId);
            startActivity(sm);
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    private void initBugly() {
        String deviceInfo = android.os.Build.BRAND + ":" +
                android.os.Build.MODEL + ":" +
                android.os.Build.VERSION.RELEASE;
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(this);
        strategy.setDeviceModel(deviceInfo);
        strategy.setAppVersion(VERSION_NAME + "." + VERSION_CODE);
        CrashReport.initCrashReport(getApplicationContext(), BUGLY_ID, DEBUG, strategy);
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
        String username = sp.getString("username", null);
        String uid = String.valueOf(sp.getInt("uid",-1));
        String qq = String.valueOf(sp.getLong("qq", -1L));
        CrashReport.setUserId(username + "/" + uid);

        Map<String, String> map = new HashMap<>();
        map.put("UID", uid);
        map.put("QQ", qq);
        UpgradeConfig.Builder builder = new UpgradeConfig.Builder();
        builder.appId(BuildConfig.SHIPLY_ID)
                .appKey(BuildConfig.SHIPLY_KEY)
                .systemVersion(String.valueOf(Build.VERSION.SDK_INT))
                .customParams(map) // 自定义属性键值对，用于匹配shiply前端创建任务时设置的自定义下发条件
                .internalInitMMKVForRDelivery(true)
                .userId(username + "_" + uid)
                // 差量APK处理器，负责差量包下载与合成（在走SDK下载链路时生效）
                .diffPkgHandler(new DiffPkgHandler())
                // 差量基准包基于原始APK文件生成（本项目无渠道包，不支持渠道包差量也够用）
                .basePkgFileForDiffUpgrade(new OriginBasePkgFile());
        UpgradeManager.getInstance().init(SplashActivity.this, builder.build());
    }
}
