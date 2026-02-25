package cn.jdnjk.simpfun.ui.setting;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.MainApi;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import org.json.JSONObject;

public class SettingsFragment extends Fragment {

    private SharedPreferences sp;
    private SharedPreferences userInfo;
    private ThemeManager themeManager;
    private TerminalThemeManager terminalThemeManager;
    private TextView tvThemeCurrent;
    private TextView tvTerminalThemeCurrent;
    private TextView tvQqCurrent;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = requireContext().getSharedPreferences("token", 0);
        userInfo = requireContext().getSharedPreferences("user_info", 0);
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
        loadUserInfo();

        return root;
    }

    private void initViews(View root) {
        tvThemeCurrent = root.findViewById(R.id.tv_theme_current);
        tvTerminalThemeCurrent = root.findViewById(R.id.tv_terminal_theme_current);
        tvQqCurrent = root.findViewById(R.id.tv_qq_current);

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
        root.findViewById(R.id.option_bind_qq).setOnClickListener(v -> showBindQQDialog());
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

    private void loadUserInfo() {
        if (userInfo != null && tvQqCurrent != null) {
            long qq = userInfo.getLong("qq", 0);
            tvQqCurrent.setText(qq == 0 ? "未绑定" : String.valueOf(qq));
        }
    }

    private void showBindQQDialog() {
        final EditText editText = new EditText(requireContext());
        long currentQq = userInfo.getLong("qq", 0);
        if (currentQq != 0) {
            editText.setText(String.valueOf(currentQq));
        }
        editText.setHint("请输入 QQ 号码");

        // Add padding to the EditText
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(padding * 2, padding, padding * 2, padding);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("绑定 QQ 号")
                .setView(editText)
                .setPositiveButton("确定", (dialog, which) -> {
                    String qqStr = editText.getText().toString().trim();
                    if (qqStr.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入 QQ 号码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        long qq = Long.parseLong(qqStr);
                        bindQQ(qq);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "请输入有效的 QQ 号码", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void bindQQ(long qq) {
        String token = sp.getString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        new MainApi(requireContext()).bindQQ(token, qq, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), "绑定成功", Toast.LENGTH_SHORT).show();
                // 更新本地缓存
                userInfo.edit().putLong("qq", qq).apply();
                loadUserInfo();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "绑定失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}