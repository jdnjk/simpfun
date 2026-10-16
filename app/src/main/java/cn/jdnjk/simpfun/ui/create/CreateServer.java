package cn.jdnjk.simpfun.ui.create;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.animation.OvershootInterpolator;
import android.view.ViewGroup;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.CServerApi;
import cn.jdnjk.simpfun.utils.AnnouncementHelper;
import cn.jdnjk.simpfun.utils.Feedback;
import cn.jdnjk.simpfun.utils.MarkdownRenderer;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import okhttp3.Call;
import okhttp3.Response;

/**
 * 创建服务器向导（Material 3 Expressive）
 * 步骤：镜像类型 → 类别 → 服务端 → 版本 → 实例规格 → 确认。
 * 复用创建 / 重装 / 变配三种模式，API 语义不变。
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
    private boolean firstRender = true;

    // --- 视图 ---
    private MaterialToolbar toolbar;
    private LinearProgressIndicator wavyProgress;
    private HorizontalScrollView hsBreadcrumb;
    private LinearLayout layoutBreadcrumb;
    private TextView tvStepTitle;
    private TextView tvStepLabel;
    private View contentHost;
    private androidx.core.widget.NestedScrollView scrollContent;
    private RecyclerView recyclerView;
    private View layoutLoading;
    private TextInputLayout layoutSearch;
    private HorizontalScrollView hsPagination;
    private LinearLayout paginationContainer;
    private LinearLayout layoutGradeFilter;
    private MaterialAutoCompleteTextView actGrade;
    private TextView tvCpuModelLink;
    private LinearLayout layoutReinstallOptions;
    private MaterialCheckBox cbSaveBeforeReinstall;
    private MaterialCheckBox cbDiffUpdate;
    private LinearLayout layoutConfirm;
    private TextView tvConfirmImageTitle;
    private TextView tvConfirmVersion;
    private TextView tvConfirmSpecBody;
    private MaterialButton btnAction;

    // 分页/搜索相关（仅第三方镜像 服务端 选择步骤使用）
    private final List<ListItem> fullImageKindList = new ArrayList<>();
    private String imageKindSearchQuery = "";
    private int imageKindCurrentPage = 1;
    private static final int IMAGE_KIND_PAGE_SIZE = 10;

    // 第三方镜像简介限制：超过部分折叠（默认仅显示前200字/前15行）
    private static final int DESC_MAX_CHARS = 200;
    private static final int DESC_MAX_LINES = 15;

    private Integer gameId;
    private Integer imageKindId;
    private Integer versionId;
    private Integer specId;

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
    private final List<String> gradeOptions = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_create_server);
        bindViews();

        if (!readIntentExtras()) {
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        adapter = new GenericAdapter(data, this::onItemSelected);
        recyclerView.setAdapter(adapter);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                refreshCurrentStep();
                return true;
            }
            return false;
        });

        btnAction.setOnClickListener(v -> onActionButton());

        setupSearch();

        tvCpuModelLink.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://yuque.com/simpfun/sfe/areas"))));

        // 系统返回手势/返回键与顶部返回按钮行为一致（反向过渡）
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackStep();
            }
        });

        token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);

        renderCurrentStep(false);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        wavyProgress = findViewById(R.id.progress_wavy);
        hsBreadcrumb = findViewById(R.id.hs_breadcrumb);
        layoutBreadcrumb = findViewById(R.id.layout_breadcrumb);
        tvStepTitle = findViewById(R.id.tv_step_title);
        tvStepLabel = findViewById(R.id.tv_step_label);
        contentHost = findViewById(R.id.content_host);
        scrollContent = findViewById(R.id.scroll_content);
        recyclerView = findViewById(R.id.recycler_view);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutSearch = findViewById(R.id.layout_search);
        hsPagination = findViewById(R.id.hs_pagination);
        paginationContainer = findViewById(R.id.pagination_container);
        layoutGradeFilter = findViewById(R.id.layout_grade_filter);
        actGrade = findViewById(R.id.act_grade);
        tvCpuModelLink = findViewById(R.id.tv_cpu_model_link);
        layoutReinstallOptions = findViewById(R.id.layout_reinstall_options);
        cbSaveBeforeReinstall = findViewById(R.id.cb_save_before_reinstall);
        cbDiffUpdate = findViewById(R.id.cb_diff_update);
        layoutConfirm = findViewById(R.id.layout_confirm);
        tvConfirmImageTitle = findViewById(R.id.tv_confirm_image_title);
        tvConfirmVersion = findViewById(R.id.tv_confirm_version);
        tvConfirmSpecBody = findViewById(R.id.tv_confirm_spec_body);
        btnAction = findViewById(R.id.btn_action);
    }

    private void setupSearch() {
        android.widget.EditText etSearch = findViewById(R.id.et_search);
        // 输入即搜索：文本有变动直接触发过滤（endIcon 为 clear_text，清空时同样自动触发）
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                imageKindSearchQuery = s.toString().trim();
                imageKindCurrentPage = 1;
                applyImageKindFiltersAndPagination();
            }
        });
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

    private void onBackStep() {
        int currentIndex = getCurrentStepIndex();
        if (currentIndex <= 0) {
            finish();
            return;
        }
        currentStep = getActiveSteps()[currentIndex - 1];
        renderCurrentStep(false);
        animateStep(false); // 返回：从右侧滑入（反向播放进入动画）
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

    // --- 步骤外观（工具栏 / 进度 / 大标题 / 步骤说明） ---

    private void renderCurrentStep() {
        renderCurrentStep(true);
    }

    private void renderCurrentStep(boolean animateForward) {
        setPaginationVisible(View.GONE);
        layoutReinstallOptions.setVisibility(View.GONE);
        layoutConfirm.setVisibility(View.GONE);
        layoutGradeFilter.setVisibility(View.GONE);
        layoutSearch.setVisibility(View.GONE);

        setRefreshVisible(false);

        Step[] steps = getActiveSteps();
        int index = getCurrentStepIndex();
        int total = steps.length;
        int percent = Math.round((index + 1) * 100f / total);
        wavyProgress.setProgressCompat(percent, true);

        btnAction.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        String toolbarTitle = "";
        String stepSub = "";
        switch (currentStep) {
            case TYPE -> {
                toolbarTitle = isReinstallMode() ? "重装实例" : getString(R.string.cs_toolbar_create);
                stepSub = getString(R.string.cs_sub_type);
                showHeader(isReinstallMode() ? "选择新镜像" : getString(R.string.cs_header_start));
                replaceData(
                        ListItem.simple(0, "基础镜像", "官方标准镜像", R.drawable.ic_verified),
                        ListItem.simple(0, "第三方镜像", "社区提供，请注意来源可信度", R.drawable.ic_group));
            }
            case GAME -> {
                toolbarTitle = isReinstallMode() ? getString(R.string.cs_toolbar_reinstall_game) : getString(R.string.cs_toolbar_game);
                stepSub = getString(R.string.cs_sub_game);
                showHeader(getString(R.string.cs_header_game));
                setRefreshVisible(true);
                loadGameList();
            }
            case IMAGE_KIND -> {
                toolbarTitle = isReinstallMode() ? getString(R.string.cs_toolbar_reinstall_kind) : getString(R.string.cs_toolbar_kind);
                stepSub = selectedGameName;
                showHeader(null);
                setRefreshVisible(true);
                loadImageKindList();
            }
            case VERSION -> {
                toolbarTitle = isReinstallMode() ? getString(R.string.cs_toolbar_reinstall_version) : getString(R.string.cs_toolbar_version);
                stepSub = selectedImageKindName;
                showHeader(null);
                setRefreshVisible(true);
                loadVersionList();
            }
            case SPEC -> {
                toolbarTitle = isChangeConfigMode() ? "选择目标规格" : getString(R.string.cs_toolbar_spec);
                stepSub = isChangeConfigMode() ? getString(R.string.cs_sub_spec_change) : getString(R.string.cs_sub_spec_sort);
                showHeader(null);
                setRefreshVisible(true);
                loadSpecList();
            }
            case CONFIRM -> {
                toolbarTitle = isChangeConfigMode() ? getString(R.string.cs_toolbar_confirm_change) : getString(R.string.cs_toolbar_confirm);
                stepSub = getString(R.string.cs_sub_confirm);
                showHeader(getString(R.string.cs_header_confirm));
                recyclerView.setVisibility(View.GONE);
                layoutConfirm.setVisibility(View.VISIBLE);
                loadConfirmation();
            }
            case REINSTALL_OPTIONS -> {
                toolbarTitle = getString(R.string.cs_toolbar_reinstall);
                stepSub = getString(R.string.cs_sub_reinstall);
                showHeader(getString(R.string.cs_header_reinstall));
                renderReinstallOptions();
            }
        }
        toolbar.setTitle(toolbarTitle);
        toolbar.setNavigationIcon(currentStep == Step.TYPE
                ? R.drawable.ic_close : R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> onBackStep());

        tvStepLabel.setText(getString(R.string.cs_step_format, index + 1, total, stepSub));
        renderBreadcrumb();

        if (currentStep == Step.CONFIRM) {
            btnAction.setText(isChangeConfigMode()
                    ? R.string.create_server_action_change_config : R.string.create_server_action_create);
            btnAction.setIconResource(R.drawable.ic_check);
        } else if (currentStep == Step.REINSTALL_OPTIONS) {
            btnAction.setText(R.string.create_server_action_reinstall);
            btnAction.setIcon(null);
        }

        scrollContent.scrollTo(0, 0);
        if (animateForward && !firstRender) {
            animateStep(true); // 前进：新内容从左侧滑入
        }
        firstRender = false;
    }

    /** 面包屑：显示当前步骤之前的所有步骤，点击可跳回。 */
    private void renderBreadcrumb() {
        layoutBreadcrumb.removeAllViews();
        Step[] steps = getActiveSteps();
        int index = getCurrentStepIndex();
        hsBreadcrumb.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        if (index <= 0) return;

        for (int i = 0; i < index; i++) {
            final Step target = steps[i];
            TextView tv = new TextView(this);
            tv.setText(stepDisplayName(target));
            tv.setTextSize(13);
            tv.setPadding(dp(12), dp(5), dp(12), dp(5));
            tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh));
            tv.setBackground(bg);

            tv.setOnClickListener(v -> {
                if (isSubmitting) return;
                currentStep = target;
                renderCurrentStep(false);
                animateStep(false); // 跳回：反向过渡
            });
            layoutBreadcrumb.addView(tv);

            if (i < index - 1) {
                TextView sep = new TextView(this);
                sep.setText("›");
                sep.setTextSize(13);
                sep.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOutline));
                sep.setPadding(dp(3), dp(5), dp(3), dp(5));
                layoutBreadcrumb.addView(sep);
            }
        }
        hsBreadcrumb.post(() -> hsBreadcrumb.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    private String stepDisplayName(Step s) {
        return switch (s) {
            case TYPE -> "发布者";
            case GAME -> "类别";
            case IMAGE_KIND -> "应用";
            case VERSION -> "版本";
            case SPEC -> "配置";
            case CONFIRM -> "确认";
            case REINSTALL_OPTIONS -> "确认重装";
        };
    }

    private void showHeader(@Nullable String bigTitle) {
        if (bigTitle == null || bigTitle.isEmpty()) {
            tvStepTitle.setVisibility(View.GONE);
        } else {
            tvStepTitle.setText(bigTitle);
            tvStepTitle.setVisibility(View.VISIBLE);
        }
    }

    private void setRefreshVisible(boolean visible) {
        android.view.MenuItem item = toolbar.getMenu().findItem(R.id.action_refresh);
        if (item != null) item.setVisible(visible);
    }

    private void animateStep(boolean forward) {
        float offset = forward ? -dp(48) : dp(48);
        contentHost.setTranslationX(offset);
        contentHost.setAlpha(0.4f);
        contentHost.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(0.9f)) // 轻微回弹
                .start();
    }

    // --- 选择与跳转 ---

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

    // --- 数据加载 ---

    private void loadGameList() {
        executeCall(CServerApi.getGameList(isCustom, token), json -> {
            List<ListItem> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                items.add(ListItem.image(o.optInt("id"), o.optString("name"), null, o.optString("pic_path"), R.drawable.ic_sports_esports));
            }
            if (items.isEmpty()) items.add(ListItem.info("提示", "暂无可选类别"));
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
                    String desc = AnnouncementHelper.normalizeEscapedLineBreaks(o.optString("description"));
                    String truncated = truncateDescription(desc);
                    boolean collapsible = !truncated.equals(desc);
                    if (collapsible && !truncated.isEmpty()) truncated = truncated + "…";
                    ListItem li = ListItem.simpleWithIdMd(o.optInt("id"), o.optString("name"), truncated, 0);
                    li.noIcon = true;
                    li.fullSubtitle = desc;
                    li.collapsible = collapsible;
                    li.expanded = false;
                    fullImageKindList.add(li);
                } else {
                    String desc = AnnouncementHelper.normalizeEscapedLineBreaks(o.optString("description"));
                    String link = o.optString("link");
                    if (!TextUtils.isEmpty(link)) {
                        if (!TextUtils.isEmpty(desc)) desc = desc + "\n";
                        desc = desc + link;
                    }
                    ListItem li = ListItem.image(o.optInt("id"), o.optString("name"), desc, o.optString("pic_path"), 0);
                    li.noIcon = true;
                    items.add(li);
                }
            }
            if (isCustom) {
                layoutSearch.setVisibility(!fullImageKindList.isEmpty() ? View.VISIBLE : View.GONE);
                imageKindCurrentPage = 1;
                applyImageKindFiltersAndPagination();
            } else {
                if (items.isEmpty()) items.add(ListItem.info("提示", "暂无可选服务端"));
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
                    String desc = AnnouncementHelper.normalizeEscapedLineBreaks(o.optString("description"));
                    String rec = o.optString("recommend_setting");
                    String sizeFmt = formatSize(o.optString("size"));
                    StringBuilder sb = new StringBuilder();
                    appendVersionInfoPart(sb, desc);
                    appendVersionInfoPart(sb, !TextUtils.isEmpty(rec) ? getString(R.string.create_server_recommended_prefix, rec) : "");
                    appendVersionInfoPart(sb, !TextUtils.isEmpty(sizeFmt) ? getString(R.string.create_server_size_prefix, sizeFmt) : "");
                    ListItem li = ListItem.simpleWithIdMd(o.optInt("id"), o.optString("name"), sb.toString(), 0);
                    li.noIcon = true;
                    items.add(li);
                } else {
                    ListItem li = ListItem.simpleWithIdMd(o.optInt("id"), o.optString("name"),
                            AnnouncementHelper.normalizeEscapedLineBreaks(o.optString("description")), 0);
                    li.noIcon = true;
                    items.add(li);
                }
            }
            if (items.isEmpty()) items.add(ListItem.info("提示", "暂无可选版本"));
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
        // 默认按 point（积分）升序展示
        masterSpecList.sort(Comparator.comparingInt(o -> o.optInt("point")));

        setupGradeMenu();
        selectedGrade = ""; // 初次进入默认全部
        updateSpecDisplay();
    }

    /** 从 area_grade 动态生成等级筛选选项，按 S+、S、A、B++、B+、C+ 的规则排序。 */
    private void setupGradeMenu() {
        layoutGradeFilter.setVisibility(View.VISIBLE);

        java.util.Set<String> gradesPresent = new java.util.HashSet<>();
        for (JSONObject o : masterSpecList) {
            String g = o.optString("area_grade");
            if (!TextUtils.isEmpty(g)) gradesPresent.add(g);
        }

        List<String> sortedGrades = new ArrayList<>(gradesPresent);
        sortedGrades.sort(Comparator.comparingInt(this::getGradeScore).reversed());

        gradeOptions.clear();
        gradeOptions.add(getString(R.string.cs_grade_all));
        gradeOptions.addAll(sortedGrades);

        actGrade.setSimpleItems(gradeOptions.toArray(new String[0]));
        actGrade.setText(gradeOptions.get(0), false);
        actGrade.setOnItemClickListener((parent, view, position, id) -> {
            // 切换时仅过滤，不改变当前积分升序
            selectedGrade = position == 0 ? "" : gradeOptions.get(position);
            updateSpecDisplay();
        });
    }

    private int getGradeScore(String grade) {
        if (grade == null) return 0;
        grade = grade.toUpperCase();
        int score = 0;
        if (grade.startsWith("S")) score = 400;
        else if (grade.startsWith("A")) score = 300;
        else if (grade.startsWith("B")) score = 200;
        else if (grade.startsWith("C")) score = 100;

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
                // 保留接口原值 area_grade / area_vendor / spec，格式为 grade.area_vendor.spec.L（Windows 为 W）
                StringBuilder nameSb = new StringBuilder();
                appendDotPart(nameSb, grade);
                appendDotPart(nameSb, o.optString("area_vendor"));
                appendDotPart(nameSb, o.optString("spec"));
                appendDotPart(nameSb, o.optBoolean("area_is_windows") ? "W" : "L");
                int cpu = o.optInt("cpu");
                int ram = o.optInt("ram");
                int disk = o.optInt("disk");
                int traffic = o.optInt("traffic");
                int point = o.optInt("point");
                String os = o.optBoolean("area_is_windows") ? getString(R.string.cs_os_windows) : getString(R.string.cs_os_linux);
                String sub = getString(R.string.cs_spec_line, cpu, ram, disk, traffic, os);

                items.add(ListItem.spec(id, nameSb.toString(), sub, point, creatable));
            }
        }
        replaceData(items);
    }

    private void appendSpecPart(StringBuilder sb, String part) {
        if (TextUtils.isEmpty(part)) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(part);
    }

    private void appendDotPart(StringBuilder sb, String part) {
        if (TextUtils.isEmpty(part)) return;
        if (sb.length() > 0) sb.append(".");
        sb.append(part);
    }

    // --- 确认页 ---

    private void loadConfirmation() {
        if (versionId == null || specId == null) return;
        tvConfirmImageTitle.setText("-");
        tvConfirmVersion.setText("加载中…");
        tvConfirmSpecBody.setText("-");
        btnAction.setVisibility(View.GONE);
        executeCall(CServerApi.getConfirmation(isCustom, versionId, specId, token), json -> {
            JSONObject d = json.optJSONObject("data");
            if (d == null) {
                tvConfirmVersion.setText(getString(R.string.cs_sub_confirm));
            } else {
                String game = d.optString("game_name");
                // 基础镜像使用 kind_name，第三方使用 image_name；为空时回退另一个
                String kind;
                if (!isCustom) {
                    kind = d.optString("kind_name");
                    if (TextUtils.isEmpty(kind)) kind = d.optString("image_name");
                } else {
                    kind = d.optString("image_name");
                    if (TextUtils.isEmpty(kind)) kind = d.optString("kind_name");
                }
                StringBuilder titleSb = new StringBuilder();
                appendSpecPart(titleSb, game);
                appendSpecPart(titleSb, kind);
                tvConfirmImageTitle.setText(titleSb.length() > 0 ? titleSb.toString() : "-");
                tvConfirmVersion.setText(getString(R.string.cs_confirm_version, d.optString("version_name")));

                String specBody = getString(R.string.cs_confirm_spec_line,
                        d.optString("grade"),
                        d.optInt("cpu"),
                        d.optInt("ram"),
                        d.optInt("disk"),
                        d.optInt("traffic"),
                        d.optInt("point"));
                tvConfirmSpecBody.setText(specBody);
            }
            btnAction.setVisibility(View.VISIBLE);
        });
    }

    // --- 重装 ---

    private void renderReinstallOptions() {
        replaceData(
                ListItem.info("当前镜像", buildCurrentImageText()),
                ListItem.info("目标镜像", buildTargetImageText()),
                ListItem.info("提示", "重装将替换实例镜像，请确认已备份重要文件"));

        layoutReinstallOptions.setVisibility(View.VISIBLE);
        btnAction.setVisibility(View.VISIBLE);
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

        new MaterialAlertDialogBuilder(this)
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

    private void createInstance() {
        if (isSubmitting) return;
        if (versionId == null || specId == null) return;
        if (TextUtils.isEmpty(token)) {
            Feedback.info(this, "尚未登录");
            return;
        }
        isSubmitting = true;
        btnAction.setEnabled(false);
        showLoading(true);
        CServerApi.createInstance(isCustom, versionId, specId, token).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> {
                    if (isDead()) return;
                    isSubmitting = false;
                    btnAction.setEnabled(true);
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
                            isSubmitting = false;
                            btnAction.setEnabled(true);
                            Feedback.error(CreateServer.this, o.optString("msg", "创建失败"));
                        }
                    } catch (Exception e) {
                        isSubmitting = false;
                        btnAction.setEnabled(true);
                        Feedback.error(CreateServer.this, "解析失败");
                    }
                });
            }
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
        if (sb.length() > 0) sb.append(" | ");
        sb.append(part);
    }

    /**
     * 截断第三方镜像简介：最多保留 {@link #DESC_MAX_CHARS} 个字符（含换行符），
     * 且最多 {@link #DESC_MAX_LINES} 行（按 \n 分割）。未超限时原样返回。
     */
    private String truncateDescription(String desc) {
        if (TextUtils.isEmpty(desc)) return "";
        String[] lines = desc.replace("\r\n", "\n").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int chars = 0;
        int lineCount = 0;
        for (String line : lines) {
            if (lineCount >= DESC_MAX_LINES || chars >= DESC_MAX_CHARS) break;
            if (lineCount > 0) {
                sb.append('\n');
                chars++;
            }
            int remain = DESC_MAX_CHARS - chars;
            if (line.length() > remain) line = line.substring(0, remain);
            sb.append(line);
            chars += line.length();
            lineCount++;
        }
        String result = sb.toString();
        while (result.endsWith("\n")) result = result.substring(0, result.length() - 1);
        return result;
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

    // --- 通用请求 ---

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

    private void showLoading(boolean show) {
        layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

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

    // --- 第三方镜像 服务端列表本地搜索+分页 ---

    private void applyImageKindFiltersAndPagination() {
        if (!isCustom) return;
        List<ListItem> filtered = new ArrayList<>();
        String q = imageKindSearchQuery == null ? "" : imageKindSearchQuery.toLowerCase();
        for (ListItem li : fullImageKindList) {
            String desc = li.fullSubtitle != null ? li.fullSubtitle : li.subtitle;
            if (TextUtils.isEmpty(q) || li.title.toLowerCase().contains(q) || (desc != null && desc.toLowerCase().contains(q))) {
                filtered.add(li);
            }
        }
        int total = filtered.size();
        List<ListItem> visibleItems = new ArrayList<>();
        if (total <= IMAGE_KIND_PAGE_SIZE) {
            setPaginationVisible(View.GONE);
            visibleItems.addAll(filtered);
        } else {
            int pages = (int) Math.ceil(total * 1.0 / IMAGE_KIND_PAGE_SIZE);
            if (imageKindCurrentPage > pages) imageKindCurrentPage = pages;
            int start = (imageKindCurrentPage - 1) * IMAGE_KIND_PAGE_SIZE;
            int end = Math.min(start + IMAGE_KIND_PAGE_SIZE, total);
            visibleItems.addAll(filtered.subList(start, end));
            buildPaginationControls(pages);
        }
        if (visibleItems.isEmpty() && !filtered.isEmpty()) {
            visibleItems.add(ListItem.info("提示", "没有匹配的镜像"));
        }
        replaceData(visibleItems);
    }

    private void setPaginationVisible(int visibility) {
        hsPagination.setVisibility(visibility);
    }

    private void buildPaginationControls(int pages) {
        paginationContainer.removeAllViews();
        setPaginationVisible(View.VISIBLE);
        for (int i = 1; i <= pages; i++) {
            paginationContainer.addView(buildPageTextView(i));
        }
    }

    private TextView buildPageTextView(int p) {
        TextView tv = new TextView(this);
        tv.setText(String.valueOf(p));
        tv.setTextSize(14);
        tv.setPadding(dp(14), dp(8), dp(14), dp(8));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTypeface(null, p == imageKindCurrentPage ? Typeface.BOLD : Typeface.NORMAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (p == imageKindCurrentPage) {
            tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer));
            bg.setColor(getThemeColor(com.google.android.material.R.attr.colorPrimaryContainer));
        } else {
            tv.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            bg.setColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh));
            tv.setOnClickListener(v -> {
                imageKindCurrentPage = p;
                applyImageKindFiltersAndPagination();
            });
        }
        tv.setBackground(bg);
        return tv;
    }

    // --- 小工具 ---

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getThemeColor(int attr) {
        return com.google.android.material.color.MaterialColors.getColor(this, attr, 0);
    }

    private String formatSize(String sizeRaw) {
        if (TextUtils.isEmpty(sizeRaw)) return "";
        try {
            long bytes = Long.parseLong(sizeRaw.trim());
            if (bytes <= 0) return "0KB";
            double kb = bytes / 1024.0;
            if (kb < 1024) {
                return ceil1(kb) + "KB";
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

    // --- 数据与适配器 ---

    private static class ListItem {
        int id;
        String title;
        String subtitle;
        String imageUrl;
        boolean showImage;
        boolean selectable = true;
        int iconRes;
        boolean noIcon; // 应用(服务端)步骤不显示图标
        int point = -1;
        boolean full = false;
        boolean markdown;
        // 第三方镜像简介折叠
        String fullSubtitle;
        boolean collapsible;
        boolean expanded;
        // 规格字段
        String specName;

        static ListItem simple(int id, String t, String sub, int iconRes) {
            ListItem li = new ListItem();
            li.id = id;
            li.title = t;
            li.subtitle = sub;
            li.iconRes = iconRes;
            return li;
        }

        static ListItem simpleWithId(int id, String t, String sub, int iconRes) {
            return simple(id, t, sub, iconRes);
        }

        static ListItem simpleWithIdMd(int id, String t, String sub, int iconRes) {
            ListItem li = simple(id, t, sub, iconRes);
            li.markdown = true;
            return li;
        }

        static ListItem image(int id, String t, String sub, String url, int fallbackIcon) {
            ListItem li = simpleWithIdMd(id, t, sub, fallbackIcon);
            li.imageUrl = url;
            li.showImage = !TextUtils.isEmpty(url);
            return li;
        }

        static ListItem info(String t, String v) {
            ListItem li = new ListItem();
            li.id = -1;
            li.title = t;
            li.subtitle = v;
            li.selectable = false;
            li.full = false;
            return li;
        }

        static ListItem spec(int id, String specName, String sub, int point, boolean creatable) {
            ListItem li = new ListItem();
            li.id = id;
            li.specName = specName;
            li.subtitle = sub;
            li.point = point;
            li.selectable = creatable;
            li.full = !creatable;
            return li;
        }
    }

    private static class GenericAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface OnSelect { void onSelect(ListItem item); }
        private final List<ListItem> data;
        private final OnSelect onSelect;
        private static final int TYPE_GENERIC = 0;
        private static final int TYPE_SPEC = 1;

        GenericAdapter(List<ListItem> d, OnSelect os) { data = d; onSelect = os; }

        @Override
        public int getItemViewType(int position) {
            ListItem item = data.get(position);
            return item.specName != null ? TYPE_SPEC : TYPE_GENERIC;
        }

        @Override
        public @NotNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_SPEC) {
                View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_spec, parent, false);
                return new SpecViewHolder(v);
            }
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_generic, parent, false);
            return new GenericViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            ListItem it = data.get(p);
            if (h instanceof SpecViewHolder) {
                ((SpecViewHolder) h).bind(it, onSelect);
            } else if (h instanceof GenericViewHolder) {
                ((GenericViewHolder) h).bind(it, onSelect);
            }
        }

        @Override
        public int getItemCount() { return data.size(); }
    }

    /** 涟漪 + 轻微缩放反馈（不消费触摸事件，涟漪照常工作）。 */
    private static void applyPressScale(View v) {
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN ->
                        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).start();
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
    }

    private static class SpecViewHolder extends RecyclerView.ViewHolder {
        private final View container, iconBg;
        private final ImageView icon;
        private final TextView specName, point, flagFull, subtitle;

        SpecViewHolder(View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.item_root);
            iconBg = itemView.findViewById(R.id.item_icon_bg);
            icon = itemView.findViewById(R.id.item_icon);
            specName = itemView.findViewById(R.id.item_spec_name);
            point = itemView.findViewById(R.id.item_point);
            flagFull = itemView.findViewById(R.id.item_flag_full);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            applyPressScale(container);
        }

        void bind(ListItem item, GenericAdapter.OnSelect cb) {
            specName.setText(item.specName);
            subtitle.setText(item.subtitle);
            point.setText(item.full
                    ? getString(itemView.getContext(), R.string.cs_point_format, item.point) + " · " + itemView.getContext().getString(R.string.cs_full)
                    : getString(itemView.getContext(), R.string.cs_point_format, item.point));

            Context ctx = itemView.getContext();
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (item.full) {
                // 已满：降低视觉强调，标记已满，不可点击
                bg.setColor(colorOf(ctx, com.google.android.material.R.attr.colorErrorContainer));
                icon.setImageResource(R.drawable.ic_block);
                icon.setColorFilter(colorOf(ctx, com.google.android.material.R.attr.colorOnErrorContainer));
                flagFull.setVisibility(View.VISIBLE);
                flagFull.setTextColor(colorOf(ctx, com.google.android.material.R.attr.colorOnErrorContainer));
                flagFull.setBackground(roundedBg(ctx, com.google.android.material.R.attr.colorErrorContainer, 6));
                container.setAlpha(0.6f);
                container.setOnClickListener(null);
                container.setClickable(false);
            } else {
                bg.setColor(colorOf(ctx, com.google.android.material.R.attr.colorPrimaryContainer));
                icon.setImageResource(R.drawable.ic_dns);
                icon.setColorFilter(colorOf(ctx, com.google.android.material.R.attr.colorOnPrimaryContainer));
                flagFull.setVisibility(View.GONE);
                container.setAlpha(1f);
                container.setOnClickListener(v -> cb.onSelect(item));
            }
            iconBg.setBackground(bg);
        }

        private String getString(Context ctx, int res, int value) {
            return ctx.getString(res, value);
        }

        private int colorOf(Context ctx, int attr) {
            return com.google.android.material.color.MaterialColors.getColor(ctx, attr, 0);
        }

        private GradientDrawable roundedBg(Context ctx, int colorAttr, int radiusDp) {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(radiusDp * ctx.getResources().getDisplayMetrics().density);
            d.setColor(colorOf(ctx, colorAttr));
            return d;
        }
    }

    private static class GenericViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final View iconSlot;
        private final ImageView img;
        private final View iconBg;
        private final ImageView icon;
        private final View container;
        private final TextView flagFull;
        private final TextView expandToggle;

        GenericViewHolder(View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.item_root);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            iconSlot = itemView.findViewById(R.id.item_icon_slot);
            img = itemView.findViewById(R.id.item_image);
            iconBg = itemView.findViewById(R.id.item_icon_bg);
            icon = itemView.findViewById(R.id.item_icon);
            flagFull = itemView.findViewById(R.id.item_flag_full);
            expandToggle = itemView.findViewById(R.id.item_expand_toggle);
            applyPressScale(container);
            if (flagFull != null) {
                flagFull.setTextColor(com.google.android.material.color.MaterialColors.getColor(flagFull, com.google.android.material.R.attr.colorOnErrorContainer));
                GradientDrawable d = new GradientDrawable();
                d.setCornerRadius(6 * flagFull.getResources().getDisplayMetrics().density);
                d.setColor(com.google.android.material.color.MaterialColors.getColor(flagFull, com.google.android.material.R.attr.colorErrorContainer));
                flagFull.setBackground(d);
            }
            GradientDrawable iconBgDrawable = new GradientDrawable();
            iconBgDrawable.setShape(GradientDrawable.OVAL);
            iconBgDrawable.setColor(com.google.android.material.color.MaterialColors.getColor(iconBg, com.google.android.material.R.attr.colorPrimaryContainer));
            iconBg.setBackground(iconBgDrawable);
            icon.setColorFilter(com.google.android.material.color.MaterialColors.getColor(icon, com.google.android.material.R.attr.colorOnPrimaryContainer));
        }

        void bind(ListItem item, GenericAdapter.OnSelect cb) {
            title.setText(item.title);
            if (item.subtitle == null || item.subtitle.isEmpty()) {
                subtitle.setVisibility(View.GONE);
                MarkdownRenderer.clear(subtitle);
            } else {
                subtitle.setVisibility(View.VISIBLE);
                if (item.markdown) {
                    MarkdownRenderer.renderAsync(subtitle, item.subtitle);
                } else {
                    MarkdownRenderer.clear(subtitle);
                    subtitle.setMovementMethod(null);
                    subtitle.setText(item.subtitle);
                }
            }
            // 第三方镜像简介：超过限制时折叠，点击展开/收起
            if (expandToggle != null) {
                if (item.collapsible && item.fullSubtitle != null) {
                    expandToggle.setVisibility(View.VISIBLE);
                    expandToggle.setText(item.expanded ? "收起" : "展开");
                    expandToggle.setOnClickListener(v -> {
                        item.expanded = !item.expanded;
                        String show = item.expanded ? item.fullSubtitle : item.subtitle;
                        if (item.markdown) {
                            MarkdownRenderer.renderAsync(subtitle, show);
                        } else {
                            MarkdownRenderer.clear(subtitle);
                            subtitle.setText(show);
                        }
                        expandToggle.setText(item.expanded ? "收起" : "展开");
                    });
                } else {
                    expandToggle.setVisibility(View.GONE);
                    expandToggle.setOnClickListener(null);
                }
            }
            if (item.showImage && img != null) {
                iconSlot.setVisibility(View.VISIBLE);
                img.setVisibility(View.VISIBLE);
                iconBg.setVisibility(View.GONE);
                img.setClipToOutline(true);
                GradientDrawable outline = new GradientDrawable();
                outline.setCornerRadius(8 * img.getResources().getDisplayMetrics().density);
                img.setBackground(outline);
                com.bumptech.glide.Glide.with(img.getContext()).load(item.imageUrl).into(img);
            } else if (item.noIcon) {
                // 应用(服务端)步骤：不显示图标，文字直接靠左
                iconSlot.setVisibility(View.GONE);
            } else {
                iconSlot.setVisibility(View.VISIBLE);
                img.setVisibility(View.GONE);
                iconBg.setVisibility(View.VISIBLE);
                if (item.iconRes != 0) icon.setImageResource(item.iconRes);
            }
            if (flagFull != null) flagFull.setVisibility(View.GONE);
            container.setAlpha(item.selectable ? 1f : 0.6f);
            container.setOnClickListener(v -> { if (item.selectable) cb.onSelect(item); });
        }
    }
}
