package cn.jdnjk.simpfun.ui.dev;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.dev.DevApi;
import cn.jdnjk.simpfun.model.DevBackupItem;
import cn.jdnjk.simpfun.model.DevVersionItem;

public class DevMirrorDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MIRROR_ID = "extra_mirror_id";
    public static final String EXTRA_MIRROR_NAME = "extra_mirror_name";

    public static final String SP_DEV_SETTINGS = "dev_settings";
    public static final String KEY_PREFILL_VERSION_INFO = "prefill_version_info";

    private DevApi devApi;
    private int mirrorId;
    private String token;

    private MaterialSwitch switchState;
    private EditText etGame, etName, etDescription, etSupportGroup, etLink;
    private TextView tvRestore, tvLike, tvDislike;

    private View versionsTab;
    private DevVersionAdapter versionAdapter;
    private RecyclerView recyclerVersions;
    private TextView tvVersionsEmpty;
    private List<DevVersionItem> versions = new ArrayList<>();
    private boolean versionsLoaded = false;

    private DevBackupAdapter backupAdapter;
    private LinearLayout llBackupStep, llVersionFormStep;
    private TextView tvSelectedBackup, tvBackupsEmpty;
    private RecyclerView recyclerBackups;
    private EditText etNewVersionName, etNewVersionDescription, etNewVersionRecommend,
            etNewVersionOneKey, etNewVersionProperties;
    private DevBackupItem selectedBackup;
    private DevBackupItem pendingPrefillBackup;
    private boolean backupsLoaded = false;

    private DevFeedbackAdapter feedbackAdapter;
    private RecyclerView recyclerFeedback;
    private TextView tvFeedbackEmpty;
    private TextView tvFbRestore, tvFbLike, tvFbDislike;

    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav;
    private View[] tabContents;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_mirror_detail);

        devApi = new DevApi();
        token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);
        mirrorId = getIntent().getIntExtra(EXTRA_MIRROR_ID, -1);
        String mirrorName = getIntent().getStringExtra(EXTRA_MIRROR_NAME);

        if (mirrorId <= 0 || token == null) {
            Toast.makeText(this, "无效的镜像", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar_mirror_detail);
        toolbar.setTitle(mirrorName == null || mirrorName.isEmpty() ? "镜像详情" : mirrorName);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        setupTabs();
        setupInfoTab();
        setupVersionsTab();
        setupCreateVersionTab();
        setupFeedbackTab();

        loadDetail();
    }

    private void bindViews() {
        bottomNav = findViewById(R.id.bottom_nav_mirror);
        View infoTab = findViewById(R.id.tab_info);
        versionsTab = findViewById(R.id.tab_versions);
        View createVersionTab = findViewById(R.id.tab_create_version);
        View feedbackTab = findViewById(R.id.tab_feedback);
        tabContents = new View[]{infoTab, versionsTab, createVersionTab, feedbackTab};

        switchState = infoTab.findViewById(R.id.switch_mirror_state);
        etGame = infoTab.findViewById(R.id.et_mirror_game);
        etName = infoTab.findViewById(R.id.et_mirror_name);
        etDescription = infoTab.findViewById(R.id.et_mirror_description);
        etSupportGroup = infoTab.findViewById(R.id.et_mirror_support_group);
        etLink = infoTab.findViewById(R.id.et_mirror_link);
        tvRestore = infoTab.findViewById(R.id.tv_mirror_restore_times);
        tvLike = infoTab.findViewById(R.id.tv_mirror_like);
        tvDislike = infoTab.findViewById(R.id.tv_mirror_dislike);

        recyclerVersions = versionsTab.findViewById(R.id.recycler_dev_versions);
        tvVersionsEmpty = versionsTab.findViewById(R.id.tv_versions_empty);

        llBackupStep = createVersionTab.findViewById(R.id.ll_backup_step);
        llVersionFormStep = createVersionTab.findViewById(R.id.ll_version_form_step);
        tvSelectedBackup = createVersionTab.findViewById(R.id.tv_selected_backup);
        tvBackupsEmpty = createVersionTab.findViewById(R.id.tv_backups_empty);
        recyclerBackups = createVersionTab.findViewById(R.id.recycler_dev_backups);
        etNewVersionName = createVersionTab.findViewById(R.id.et_new_version_name);
        etNewVersionDescription = createVersionTab.findViewById(R.id.et_new_version_description);
        etNewVersionRecommend = createVersionTab.findViewById(R.id.et_new_version_recommend);
        etNewVersionOneKey = createVersionTab.findViewById(R.id.et_new_version_one_key);
        etNewVersionProperties = createVersionTab.findViewById(R.id.et_new_version_properties);

        tvFbRestore = feedbackTab.findViewById(R.id.tv_fb_restore);
        tvFbLike = feedbackTab.findViewById(R.id.tv_fb_like);
        tvFbDislike = feedbackTab.findViewById(R.id.tv_fb_dislike);
        recyclerFeedback = feedbackTab.findViewById(R.id.recycler_dev_feedback);
        tvFeedbackEmpty = feedbackTab.findViewById(R.id.tv_feedback_empty);
    }

    private void setupTabs() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_mirror_info) {
                selectTab(0);
                return true;
            }
            if (itemId == R.id.nav_mirror_versions) {
                selectTab(1);
                return true;
            }
            if (itemId == R.id.nav_mirror_update) {
                selectTab(2);
                return true;
            }
            if (itemId == R.id.nav_mirror_feedback) {
                selectTab(3);
                return true;
            }
            return false;
        });
        selectTab(0);
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabContents.length; i++) {
            tabContents[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        if (index == 1 && !versionsLoaded) {
            loadVersions();
        }
        if (index == 2 && !backupsLoaded) {
            loadBackups();
        }
        if (index == 3) {
            loadFeedback();
        }
    }

    private void setupInfoTab() {
        findViewById(R.id.btn_save_mirror).setOnClickListener(v -> saveMirrorInfo());
        findViewById(R.id.btn_delete_mirror).setOnClickListener(v -> confirmDeleteMirror());
    }

    private void loadDetail() {
        devApi.getMirrorDetail(token, mirrorId, new DevApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject data = response.optJSONObject("data");
                if (data == null) {
                    Toast.makeText(DevMirrorDetailActivity.this, "镜像详情为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                switchState.setChecked(data.optBoolean("state", false));
                etGame.setText(data.optString("game", "--"));
                etName.setText(data.optString("name", ""));
                etDescription.setText(data.optString("description", ""));
                etSupportGroup.setText(String.valueOf(data.optLong("support_group", 0)));
                etLink.setText(data.optString("link", ""));

                String restore = String.valueOf(data.optInt("restore_times", 0));
                tvRestore.setText(restore);
                tvLike.setText(String.valueOf(data.optInt("like", 0)));
                tvDislike.setText(String.valueOf(data.optInt("dislike", 0)));
                tvFbRestore.setText(restore);
                tvFbLike.setText(String.valueOf(data.optInt("like", 0)));
                tvFbDislike.setText(String.valueOf(data.optInt("dislike", 0)));
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onForbidden() {
                Toast.makeText(DevMirrorDetailActivity.this, "没有权限管理该镜像", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveMirrorInfo() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "镜像名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        long supportGroup;
        try {
            supportGroup = Long.parseLong(etSupportGroup.getText().toString().trim());
        } catch (Exception e) {
            supportGroup = 0;
        }

        devApi.updateMirrorDetail(token, mirrorId, switchState.isChecked(), name,
                etDescription.getText().toString(), supportGroup,
                etLink.getText().toString(), new DevApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        String msg = response.optString("msg", "保存成功");
                        Toast.makeText(DevMirrorDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                        loadDetail();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmDeleteMirror() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除镜像")
                .setMessage("确定删除镜像「" + (etName.getText().toString().isEmpty() ? "#" + mirrorId : etName.getText().toString()) + "」吗？此操作不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> devApi.deleteMirror(token, mirrorId, new DevApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        Toast.makeText(DevMirrorDetailActivity.this,
                                response.optString("msg", "删除成功"), Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                }))
                .show();
    }

    private void setupVersionsTab() {
        versionAdapter = new DevVersionAdapter(
                item -> openVersionEditor(item),
                item -> confirmDeleteVersion(item));
        recyclerVersions.setLayoutManager(new LinearLayoutManager(this));
        recyclerVersions.setAdapter(versionAdapter);

        com.google.android.material.floatingactionbutton.FloatingActionButton fabAddVersion =
                versionsTab.findViewById(R.id.fab_add_version);
        fabAddVersion.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_mirror_update));
    }

    private void loadVersions() {
        devApi.getVersions(token, mirrorId, new DevApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                versionsLoaded = true;
                JSONArray list = response.optJSONArray("list");
                versions = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        DevVersionItem item = DevVersionItem.fromJson(list.optJSONObject(i));
                        if (item != null && item.getId() > 0) versions.add(item);
                    }
                }
                versionAdapter.submitItems(versions);
                tvVersionsEmpty.setVisibility(versions.isEmpty() ? View.VISIBLE : View.GONE);

                if (pendingPrefillBackup != null) {
                    DevBackupItem item = pendingPrefillBackup;
                    pendingPrefillBackup = null;
                    onBackupSelected(item);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                tvVersionsEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openVersionEditor(DevVersionItem item) {
        android.content.Intent intent = new android.content.Intent(this, DevVersionEditActivity.class);
        intent.putExtra(DevVersionEditActivity.EXTRA_MIRROR_ID, mirrorId);
        intent.putExtra(DevVersionEditActivity.EXTRA_VERSION_ID, (long) item.getId());
        intent.putExtra(DevVersionEditActivity.EXTRA_STATE, item.isState());
        intent.putExtra(DevVersionEditActivity.EXTRA_NAME, item.getName());
        intent.putExtra(DevVersionEditActivity.EXTRA_DESCRIPTION, item.getDescription());
        intent.putExtra(DevVersionEditActivity.EXTRA_RECOMMEND_SETTING, item.getRecommendSetting());
        intent.putExtra(DevVersionEditActivity.EXTRA_ONE_KEY, item.getOneKey());
        intent.putExtra(DevVersionEditActivity.EXTRA_PROPERTIES, item.getProperties());
        try {
            intent.putExtra(DevVersionEditActivity.EXTRA_RESTORE_TIMES, Integer.parseInt(item.getRestoreTimes().trim()));
        } catch (Exception ignored) {
        }
        intent.putExtra(DevVersionEditActivity.EXTRA_LIKE, item.getLike());
        intent.putExtra(DevVersionEditActivity.EXTRA_DISLIKE, item.getDislike());
        startActivityForResult(intent, REQ_EDIT_VERSION);
    }

    private void confirmDeleteVersion(DevVersionItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除版本")
                .setMessage("确定删除版本「" + item.getName() + "」吗？此操作不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> devApi.deleteVersion(token, mirrorId, item.getId(), new DevApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        Toast.makeText(DevMirrorDetailActivity.this,
                                response.optString("msg", "删除成功"), Toast.LENGTH_SHORT).show();
                        loadVersions();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                }))
                .show();
    }

    private void setupCreateVersionTab() {
        backupAdapter = new DevBackupAdapter(this::onBackupSelected);
        recyclerBackups.setLayoutManager(new LinearLayoutManager(this));
        recyclerBackups.setAdapter(backupAdapter);

        findViewById(R.id.btn_change_backup).setOnClickListener(v -> showBackupStep());
        findViewById(R.id.btn_submit_new_version).setOnClickListener(v -> confirmCreateVersion());
    }

    private void loadBackups() {
        devApi.getAllBackups(token, new DevApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                backupsLoaded = true;
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
                Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                tvBackupsEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onBackupSelected(DevBackupItem item) {
        selectedBackup = item;
        tvSelectedBackup.setText(item.getTag().isEmpty() ? "备份 #" + item.getId() : item.getTag());

        SharedPreferences sp = getSharedPreferences(SP_DEV_SETTINGS, MODE_PRIVATE);
        boolean prefill = sp.getBoolean(KEY_PREFILL_VERSION_INFO, true);
        etNewVersionName.setText("");
        etNewVersionDescription.setText("");
        etNewVersionRecommend.setText("");
        etNewVersionOneKey.setText("");
        etNewVersionProperties.setText("");

        if (prefill && versions.isEmpty()) {
            pendingPrefillBackup = item;
            Toast.makeText(this, "正在载入版本配置…", Toast.LENGTH_SHORT).show();
            loadVersions();
            return;
        }

        if (prefill && !versions.isEmpty()) {
            DevVersionItem latest = versions.get(0);
            etNewVersionRecommend.setText(latest.getRecommendSetting());
            etNewVersionOneKey.setText(latest.getOneKey());
            etNewVersionProperties.setText(latest.getProperties());
        }
        showFormStep();
    }

    private void showBackupStep() {
        llBackupStep.setVisibility(View.VISIBLE);
        llVersionFormStep.setVisibility(View.GONE);
        if (!backupsLoaded) {
            loadBackups();
        }
    }

    private void showFormStep() {
        llBackupStep.setVisibility(View.GONE);
        llVersionFormStep.setVisibility(View.VISIBLE);
    }

    private void confirmCreateVersion() {
        String versionName = etNewVersionName.getText().toString().trim();
        if (versionName.isEmpty()) {
            Toast.makeText(this, "版本名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedBackup == null) {
            Toast.makeText(this, "请先选择备份", Toast.LENGTH_SHORT).show();
            showBackupStep();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("创建版本")
                .setMessage("确定使用备份「" + tvSelectedBackup.getText().toString() + "」创建版本「" + versionName + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> submitCreateVersion())
                .show();
    }

    private void submitCreateVersion() {
        devApi.createVersion(token, mirrorId, selectedBackup.getId(),
                etNewVersionName.getText().toString().trim(),
                etNewVersionDescription.getText().toString(),
                etNewVersionRecommend.getText().toString(),
                etNewVersionOneKey.getText().toString(),
                etNewVersionProperties.getText().toString(),
                new DevApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        String msg = response.optString("msg", "创建成功");
                        Toast.makeText(DevMirrorDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                        versionsLoaded = false;
                        // 通过底栏选中态切回版本编辑（触发 listener -> selectTab(1)）
                        bottomNav.setSelectedItemId(R.id.nav_mirror_versions);
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupFeedbackTab() {
        feedbackAdapter = new DevFeedbackAdapter();
        recyclerFeedback.setLayoutManager(new LinearLayoutManager(this));
        recyclerFeedback.setNestedScrollingEnabled(false);
        recyclerFeedback.setAdapter(feedbackAdapter);
    }

    private void loadFeedback() {
        devApi.getFeedback(token, mirrorId, new DevApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray list = response.optJSONArray("list");
                List<cn.jdnjk.simpfun.model.DevFeedbackItem> items = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        cn.jdnjk.simpfun.model.DevFeedbackItem item =
                                cn.jdnjk.simpfun.model.DevFeedbackItem.fromJson(list.optJSONObject(i));
                        if (item != null && item.getId() > 0) items.add(item);
                    }
                }
                feedbackAdapter.submitItems(items);
                tvFeedbackEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(String errorMsg) {
                tvFeedbackEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(DevMirrorDetailActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT_VERSION && resultCode == RESULT_OK) {
            versionsLoaded = false;
            loadVersions();
        }
    }

    private static final int REQ_EDIT_VERSION = 1001;
}
