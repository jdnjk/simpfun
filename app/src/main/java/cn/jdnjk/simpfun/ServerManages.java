package cn.jdnjk.simpfun;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import cn.jdnjk.simpfun.api.ins.PowerApi;
import cn.jdnjk.simpfun.databinding.ActivityMainBinding;

public class ServerManages extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    private int deviceId = -1;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        extractDeviceId();
    }

    private void extractDeviceId() {
        if (getIntent() != null) {
            deviceId = getIntent().getIntExtra(EXTRA_DEVICE_ID, -1);

            if (deviceId != -1) {
                Log.d("ServerManages", "接收到 deviceId: " + deviceId);
            } else {
                Log.e("ServerManages", "未接收到有效的 deviceId");
                Snackbar.make(binding.getRoot(), "服务器信息无效", Snackbar.LENGTH_INDEFINITE)
                        .setAction("关闭", v -> finish()).show();
            }
        }
    }

    private String getToken() {
        SharedPreferences sp = getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        String token = getToken();

        if (token == null || deviceId == -1) {
            Toast.makeText(this, "Token 或设备ID无效", Toast.LENGTH_SHORT).show();
            return true;
        }

        PowerApi api = new PowerApi(this);
        int id = item.getItemId();
        if (id == R.id.action_start) {
            api.powerControl(token, deviceId, PowerApi.Action.START, getPowerCallback("开机"));
            return true;
        } else if (id == R.id.action_restart) {
            api.powerControl(token, deviceId, PowerApi.Action.RESTART, getPowerCallback("重启"));
            return true;
        } else if (id == R.id.action_stop) {
            api.powerControl(token, deviceId, PowerApi.Action.STOP, getPowerCallback("关机"));
            return true;
        } else if (id == R.id.action_kill) {
            api.powerControl(token, deviceId, PowerApi.Action.KILL, getPowerCallback("强制关机"));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private PowerApi.Callback getPowerCallback(String actionName) {
        return new PowerApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(ServerManages.this, actionName + "成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(ServerManages.this, actionName + "失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        };
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
