package cn.jdnjk.simpfun;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.graphics.Color;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import cn.jdnjk.simpfun.mcp.McpServerService;
import cn.jdnjk.simpfun.mcp.McpSettingsManager;
import cn.jdnjk.simpfun.ui.invite.InviteFragment;
import cn.jdnjk.simpfun.ui.profile.ProfileFragment;
import cn.jdnjk.simpfun.ui.server.ServerFragment;
import cn.jdnjk.simpfun.utils.AnnouncementHelper;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import cn.jdnjk.simpfun.utils.UpdateChecker;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_REFRESH_SERVER_LIST = "extra_refresh_server_list";

    private BottomNavigationView navView;
    private boolean bottomNavHidden = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            windowInsetsController.setAppearanceLightStatusBars(!isNightMode);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_server);

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            View navHostFragment = findViewById(R.id.nav_host_fragment);
            if (navHostFragment != null) {
                navHostFragment.setPadding(
                    navHostFragment.getPaddingLeft(),
                    statusBarHeight,
                    navHostFragment.getPaddingRight(),
                    0);
            }

            if (navView != null) {
                navView.setPadding(
                        navView.getPaddingLeft(),
                        navView.getPaddingTop(),
                        navView.getPaddingRight(),
                        navigationBarHeight);
            }

            return insets;
        });

        navView = findViewById(R.id.nav_view);

        if (savedInstanceState == null) {
            loadServerFragment();
        }

        navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_server) {
                loadServerFragment();
                return true;
            }
            if (itemId == R.id.navigation_invite) {
                loadInviteFragment();
                return true;
            }
            if (itemId == R.id.navigation_profile) {
                loadProfileFragment();
                return true;
            }

            return false;
        });

        handleIntent(getIntent());

        String token = getTokenFromSharedPreferences();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
        }

        resetMcpServerOnColdStart();
        AnnouncementHelper.maybeShowAnnouncement(this);
        UpdateChecker.checkUpdateIfNeeded(this);
    }

    /**
     * MCP 服务默认关闭：App 冷启动时停止服务并把开关复位为关，
     * 需要手动在设置页重新开启。避免无鉴权的高危接口意外常驻。
     */
    private void resetMcpServerOnColdStart() {
        try {
            McpSettingsManager manager = new McpSettingsManager(this);
            if (manager.isEnabled()) {
                manager.setEnabled(false);
            }
            if (McpServerService.isRunning()) {
                McpServerService.stop(this);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_SERVER_LIST, false)) {
            intent.removeExtra(EXTRA_REFRESH_SERVER_LIST);
            if (navView != null) {
                navView.setSelectedItemId(R.id.navigation_server);
            }
            loadServerFragment(true);
        }
    }


    private String getTokenFromSharedPreferences() {
        return getSharedPreferences("token", MODE_PRIVATE)
                .getString("token", null);
    }

    private void loadServerFragment() {
        loadServerFragment(false);
    }

    private void loadServerFragment(boolean forceRefresh) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, ServerFragment.newInstance(forceRefresh))
                .commit();
        showBottomNav(false);
    }

    private void loadInviteFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, new InviteFragment())
                .commit();
        showBottomNav(false);
    }

    private void loadProfileFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, new ProfileFragment())
                .commit();
        showBottomNav(false);
    }

    public void onPrimaryScroll(int dy, boolean atTop) {
        if (atTop || dy < -4) {
            showBottomNav(true);
        } else if (dy > 4) {
            hideBottomNav(true);
        }
    }

    public void showBottomNav(boolean animate) {
        if (navView == null || !bottomNavHidden) return;
        bottomNavHidden = false;
        if (animate) {
            navView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            navView.setTranslationY(0f);
            navView.setAlpha(1f);
        }
    }

    public void hideBottomNav(boolean animate) {
        if (navView == null || bottomNavHidden) return;
        bottomNavHidden = true;
        float target = navView.getHeight() + navView.getPaddingBottom();
        if (animate) {
            navView.animate()
                    .translationY(target)
                    .alpha(0.96f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            navView.setTranslationY(target);
            navView.setAlpha(0.96f);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_documentation) {
            openDocumentation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void openDocumentation() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://www.yuque.com/simpfox/simpdoc/main"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文档链接", Toast.LENGTH_SHORT).show();
        }
    }
}
