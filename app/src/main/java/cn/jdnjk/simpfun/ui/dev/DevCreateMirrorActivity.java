package cn.jdnjk.simpfun.ui.dev;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.CServerApi;
import cn.jdnjk.simpfun.api.dev.DevApi;
import cn.jdnjk.simpfun.model.DevBackupItem;

public class DevCreateMirrorActivity extends AppCompatActivity {

    private DevApi devApi;
    private String token;

    private DevBackupAdapter backupAdapter;
    private LinearLayout llBackupStep, llFormStep;
    private TextView tvSelectedBackup, tvBackupsEmpty;
    private DevBackupItem selectedBackup;

    private static class GameOption {
        final int id;
        final String name;
        GameOption(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final List<GameOption> gameOptions = new ArrayList<>();
    private GameOption selectedGame;
    private MaterialAutoCompleteTextView actGame;

    private EditText etMirrorName, etMirrorDescription, etVersionName,
            etVersionDescription, etVersionRecommend, etSupportGroup, etLink,
            etOneKey, etProperties;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_create_mirror);

        devApi = new DevApi();
        token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);
        if (token == null) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar_create_mirror);
        toolbar.setNavigationOnClickListener(v -> finish());

        llBackupStep = findViewById(R.id.ll_backup_step);
        llFormStep = findViewById(R.id.ll_mirror_form_step);
        tvSelectedBackup = findViewById(R.id.tv_create_selected_backup);
        tvBackupsEmpty = findViewById(R.id.tv_create_backups_empty);

        RecyclerView recyclerBackups = findViewById(R.id.recycler_create_backups);
        backupAdapter = new DevBackupAdapter(this::onBackupSelected);
        recyclerBackups.setLayoutManager(new LinearLayoutManager(this));
        recyclerBackups.setAdapter(backupAdapter);

        actGame = findViewById(R.id.act_mirror_game);
        etMirrorName = findViewById(R.id.et_create_mirror_name);
        etMirrorDescription = findViewById(R.id.et_create_mirror_description);
        etVersionName = findViewById(R.id.et_create_version_name);
        etVersionDescription = findViewById(R.id.et_create_version_description);
        etVersionRecommend = findViewById(R.id.et_create_version_recommend);
        etSupportGroup = findViewById(R.id.et_create_support_group);
        etLink = findViewById(R.id.et_create_link);
        etOneKey = findViewById(R.id.et_create_one_key);
        etProperties = findViewById(R.id.et_create_properties);

        findViewById(R.id.btn_create_change_backup).setOnClickListener(v -> showBackupStep());
        findViewById(R.id.btn_submit_create_mirror).setOnClickListener(v -> confirmCreateMirror());

        loadGameOptions();
        loadBackups();
    }

    private void loadGameOptions() {
        CServerApi.getGameList(true, token).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(DevCreateMirrorActivity.this,
                        "镜像类别加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                List<GameOption> options = new ArrayList<>();
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject json = new JSONObject(body);
                    JSONArray arr = json.optJSONArray("list");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            options.add(new GameOption(o.optInt("id", -1), o.optString("name", "")));
                        }
                    }
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    gameOptions.clear();
                    gameOptions.addAll(options);
                    List<String> names = new ArrayList<>();
                    for (GameOption option : gameOptions) names.add(option.name);
                    actGame.setSimpleItems(names.toArray(new String[0]));

                    // 默认选 MinecraftJE，找不到则选第一项
                    selectedGame = null;
                    for (GameOption option : gameOptions) {
                        if ("MinecraftJE".equalsIgnoreCase(option.name)) {
                            selectedGame = option;
                            break;
                        }
                    }
                    if (selectedGame == null && !gameOptions.isEmpty()) {
                        selectedGame = gameOptions.get(0);
                    }
                    if (selectedGame != null) {
                        actGame.setText(selectedGame.name, false);
                    }
                    actGame.setOnItemClickListener((parent, view, position, id) -> {
                        if (position >= 0 && position < gameOptions.size()) {
                            selectedGame = gameOptions.get(position);
                        }
                    });
                });
            }
        });
    }

    private void loadBackups() {
        devApi.getAllBackups(token, new DevApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray list = response.optJSONArray("list");
                List<DevBackupItem> backups = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        DevBackupItem item = DevBackupItem.fromJson(list.optJSONObject(i));
                        if (item != null && item.getId() > 0) backups.add(item);
                    }
                }
                backupAdapter.submitItems(backups);
                tvBackupsEmpty.setVisibility(backups.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(DevCreateMirrorActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                tvBackupsEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onBackupSelected(DevBackupItem item) {
        selectedBackup = item;
        tvSelectedBackup.setText(item.getTag().isEmpty() ? "备份 #" + item.getId() : item.getTag());
        llBackupStep.setVisibility(View.GONE);
        llFormStep.setVisibility(View.VISIBLE);
    }

    private void showBackupStep() {
        llBackupStep.setVisibility(View.VISIBLE);
        llFormStep.setVisibility(View.GONE);
    }

    private void confirmCreateMirror() {
        String name = etMirrorName.getText().toString().trim();
        String version = etVersionName.getText().toString().trim();
        if (selectedBackup == null) {
            showBackupStep();
            return;
        }
        if (name.isEmpty()) {
            Toast.makeText(this, "镜像名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (version.isEmpty()) {
            Toast.makeText(this, "版本名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedGame == null) {
            Toast.makeText(this, "请选择镜像类别", Toast.LENGTH_SHORT).show();
            return;
        }
        long supportGroup;
        try {
            supportGroup = Long.parseLong(etSupportGroup.getText().toString().trim());
        } catch (Exception e) {
            supportGroup = 0;
        }
        final long fSupportGroup = supportGroup;
        final GameOption fGame = selectedGame;

        new MaterialAlertDialogBuilder(this)
                .setTitle("创建镜像")
                .setMessage("确定使用备份「" + tvSelectedBackup.getText().toString()
                        + "」创建镜像「" + name + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> submitCreateMirror(fGame.id, fSupportGroup))
                .show();
    }

    private void submitCreateMirror(long gameId, long supportGroup) {
        devApi.createMirror(token, selectedBackup.getId(),
                etMirrorName.getText().toString().trim(),
                etMirrorDescription.getText().toString(),
                etVersionName.getText().toString().trim(),
                etVersionDescription.getText().toString(),
                etVersionRecommend.getText().toString(),
                supportGroup,
                etLink.getText().toString(),
                etOneKey.getText().toString(),
                etProperties.getText().toString(),
                gameId,
                new DevApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        Toast.makeText(DevCreateMirrorActivity.this, "创建成功", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(DevCreateMirrorActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
