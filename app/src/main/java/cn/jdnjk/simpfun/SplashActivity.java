package cn.jdnjk.simpfun;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import cn.jdnjk.simpfun.ui.setting.ThemeManager;
import com.tencent.bugly.crashreport.CrashReport;
import static cn.jdnjk.simpfun.BuildConfig.*;

public class SplashActivity extends AppCompatActivity {

    private int deepLinkDeviceId = -1; // 深链指定的服务器ID
    private boolean deepLinkError = false; // 深链是否错误
    private String deepLinkRaw = null; // 原始深链内容

    public static final String EXTRA_DEEP_SERVER_ID = "extra_deep_server_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.getInstance(this).initializeTheme();

        parseDeepLink();
        if (deepLinkError) {
            showLinkErrorDialog();
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences sp = getSharedPreferences("token", MODE_PRIVATE);
            String token = sp.getString("token", null);

            if (token != null && !token.isEmpty()) {
                new UserApi(this).UserInfo(token, new UserApi.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        initBugly();
                        navigateAfterAuth();
                    }

                    @Override
                    public void onFailure() {
                        // 验证失败转登录（保留有效跳转信息）
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
        }, 1500);
    }

    private void parseDeepLink() {
        Intent intent = getIntent();
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        if (!"simpfun".equalsIgnoreCase(data.getScheme())) return;
        deepLinkRaw = data.toString();
        String host = data.getHost(); // simpfun://server?id=xxx -> host=server
        if (host == null || host.isEmpty()) {
            return;
        }
        if ("server".equalsIgnoreCase(host)) {
            String idStr = data.getQueryParameter("id");
            if (idStr == null) {
                deepLinkError = true; // 缺少ID
                return;
            }
            try {
                deepLinkDeviceId = Integer.parseInt(idStr);
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
        CrashReport.initCrashReport(getApplicationContext(), BUGLY_ID, DEBUG, strategy);
        SharedPreferences sp = getSharedPreferences("user_info", MODE_PRIVATE);
        String username = sp.getString("username", null);
        SharedPreferences sp1 = getSharedPreferences("user_info", MODE_PRIVATE);
        String uid = sp1.getString("uid", null);
        CrashReport.setUserId(username + "/" + uid);
    }
}