package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.QuickCommandNode;

/**
 * 全页一键指令编辑器 — Shortcuts 风格。
 * 支持多动作（命令模板）序列。
 */
public class QuickCommandEditorFragment extends Fragment {

    private static final String ARG_INDEX = "index";
    private static final String ARG_JSON = "json";

    private int editIndex = -1;
    private QuickCommandNode editingNode;

    private TextInputEditText etName;
    private LinearLayout actionsContainer;
    private final List<String> actions = new ArrayList<>();
    private final List<View> actionRows = new ArrayList<>();
    private QuickCommandStorage storage;
    private OnSavedListener onSavedListener;

    public interface OnSavedListener {
        void onSaved();
    }

    public static QuickCommandEditorFragment newInstance(int index, QuickCommandNode node) {
        QuickCommandEditorFragment f = new QuickCommandEditorFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INDEX, index);
        if (node != null) {
            args.putString(ARG_JSON, node.toJson().toString());
        }
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            editIndex = args.getInt(ARG_INDEX, -1);
            String json = args.getString(ARG_JSON);
            if (json != null) {
                try {
                    editingNode = QuickCommandNode.fromJson(new org.json.JSONObject(json));
                } catch (Exception ignored) {
                }
            }
        }
        storage = new QuickCommandStorage(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_quick_command_editor, container, false);

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle(editingNode != null ? "编辑一键指令" : "新建一键指令");
        }

        etName = root.findViewById(R.id.input_name);
        if (editingNode != null) {
            etName.setText(editingNode.name);
        }

        if (editingNode != null && editingNode.actions != null && !editingNode.actions.isEmpty()) {
            actions.addAll(editingNode.actions);
        } else {
            actions.add("");
        }

        actionsContainer = root.findViewById(R.id.actions_container);
        rebuildActionRows();

        if (getActivity() instanceof SettingsActivity activity) {
            activity.showAddActionToolbarButton();
        }

        ExtendedFloatingActionButton fabSave = root.findViewById(R.id.fab_save);
        fabSave.setOnClickListener(v -> save());

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.restoreToolbarButtons();
            activity.setAppBarTitle("设置");
        }
    }

    public void addAction() {
        actions.add("");
        rebuildActionRows();
    }

    private void rebuildActionRows() {
        if (actionsContainer == null) return;
        actionsContainer.removeAllViews();
        actionRows.clear();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < actions.size(); i++) {
            final int pos = i;
            View row = inflater.inflate(R.layout.item_quick_action, actionsContainer, false);
            TextView tvActionName = row.findViewById(R.id.tv_action_name);
            TextView tvCommand = row.findViewById(R.id.tv_command);
            ImageView ivEdit = row.findViewById(R.id.iv_edit);
            ImageView ivDelete = row.findViewById(R.id.iv_delete);

            String cmd = actions.get(i);
            if (cmd == null || cmd.trim().isEmpty()) {
                tvActionName.setText("空动作");
                tvCommand.setText("点击编辑输入命令");
            } else {
                tvActionName.setText("执行命令");
                tvCommand.setText(cmd);
            }

            ivEdit.setOnClickListener(v -> showEditActionDialog(pos));
            ivDelete.setOnClickListener(v -> {
                actions.remove(pos);
                rebuildActionRows();
            });

            actionsContainer.addView(row);
            actionRows.add(row);
        }
    }

    private void showEditActionDialog(int position) {
        String current = position >= 0 && position < actions.size() ? actions.get(position) : "";

        Context ctx = requireContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        EditText et = new EditText(ctx);
        et.setText(current);
        et.setHint("例如: say !{1}");
        et.setMinLines(2);
        et.setSingleLine(false);
        container.addView(et);

        TextView tip = new TextView(ctx);
        tip.setPadding(0, padding / 4, 0, 0);
        tip.setText("使用 !{1} !{2} 作为参数占位符");
        tip.setTextSize(12);
        tip.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                container, com.google.android.material.R.attr.colorOnSurfaceVariant));
        container.addView(tip);

        new AlertDialog.Builder(ctx)
                .setTitle("编辑执行动作")
                .setView(container)
                .setPositiveButton("确定", (d, which) -> {
                    String cmd = et.getText().toString().trim();
                    if (position >= 0 && position < actions.size()) {
                        actions.set(position, cmd);
                        rebuildActionRows();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void save() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "请输入指令名称", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> validActions = new ArrayList<>();
        for (String a : actions) {
            if (a != null && !a.trim().isEmpty()) {
                validActions.add(a.trim());
            }
        }
        if (validActions.isEmpty()) {
            Toast.makeText(requireContext(), "请至少添加一个动作", Toast.LENGTH_SHORT).show();
            return;
        }

        QuickCommandNode node = new QuickCommandNode();
        node.type = "item";
        node.name = name;
        node.actions = validActions;
        node.isCustom = true;

        if (editIndex >= 0) {
            storage.update(editIndex, node);
        } else {
            storage.add(node);
        }

        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show();
        if (onSavedListener != null) onSavedListener.onSaved();
        popBack();
    }

    private void popBack() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    }