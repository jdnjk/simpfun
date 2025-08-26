package cn.jdnjk.simpfun;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import com.tencent.bugly.crashreport.CrashReport;

import android.os.Build;
import com.tencent.upgrade.bean.UpgradeConfig;
import com.tencent.upgrade.core.UpgradeManager;

import static cn.jdnjk.simpfun.BuildConfig.*;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences sp = getSharedPreferences("token", MODE_PRIVATE);
            String token = sp.getString("token", null);

            Intent intent;
            if (token != null && !token.isEmpty()) {
                new UserApi(this).UserInfo(token);
                initBugly();
                initShiplyUpgradeSDK();
            } else {
                intent = new Intent(SplashActivity.this, AuthActivity.class);
                startActivity(intent);
                finish();
            }
        }, 1500);
    }

    private void initBugly() {
        String deviceInfo = android.os.Build.BRAND + ":" +
                android.os.Build.MODEL + ":" +
                android.os.Build.VERSION.RELEASE;
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(this);
        strategy.setDeviceModel(deviceInfo);
        CrashReport.initCrashReport(getApplicationContext(), BUGLY_ID, false, strategy);
    }

    private void initShiplyUpgradeSDK() {
        SharedPreferences userInfoSp = getSharedPreferences("user_info", MODE_PRIVATE);
        String username = userInfoSp.getString("username", "unknown");
        int uid = userInfoSp.getInt("uid", 0);
        String userId = username + "_" + uid;

        UpgradeConfig.Builder builder = new UpgradeConfig.Builder();
        builder.appId(SHIPLY_ID)
                .appKey(SHIPLY_KEY)
                .systemVersion(String.valueOf(Build.VERSION.SDK_INT)) // 用户手机系统版本，用于匹配shiply前端创建任务时设置的系统版本下发条件
                .cacheExpireTime(1000 * 60 * 60 * 6) // 灰度策略的缓存时长（ms），如果不设置，默认缓存时长为1天
                .internalInitMMKVForRDelivery(true) // 是否由sdk内部初始化mmkv(调用MMKV.initialize()),业务方如果已经初始化过mmkv，可以设置为false
                .userId(userId); // 用户Id,用于匹配shiply前端创建的任务中的体验名单以及下发条件中的用户号码包
        UpgradeManager.getInstance().init(this, builder.build());
    }
}