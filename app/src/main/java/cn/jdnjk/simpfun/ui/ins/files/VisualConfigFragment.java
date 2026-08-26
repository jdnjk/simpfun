package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.PropertiesApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.model.ConfigFieldDef;
import cn.jdnjk.simpfun.model.ConfigFileDef;
import cn.jdnjk.simpfun.utils.ConfigTextReplacer;

public class VisualConfigFragment extends Fragment {

    private static final String TAG = "VisualConfig";

    /** 宿主回调接口，由 FilePaneHostFragment 实现。 */
    public interface Host {
        /** 统一离开配置页（含脏检查弹窗）。 */
        void exitConfigMode();
        /** 保存成功后通知宿主（可选，用于退出时刷新）。 */
        void onConfigSaved();
    }

    private static final String ARG_DEVICE_ID = "device_id";

    private int deviceId = -1;
    private List<ConfigFileDef> configFiles = new ArrayList<>();
    private ConfigFileDef currentFile;
    private String originalText = "";
    private final List<FieldRow> fieldRows = new ArrayList<>();
    private int selectedFileIndex = -1;
    private boolean saving;
    private boolean defsLoaded;
    /** 每个配置文件是否可加载（文件不存在/读取失败时标记为不可加载，隐藏对应分段按钮） */
    private boolean[] fileLoadable;
    /** 最近一次保存前的文本，用于"撤销"恢复 */
    private String preSaveText;

    // views
    private MaterialButtonToggleGroup fileToggle;
    private View scrollFileToggle;
    private TextInputLayout layoutSearch;
    private TextInputEditText etSearch;
    private LinearLayout fieldsContainer;
    private ExtendedFloatingActionButton fabSave;
    private ProgressBar progress;
    private View contentView;
    private View errorView;
    private TextView errorText;

    private final FileApi fileApi = new FileApi();

