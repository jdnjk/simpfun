package cn.jdnjk.simpfun;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cn.jdnjk.simpfun.api.MainApi;
import cn.jdnjk.simpfun.ui.invite.InviteFragment;
import cn.jdnjk.simpfun.ui.profile.ProfileFragment;
import cn.jdnjk.simpfun.ui.server.ServerFragment;
import cn.jdnjk.simpfun.ui.setting.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private JSONArray instanceList;
    private BottomNavigationView navView;
    private boolean bottomNavHidden = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
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
            loadFragment(new ServerFragment());
        }

        navView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_server) {
                fragment = new ServerFragment();
            } else if (itemId == R.id.navigation_invite) {
                fragment = new InviteFragment();
            } else if (itemId == R.id.navigation_profile) {
                fragment = new ProfileFragment();
            } else if (itemId == R.id.navigation_settings) {
                fragment = new SettingsFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            SharedPreferences sp = getSharedPreferences("server_data", Context.MODE_PRIVATE);
            String cachedJson = sp.getString("instance_list", null);
            if (cachedJson != null) {
                try {
                    instanceList = new JSONArray(cachedJson);
                    updateCurrentFragment();
                } catch (JSONException e) {
                    Log.e("MainActivity", "Failed to parse cached instance list", e);
                }
            }

            SharedPreferences sp1 = getSharedPreferences("token", Context.MODE_PRIVATE);
            String token = sp1.getString("token", null);
            fetchInstanceList(token);
            return false;
        });

        String token = getTokenFromSharedPreferences();
        if (token != null && !token.isEmpty()) {
            fetchInstanceList(token);
        } else {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCurrentFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (currentFragment instanceof ServerFragment) {
            ((ServerFragment) currentFragment).updateInstanceList(instanceList);
        }
    }

    private void fetchInstanceList(String token) {
        new MainApi(this).getInstanceList(token, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    instanceList = data.getJSONArray("list");
                    Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                    if (currentFragment instanceof ServerFragment) {
                        ((ServerFragment) currentFragment).updateInstanceList(instanceList);
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "InsFail", e);
                    Toast.makeText(MainActivity.this, "解析数据失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(MainActivity.this, "获取失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getTokenFromSharedPreferences() {
        return getSharedPreferences("token", MODE_PRIVATE)
                .getString("token", null);
    }
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
        showBottomNav(false);
    }
    public JSONArray getInstanceList() {
        return instanceList;
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