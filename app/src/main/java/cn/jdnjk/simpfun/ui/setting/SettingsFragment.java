package cn.jdnjk.simpfun.ui.setting;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;

public class SettingsFragment extends Fragment {

    private SharedPreferences sp;
    private ThemeManager themeManager;
    private TerminalThemeManager terminalThemeManager;
    private TextView tvThemeCurrent;
    private TextView tvTerminalThemeCurrent;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = requireContext().getSharedPreferences("token", 0);
        themeManager = ThemeManager.getInstance(requireContext());
        terminalThemeManager = TerminalThemeManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        initViews(root);
        setupClickListeners(root);
        updateThemeDisplay();

        return root;
    }

    private void initViews(View root) {
        tvThemeCurrent = root.findViewById(R.id.tv_theme_current);
        tvTerminalThemeCurrent = root.findViewById(R.id.tv_terminal_theme_current);

        TextView tvVersion = root.findViewById(R.id.tv_version);
        String currentVersion = BuildConfig.VERSION_NAME;
        tvVersion.setText("当前版本：" + currentVersion);
    }

    private void setupClickListeners(View root) {
        root.findViewById(R.id.option_theme).setOnClickListener(v -> showThemeDialog());
        root.findViewById(R.id.option_terminal_theme).setOnClickListener(v -> showTerminalThemeDialog());
        root.findViewById(R.id.option_tutorial).setOnClickListener(v -> openTutorialDocumentation());
        root.findViewById(R.id.option_login_browser).setOnClickListener(v -> openBrowserLogin());
        root.findViewById(R.id.option_logout).setOnClickListener(v -> showLogoutDialog());
    }

    private void updateThemeDisplay() {
        int currentTheme = themeManager.getThemeMode();
        tvThemeCurrent.setText(themeManager.getThemeName(currentTheme));

        int currentTerminalTheme = terminalThemeManager.getTerminalThemeMode();
        tvTerminalThemeCurrent.setText(terminalThemeManager.getTerminalThemeName(currentTerminalTheme));
    }

    private void showThemeDialog() {
        String[] themeOptions = {"跟随系统", "浅色模式", "深色模式"};
        int currentTheme = themeManager.getThemeMode();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择主题")
                .setSingleChoiceItems(themeOptions, currentTheme, (dialog, which) -> {
                    themeManager.setThemeMode(which);
                    updateThemeDisplay();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTerminalThemeDialog() {
        String[] terminalThemeOptions = {"跟随主题", "强制浅色", "强制深色"};
        int currentTerminalTheme = terminalThemeManager.getTerminalThemeMode();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择终端颜色")
                .setSingleChoiceItems(terminalThemeOptions, currentTerminalTheme, (dialog, which) -> {
                    terminalThemeManager.setTerminalThemeMode(which);
                    updateThemeDisplay();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openBrowserLogin() {
        String token = sp.getString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = "https://simpfun.cn/auth?autologin=" + token;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /**
     * 打开教程文档
     */
    private void openTutorialDocumentation() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://www.yuque.com/simpfox/simpdoc/main"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开教程文档", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账户吗？")
                .setPositiveButton("退出", (dialog, which) -> {
                    sp.edit().remove("token").apply();
                    Intent intent = new Intent(requireContext(), AuthActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}