package cn.jdnjk.simpfun;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import cn.jdnjk.simpfun.api.MainApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import cn.jdnjk.simpfun.ui.server.ServerFragment;
import cn.jdnjk.simpfun.ui.profile.ProfileFragment;

public class MainActivity extends AppCompatActivity {

    private JSONArray instanceList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        BottomNavigationView navView = findViewById(R.id.nav_view);

        loadFragment(new ServerFragment());

        navView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_server) {
                fragment = new ServerFragment();
            }/* else if (itemId == R.id.navigation_profile) {
                fragment = new ProfileFragment();
            }*/

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
                    e.printStackTrace();
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

                    SharedPreferences sp = getSharedPreferences("server_data", Context.MODE_PRIVATE);
                    sp.edit().putString("instance_list", instanceList.toString()).apply();

                    Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                    if (currentFragment instanceof ServerFragment) {
                        ((ServerFragment) currentFragment).updateInstanceList(instanceList);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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
}