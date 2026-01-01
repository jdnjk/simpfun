package cn.jdnjk.simpfun;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import cn.jdnjk.simpfun.api.MainApi;
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
                fetchServerDetails();
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

    private void fetchServerDetails() {
        String token = getToken();
        if (token == null) return;

        new MainApi(this).getInstanceDetail(token, String.valueOf(deviceId), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject jsonResponse) {
                try {
                    JSONObject data = jsonResponse.optJSONObject("data");
                    if (data == null) {
                        data = jsonResponse;
                    }

                    String name;
                    if (data.isNull("name")) {
                        name = "未命名实例";
                    } else {
                        name = data.optString("name");
                        if (name.isEmpty()) {
                            name = "未命名实例";
                        }
                    }
                    String status = data.optString("status", "offline");

                    String statusText = switch (status) {
                        case "offline" -> "已离线";
                        case "running" -> "运行中";
                        case "installing" -> "安装中";
                        case "stopping" -> "停止中";
                        case "starting" -> "启动中";
                        default -> "未知状态";
                    };

                    String ipStr = "IP: N/A";
                    String fullIp = "";
                    JSONArray allocations = data.optJSONArray("allocations");
                    if (allocations != null) {
                        for (int i = 0; i < allocations.length(); i++) {
                            JSONObject alloc = allocations.getJSONObject(i);
                            if (alloc.optBoolean("is_default")) {
                                String ip = alloc.optString("ip");
                                int port = alloc.optInt("port");
                                fullIp = ip + ":" + port;
                                ipStr = "IP: " + fullIp;
                                break;
                            }
                        }
                        // If no default found, use the first one if available
                        if (fullIp.isEmpty() && allocations.length() > 0) {
                            JSONObject alloc = allocations.getJSONObject(0);
                            String ip = alloc.optString("ip");
                            int port = alloc.optInt("port");
                            fullIp = ip + ":" + port;
                            ipStr = "IP: " + fullIp;
                        }
                    }

                    String finalName = name;
                    String finalStatusText = statusText;
                    String finalIpStr = ipStr;
                    String finalFullIp = fullIp;

                    runOnUiThread(() -> {
                        NavigationView navigationView = binding.navView;
                        View headerView = navigationView.getHeaderView(0);

                        TextView textName = headerView.findViewById(R.id.textServerName);
                        TextView textIp = headerView.findViewById(R.id.textServerIp);
                        TextView textStatus = headerView.findViewById(R.id.textServerStatus);

                        if (textName != null) {
                            textName.setText(finalName);
                            textName.setOnClickListener(v -> showRenameDialog(finalName));
                        }
                        if (textStatus != null) textStatus.setText(finalStatusText);
                        if (textIp != null) {
                            textIp.setText(finalIpStr);
                            textIp.setOnClickListener(v -> {
                                if (!finalFullIp.isEmpty()) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText("Server IP", finalFullIp);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(ServerManages.this, "IP已复制", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                } catch (Exception e) {
                    Log.e("ServerManages", "Error parsing server details", e);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("ServerManages", "Failed to get server details: " + errorMsg);
            }
        });
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

    private void showRenameDialog(String currentName) {
        final EditText input = new EditText(this);
        input.setText(currentName);
        input.setSelection(currentName.length());

        new AlertDialog.Builder(this)
                .setTitle("重命名服务器")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString();
                    performRename(newName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performRename(String newName) {
        String token = getToken();
        if (token == null || deviceId == -1) return;

        new MainApi(this).renameInstance(token, String.valueOf(deviceId), newName, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(ServerManages.this, "重命名成功", Toast.LENGTH_SHORT).show();
                    // Update the name in the UI
                    NavigationView navigationView = binding.navView;
                    View headerView = navigationView.getHeaderView(0);
                    TextView textName = headerView.findViewById(R.id.textServerName);
                    if (textName != null) {
                        textName.setText(newName);
                        // Update the click listener with the new name
                        textName.setOnClickListener(v -> showRenameDialog(newName));
                    }
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                runOnUiThread(() -> {
                    Toast.makeText(ServerManages.this, "重命名失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
