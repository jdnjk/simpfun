package cn.jdnjk.simpfun.ui.create;

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
import com.bumptech.glide.Glide;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.view.ViewGroup;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.textfield.TextInputLayout;
import android.view.MenuItem;
import android.content.Intent;
import android.net.Uri;

/**
 * 创建服务器向导
 */
public class CreateServer extends AppCompatActivity {

    private enum Step {
        TYPE, GAME, IMAGE_KIND, VERSION, SPEC, CONFIRM
    }

    private Step currentStep = Step.TYPE;
    private boolean isCustom = false;
    private RecyclerView recyclerView;
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
    private TextView tvCpuModelLink;
    private HorizontalScrollView hsSteps;
    private LinearLayout layoutSteps;
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

    private String token;

    private final List<ListItem> data = new ArrayList<>();
    private GenericAdapter adapter;

    private final List<JSONObject> masterSpecList = new ArrayList<>();
    private String selectedGrade = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_create_server);
        recyclerView = findViewById(R.id.recycler_view);
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
        tvCpuModelLink = findViewById(R.id.tv_cpu_model_link);
        if (tvCpuModelLink != null) {
            tvCpuModelLink.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://yuque.com/simpfun/sfe/areas"));
                startActivity(intent);
            });
        }
        hsSteps = findViewById(R.id.hs_steps);
        layoutSteps = findViewById(R.id.layout_steps);
        fabHelp = findViewById(R.id.fab_help);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GenericAdapter(data, this::onItemSelected);
        recyclerView.setAdapter(adapter);

        toolbar.setNavigationOnClickListener(v -> onBackStep());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                refreshCurrentStep();
                return true;
            } else if (item.getItemId() == R.id.action_close) {
                finish();
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

    private void onBackStep() {
        switch (currentStep) {
            case TYPE -> finish();
            case GAME -> currentStep = Step.TYPE;
            case IMAGE_KIND -> currentStep = Step.GAME;
            case VERSION -> currentStep = Step.IMAGE_KIND;
            case SPEC -> currentStep = Step.VERSION;
            case CONFIRM -> currentStep = Step.SPEC;
        }
        renderCurrentStep();
    }

    private void onActionButton() {
        if (currentStep == Step.CONFIRM) {
            createInstance();
        }
    }

    private void renderCurrentStep() {
        layoutSearch.setVisibility(View.GONE);
        hsPagination.setVisibility(View.GONE);

        MenuItem refreshItem = toolbar.getMenu().findItem(R.id.action_refresh);
        if (refreshItem != null) refreshItem.setVisible(false);

        layoutGradeFilter.setVisibility(View.GONE);

        renderSteps(); // 更新步骤导航

        btnAction.setVisibility(currentStep == Step.CONFIRM ? View.VISIBLE : View.GONE);
        btnAction.setText("创建");

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
                data.clear();
                data.add(ListItem.simple("基础镜像", "官方标准镜像", true));
                data.add(ListItem.simple("第三方镜像", "社区提供的镜像", true));
                adapter.notifyDataSetChanged();
            }
            case GAME -> {
                collapsingToolbar.setTitle("选择镜像类别");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadGameList();
            }
            case IMAGE_KIND -> {
                collapsingToolbar.setTitle("选择镜像服务端");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadImageKindList();
            }
            case VERSION -> {
                collapsingToolbar.setTitle("选择镜像版本");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadVersionList();
            }
            case SPEC -> {
                collapsingToolbar.setTitle("选择实例规格");
                if (refreshItem != null) refreshItem.setVisible(true);
                loadSpecList();
            }
            case CONFIRM -> {
                collapsingToolbar.setTitle("确认信息");
                loadConfirmation();
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
                .setMessage("实例命名方式为[CPU级别.CPU厂商.具体配置.操作系统]\n" +
                        "CPU级别以字母[C,B,A,S]等分级，代表具体CPU性能，其中性能S>A>B>C，可参考具体CPU型号\n" +
                        "CPU厂商以字母[A,I]分类，代表AMD,Intel\n" +
                        "具体配置以字母[M,L,XL]等分类，代表各个配置套餐\n" +
                        "操作系统以字母[L,W]分类，代表实例操作系统(Linux,Windows)\n" +
                        "推荐在预算或内存足够的情况下，选择更高级别的CPU，以获得更流畅的体验，您也可以更换CPU厂商观察是否获得性能提升。\n\n" +
                        "实例计费方式为按天付费，当日不开服不扣积分\n" +
                        "当日开服指：服务器实例在当日24小时内启动过，无论是否是否进入服务器，服务器运行状态是否正常，只要启动即视为当日已开服\n" +
                        "超套餐额磁盘将被计费1积分/G/天\n" +
                        "若当日开服所需积分不足，则会关闭实例，若连续7天未启动实例，则会销毁实例，实例销毁前将会默认创建完整镜像，此镜像保留60天，可随时通过新建实例->备份->还原功能恢复实例文件")
                .setPositiveButton("了解", null)
                .show();
        }
    }

    private void renderSteps() {
        layoutSteps.removeAllViews();
        Step[] steps = Step.values();
        for (int i = 0; i <= currentStep.ordinal(); i++) {
            Step s = steps[i];
            String name = getStepName(s);

            TextView tv = new TextView(this);
            tv.setText(name);
            tv.setTextSize(14);
            tv.setPadding(dp(8), dp(4), dp(8), dp(4));

            if (s == currentStep) {
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_primary)); // 高亮当前
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.darker_gray));
                // 可点击返回
                tv.setOnClickListener(v -> {
                    currentStep = s;
                    renderCurrentStep();
                });
            }
            layoutSteps.addView(tv);

            // 添加分隔符
            if (i < currentStep.ordinal()) {
                TextView divider = new TextView(this);
                divider.setText(">");
                divider.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.darker_gray));
                layoutSteps.addView(divider);
            }
        }
        // 滚动到最右侧
        hsSteps.post(() -> hsSteps.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    private String getStepName(Step s) {
        switch (s) {
            case TYPE: return "类型";
            case GAME: return "类别";
            case IMAGE_KIND: return "服务端";
            case VERSION: return "版本";
            case SPEC: return "规格";
            case CONFIRM: return "确认";
            default: return "";
        }
    }

    private void onItemSelected(ListItem item) {
        if (currentStep == Step.TYPE) {
            isCustom = item.title.contains("第三方");
//            if (isCustom) {
//                Toast.makeText(this, "第三方镜像为社区提供，请注意安全与可信度", Toast.LENGTH_LONG).show();
//            }
            currentStep = Step.GAME;
            renderCurrentStep();
            return;
        }
        if (!item.selectable) {
            Toast.makeText(this, "该项不可创建", Toast.LENGTH_SHORT).show();
            return;
        }
        switch (currentStep) {
            case GAME -> {
                gameId = item.id;
                currentStep = Step.IMAGE_KIND;
            }
            case IMAGE_KIND -> {
                imageKindId = item.id;
                currentStep = Step.VERSION;
            }
            case VERSION -> {
                versionId = item.id;
                currentStep = Step.SPEC;
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
            data.clear();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                data.add(ListItem.image(o.optInt("id"), o.optString("name"), null, o.optString("pic_path"), true));
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void loadImageKindList() {
        if (gameId == null) return;
        executeCall(CServerApi.getImageKindList(isCustom, gameId, token), json -> {
            data.clear();
            fullImageKindList.clear();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (isCustom) {
                    fullImageKindList.add(ListItem.simpleWithId(o.optInt("id"), o.optString("name"), o.optString("description")));
                } else {
                    data.add(ListItem.image(o.optInt("id"), o.optString("name"), o.optString("description"), o.optString("pic_path"), true));
                }
            }
            if (isCustom) {
                layoutSearch.setVisibility(fullImageKindList.size() > 0 ? View.VISIBLE : View.GONE);
                imageKindCurrentPage = 1;
                applyImageKindFiltersAndPagination();
            } else {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void loadVersionList() {
        if (imageKindId == null) return;
        executeCall(CServerApi.getVersionList(isCustom, imageKindId, token), json -> {
            data.clear();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (isCustom) {
                    // 展示更多信息：描述 + 推荐配置 + 大小
                    String desc = o.optString("description");
                    String rec = o.optString("recommend_setting");
                    String sizeRaw = o.optString("size");
                    String sizeFmt = formatSize(sizeRaw); // 转换大小
                    StringBuilder sb = new StringBuilder();
                    if (!TextUtils.isEmpty(desc)) sb.append(desc);
                    if (!TextUtils.isEmpty(rec)) {
                        if (sb.length() > 0) sb.append(" | ");
                        sb.append("推荐:").append(rec);
                    }
                    if (!TextUtils.isEmpty(sizeFmt)) {
                        if (sb.length() > 0) sb.append(" | ");
                        sb.append("大小:").append(sizeFmt);
                    }
                    data.add(ListItem.simpleWithId(o.optInt("id"), o.optString("name"), sb.toString()));
                } else {
                    data.add(ListItem.simpleWithId(o.optInt("id"), o.optString("name"), o.optString("description")));
                }
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void loadSpecList() {
        if (versionId == null || imageKindId == null) return;
        executeCall(CServerApi.getSpecList(isCustom, versionId, imageKindId, token), json -> {
            masterSpecList.clear();
            data.clear();
            JSONArray arr = json.optJSONArray("list");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    masterSpecList.add(o);
                }
            }
            // Sort by point (price) ascending
            java.util.Collections.sort(masterSpecList, (o1, o2) -> Integer.compare(o1.optInt("point"), o2.optInt("point")));

            setupGradeSpinner();
            selectedGrade = ""; // 默认全部
            updateSpecDisplay();
        });
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
        java.util.Collections.sort(sortedGrades, (g1, g2) -> {
            int s1 = getGradeScore(g1);
            int s2 = getGradeScore(g2);
            return Integer.compare(s2, s1); // Descending score (S is highest)
        });

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
        data.clear();
        if (masterSpecList.isEmpty()) {
            data.add(ListItem.info("提示", "暂无可用规格"));
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

                data.add(ListItem.spec(id, grade, specName, cpu, ram, disk, traffic, point, creatable));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadConfirmation() {
        if (versionId == null || specId == null) return;
        // 进入确认页取消搜索与分页
        layoutSearch.setVisibility(View.GONE);
        hsPagination.setVisibility(View.GONE);
        executeCall(CServerApi.getConfirmation(isCustom, versionId, specId, token), json -> {
            data.clear();
            JSONObject d = json.optJSONObject("data");
            if (d != null) {
                data.add(ListItem.info("游戏名称", d.optString("game_name")));
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
                data.add(ListItem.info("镜像名称", displayImageName));
                data.add(ListItem.info("镜像版本", d.optString("version_name")));
                data.add(ListItem.info("配置", d.optInt("cpu") + "核/" + d.optInt("ram") + "G/" + d.optInt("disk") + "G"));
                data.add(ListItem.info("型号", d.optString("grade")));
                data.add(ListItem.info("可用流量", d.optInt("traffic") + "G"));
                data.add(ListItem.info("费用(积分)", String.valueOf(d.optInt("point"))));
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void createInstance() {
        if (versionId == null || specId == null) return;
        showLoading(true);
        CServerApi.createInstance(isCustom, versionId, specId, token).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) { runOnUiThread(() -> { showLoading(false); Toast.makeText(CreateServer.this, "创建失败:" + e.getMessage(), Toast.LENGTH_LONG).show();}); }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException { String body = response.body()!=null?response.body().string():""; runOnUiThread(() -> { showLoading(false); try { JSONObject o = new JSONObject(body); if (o.optInt("code") == 200) { Toast.makeText(CreateServer.this, "创建成功", Toast.LENGTH_SHORT).show(); finish(); } else { Toast.makeText(CreateServer.this, o.optString("msg","创建失败"), Toast.LENGTH_LONG).show(); } } catch (Exception e){ Toast.makeText(CreateServer.this, "解析失败", Toast.LENGTH_LONG).show(); }}); }
        });
    }

    private interface JsonHandler { void handle(JSONObject json); }

    private void executeCall(Call call, JsonHandler handler) {
        showLoading(true);
        call.enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> {
                    showLoading(false); Toast.makeText(CreateServer.this, "网络失败:"+e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String body = response.body()!=null?response.body().string():"";
                runOnUiThread(() -> {
                    showLoading(false);
                    try {
                        JSONObject obj = new JSONObject(body);
                        if (obj.optInt("code") == 200) {
                            handler.handle(obj);
                        } else {
                            Toast.makeText(CreateServer.this, obj.optString("msg","请求失败"), Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e){
                        // 调试输出原始响应，方便定位解析错误
                        if (body.length() > 500) {
                            String preview = body.substring(0, 500) + "...(" + body.length() + ")";
                            Toast.makeText(CreateServer.this, "数据解析错误, 响应预览:"+preview, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(CreateServer.this, "数据解析错误, 响应:"+body, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    private void showLoading(boolean show) { progressBar.setVisibility(show?View.VISIBLE:View.GONE); }

    // --- 数据与适配器 ---
    private static class ListItem {
        int id; String title; String subtitle; String imageUrl; boolean showImage; boolean selectable = true; boolean isGroup = false; int point = -1; boolean full = false; // full: 标记已满状态
        // Spec specific fields
        String grade; String specName; int cpu; int ram; int disk; int traffic;

        static ListItem simple(String t, String sub, boolean selectable){ ListItem li = new ListItem(); li.id = -1; li.title=t; li.subtitle=sub; li.selectable=selectable; return li; }
        static ListItem simpleWithId(int id, String t, String sub){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; li.selectable=true; return li; }
        static ListItem image(int id, String t, String sub, String url, boolean selectable){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; li.imageUrl=url; li.showImage=true; li.selectable=selectable; return li; }
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

        @Override public @NotNull RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType){
            if (viewType == TYPE_SPEC) {
                View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_spec, parent, false);
                return new SpecViewHolder(v);
            }
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_generic, parent,false);
            return new GenericViewHolder(v);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int p){
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
        }

        void bind(ListItem item, GenericAdapter.OnSelect cb) {
            grade.setText(item.grade);

            // Set color based on grade
            int colorRes;
            if (item.grade != null) {
                if (item.grade.startsWith("S")) colorRes = R.color.md_theme_error;
                else if (item.grade.startsWith("A")) colorRes = R.color.md_theme_primary;
                else if (item.grade.startsWith("B")) colorRes = R.color.md_theme_tertiary;
                else colorRes = R.color.md_theme_secondary;
            } else {
                colorRes = R.color.md_theme_secondary;
            }
            int color = androidx.core.content.ContextCompat.getColor(grade.getContext(), colorRes);

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
                status.setText("已满");
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
        GenericViewHolder(View itemView){ super(itemView); title=itemView.findViewById(R.id.item_title); subtitle=itemView.findViewById(R.id.item_subtitle); img=itemView.findViewById(R.id.item_image); container=itemView; flagFull=itemView.findViewById(R.id.item_flag_full); flagPoint=itemView.findViewById(R.id.item_flag_point); }
        void bind(ListItem item, GenericAdapter.OnSelect cb){
            title.setText(item.title);
            if (item.subtitle==null||item.subtitle.isEmpty()){ subtitle.setVisibility(item.isGroup?View.GONE:View.GONE);} else { subtitle.setVisibility(View.VISIBLE); subtitle.setText(item.subtitle);}
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

    // 第三方镜像 服务端列表本地搜索+分页
    private void applyImageKindFiltersAndPagination() {
        if (!isCustom) return; // 仅第三方
        data.clear();
        List<ListItem> filtered = new ArrayList<>();
        String q = imageKindSearchQuery == null ? "" : imageKindSearchQuery.toLowerCase();
        for (ListItem li : fullImageKindList) {
            if (TextUtils.isEmpty(q) || li.title.toLowerCase().contains(q) || (li.subtitle != null && li.subtitle.toLowerCase().contains(q))) {
                filtered.add(li);
            }
        }
        int total = filtered.size();
        if (total <= IMAGE_KIND_PAGE_SIZE) {
            hsPagination.setVisibility(View.GONE);
            data.addAll(filtered);
        } else {
            int pages = (int) Math.ceil(total * 1.0 / IMAGE_KIND_PAGE_SIZE);
            if (imageKindCurrentPage > pages) imageKindCurrentPage = pages;
            int start = (imageKindCurrentPage - 1) * IMAGE_KIND_PAGE_SIZE;
            int end = Math.min(start + IMAGE_KIND_PAGE_SIZE, total);
            data.addAll(filtered.subList(start, end));
            buildPaginationControls(pages);
        }
        adapter.notifyDataSetChanged();
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
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_onPrimary));
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setBackgroundResource(R.drawable.bg_pagination_selected);
            } else {
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_onSurface));
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