    public static VisualConfigFragment newInstance(int deviceId) {
        VisualConfigFragment f = new VisualConfigFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DEVICE_ID, deviceId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            deviceId = getArguments().getInt(ARG_DEVICE_ID, -1);
        }
        if (deviceId <= 0) {
            deviceId = resolveDeviceId();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_visual_config, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fileToggle = view.findViewById(R.id.toggle_file_selection);
        scrollFileToggle = view.findViewById(R.id.scroll_file_toggle);
        layoutSearch = view.findViewById(R.id.layout_search);
        etSearch = view.findViewById(R.id.et_search);
        fieldsContainer = view.findViewById(R.id.fields_container);
        fabSave = view.findViewById(R.id.fab_save);
        progress = view.findViewById(R.id.progress);
        contentView = view.findViewById(R.id.content_view);
        errorView = view.findViewById(R.id.error_view);
        errorText = view.findViewById(R.id.error_text);
        View buttonRetry = view.findViewById(R.id.button_retry);

        fabSave.setOnClickListener(v -> onSaveClick());
        buttonRetry.setOnClickListener(v -> loadDefinitions());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                applySearchFilter(s == null ? "" : s.toString());
            }
        });

        fileToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int index = findFileIndexByButtonId(checkedId);
            if (index >= 0 && index != selectedFileIndex) {
                confirmSwitchToFile(index);
            }
        });

        // 系统返回键统一交给宿主退出配置页（含脏检查）
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Host host = configHost();
                if (host != null) host.exitConfigMode();
            }
        });

        loadDefinitions();
    }

    private Host configHost() {
        if (getParentFragment() instanceof Host h) return h;
        return null;
    }

    private int resolveDeviceId() {
        if (getActivity() instanceof ServerManages a && a.getDeviceId() > 0) {
            return a.getDeviceId();
        }
        Context ctx = getContext();
        if (ctx != null) {
            SharedPreferences sp = ctx.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            return sp.getInt("device_id", -1);
        }
        return -1;
    }

    // ---------- 加载 ----------

    private void loadDefinitions() {
        if (deviceId <= 0) {
            showError("无效的服务器ID");
            return;
        }
        showProgress();
        new PropertiesApi().getConfigDefinitions(requireContext(), deviceId, new PropertiesApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                configFiles = ConfigFileDef.parse(data.optJSONArray("list"));
                if (configFiles.isEmpty()) {
                    showError(getString(R.string.visual_config_no_config));
                    return;
                }
                defsLoaded = true;
                fileLoadable = new boolean[configFiles.size()];
                java.util.Arrays.fill(fileLoadable, true);
                buildFileToggle();
                // 先标记选中索引，避免 check() 触发监听导致重复加载
                selectedFileIndex = 0;
                if (fileToggle.getChildCount() > 0) {
                    fileToggle.check(fileToggle.getChildAt(0).getId());
                }
                switchToFile(0);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                showError(errorMsg);
            }
        });
    }

    private void buildFileToggle() {
        fileToggle.removeAllViews();
        int visibleCount = countLoadableFiles();
        if (visibleCount > 1) {
            scrollFileToggle.setVisibility(View.VISIBLE);
        } else {
            scrollFileToggle.setVisibility(View.GONE);
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < configFiles.size(); i++) {
            // 不可加载（文件不存在/读取失败）的文件不显示分段按钮
            if (!fileLoadable[i]) continue;
            ConfigFileDef def = configFiles.get(i);
            MaterialButton btn = (MaterialButton) inflater.inflate(R.layout.item_toggle_button, fileToggle, false);
            btn.setId(View.generateViewId());
            btn.setTag(i);
            btn.setText(def.name);
            fileToggle.addView(btn);
        }
    }

    private int countLoadableFiles() {
        int n = 0;
        if (fileLoadable == null) return 0;
        for (boolean b : fileLoadable) {
            if (b) n++;
        }
        return n;
    }

    private int findFileIndexByButtonId(int buttonId) {
        for (int i = 0; i < configFiles.size(); i++) {
            View btn = fileToggle.findViewById(buttonId);
            if (btn != null && btn.getTag() instanceof Integer idx && idx == i) return i;
        }
        return -1;
    }

    private void switchToFile(int index) {
        if (index < 0 || index >= configFiles.size()) return;
        selectedFileIndex = index;
        currentFile = configFiles.get(index);
        loadFile(currentFile);
    }

    /** 手动切换文件：若当前文件有未保存修改，先确认。 */
    private void confirmSwitchToFile(int index) {
        if (!isDirty()) {
            switchToFile(index);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.visual_config_confirm_title)
                .setMessage(R.string.visual_config_switch_file_msg)
                .setPositiveButton(R.string.visual_config_discard, (d, w) -> switchToFile(index))
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    // 恢复原选中文件的分段按钮
                    fileToggle.check(findButtonIdForFileIndex(selectedFileIndex));
                })
                .show();
    }

    private void loadFile(ConfigFileDef def) {
        showProgress();
        fileApi.fetchFileContent(requireContext(), deviceId, def.file, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                originalText = data.optString("content", "");
                hideProgress();
                rebuildFieldRows();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                Log.w(TAG, "配置加载失败 file=" + def.file + ": " + errorMsg);
                // 文件读取失败（如文件不存在）→ 标记为不可加载，隐藏该文件的分段按钮，
                // 并尝试切换到下一个可加载文件；全部失败才显示"暂无可用配置文件"。
                markFileUnloadable(selectedFileIndex);
            }
        });
    }

    /** 标记指定索引的文件为不可加载，重建分段按钮，并切到下一个可加载文件。 */
    private void markFileUnloadable(int index) {
        if (fileLoadable == null || index < 0 || index >= fileLoadable.length) {
            showError(getString(R.string.visual_config_load_error));
            return;
        }
        fileLoadable[index] = false;
        // 若当前文件正好是刚失败的，清掉
        if (currentFile != null && configFiles.get(index) == currentFile) {
            currentFile = null;
            fieldRows.clear();
            fieldsContainer.removeAllViews();
        }
        int next = findNextLoadable(index);
        if (next < 0) {
            // 所有文件都不可加载
            showError(getString(R.string.visual_config_no_config));
            return;
        }
        buildFileToggle();
        // 先更新选中索引，避免 check() 触发监听导致重复加载
        selectedFileIndex = next;
        int buttonId = findButtonIdForFileIndex(next);
        if (buttonId != View.NO_ID) {
            fileToggle.check(buttonId);
        }
        switchToFile(next);
    }

    private int findNextLoadable(int afterIndex) {
        if (fileLoadable == null) return -1;
        // 从 afterIndex+1 开始找，找不到则从头找
        for (int i = afterIndex + 1; i < fileLoadable.length; i++) {
            if (fileLoadable[i]) return i;
        }
        for (int i = 0; i < fileLoadable.length; i++) {
            if (fileLoadable[i]) return i;
        }
        return -1;
    }

    private int findButtonIdForFileIndex(int index) {
        for (int i = 0; i < fileToggle.getChildCount(); i++) {
            View child = fileToggle.getChildAt(i);
            if (child.getTag() instanceof Integer idx && idx == index) {
                return child.getId();
            }
        }
        return View.NO_ID;
    }

    // ---------- 字段行 ----------

    private void rebuildFieldRows() {
        fieldRows.clear();
        fieldsContainer.removeAllViews();
        if (currentFile == null || currentFile.fields == null) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (ConfigFieldDef def : currentFile.fields) {
            View rowView = inflater.inflate(R.layout.item_visual_config_field, fieldsContainer, false);
            FieldRow row = new FieldRow(rowView, def);
            bindRow(row);
            // 文件里未找到该字段（matched=false）→ 不显示这一项
            if (!row.matched) continue;
            fieldsContainer.addView(rowView);
            fieldRows.add(row);
        }
        layoutSearch.setVisibility(fieldRows.isEmpty() ? View.GONE : View.VISIBLE);
        applySearchFilter(etSearch.getText() == null ? "" : etSearch.getText().toString());
    }

    private void bindRow(FieldRow row) {
        ConfigFieldDef def = row.def;
        row.tvName.setText(def.name);
        if (!TextUtils.isEmpty(def.notice)) {
            row.tvNotice.setText(def.notice);
            row.tvNotice.setVisibility(View.VISIBLE);
        } else {
            row.tvNotice.setVisibility(View.GONE);
        }

        String raw = ConfigTextReplacer.extractValue(originalText, def.exp);
        row.matched = (raw != null);
        row.originalValue = raw == null ? "" : raw;

        if ("boolean".equals(def.type)) {
            showControl(row, R.id.row_switch);
            row.tvSwitchLabel.setText(def.name);
            row.sw.setChecked("true".equals(raw));
        } else if (hasChoiceOptions(def)) {
            if (useDropdownFor(def)) {
                // 选项多/文字长，屏幕放不下 → 用下拉菜单
                showControl(row, R.id.dropdown_field);
                setupDropdown(row, raw);
            } else {
                // 选项少 → 用分段按钮
                showControl(row, R.id.toggle_field);
                buildChoiceButtons(row);
                if (row.matched && raw != null) {
                    row.tg.check(findButtonIdForValue(row, raw));
                }
            }
        } else {
            showControl(row, R.id.til_field);
            row.et.setText(raw == null ? "" : raw);
            row.et.setEnabled(row.matched);
            if ("number".equals(def.type)) {
                row.et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
            setupExpandOnFocus(row.et);
        }
    }

    /**
     * 输入框聚焦时展开为多行（显示全部内容），失焦恢复单行。
     */
    private void setupExpandOnFocus(TextInputEditText et) {
        et.setSingleLine(true);
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 聚焦：展开多行，显示全部内容
                et.setSingleLine(false);
                et.setMaxLines(Integer.MAX_VALUE);
                et.setHorizontallyScrolling(false);
            } else {
                // 失焦：恢复单行折叠
                et.setSingleLine(true);
                et.setMaxLines(1);
            }
            et.requestLayout();
        });
    }

    private boolean hasChoiceOptions(ConfigFieldDef def) {
        return (def.options != null && !def.options.isEmpty()) || (def.rules != null && !def.rules.isEmpty());
    }

    private void showControl(FieldRow row, int visibleId) {
        row.til.setVisibility(visibleId == R.id.til_field ? View.VISIBLE : View.GONE);
        row.rowSwitch.setVisibility(visibleId == R.id.row_switch ? View.VISIBLE : View.GONE);
        row.tg.setVisibility(visibleId == R.id.toggle_field ? View.VISIBLE : View.GONE);
        row.dropdownLayout.setVisibility(visibleId == R.id.dropdown_field ? View.VISIBLE : View.GONE);
    }

    /**
     * 是否用下拉菜单展示 select 字段。
     * 选项较多（Chip 自动换行也会太长）时用下拉菜单，否则用 Chip 单选组。
     */
    private boolean useDropdownFor(ConfigFieldDef def) {
        int count = 0;
        if (def.options != null) count += def.options.size();
        if (def.rules != null) count += def.rules.size();
        return count > 8;
    }

    /** 构建单选 Chip 组（自动换行，选项多也不会截断）；select 显示 label 存 value。 */
    private void buildChoiceButtons(FieldRow row) {
        ConfigFieldDef def = row.def;
        row.tg.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        if (def.options != null && !def.options.isEmpty()) {
            for (ConfigFieldDef.ConfigOptionDef opt : def.options) {
                Chip chip = (Chip) inflater.inflate(R.layout.item_config_chip, row.tg, false);
                chip.setId(View.generateViewId());
                chip.setText(opt.label);
                chip.setTag(opt.value);
                row.tg.addView(chip);
            }
        } else if (def.rules != null && !def.rules.isEmpty()) {
            for (String rule : def.rules) {
                Chip chip = (Chip) inflater.inflate(R.layout.item_config_chip, row.tg, false);
                chip.setId(View.generateViewId());
                chip.setText(rule);
                chip.setTag(rule);
                row.tg.addView(chip);
            }
        }
    }

    private int findButtonIdForValue(FieldRow row, String rawValue) {
        for (int i = 0; i < row.tg.getChildCount(); i++) {
            View child = row.tg.getChildAt(i);
            Object tag = child.getTag();
            if (tag != null && Objects.equals(tag.toString(), rawValue)) {
                return child.getId();
            }
        }
        return View.NO_ID;
    }

    /**
     * 配置下拉字段：输入框只读展示当前选中项，点击弹出单选对话框列出全部选项。
     * 用对话框而不是 AutoCompleteTextView 的内置下拉，避免"只显示已选选项"的过滤问题，
     * 且选项再多也能滚动显示全部。
     */
    private void setupDropdown(FieldRow row, String rawValue) {
        ConfigFieldDef def = row.def;
        List<DropdownItem> items = new ArrayList<>();
        if (def.options != null && !def.options.isEmpty()) {
            for (ConfigFieldDef.ConfigOptionDef opt : def.options) {
                items.add(new DropdownItem(opt.label, opt.value));
            }
        } else if (def.rules != null && !def.rules.isEmpty()) {
            for (String rule : def.rules) {
                items.add(new DropdownItem(rule, rule));
            }
        }
        final String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).label;

        // 回填当前值：按 value 找到对应 label 显示
        String currentLabel = findLabelForValue(items, rawValue);
        row.ac.setText(currentLabel == null ? (rawValue == null ? "" : rawValue) : currentLabel);
        row.ac.setTag(findValueForValue(items, rawValue));
        row.ac.setInputType(InputType.TYPE_NULL); // 只读，禁止手输，避免过滤混乱

        row.ac.setOnClickListener(v -> {
            if (items.isEmpty()) return;
            // 定位当前选中项
            Object cur = row.ac.getTag();
            int checkPosition = -1;
            for (int i = 0; i < items.size(); i++) {
                if (cur != null && Objects.equals(items.get(i).value, cur.toString())) {
                    checkPosition = i;
                    break;
                }
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(def.name)
                    .setSingleChoiceItems(labels, checkPosition, (d, which) -> {
                        DropdownItem it = items.get(which);
                        row.ac.setText(it.label);
                        row.ac.setTag(it.value);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private String findLabelForValue(List<DropdownItem> items, String value) {
        if (value == null) return null;
        for (DropdownItem it : items) {
            if (Objects.equals(it.value, value)) return it.label;
        }
        return null;
    }

    private String findValueForValue(List<DropdownItem> items, String rawValue) {
        if (rawValue == null) return null;
        for (DropdownItem it : items) {
            if (Objects.equals(it.value, rawValue)) return it.value;
        }
        return null;
    }

    /** 下拉选项：label 显示、value 存值。 */
    private static class DropdownItem {
        final String label;
        final String value;
        DropdownItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    // ---------- 搜索 ----------

    private void applySearchFilter(String query) {
        if (fieldsContainer == null) return;
        String q = query == null ? "" : query.trim();
        for (FieldRow row : fieldRows) {
            boolean show = q.isEmpty() || row.def.name.contains(q);
            row.root.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ---------- 保存 ----------

    private void onSaveClick() {
        if (saving || currentFile == null) return;
        if (fieldRows.isEmpty()) {
            Toast.makeText(requireContext(), R.string.visual_config_no_config, Toast.LENGTH_SHORT).show();
            return;
        }
        // 校验 number 字段
        for (FieldRow row : fieldRows) {
            if (row.matched && "number".equals(row.def.type) && row.et.getVisibility() == View.VISIBLE) {
                String s = row.et.getText() == null ? "" : row.et.getText().toString().trim();
                if (!s.isEmpty()) {
                    try {
                        Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), R.string.visual_config_invalid_number, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
        }

        String current = originalText;
        for (FieldRow row : fieldRows) {
            if (!row.matched) continue;
            String ui = readValueFromRow(row);
            String next = ConfigTextReplacer.replaceValue(current, row.def.exp, ui);
            if (next != null) current = next;
        }

        // 记录保存前文本，供"撤销"恢复
        preSaveText = originalText;
        String finalText = current;
        saving = true;
        fabSave.setEnabled(false);
        fileApi.saveFileContent(requireContext(), deviceId, currentFile.file, finalText, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                saving = false;
                fabSave.setEnabled(true);
                originalText = finalText;
                // 更新每行 originalValue
                for (FieldRow row : fieldRows) {
                    row.originalValue = readValueFromRow(row);
                }
                showSaveSnackbar();
                Host host = configHost();
                if (host != null) host.onConfigSaved();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                saving = false;
                fabSave.setEnabled(true);
                Toast.makeText(requireContext(), "保存失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 保存成功后显示 Snackbar，带"撤销"动作恢复保存前内容。 */
    private void showSaveSnackbar() {
        View root = getView();
        if (root == null) return;
        Snackbar.make(root, R.string.visual_config_saved, Snackbar.LENGTH_LONG)
                .setAction(R.string.visual_config_undo, v -> undoLastSave())
                .show();
    }

    /** 撤销最近一次保存：把文件内容恢复为保存前文本。 */
    private void undoLastSave() {
        if (saving || currentFile == null || preSaveText == null) return;
        String undoText = preSaveText;
        saving = true;
        fabSave.setEnabled(false);
        fileApi.saveFileContent(requireContext(), deviceId, currentFile.file, undoText, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                saving = false;
                fabSave.setEnabled(true);
                originalText = undoText;
                rebuildFieldRows();
                Toast.makeText(requireContext(), R.string.visual_config_undo_done, Toast.LENGTH_SHORT).show();
                Host host = configHost();
                if (host != null) host.onConfigSaved();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                saving = false;
                fabSave.setEnabled(true);
                Toast.makeText(requireContext(), "撤销失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String readValueFromRow(FieldRow row) {
        if ("boolean".equals(row.def.type)) {
            return row.sw.isChecked() ? "true" : "false";
        }
        if (hasChoiceOptions(row.def)) {
            if (row.dropdownLayout.getVisibility() == View.VISIBLE) {
                // 下拉菜单：读取 tag 存的 value
                Object tag = row.ac.getTag();
                return tag == null ? "" : tag.toString();
            }
            int checkedId = row.tg.getCheckedChipId();
            View checked = checkedId == View.NO_ID ? null : row.tg.findViewById(checkedId);
            Object tag = checked == null ? null : checked.getTag();
            return tag == null ? "" : tag.toString();
        }
        return row.et.getText() == null ? "" : row.et.getText().toString().trim();
    }

    // ---------- 脏检查 ----------

    boolean isDirty() {
        if (saving) return false;
        for (FieldRow row : fieldRows) {
            if (!row.matched) continue;
            String now = readValueFromRow(row);
            String before = row.originalValue == null ? "" : row.originalValue;
            if (!Objects.equals(now, before)) return true;
        }
        return false;
    }

    /** 有未保存改动时弹三按钮对话框；无改动直接 onDiscard。 */
    void showUnsavedDialog(Runnable onDiscard) {
        if (!isDirty()) {
            onDiscard.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.visual_config_confirm_title)
                .setMessage(R.string.visual_config_confirm_msg)
                .setPositiveButton(R.string.visual_config_save_and_exit, (d, w) -> saveNowThen(onDiscard))
                .setNegativeButton(R.string.visual_config_discard, (d, w) -> onDiscard.run())
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    /** 保存成功后执行 onSaved（用于"保存并退出"）。保存失败则留在页面。 */
    private void saveNowThen(Runnable onSaved) {
        if (saving || currentFile == null) return;
        String current = originalText;
        for (FieldRow row : fieldRows) {
            if (!row.matched) continue;
            String ui = readValueFromRow(row);
            String next = ConfigTextReplacer.replaceValue(current, row.def.exp, ui);
            if (next != null) current = next;
        }
        String finalText = current;
        saving = true;
        fabSave.setEnabled(false);
        fileApi.saveFileContent(requireContext(), deviceId, currentFile.file, finalText, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                saving = false;
                fabSave.setEnabled(true);
                originalText = finalText;
                for (FieldRow row : fieldRows) {
                    row.originalValue = readValueFromRow(row);
                }
                Host host = configHost();
                if (host != null) host.onConfigSaved();
                onSaved.run();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                saving = false;
                fabSave.setEnabled(true);
                Toast.makeText(requireContext(), "保存失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- 状态显示 ----------

    private void showProgress() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (contentView != null) contentView.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.GONE);
        if (fabSave != null) fabSave.setVisibility(View.GONE);
    }

    private void hideProgress() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (contentView != null) contentView.setVisibility(View.VISIBLE);
        if (errorView != null) errorView.setVisibility(View.GONE);
        if (fabSave != null) fabSave.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        if (progress != null) progress.setVisibility(View.GONE);
        if (contentView != null) contentView.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
        if (errorText != null && message != null) errorText.setText(message);
        if (fabSave != null) fabSave.setVisibility(View.GONE);
    }

    // ---------- 内部行模型 ----------

    private static class FieldRow {
        final View root;
        final ConfigFieldDef def;
        final TextView tvName;
        final TextView tvNotice;
        final TextInputLayout til;
        final TextInputEditText et;
        final LinearLayout rowSwitch;
        final MaterialSwitch sw;
        final TextView tvSwitchLabel;
        final ChipGroup tg;
        final TextInputLayout dropdownLayout;
        final AutoCompleteTextView ac;
        boolean matched;
        String originalValue = "";

        FieldRow(View root, ConfigFieldDef def) {
            this.root = root;
            this.def = def;
            tvName = root.findViewById(R.id.tv_field_name);
            tvNotice = root.findViewById(R.id.tv_field_notice);
            til = root.findViewById(R.id.til_field);
            et = root.findViewById(R.id.et_field_value);
            rowSwitch = root.findViewById(R.id.row_switch);
            sw = root.findViewById(R.id.switch_field_value);
            tvSwitchLabel = root.findViewById(R.id.tv_switch_label);
            tg = root.findViewById(R.id.toggle_field);
            dropdownLayout = root.findViewById(R.id.dropdown_field);
            ac = root.findViewById(R.id.ac_dropdown_value);
        }
    }
}
