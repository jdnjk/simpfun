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

        btnAction.setVisibility(currentStep == Step.CONFIRM ? View.VISIBLE : View.GONE);
        btnAction.setText("创建");
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
        if (imageKindId == null && gameId == null) return;
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
        if (versionId == null) return;
        executeCall(CServerApi.getSpecList(isCustom, versionId, token), json -> {
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
            setupGradeSpinner();
            selectedGrade = ""; // 默认全部
            updateSpecDisplay();
        });
    }

    private void setupGradeSpinner() {
        if (spGrade == null) return;
        layoutGradeFilter.setVisibility(View.VISIBLE);
        // 固定等级顺序 (C+ 最低 -> S+ 最高)
        String[] order = {"C+","C","C++","B","B+","B++","A","A+","S","S+"};
        // 过滤出在数据中出现过的等级
        java.util.LinkedHashSet<String> gradesPresent = new java.util.LinkedHashSet<>();
        for (String g : order) {
            for (JSONObject o : masterSpecList) {
                if (g.equalsIgnoreCase(o.optString("area_grade"))) { gradesPresent.add(g); break; }
            }
        }
        List<String> spinnerItems = new ArrayList<>();
        spinnerItems.add("全部");
        spinnerItems.addAll(gradesPresent);
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

    private void updateSpecDisplay() {
        data.clear();
        for (JSONObject o : masterSpecList) {
            String grade = o.optString("area_grade");
            if (!TextUtils.isEmpty(selectedGrade) && !selectedGrade.equalsIgnoreCase(grade)) continue;
            int id = o.optInt("id");
            boolean creatable = o.optBoolean("creatable", true);
            String title = grade + "." + o.optString("area_vendor") + "." + o.optString("spec") + "." + (o.optBoolean("area_is_windows") ? "W" : "L");
            int cpu = o.optInt("cpu");
            int ram = o.optInt("ram");
            int disk = o.optInt("disk");
            int traffic = o.optInt("traffic");
            // 调整显示格式: cpu核 / ramG内存 / diskG硬盘 / trafficG流量
            String detail = cpu + "核 / " + ram + "G内存 / " + disk + "G硬盘 / " + traffic + "G流量";
            int point = o.optInt("point");
            ListItem li = ListItem.simpleWithId(id, title, detail);
            li.selectable = creatable; // 仅可创建项可点击
            li.point = point;
            li.full = !creatable; // 不可创建表示已满
            data.add(li);
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
        static ListItem simple(String t, String sub, boolean selectable){ ListItem li = new ListItem(); li.id = -1; li.title=t; li.subtitle=sub; li.selectable=selectable; return li; }
        static ListItem simpleWithId(int id, String t, String sub){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; li.selectable=true; return li; }
        static ListItem image(int id, String t, String sub, String url, boolean selectable){ ListItem li = new ListItem(); li.id=id; li.title=t; li.subtitle=sub; li.imageUrl=url; li.showImage=true; li.selectable=selectable; return li; }
        static ListItem info(String t, String v){ ListItem li = new ListItem(); li.id=-1; li.title=t; li.subtitle=v; li.selectable=false; li.full=false; return li; }
    }

    private static class GenericAdapter extends RecyclerView.Adapter<GenericViewHolder> {
        interface OnSelect { void onSelect(ListItem item); }
        private final List<ListItem> data; private final OnSelect onSelect;
        GenericAdapter(List<ListItem> d, OnSelect os){ data=d; onSelect=os; }
        @Override public @NotNull GenericViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType){ View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_option_generic, parent,false); return new GenericViewHolder(v); }
        @Override public void onBindViewHolder(GenericViewHolder h, int p){ ListItem it = data.get(p); h.bind(it,onSelect); }
        @Override public int getItemCount(){ return data.size(); }
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
        for (int i = 1; i <= pages; i++) {
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(i));
            tv.setPadding(dp(12), dp(6), dp(12), dp(6));
            tv.setTextSize(14f);
            tv.setBackgroundResource(i == imageKindCurrentPage ? android.R.color.holo_blue_dark : android.R.color.darker_gray);
            tv.setTextColor(getResources().getColor(android.R.color.white));
            int pageIndex = i;
            tv.setOnClickListener(v -> {
                if (pageIndex != imageKindCurrentPage) {
                    imageKindCurrentPage = pageIndex;
                    applyImageKindFiltersAndPagination();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 1) lp.setMargins(dp(4), 0, 0, 0);
            paginationContainer.addView(tv, lp);
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
