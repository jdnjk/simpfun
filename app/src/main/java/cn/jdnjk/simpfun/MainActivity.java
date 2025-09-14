package cn.jdnjk.simpfun;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import cn.jdnjk.simpfun.ui.profile.ProfileFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import cn.jdnjk.simpfun.api.MainApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import cn.jdnjk.simpfun.ui.server.ServerFragment;

public class MainActivity extends AppCompatActivity {
    private JSONArray instanceList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_server);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            View appBarLayout = findViewById(R.id.app_bar_layout);
            if (appBarLayout != null) {
                appBarLayout.setPadding(
                    appBarLayout.getPaddingLeft(),
                    statusBarHeight,
                    appBarLayout.getPaddingRight(),
                    appBarLayout.getPaddingBottom());
            }

            return insets;
        });

        BottomNavigationView navView = findViewById(R.id.nav_view);

        loadFragment(new ServerFragment());

        navView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_server) {
                fragment = new ServerFragment();
            } else if (itemId == R.id.navigation_profile) {
                fragment = new ProfileFragment();
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
                    Log.e("MainActivity", "Failed to parse instance list from API response", e);
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
    }
    public JSONArray getInstanceList() {
        return instanceList;
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