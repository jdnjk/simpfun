package cn.jdnjk.simpfun;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import cn.jdnjk.simpfun.api.UserApi;
import com.tencent.bugly.crashreport.CrashReport;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initBugly();
    }

    private void initBugly() {
        String deviceInfo = android.os.Build.BRAND + ":" +
                android.os.Build.MODEL + ":" +
                android.os.Build.VERSION.RELEASE;
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(this);
        strategy.setDeviceModel(deviceInfo);
        CrashReport.initCrashReport(getApplicationContext(), "bb4476237b", true, strategy);
    }
}