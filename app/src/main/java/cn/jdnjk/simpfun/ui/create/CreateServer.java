package cn.jdnjk.simpfun.ui.create;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.CServerApi;
import cn.jdnjk.simpfun.utils.Feedback;
import cn.jdnjk.simpfun.utils.MarkdownRenderer;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import com.bumptech.glide.Glide;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import android.view.MenuItem;
import android.content.Intent;
import android.net.Uri;
import org.jspecify.annotations.NonNull;

/**
 * 创建服务器向导
 */
public class CreateServer extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_REINSTALL = "reinstall";
    public static final String MODE_CHANGE_CONFIG = "change_config";
    public static final String EXTRA_SERVER_ID = "extra_server_id";
    public static final String EXTRA_RESULT_NEW_ID = "extra_result_new_id";
    public static final String EXTRA_RESULT_CHANGE_CONFIG = "extra_result_change_config";
    public static final String EXTRA_RESULT_CREATED = "extra_result_created";
    public static final String EXTRA_CURRENT_GAME_NAME = "extra_current_game_name";
    public static final String EXTRA_CURRENT_KIND_NAME = "extra_current_kind_name";
    public static final String EXTRA_CURRENT_IMAGE_NAME = "extra_current_image_name";
    public static final String EXTRA_CURRENT_VERSION_NAME = "extra_current_version_name";
    public static final String EXTRA_CURRENT_GAME_ID = "extra_current_game_id";
    public static final String EXTRA_CURRENT_KIND_ID = "extra_current_kind_id";
    public static final String EXTRA_CURRENT_VERSION_ID = "extra_current_version_id";
    public static final String EXTRA_CURRENT_CUSTOM_ID = "extra_current_custom_id";
    public static final String EXTRA_CURRENT_CUSTOM = "extra_current_custom";

    private enum Step {
        TYPE, GAME, IMAGE_KIND, VERSION, SPEC, CONFIRM, REINSTALL_OPTIONS
    }

    private enum WizardMode {
        CREATE, REINSTALL, CHANGE_CONFIG
    }

    private static final Step[] CREATE_STEPS = {Step.TYPE, Step.GAME, Step.IMAGE_KIND, Step.VERSION, Step.SPEC, Step.CONFIRM};
    private static final Step[] REINSTALL_STEPS = {Step.TYPE, Step.GAME, Step.IMAGE_KIND, Step.VERSION, Step.REINSTALL_OPTIONS};
    private static final Step[] CHANGE_CONFIG_STEPS = {Step.SPEC, Step.CONFIRM};

    private Step currentStep = Step.TYPE;
    private boolean isCustom = false;
    private ProgressBar progressBar;
    private MaterialToolbar toolbar;
    private CollapsingToolbarLayout collapsingToolbar;
    private TextView btnAction;
    private TextInputLayout layoutSearch;
    private EditText etSearch;
    private HorizontalScrollView hsPagination;
    private LinearLayout paginationContainer;
    private LinearLayout layoutGradeFilter;
    private android.widget.Spinner spGrade;
    private HorizontalScrollView hsSteps;
    private LinearLayout layoutSteps;
    private LinearLayout layoutReinstallOptions;
    private com.google.android.material.checkbox.MaterialCheckBox cbSaveBeforeReinstall;
    private com.google.android.material.checkbox.MaterialCheckBox cbDiffUpdate;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabHelp;

    // 分页/搜索相关（仅第三方镜像 服务端 选择步骤使用）
    private final List<ListItem> fullImageKindList = new ArrayList<>();
    private String imageKindSearchQuery = "";
    private int imageKindCurrentPage = 1;
    private static final int IMAGE_KIND_PAGE_SIZE = 10;

    private Integer gameId; // 选择的游戏/镜像分类
    private Integer imageKindId; // 镜像服务端/Kind id 或 customlist 的 id
    private Integer versionId; // 镜像版本 id
    private Integer specId; // 实例规格 item id

    private WizardMode wizardMode = WizardMode.CREATE;
    private int serverId = -1;
    private boolean isSubmitting = false;
    private String selectedGameName = "";
    private String selectedImageKindName = "";
    private String selectedVersionName = "";
    private String currentGameName = "";
    private String currentKindName = "";
    private String currentImageName = "";
    private String currentVersionName = "";
    private int currentGameId = -1;
    private int currentKindId = -1;
    private boolean hasCurrentCustom = false;
    private boolean currentCustom = false;

    private String token;

    private final List<ListItem> data = new ArrayList<>();
    private GenericAdapter adapter;

    private final List<JSONObject> masterSpecList = new ArrayList<>();
    private String selectedGrade = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_create_server);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress);
        toolbar = findViewById(R.id.toolbar);
        collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        btnAction = findViewById(R.id.btn_action);
        layoutSearch = findViewById(R.id.layout_search);
        etSearch = findViewById(R.id.et_search);
        hsPagination = findViewById(R.id.hs_pagination);
        paginationContainer = findViewById(R.id.pagination_container);
        layoutGradeFilter = findViewById(R.id.layout_grade_filter);
        spGrade = findViewById(R.id.sp_grade);
        TextView tvCpuModelLink = findViewById(R.id.tv_cpu_model_link);
        if (tvCpuModelLink != null) {
            tvCpuModelLink.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://yuque.com/simpfun/sfe/areas"));
                startActivity(intent);
            });
        }
        hsSteps = findViewById(R.id.hs_steps);
        layoutSteps = findViewById(R.id.layout_steps);
        layoutReinstallOptions = findViewById(R.id.layout_reinstall_options);
        cbSaveBeforeReinstall = findViewById(R.id.cb_save_before_reinstall);
        cbDiffUpdate = findViewById(R.id.cb_diff_update);
        fabHelp = findViewById(R.id.fab_help);

        if (!readIntentExtras()) {
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GenericAdapter(data, this::onItemSelected);
        recyclerView.setAdapter(adapter);

        toolbar.setNavigationOnClickListener(v -> onBackStep());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                refreshCurrentStep();
                return true;
            }
            return false;
        });

        btnAction.setOnClickListener(v -> onActionButton());

        layoutSearch.setEndIconOnClickListener(v -> {
            imageKindSearchQuery = etSearch.getText().toString().trim();
            imageKindCurrentPage = 1;
            applyImageKindFiltersAndPagination();
        });

        etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                imageKindSearchQuery = etSearch.getText().toString().trim();
                imageKindCurrentPage = 1;
                applyImageKindFiltersAndPagination();
                return true;
            }
            return false;
        });

        token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);

        renderCurrentStep();
    }

    private boolean readIntentExtras() {
        Intent intent = getIntent();
        String mode = intent.getStringExtra(EXTRA_MODE);
        if (MODE_REINSTALL.equals(mode)) {
            wizardMode = WizardMode.REINSTALL;
        } else if (MODE_CHANGE_CONFIG.equals(mode)) {
            wizardMode = WizardMode.CHANGE_CONFIG;
        } else {
            return true;
        }

        serverId = intent.getIntExtra(EXTRA_SERVER_ID, -1);
        currentGameName = emptyIfNull(intent.getStringExtra(EXTRA_CURRENT_GAME_NAME));
        currentKindName = emptyIfNull(intent.getStringExtra(EXTRA_CURRENT_KIND_NAME));
        currentImageName = emptyIfNull(intent.getStringExtra(EXTRA_CURRENT_IMAGE_NAME));
        currentVersionName = emptyIfNull(intent.getStringExtra(EXTRA_CURRENT_VERSION_NAME));
        currentGameId = intent.getIntExtra(EXTRA_CURRENT_GAME_ID, -1);
        currentKindId = intent.getIntExtra(EXTRA_CURRENT_KIND_ID, -1);
        int currentVersionId = intent.getIntExtra(EXTRA_CURRENT_VERSION_ID, -1);
        int currentCustomId = intent.getIntExtra(EXTRA_CURRENT_CUSTOM_ID, -1);
        hasCurrentCustom = intent.hasExtra(EXTRA_CURRENT_CUSTOM);
        currentCustom = intent.getBooleanExtra(EXTRA_CURRENT_CUSTOM, false);

        if (serverId <= 0) {
            Toast.makeText(this, "实例ID无效", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        if (isChangeConfigMode()) {
            imageKindId = currentCustom ? (currentCustomId > 0 ? currentCustomId : null) : (currentKindId > 0 ? currentKindId : null);
            versionId = currentVersionId > 0 ? currentVersionId : null;
            isCustom = currentCustom;
            selectedGameName = currentGameName;
            selectedImageKindName = firstNonEmpty(currentKindName, currentImageName);
            selectedVersionName = currentVersionName;
            currentStep = Step.SPEC;
        }
        return true;
    }

    private void onBackStep() {
        int currentIndex = getCurrentStepIndex();
        if (currentIndex <= 0) {
            finish();
            return;
        }
        currentStep = getActiveSteps()[currentIndex - 1];
        renderCurrentStep();
    }

    private void onActionButton() {
        if (currentStep == Step.CONFIRM) {
            if (isChangeConfigMode()) {
                changeInstance();
            } else {
                createInstance();
            }
        } else if (currentStep == Step.REINSTALL_OPTIONS) {
            showReinstallConfirmDialog();
        }
    }

    private void renderCurrentStep() {
        layoutSearch.setVisibility(View.GONE);
        hsPagination.setVisibility(View.GONE);
        layoutReinstallOptions.setVisibility(View.GONE);

        MenuItem refreshItem = toolbar.getMenu().findItem(R.id.action_refresh);
        if (refreshItem != null) refreshItem.setVisible(false);

        layoutGradeFilter.setVisibility(View.GONE);

        renderSteps(); // 更新步骤导航

        btnAction.setVisibility((currentStep == Step.CONFIRM || currentStep == Step.REINSTALL_OPTIONS) ? View.VISIBLE : View.GONE);
        if (currentStep == Step.REINSTALL_OPTIONS) {
            btnAction.setText(R.string.create_server_action_reinstall);
        } else if (isChangeConfigMode() && currentStep == Step.CONFIRM) {
            btnAction.setText(R.string.create_server_action_change_config);
        } else {
            btnAction.setText(R.string.create_server_action_create);
        }

        // Help FAB visibility and action
        if ((currentStep == Step.IMAGE_KIND && isCustom) || currentStep == Step.SPEC) {
            fabHelp.setVisibility(View.VISIBLE);
            fabHelp.setOnClickListener(v -> showHelpDialog());
        } else {
            fabHelp.setVisibility(View.GONE);
        }

        switch (currentStep) {
            case TYPE -> {
                collapsingToolbar.setTitle("选择镜像类型");
                replaceData(
                        ListItem.simple("基础镜像", "官方标准镜像"),
                        ListItem.simple("第三方镜像", "社区提供的镜像"));
            }
            case GAME -> {
                collapsingToolbar.setTitle(isReinstallMode() ? "选择实例类别" : "选择镜像类别");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadGameList();
            }
            case IMAGE_KIND -> {
                collapsingToolbar.setTitle(isReinstallMode() ? "选择实例服务端" : "选择镜像服务端");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadImageKindList();
            }
            case VERSION -> {
                collapsingToolbar.setTitle(isReinstallMode() ? "选择实例版本" : "选择镜像版本");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadVersionList();
            }
            case SPEC -> {
                collapsingToolbar.setTitle(isChangeConfigMode() ? "选择目标规格" : "选择实例规格");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadSpecList();
            }
            case CONFIRM -> {
                collapsingToolbar.setTitle(isChangeConfigMode() ? "确认变配" : "确认信息");
                loadConfirmation();
            }
            case REINSTALL_OPTIONS -> {
                collapsingToolbar.setTitle("确认重装");
                renderReinstallOptions();
            }
        }
    }

    private void showHelpDialog() {
        if (currentStep == Step.IMAGE_KIND && isCustom) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("没有您需要的整合包？")
                .setMessage("加入QQ群：1020961156，查看群公告，提交收录需求")
                .setPositiveButton("确定", null)
                .show();
        } else if (currentStep == Step.SPEC) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("实例命名与计费规则")
                .setMessage("""
                        实例命名方式为[CPU级别.CPU厂商.具体配置.操作系统]
                        CPU级别以字母[C,B,A,S]等分级，代表具体CPU性能，其中性能S>A>B>C，可参考具体CPU型号
                        CPU厂商以字母[A,I]分类，代表AMD,Intel
                        具体配置以字母[M,L,XL]等分类，代表各个配置套餐
                        操作系统以字母[L,W]分类，代表实例操作系统(Linux,Windows)
                        推荐在预算或内存足够的情况下，选择更高级别的CPU，以获得更流畅的体验，您也可以更换CPU厂商观察是否获得性能提升。

                        实例计费方式为按天付费，当日不开服不扣积分
                        当日开服指：服务器实例在当日24小时内启动过，无论是否是否进入服务器，服务器运行状态是否正常，只要启动即视为当日已开服
                        超套餐额磁盘将被计费1积分/G/天
                        若当日开服所需积分不足，则会关闭实例，若连续7天未启动实例，则会销毁实例，实例销毁前将会默认创建完整镜像，此镜像保留60天，可随时通过新建实例->备份->还原功能恢复实例文件""")
                .setPositiveButton("了解", null)
                .show();
        }
    }

    private void renderSteps() {
        layoutSteps.removeAllViews();
        Step[] steps = getActiveSteps();
        int currentIndex = getCurrentStepIndex();
        for (int i = 0; i <= currentIndex; i++) {
            Step s = steps[i];
            String name = getStepName(s);

            TextView tv = new TextView(this);
            tv.setText(name);
            tv.setTextSize(14);
            tv.setPadding(dp(8), dp(4), dp(8), dp(4));

            if (s == currentStep) {
                tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer));
                tv.setBackground(roundedBackground(this, com.google.android.material.R.attr.colorPrimaryContainer, 18));
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
                tv.setBackground(roundedBackground(this, com.google.android.material.R.attr.colorSurfaceContainer, 18));
                // 可点击返回
                tv.setOnClickListener(v -> {
                    currentStep = s;
                    renderCurrentStep();
                });
            }
            layoutSteps.addView(tv);

            // 添加分隔符
            if (i < currentIndex) {
                TextView divider = new TextView(this);
                divider.setText(">");
                divider.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOutline));
                layoutSteps.addView(divider);
            }
        }
        // 滚动到最右侧
        hsSteps.post(() -> hsSteps.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    private boolean isReinstallMode() {
        return wizardMode == WizardMode.REINSTALL;
    }

    private boolean isChangeConfigMode() {
        return wizardMode == WizardMode.CHANGE_CONFIG;
    }

    private Step[] getActiveSteps() {
        return switch (wizardMode) {
            case CREATE -> CREATE_STEPS;
            case REINSTALL -> REINSTALL_STEPS;
            case CHANGE_CONFIG -> CHANGE_CONFIG_STEPS;
        };
    }

    private int getCurrentStepIndex() {
        Step[] steps = getActiveSteps();
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == currentStep) return i;
        }
        return 0;
    }

    private String getStepName(Step s) {
        return switch (s) {
            case TYPE -> "类型";
            case GAME -> "类别";
            case IMAGE_KIND -> "服务端";
            case VERSION -> "版本";
            case SPEC -> "规格";
            case CONFIRM -> "确认";
            case REINSTALL_OPTIONS -> "重装确认";
        };
    }

    private void onItemSelected(ListItem item) {
        if (currentStep == Step.TYPE) {
            isCustom = item.title.contains("第三方");
            gameId = null;
            imageKindId = null;
            versionId = null;
            specId = null;
            selectedGameName = "";
            selectedImageKindName = "";
            selectedVersionName = "";
//            if (isCustom) {
//                Toast.makeText(this, "第三方镜像为社区提供，请注意安全与可信度", Toast.LENGTH_LONG).show();
//            }
            currentStep = Step.GAME;
            renderCurrentStep();
            return;
        }
        if (!item.selectable) {
            Feedback.info(this, "该项不可选择");
            return;
        }
        switch (currentStep) {
            case GAME -> {
                gameId = item.id;
                selectedGameName = item.title;
                imageKindId = null;
                versionId = null;
                specId = null;
                selectedImageKindName = "";
                selectedVersionName = "";
                currentStep = Step.IMAGE_KIND;
            }
            case IMAGE_KIND -> {
                imageKindId = item.id;
                selectedImageKindName = item.title;
                versionId = null;
                specId = null;
                selectedVersionName = "";
                currentStep = Step.VERSION;
            }
            case VERSION -> {
                versionId = item.id;
                selectedVersionName = item.title;
                specId = null;
                currentStep = isReinstallMode() ? Step.REINSTALL_OPTIONS : Step.SPEC;
            }
            case SPEC -> {
                specId = item.id;
                currentStep = Step.CONFIRM;
            }
        }
        renderCurrentStep();
    }

    private void loadGameList() {
        executeCall(CServerApi.getGameList(isCustom, token), json -> {
            List<ListItem> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                items.add(ListItem.image(o.optInt("id"), o.optString("name"), null, o.optString("pic_path")));
            }
            replaceData(items);
        });
    }

    private void loadImageKindList() {
        if (gameId == null) return;
        executeCall(CServerApi.getImageKindList(isCustom, gameId, token), json -> {
            List<ListItem> items = new ArrayList<>();
            fullImageKindList.clear();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (isCustom) {
                    fullImageKindList.add(ListItem.simpleWithIdMd(o.optInt("id"), o.optString("name"), o.optString("description")));
                } else {
                    String desc = o.optString("description");
                    String link = o.optString("link");
                    if (!TextUtils.isEmpty(link)) {
                        if (!TextUtils.isEmpty(desc)) desc = desc + "\n";
                        desc = desc + link;
                    }
                    items.add(ListItem.image(o.optInt("id"), o.optString("name"), desc, o.optString("pic_path")));
                }
            }
            if (isCustom) {
                layoutSearch.setVisibility(!fullImageKindList.isEmpty() ? View.VISIBLE : View.GONE);
                imageKindCurrentPage = 1;
                applyImageKindFiltersAndPagination();
            } else {
                replaceData(items);
            }
        });
    }

    private void loadVersionList() {
        if (imageKindId == null) return;
        executeCall(CServerApi.getVersionList(isCustom, imageKindId, token), json -> {
            List<ListItem> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (isCustom) {
                    // 展示更多信息：描述 + 推荐配置 + 大小
                    String desc = o.optString("description");
                    String rec = o.optString("recommend_setting");
                    String sizeFmt = formatSize(o.optString("size")); // 转换大小
                    StringBuilder sb = new StringBuilder();
                    appendVersionInfoPart(sb, desc);
                    appendVersionInfoPart(sb, !TextUtils.isEmpty(rec) ? getString(R.string.create_server_recommended_prefix, rec) : "");
                    appendVersionInfoPart(sb, !TextUtils.isEmpty(sizeFmt) ? getString(R.string.create_server_size_prefix, sizeFmt) : "");
                    items.add(ListItem.simpleWithIdMd(o.optInt("id"), o.optString("name"), sb.toString()));
                } else {
                    items.add(ListItem.simpleWithIdMd(o.optInt("id"), o.optString("name"), o.optString("description")));
                }
            }
            replaceData(items);
        });
    }

    private void loadSpecList() {
        if (isChangeConfigMode()) {
            loadChangeConfigSpecList();
            return;
        }
        if (versionId == null || imageKindId == null) return;
        executeCall(CServerApi.getSpecList(isCustom, versionId, imageKindId, token), this::handleSpecList);
    }

    private void loadChangeConfigSpecList() {
        if (versionId != null) {
            executeCall(CServerApi.getChangeSpecList(versionId, serverId, token), this::handleSpecList);
            return;
        }
        showChangeConfigResolvingMessage();
        if (imageKindId != null && imageKindId > 0) {
            resolveChangeConfigVersion(isCustom);
            return;
        }
        resolveChangeConfigSpecContext(hasCurrentCustom && currentCustom);
    }

    private void showChangeConfigResolvingMessage() {
        replaceData(ListItem.info("提示", "正在根据当前镜像匹配可变配规格..."));
    }

    private void resolveChangeConfigSpecContext(boolean custom) {
        executeCall(CServerApi.getGameList(custom, token), json -> {
            JSONObject game = findByName(json.optJSONArray("list"), currentGameName, "name", "game_name");
            if (game == null) {
                handleChangeConfigGameResolveFailure(custom, "未找到当前镜像类别，请从创建页重新选择");
                return;
            }
            isCustom = custom;
            gameId = game.optInt("id");
            selectedGameName = game.optString("name", currentGameName);
            resolveChangeConfigKind(custom);
        }, message -> handleChangeConfigGameResolveFailure(custom, message));
    }

    private void handleChangeConfigGameResolveFailure(boolean custom, String message) {
        if (!hasCurrentCustom && !custom) {
            resolveChangeConfigSpecContext(true);
        } else {
            showChangeConfigSpecResolveError(message);
        }
    }

    private void resolveChangeConfigKind(boolean custom) {
        if (gameId == null || gameId <= 0) {
            showChangeConfigSpecResolveError("实例镜像信息不完整，无法变配");
            return;
        }
        executeCall(CServerApi.getImageKindList(custom, gameId, token), json -> {
            String targetKindName = firstNonEmpty(currentKindName, currentImageName);
            JSONObject kind = findByName(json.optJSONArray("list"), targetKindName, "name", "kind_name", "image_name");
            if (kind == null) {
                showChangeConfigSpecResolveError("未找到当前镜像服务端，请从创建页重新选择");
                return;
            }
            imageKindId = kind.optInt("id");
            selectedImageKindName = kind.optString("name", targetKindName);
            resolveChangeConfigVersion(custom);
        }, this::showChangeConfigSpecResolveError);
    }

    private void resolveChangeConfigVersion(boolean custom) {
        if (imageKindId == null || imageKindId <= 0) {
            showChangeConfigSpecResolveError("实例镜像信息不完整，无法变配");
            return;
        }
        executeCall(CServerApi.getVersionList(custom, imageKindId, token), json -> {
            JSONObject version = findByName(json.optJSONArray("list"), currentVersionName, "name", "version_name");
            if (version == null) {
                showChangeConfigSpecResolveError("未找到当前镜像版本，请从创建页重新选择");
                return;
            }
            versionId = version.optInt("id");
            selectedVersionName = version.optString("name", currentVersionName);
            loadSpecList();
        }, this::showChangeConfigSpecResolveError);
    }

    private JSONObject findByName(JSONArray arr, String targetName, String... nameKeys) {
        if (arr == null || TextUtils.isEmpty(targetName)) return null;
        String target = normalizeName(targetName);
        JSONObject fallbackContains = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            for (String key : nameKeys) {
                String name = normalizeName(o.optString(key));
                if (TextUtils.isEmpty(name)) continue;
                if (target.equals(name)) return o;
                if (fallbackContains == null && (target.contains(name) || name.contains(target))) {
                    fallbackContains = o;
                }
            }
        }
        return fallbackContains;
    }

    private void showChangeConfigSpecResolveError(String message) {
        replaceData(ListItem.info("提示", message));
        Feedback.error(this, message);
    }

    private void handleSpecList(JSONObject json) {
        masterSpecList.clear();
        JSONArray arr = json.optJSONArray("list");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                masterSpecList.add(o);
            }
        }
        // Sort by point (price) ascending
        masterSpecList.sort(Comparator.comparingInt(o -> o.optInt("point")));

        setupGradeSpinner();
        selectedGrade = ""; // 默认全部
        updateSpecDisplay();
    }

    private void setupGradeSpinner() {
        if (spGrade == null) return;
        layoutGradeFilter.setVisibility(View.VISIBLE);

        // Dynamically collect all grades
        java.util.Set<String> gradesPresent = new java.util.HashSet<>();
        for (JSONObject o : masterSpecList) {
            String g = o.optString("area_grade");
            if (!TextUtils.isEmpty(g)) gradesPresent.add(g);
        }

        List<String> sortedGrades = new ArrayList<>(gradesPresent);
        // Sort grades: S > A > B > C, and modifiers ++ > + > (none) > -
        sortedGrades.sort(Comparator.comparingInt(this::getGradeScore).reversed());

        List<String> spinnerItems = new ArrayList<>();
        spinnerItems.add("全部");
        spinnerItems.addAll(sortedGrades);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerItems);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGrade.setAdapter(ad);
        spGrade.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String val = spinnerItems.get(position);
                selectedGrade = "全部".equals(val)?"":val;
                updateSpecDisplay();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private int getGradeScore(String grade) {
        if (grade == null) return 0;
        grade = grade.toUpperCase();
        int score = 0;
        // Base score
        if (grade.startsWith("S")) score = 400;
        else if (grade.startsWith("A")) score = 300;
        else if (grade.startsWith("B")) score = 200;
        else if (grade.startsWith("C")) score = 100;

        // Modifier score
        if (grade.contains("++")) score += 3;
        else if (grade.contains("+")) score += 2;
        else if (grade.contains("-")) score -= 1;

        return score;
    }

    private void updateSpecDisplay() {
        List<ListItem> items = new ArrayList<>();
        if (masterSpecList.isEmpty()) {
            items.add(ListItem.info("提示", "暂无可用规格"));
        } else {
            for (JSONObject o : masterSpecList) {
                String grade = o.optString("area_grade");
                if (!TextUtils.isEmpty(selectedGrade) && !selectedGrade.equalsIgnoreCase(grade)) continue;
                int id = o.optInt("id");
                boolean creatable = o.optBoolean("creatable", true);
                String specName = o.optString("area_vendor") + " . " + o.optString("spec") + " . " + (o.optBoolean("area_is_windows") ? "W" : "L");
                int cpu = o.optInt("cpu");
                int ram = o.optInt("ram");
                int disk = o.optInt("disk");
                int traffic = o.optInt("traffic");
                int point = o.optInt("point");

                items.add(ListItem.spec(id, grade, specName, cpu, ram, disk, traffic, point, creatable));
            }
        }
        replaceData(items);
    }

    private void loadConfirmation() {
        if (versionId == null || specId == null) return;
        // 进入确认页取消搜索与分页
        layoutSearch.setVisibility(View.GONE);
        hsPagination.setVisibility(View.GONE);
        executeCall(CServerApi.getConfirmation(isCustom, versionId, specId, token), json -> {
            List<ListItem> items = new ArrayList<>();
            JSONObject d = json.optJSONObject("data");
            if (d != null) {
                items.add(ListItem.info("游戏名称", d.optString("game_name")));
                // 基础镜像使用 kind_name，自定义(第三方)使用 image_name；若对应字段为空则回退另一个
                String displayImageName;
                if (!isCustom) {
                    String kindName = d.optString("kind_name");
                    if (TextUtils.isEmpty(kindName)) kindName = d.optString("image_name");
                    displayImageName = kindName;
                } else {
                    String imageName = d.optString("image_name");
                    if (TextUtils.isEmpty(imageName)) imageName = d.optString("kind_name");
                    displayImageName = imageName;
                }
                items.add(ListItem.info("镜像名称", displayImageName));
                items.add(ListItem.info("镜像版本", d.optString("version_name")));
                items.add(ListItem.info("配置", getString(R.string.create_server_spec_summary, d.optInt("cpu"), d.optInt("ram"), d.optInt("disk"))));
                items.add(ListItem.info("型号", d.optString("grade")));
                items.add(ListItem.info("可用流量", getString(R.string.create_server_storage_gb, d.optInt("traffic"))));
                items.add(ListItem.info("费用(积分)", String.valueOf(d.optInt("point"))));
            }
            replaceData(items);
        });
    }

    private void renderReinstallOptions() {
        replaceData(
                ListItem.info("当前镜像", buildCurrentImageText()),
                ListItem.info("目标镜像", buildTargetImageText()),
                ListItem.info("提示", "重装将替换实例镜像，请确认已备份重要文件"));

        layoutReinstallOptions.setVisibility(View.VISIBLE);
        cbSaveBeforeReinstall.setChecked(true);
        boolean showDiff = isSameImageKindForDiff();
        cbDiffUpdate.setVisibility(showDiff ? View.VISIBLE : View.GONE);
        cbDiffUpdate.setChecked(false);
    }

    private boolean isSameImageKindForDiff() {
        if (imageKindId == null) return false;
        if (hasCurrentCustom && currentCustom != isCustom) return false;

        if (hasCurrentCustom && currentKindId > 0 && currentKindId == imageKindId) {
            return currentGameId <= 0 || gameId == null || currentGameId == gameId;
        }

        String currentGame = normalizeName(currentGameName);
        String selectedGame = normalizeName(selectedGameName);
        if (TextUtils.isEmpty(currentGame) || TextUtils.isEmpty(selectedGame) || !currentGame.equals(selectedGame)) {
            return false;
        }

        String selectedKind = normalizeName(selectedImageKindName);
        if (TextUtils.isEmpty(selectedKind)) return false;
        String currentKind = normalizeName(currentKindName);
        String currentImage = normalizeName(currentImageName);
        return selectedKind.equals(currentKind) || selectedKind.equals(currentImage);
    }

    private void showReinstallConfirmDialog() {
        if (versionId == null) {
            Feedback.info(this, "请选择实例版本");
            return;
        }

        boolean save = cbSaveBeforeReinstall.isChecked();
        boolean diff = cbDiffUpdate.getVisibility() == View.VISIBLE && cbDiffUpdate.isChecked();
        StringBuilder message = new StringBuilder();
        message.append("目标镜像: ").append(buildTargetImageText()).append("\n");
        message.append("重装前备份: ").append(save ? "是" : "否").append("\n");
        if (save) {
            message.append("将扣费30积分用于备份\n");
        }
        if (cbDiffUpdate.getVisibility() == View.VISIBLE) {
            message.append("差异更新: ").append(diff ? "启用" : "不启用").append("\n");
        }
        message.append("\n重装可能覆盖当前实例文件，请确认继续。");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("确认重装实例")
                .setMessage(message.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("确认重装", (dialog, which) -> reinstallInstance(save, diff))
                .show();
    }

    private void reinstallInstance(boolean save, boolean diff) {
        if (isSubmitting) return;
        if (serverId <= 0 || versionId == null || versionId <= 0) {
            Feedback.error(this, "重装参数无效");
            return;
        }
        if (TextUtils.isEmpty(token)) {
            Feedback.info(this, "尚未登录");
            return;
        }

        submitInstanceAction(
                CServerApi.reinstallInstance(serverId, versionId, diff, save, isCustom, token),
                "重装失败",
                "重装请求已提交",
                json -> {
                    setResult(RESULT_OK);
                    finish();
                });
    }

    private void changeInstance() {
        if (isSubmitting) return;
        if (serverId <= 0 || specId == null || specId <= 0) {
            Feedback.error(this, "变配参数无效");
            return;
        }
        if (TextUtils.isEmpty(token)) {
            Feedback.error(this, "尚未登录");
            return;
        }

        submitInstanceAction(
                CServerApi.changeInstance(serverId, specId, token),
                "变配失败",
                "变配请求已提交",
                json -> {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_RESULT_CHANGE_CONFIG, true);
                    int newId = json.optInt("new_id", -1);
                    if (newId > 0) {
                        result.putExtra(EXTRA_RESULT_NEW_ID, newId);
                    }
                    setResult(RESULT_OK, result);
                    finish();
                });
    }

    private void submitInstanceAction(Call call, String defaultFailureMessage, String defaultSuccessMessage, JsonHandler onSuccess) {
        isSubmitting = true;
        btnAction.setEnabled(false);
        showLoading(true);
        call.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> {
                    if (isDead()) return;
                    resetSubmitState();
                    showLoading(false);
                    Feedback.error(CreateServer.this, defaultFailureMessage + ":" + e.getMessage());
                });
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    if (isDead()) return;
                    showLoading(false);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.optInt("code") == 200) {
                            Toast.makeText(CreateServer.this, json.optString("msg", defaultSuccessMessage), Toast.LENGTH_SHORT).show();
                            onSuccess.handle(json);
                        } else {
                            resetSubmitState();
                            Feedback.error(CreateServer.this, json.optString("msg", defaultFailureMessage));
                        }
                    } catch (Exception e) {
                        resetSubmitState();
                        Feedback.error(CreateServer.this, "解析失败");
                    }
                });
            }
        });
    }

    private void resetSubmitState() {
        isSubmitting = false;
        btnAction.setEnabled(true);
    }

    private String buildCurrentImageText() {
        return joinImageText(currentGameName, firstNonEmpty(currentKindName, currentImageName), currentVersionName);
    }

    private String buildTargetImageText() {
        return joinImageText(selectedGameName, selectedImageKindName, selectedVersionName);
    }

    private String joinImageText(String game, String kind, String version) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(game)) parts.add(game);
        if (!TextUtils.isEmpty(kind)) parts.add(kind);
        if (!TextUtils.isEmpty(version)) parts.add(version);
        return parts.isEmpty() ? "-" : String.join(" - ", parts);
    }

    private void appendVersionInfoPart(StringBuilder sb, String part) {
        if (TextUtils.isEmpty(part)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (!sb.isEmpty()) sb.append(" | ");
        }
        sb.append(part);
    }

    private String firstNonEmpty(String first, String second) {
        return !TextUtils.isEmpty(first) ? first : second;
    }

    private String normalizeName(String value) {
        return emptyIfNull(value).trim().toLowerCase(Locale.ROOT);
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private void createInstance() {
        if (versionId == null || specId == null) return;
        showLoading(true);
        CServerApi.createInstance(isCustom, versionId, specId, token).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> {
                    if (isDead()) return;
                    showLoading(false);
                    Feedback.error(findViewById(android.R.id.content), "创建失败:" + e.getMessage(),
                            "重试", CreateServer.this::createInstance);
                });
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    if (isDead()) return;
                    showLoading(false);
                    try {
                        JSONObject o = new JSONObject(body);
                        if (o.optInt("code") == 200) {
                            Toast.makeText(CreateServer.this, "创建成功", Toast.LENGTH_SHORT).show();
                            Intent result = new Intent();
                            result.putExtra(EXTRA_RESULT_CREATED, true);
                            setResult(RESULT_OK, result);
                            finish();
                        } else {
                            Feedback.error(CreateServer.this, o.optString("msg", "创建失败"));
                        }
                    } catch (Exception e) {
                        Feedback.error(CreateServer.this, "解析失败");
                    }
                });
            }
        });
    }

    private interface JsonHandler { void handle(JSONObject json); }
    private interface ErrorHandler { void handle(String message); }

    private void executeCall(Call call, JsonHandler handler) {
        executeCall(call, handler, message -> Feedback.error(findViewById(android.R.id.content), message,
                "重试", this::refreshCurrentStep));
    }

    private void executeCall(Call call, JsonHandler handler, ErrorHandler errorHandler) {
        showLoading(true);
        call.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> {
                    if (isDead()) return;
                    showLoading(false);
                    errorHandler.handle("网络失败:" + e.getMessage());
                });
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    if (isDead()) return;
                    showLoading(false);
                    try {
                        JSONObject obj = new JSONObject(body);
                        if (obj.optInt("code") == 200) {
                            handler.handle(obj);
                        } else {
                            errorHandler.handle(obj.optString("msg","请求失败"));
                        }
                    } catch (Exception e){
                        errorHandler.handle(buildParseErrorMessage(body));
                    }
                });
            }
        });
    }

    private boolean isDead() {
        return isFinishing() || isDestroyed();
    }

    private String buildParseErrorMessage(String body) {
        if (body.length() > 500) {
            return "数据解析错误, 响应预览:" + body.substring(0, 500) + "...(" + body.length() + ")";
        }
        return "数据解析错误, 响应:" + body;
    }

    private void showLoading(boolean show) { progressBar.setVisibility(show?View.VISIBLE:View.GONE); }

    private void replaceData(ListItem... items) {
        replaceData(Arrays.asList(items));
    }

    private void replaceData(List<ListItem> items) {
        int oldSize = data.size();
        int newSize = items.size();
        data.clear();
        data.addAll(items);
        int changed = Math.min(oldSize, newSize);
        if (oldSize > newSize) {
            adapter.notifyItemRangeRemoved(newSize, oldSize - newSize);
        } else if (newSize > oldSize) {
            adapter.notifyItemRangeInserted(oldSize, newSize - oldSize);
        }
        if (changed > 0) {
            adapter.notifyItemRangeChanged(0, changed);
        }
    }

    // --- 数据与适配器 ---
    private static class ListItem {
        int id; String title; String subtitle; String imageUrl; boolean showImage; boolean selectable = true; boolean isGroup = false; int point = -1; boolean full = false; // full: 标记已满状态
        boolean markdown;
        // Spec specific fields
        String grade; String specName; int cpu; int ram; int disk; int traffic;

        static ListItem simple(String t, String sub){ ListItem li = new ListItem(); li.id = -1; li.title=t; li.subtitle=sub; return li; }
        static ListItem simpleWithId(int id, String t, String sub){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; return li; }
        static ListItem simpleWithIdMd(int id, String t, String sub){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; li.markdown=true; return li; }
        static ListItem image(int id, String t, String sub, String url){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; li.imageUrl=url; li.showImage=true; li.markdown=true; return li; }
        static ListItem info(String t, String v){ ListItem li = new ListItem(); li.id=-1; li.title=t; li.subtitle=v; li.selectable=false; li.full=false; return li; }
        static ListItem spec(int id, String grade, String specName, int cpu, int ram, int disk, int traffic, int point, boolean creatable) {
            ListItem li = new ListItem();
            li.id = id;
            li.grade = grade;
            li.specName = specName;
            li.cpu = cpu;
            li.ram = ram;
            li.disk = disk;
            li.traffic = traffic;
            li.point = point;
            li.selectable = creatable;
            li.full = !creatable;
            return li;
        }
    }

    private static class GenericAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface OnSelect { void onSelect(ListItem item); }
        private final List<ListItem> data; private final OnSelect onSelect;
        private static final int TYPE_GENERIC = 0;
        private static final int TYPE_SPEC = 1;

        GenericAdapter(List<ListItem> d, OnSelect os){ data=d; onSelect=os; }

        @Override
        public int getItemViewType(int position) {
            ListItem item = data.get(position);
            return item.grade != null ? TYPE_SPEC : TYPE_GENERIC;
        }

        @Override public @NotNull RecyclerView.ViewHolder onCreateViewHolder(android.view.@NonNull ViewGroup parent, int viewType){
            if (viewType == TYPE_SPEC) {
                View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_spec, parent, false);
                return new SpecViewHolder(v);
            }
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_generic, parent,false);
            return new GenericViewHolder(v);
        }

        @Override public void onBindViewHolder(RecyclerView.@NonNull ViewHolder h, int p){
            ListItem it = data.get(p);
            if (h instanceof SpecViewHolder) {
                ((SpecViewHolder) h).bind(it, onSelect);
            } else if (h instanceof GenericViewHolder) {
                ((GenericViewHolder) h).bind(it, onSelect);
            }
        }
        @Override public int getItemCount(){ return data.size(); }
    }

    private static class SpecViewHolder extends RecyclerView.ViewHolder {
        private final TextView grade, specName, cpu, ram, disk, traffic, point, status;
        private final View container, gradeStrip;

        SpecViewHolder(View itemView) {
            super(itemView);
            container = itemView;
            grade = itemView.findViewById(R.id.item_grade);
            gradeStrip = itemView.findViewById(R.id.item_grade_strip);
            specName = itemView.findViewById(R.id.item_spec_name);
            cpu = itemView.findViewById(R.id.item_cpu);
            ram = itemView.findViewById(R.id.item_ram);
            disk = itemView.findViewById(R.id.item_disk);
            traffic = itemView.findViewById(R.id.item_traffic);
            point = itemView.findViewById(R.id.item_point);
            status = itemView.findViewById(R.id.item_status);
            if (container instanceof MaterialCardView card) {
                card.setCardBackgroundColor(getThemeColor(container.getContext(), com.google.android.material.R.attr.colorSurfaceContainerLow));
            }
            status.setText("已满");
            status.setTextColor(getThemeColor(status.getContext(), com.google.android.material.R.attr.colorOnErrorContainer));
            status.setBackground(roundedBackground(status.getContext(), com.google.android.material.R.attr.colorErrorContainer, 8));
        }

        void bind(ListItem item, GenericAdapter.OnSelect cb) {
            grade.setText(item.grade);

            // Set color based on grade
            int colorAttr;
            if (item.grade != null) {
                if (item.grade.startsWith("S")) colorAttr = androidx.appcompat.R.attr.colorError;
                else if (item.grade.startsWith("A")) colorAttr = androidx.appcompat.R.attr.colorPrimary;
                else if (item.grade.startsWith("B")) colorAttr = com.google.android.material.R.attr.colorTertiary;
                else colorAttr = com.google.android.material.R.attr.colorSecondary;
            } else {
                colorAttr = com.google.android.material.R.attr.colorSecondary;
            }
            int color = getThemeColor(grade.getContext(), colorAttr);

            grade.setTextColor(color);
            gradeStrip.setBackgroundColor(color);

            specName.setText(item.specName);
            cpu.setText(item.cpu + "核");
            ram.setText(item.ram + "G");
            disk.setText(item.disk + "G");
            traffic.setText(item.traffic + "G");
            point.setText(String.valueOf(item.point)); // Just the number

            if (item.full) {
                status.setVisibility(View.VISIBLE);
                container.setAlpha(0.6f);
            } else {
                status.setVisibility(View.GONE);
                container.setAlpha(1f);
            }

            container.setOnClickListener(v -> {
                if (item.selectable) cb.onSelect(item);
            });
        }
    }

    private static class GenericViewHolder extends RecyclerView.ViewHolder {
        private final TextView title; private final TextView subtitle; private final View img; private final View container;
        private final TextView flagFull; private final TextView flagPoint; // 新增标签
        GenericViewHolder(View itemView){
            super(itemView);
            title=itemView.findViewById(R.id.item_title);
            subtitle=itemView.findViewById(R.id.item_subtitle);
            img=itemView.findViewById(R.id.item_image);
            container=itemView;
            flagFull=itemView.findViewById(R.id.item_flag_full);
            flagPoint=itemView.findViewById(R.id.item_flag_point);
            if (container instanceof MaterialCardView card) {
                card.setCardBackgroundColor(getThemeColor(container.getContext(), com.google.android.material.R.attr.colorSurfaceContainerLow));
                card.setStrokeColor(getThemeColor(container.getContext(), com.google.android.material.R.attr.colorOutlineVariant));
            }
            if (flagFull != null) {
                flagFull.setTextColor(getThemeColor(flagFull.getContext(), com.google.android.material.R.attr.colorOnErrorContainer));
                flagFull.setBackground(roundedBackground(flagFull.getContext(), com.google.android.material.R.attr.colorErrorContainer, 6));
            }
            if (flagPoint != null) {
                flagPoint.setTextColor(getThemeColor(flagPoint.getContext(), com.google.android.material.R.attr.colorOnPrimaryContainer));
                flagPoint.setBackground(roundedBackground(flagPoint.getContext(), com.google.android.material.R.attr.colorPrimaryContainer, 6));
            }
        }
        void bind(ListItem item, GenericAdapter.OnSelect cb){
            title.setText(item.title);
            if (item.subtitle==null||item.subtitle.isEmpty()){
                subtitle.setVisibility(View.GONE);
                MarkdownRenderer.clear(subtitle);
            } else {
                subtitle.setVisibility(View.VISIBLE);
                if (item.markdown) {
                    android.util.Log.d("CreateServerBind", "markdown render: title=" + item.title + " sub=" + (item.subtitle == null ? "null" : item.subtitle.substring(0, Math.min(30, item.subtitle.length()))));
                    MarkdownRenderer.renderAsync(subtitle, item.subtitle);
                } else {
                    android.util.Log.d("CreateServerBind", "plain text: title=" + item.title);
                    MarkdownRenderer.clear(subtitle);
                    subtitle.setText(item.subtitle);
                }
            }
            if (item.showImage && img instanceof android.widget.ImageView){ img.setVisibility(View.VISIBLE); Glide.with(img.getContext()).load(item.imageUrl).into((android.widget.ImageView) img);} else { img.setVisibility(item.showImage?View.VISIBLE:View.GONE);}
            if (item.isGroup) {
                if (flagFull!=null) flagFull.setVisibility(View.GONE);
                if (flagPoint!=null) flagPoint.setVisibility(View.GONE);
                container.setAlpha(1f);
                container.setOnClickListener(null);
            } else {
                if (flagFull != null) flagFull.setVisibility(item.full?View.VISIBLE:View.GONE); // 仅 full=true 显示已满
                if (flagPoint != null) {
                    if (item.point >= 0 && !item.full && item.id != -1) { // 规格行且非已满显示积分
                        flagPoint.setVisibility(View.VISIBLE);
                        flagPoint.setText(item.point + "积分");
                    } else {
                        flagPoint.setVisibility(View.GONE);
                    }
                }
                container.setAlpha(item.selectable?1f:0.6f);
                container.setOnClickListener(v->{ if(item.selectable) cb.onSelect(item); });
            }
        }
    }

    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private int getThemeColor(int attr) {
        return getThemeColor(this, attr);
    }

    private static int getThemeColor(Context context, int attr) {
        return com.google.android.material.color.MaterialColors.getColor(context, attr, 0);
    }

    private static GradientDrawable roundedBackground(Context context, int colorAttr, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(getThemeColor(context, colorAttr));
        drawable.setCornerRadius(radiusDp * context.getResources().getDisplayMetrics().density);
        return drawable;
    }

    // 第三方镜像 服务端列表本地搜索+分页
    private void applyImageKindFiltersAndPagination() {
        if (!isCustom) return; // 仅第三方
        List<ListItem> filtered = new ArrayList<>();
        String q = imageKindSearchQuery == null ? "" : imageKindSearchQuery.toLowerCase();
        for (ListItem li : fullImageKindList) {
            if (TextUtils.isEmpty(q) || li.title.toLowerCase().contains(q) || (li.subtitle != null && li.subtitle.toLowerCase().contains(q))) {
                filtered.add(li);
            }
        }
        int total = filtered.size();
        List<ListItem> visibleItems = new ArrayList<>();
        if (total <= IMAGE_KIND_PAGE_SIZE) {
            hsPagination.setVisibility(View.GONE);
            visibleItems.addAll(filtered);
        } else {
            int pages = (int) Math.ceil(total * 1.0 / IMAGE_KIND_PAGE_SIZE);
            if (imageKindCurrentPage > pages) imageKindCurrentPage = pages;
            int start = (imageKindCurrentPage - 1) * IMAGE_KIND_PAGE_SIZE;
            int end = Math.min(start + IMAGE_KIND_PAGE_SIZE, total);
            visibleItems.addAll(filtered.subList(start, end));
            buildPaginationControls(pages);
        }
        replaceData(visibleItems);
    }

    private void buildPaginationControls(int pages) {
        paginationContainer.removeAllViews();
        hsPagination.setVisibility(pages > 1 ? View.VISIBLE : View.GONE);
        if (pages <= 1) return;

        for (int i = 1; i <= pages; i++) {
            final int p = i;
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(p));
            tv.setTextSize(16);
            tv.setPadding(dp(12), dp(8), dp(12), dp(8));
            tv.setGravity(android.view.Gravity.CENTER);

            if (p == imageKindCurrentPage) {
                tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer));
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setBackground(roundedBackground(this, com.google.android.material.R.attr.colorPrimaryContainer, 8));
            } else {
                tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
                tv.setBackground(roundedBackground(this, com.google.android.material.R.attr.colorSurfaceContainer, 8));
                tv.setOnClickListener(v -> {
                    imageKindCurrentPage = p;
                    applyImageKindFiltersAndPagination();
                });
            }
            paginationContainer.addView(tv);
        }
    }

    private void refreshCurrentStep() {
        switch (currentStep) {
            case GAME -> loadGameList();
            case IMAGE_KIND -> loadImageKindList();
            case VERSION -> loadVersionList();
            case SPEC -> loadSpecList();
            case CONFIRM -> loadConfirmation();
            default -> {}
        }
    }

    private String formatSize(String sizeRaw) {
        if (TextUtils.isEmpty(sizeRaw)) return "";
        try {
            long bytes = Long.parseLong(sizeRaw.trim());
            if (bytes <= 0) return "0KB";
            double kb = bytes / 1024.0;
            if (kb < 1024) {
                return ceil1(kb) + "KB"; // 小于1MB 显示KB
            }
            double mb = kb / 1024.0;
            if (mb < 1024) {
                double mbUp = Math.ceil(mb * 10.0) / 10.0;
                if (mbUp == (long) mbUp) return ((long) mbUp) + "MB";
                return mbUp + "MB";
            }
            double gb = mb / 1024.0;
            double gbUp = Math.ceil(gb * 10.0) / 10.0;
            if (gbUp == (long) gbUp) return ((long) gbUp) + "GB";
            return gbUp + "GB";
        } catch (NumberFormatException e) {
            return sizeRaw;
        }
    }

    private String ceil1(double v) {
        double up = Math.ceil(v * 10.0) / 10.0;
        if (up == (long) up) return String.valueOf((long) up);
        return String.valueOf(up);
    }
}
