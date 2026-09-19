package cn.jdnjk.simpfun.ui.setting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.Locale;

import cn.jdnjk.simpfun.R;

/**
 * 外观与终端：应用主题、终端颜色、终端字体大小与服务器总览显示。
 */
public class AppearanceTerminalFragment extends Fragment {

    private static final String[] THEME_OPTIONS = {"跟随系统", "浅色", "深色"};
    private static final String[] TERMINAL_COLOR_OPTIONS = {"跟随应用主题", "强制浅色", "强制深色"};
    private static final String CPU_LIMIT_SUBTITLE_NORMAL = "仅在新版服务器卡片中显示";
    private static final String CPU_LIMIT_SUBTITLE_DISABLED = "依赖新版服务器卡片，请先开启上一项";

    private ThemeManager themeManager;
    private TerminalThemeManager terminalThemeManager;
    private ServerCardStyleManager serverCardStyleManager;
    private OverviewDisplayManager overviewDisplayManager;
    private TerminalFontSizeManager terminalFontSizeManager;

    private TextView tvFontSizeHeader;
    private MaterialCardView optionCpuLimit;
    private MaterialSwitch switchCpuLimit;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeManager = ThemeManager.getInstance(requireContext());
        terminalThemeManager = TerminalThemeManager.getInstance(requireContext());
        serverCardStyleManager = new ServerCardStyleManager(requireContext());
        overviewDisplayManager = new OverviewDisplayManager(requireContext());
        terminalFontSizeManager = TerminalFontSizeManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_appearance, container, false);

        setupThemeDropdown(root);
        setupTerminalColorDropdown(root);
        setupFontSizeSlider(root);
        setupOverviewToggles(root);

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("外观与终端");
            activity.setHelpEnabled(false);
            activity.bindToolbarScroll(root.findViewById(R.id.scroll_appearance));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("外观与终端");
            activity.setHelpEnabled(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    private void setupThemeDropdown(View root) {
        MaterialAutoCompleteTextView actv = root.findViewById(R.id.actv_theme);
        actv.setSimpleItems(THEME_OPTIONS);
        actv.setText(THEME_OPTIONS[themeManager.getThemeMode()], false);
        // 选择后即时应用配色主题（AppCompatDelegate 触发全局重建）
        actv.setOnItemClickListener((parent, view, position, id) ->
                themeManager.setThemeMode(position));
    }

    private void setupTerminalColorDropdown(View root) {
        MaterialAutoCompleteTextView actv = root.findViewById(R.id.actv_terminal_color);
        actv.setSimpleItems(TERMINAL_COLOR_OPTIONS);
        actv.setText(TERMINAL_COLOR_OPTIONS[terminalThemeManager.getTerminalThemeMode()], false);
        actv.setOnItemClickListener((parent, view, position, id) ->
                terminalThemeManager.setTerminalThemeMode(position));
    }

    private void setupFontSizeSlider(View root) {
        tvFontSizeHeader = root.findViewById(R.id.tv_font_size_header);
        Slider slider = root.findViewById(R.id.slider_terminal_font_size);
        float size = terminalFontSizeManager.getFontSize();
        slider.setValue(Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), size)));
        updateFontSizeHeader(slider.getValue());
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                terminalFontSizeManager.setFontSize(value);
            }
            updateFontSizeHeader(value);
        });
    }

    private void updateFontSizeHeader(float value) {
        if (tvFontSizeHeader != null) {
            tvFontSizeHeader.setText(String.format(Locale.getDefault(),
                    "终端字体大小 · %.0fsp", value));
        }
    }

    private void setupOverviewToggles(View root) {
        // 使用新版服务器卡片
        View rowModern = root.findViewById(R.id.row_server_card);
        MaterialSwitch switchModern = rowModern.findViewById(R.id.switch_entry);
        bindToggleRow(rowModern, R.drawable.ic_dashboard_customize,
                "使用新版服务器卡片", "显示更清晰的服务器状态布局");
        switchModern.setChecked(serverCardStyleManager.isModernServerCardEnabled());
        switchModern.setOnCheckedChangeListener((buttonView, isChecked) -> {
            serverCardStyleManager.setModernServerCardEnabled(isChecked);
            updateCpuLimitState();
        });

        // 显示 CPU 使用率上限：关闭新版卡片时以禁用态保留并说明依赖关系
        optionCpuLimit = root.findViewById(R.id.option_cpu_limit);
        View rowCpu = root.findViewById(R.id.row_cpu_limit);
        switchCpuLimit = rowCpu.findViewById(R.id.switch_entry);
        bindToggleRow(rowCpu, R.drawable.ic_memory,
                "显示 CPU 使用率上限", CPU_LIMIT_SUBTITLE_NORMAL);
        switchCpuLimit.setChecked(serverCardStyleManager.isCpu100PercentEnabled());
        switchCpuLimit.setOnCheckedChangeListener((buttonView, isChecked) ->
                serverCardStyleManager.setCpu100PercentEnabled(isChecked));
        updateCpuLimitState();

        // 显示总览折线图
        View rowLineChart = root.findViewById(R.id.row_line_chart);
        MaterialSwitch switchLineChart = rowLineChart.findViewById(R.id.switch_entry);
        bindToggleRow(rowLineChart, R.drawable.ic_show_chart,
                "显示总览折线图", "在 CPU、内存和网速上显示趋势");
        switchLineChart.setChecked(overviewDisplayManager.isLineChartEnabled());
        switchLineChart.setOnCheckedChangeListener((buttonView, isChecked) ->
                overviewDisplayManager.setLineChartEnabled(isChecked));
    }

    private void updateCpuLimitState() {
        if (optionCpuLimit == null || switchCpuLimit == null) return;
        boolean modern = serverCardStyleManager.isModernServerCardEnabled();
        switchCpuLimit.setEnabled(modern);
        optionCpuLimit.setAlpha(modern ? 1f : 0.5f);
        TextView subtitle = optionCpuLimit.findViewById(R.id.tv_entry_subtitle);
        if (subtitle != null) {
            subtitle.setText(modern ? CPU_LIMIT_SUBTITLE_NORMAL : CPU_LIMIT_SUBTITLE_DISABLED);
        }
    }

    private void bindToggleRow(View row, int iconRes, String title, String subtitle) {
        if (row == null) return;
        SettingsRowUtils.hideLeadingIcon(row);
        ImageView icon = row.findViewById(R.id.iv_entry_icon);
        icon.setImageResource(iconRes);
        TextView tvTitle = row.findViewById(R.id.tv_entry_title);
        tvTitle.setText(title);
        TextView tvSubtitle = row.findViewById(R.id.tv_entry_subtitle);
        tvSubtitle.setText(subtitle);
    }
}
