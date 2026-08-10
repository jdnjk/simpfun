package cn.jdnjk.simpfun.ui.troubleshoot;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.utils.ThemeUtils;

/**
 * 故障排错页（独立 Activity）。
 * 支持外部打开与 scheme 深链：
 *   simpfun://troubleshoot            -> 故障排错入口页
 *   simpfun://troubleshoot/status     -> 直接进入状态监控
 */
public class TroubleshootActivity extends AppCompatActivity {
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        ThemeUtils.applySystemBarAppearance(this);

        setContentView(R.layout.activity_troubleshoot);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            } else {
                finish();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setToolbarTitle("故障排错");
            }
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, resolveInitialFragment())
                .commit();
    }

    private Fragment resolveInitialFragment() {
        Uri uri = getIntent() != null ? getIntent().getData() : null;
        if (uri != null && "troubleshoot".equals(uri.getHost()) && uri.getPathSegments().contains("status")) {
            setToolbarTitle("状态监控");
            return new StatusMonitorFragment();
        }
        setToolbarTitle("故障排错");
        return new TroubleshootFragment();
    }

    public void openFirewallPage() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new FirewallFragment())
                .addToBackStack("firewall")
                .commit();
        setToolbarTitle("防火墙");
    }

    public void openStatusMonitorPage() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new StatusMonitorFragment())
                .addToBackStack("status_monitor")
                .commit();
        setToolbarTitle("状态监控");
    }

    private void setToolbarTitle(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }
}
