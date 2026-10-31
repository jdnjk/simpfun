package cn.jdnjk.simpfun.ui.ins.term;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.LogShareApi;
import cn.jdnjk.simpfun.utils.ClipboardUtils;

public final class LogShareDialogFragment extends DialogFragment {
    private static final String TAG = "terminal_log_share";
    private String initialContent;
    private LogShareViewModel model;
    private View contentView;
    private Spinner sites;
    private CheckBox includeInfo;

    public static void open(FragmentManager manager, int deviceId, String content) {
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        LogShareDialogFragment dialog = new LogShareDialogFragment();
        Bundle args = new Bundle();
        args.putInt("device_id", deviceId);
        dialog.setArguments(args);
        // 不把可能达到 10 MiB 的日志塞进 Fragment arguments / saved state。
        dialog.initialContent = content;
        dialog.showNow(manager, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(LogShareViewModel.class);
        model.deviceId = requireArguments().getInt("device_id");
        if (model.content == null) model.content = initialContent;
        initialContent = null;
        model.changes.observe(this, ignored -> render());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle state) {
        contentView = getLayoutInflater().inflate(R.layout.dialog_log_share, null);
        sites = contentView.findViewById(R.id.log_share_site);
        includeInfo = contentView.findViewById(R.id.log_share_include_info);
        LogShareApi.Site[] values = LogShareApi.Site.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i].displayName;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sites.setAdapter(adapter);
        sites.setSelection(model.site.ordinal());
        sites.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!model.busy && model.sharedLog == null) model.site = values[position];
                render();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        includeInfo.setChecked(model.includeInfo);
        includeInfo.setOnCheckedChangeListener((button, checked) -> model.setIncludeInfo(checked, requireContext()));
        contentView.findViewById(R.id.log_share_url).setOnClickListener(v -> {
            if (model.sharedLog != null) ClipboardUtils.copyPlainText(getContext(), "分享链接", model.sharedLog.url, "已复制分享链接");
        });
        contentView.findViewById(R.id.log_share_raw).setOnClickListener(v -> {
            if (model.sharedLog != null) ClipboardUtils.copyPlainText(getContext(), "原始日志", model.sharedLog.raw, "已复制原始日志链接");
        });
        return new AlertDialog.Builder(requireContext()).setTitle("分享日志")
                .setView(contentView).setPositiveButton("开始分享", null).setNegativeButton("取消", null).create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (model.sharedLog != null || model.content == null) dismiss();
            else model.upload();
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            if (model.sharedLog != null) model.delete();
            else dismiss();
        });
        render();
    }

    private void render() {
        if (contentView == null || !(getDialog() instanceof AlertDialog)) return;
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) == null) return;
        if (model.deleted) {
            Toast.makeText(requireContext(), "已取消分享，站点日志已删除", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }
        boolean shared = model.sharedLog != null;
        boolean expired = model.content == null;
        contentView.findViewById(R.id.log_share_options).setVisibility(shared || expired ? View.GONE : View.VISIBLE);
        contentView.findViewById(R.id.log_share_result).setVisibility(shared ? View.VISIBLE : View.GONE);
        sites.setEnabled(!model.busy);
        includeInfo.setEnabled(!model.busy);
        ((TextView) contentView.findViewById(R.id.log_share_limit)).setText(
                "最多 10 MiB / " + model.site.maxLines + " 行；超出时仅分享最新部分。");
        if (shared) {
            ((TextView) contentView.findViewById(R.id.log_share_url)).setText(model.sharedLog.url);
            ((TextView) contentView.findViewById(R.id.log_share_raw)).setText(model.sharedLog.raw);
            ((TextView) contentView.findViewById(R.id.log_share_summary)).setText(
                    "已分享到 " + model.sharedLog.site.displayName + "，共 " + model.preparedLog.lines + " 行"
                            + (model.preparedLog.truncated ? "（已按站点上限截取最新日志）" : ""));
        }
        String status = expired ? "日志快照已失效，请关闭窗口后重新打开分享。"
                : !model.error.isEmpty() ? model.error
                : model.busy ? model.progress
                : model.includeInfo && model.loadingInfo ? "正在获取实例信息…" : "";
        TextView statusView = contentView.findViewById(R.id.log_share_status);
        statusView.setText(status);
        statusView.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        contentView.findViewById(R.id.log_share_progress).setVisibility(
                model.busy || (model.includeInfo && model.loadingInfo) ? View.VISIBLE : View.GONE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(shared || expired ? "完成" : "开始分享");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!model.busy && (shared || expired || model.canUpload()));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(shared ? "取消分享" : "取消");
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!model.busy);
        // 已发布时必须显式选择保留或删除，避免误触关闭后丢失删除凭据。
        setCancelable(!model.busy && !shared);
        dialog.setCanceledOnTouchOutside(!model.busy && !shared);
    }

    @Override
    public void onDestroyView() {
        contentView = null;
        sites = null;
        includeInfo = null;
        super.onDestroyView();
    }
}
