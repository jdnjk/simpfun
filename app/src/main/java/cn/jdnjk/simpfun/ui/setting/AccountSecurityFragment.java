package cn.jdnjk.simpfun.ui.setting;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import cn.jdnjk.simpfun.ui.setting.QqBindFragment;
import cn.jdnjk.simpfun.utils.PageDataStore;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;

/**
 * 账户与安全：登录身份概览、网站登录、QQ 绑定与退出登录。
 */
public class AccountSecurityFragment extends Fragment {
    private static final String SP_TOKEN = "token";
    private static final String SP_USER_INFO = "user_info";
    private static final String SP_SERVER_DATA = "server_data";
    private static final String SP_DEVICE_ID = "deviceid";
    private static final String KEY_TOKEN = "token";

    private SharedPreferences authInfo;
    private SharedPreferences userInfo;

    private TextView tvUsername;
    private TextView tvMeta;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authInfo = requireContext().getSharedPreferences(SP_TOKEN, 0);
        userInfo = requireContext().getSharedPreferences(SP_USER_INFO, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_account_security, container, false);

        tvUsername = root.findViewById(R.id.tv_account_username);
        tvMeta = root.findViewById(R.id.tv_account_meta);
        loadAccountOverview();

        // 账户管理
        View rowLoginBrowser = root.findViewById(R.id.row_login_browser);
        bindEntryRow(rowLoginBrowser, R.drawable.ic_open_in_new,
                "登录到 简幻欢", "使用该账户登录到官网");
        ImageView endIcon = rowLoginBrowser.findViewById(R.id.iv_entry_end);
        if (endIcon != null) {
            endIcon.setImageResource(R.drawable.ic_open_in_new);
        }
        rowLoginBrowser.setOnClickListener(v -> openBrowserLogin());

        View rowBindQq = root.findViewById(R.id.row_bind_qq);
        long qq = userInfo.getLong("qq", 0);
        bindEntryRow(rowBindQq, R.drawable.ic_chat,
                "绑定 QQ", qq == 0 ? "未绑定 · 管理 QQ 群和验证方式" : "已绑定 · 管理 QQ 群和验证方式");
        rowBindQq.setOnClickListener(v -> openQqBindPage());

        // 安全操作
        root.findViewById(R.id.btn_logout).setOnClickListener(v -> showLogoutDialog());

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("账户与安全");
            activity.setHelpEnabled(false);
            activity.bindToolbarScroll(root.findViewById(R.id.scroll_account));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("账户与安全");
            activity.setHelpEnabled(false);
        }
        loadAccountOverview();
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    /** 账户概览卡：仅展示用户名、UID、Pro 状态、积分与钻石。 */
    private void loadAccountOverview() {
        if (tvUsername == null || tvMeta == null) return;
        String username = userInfo.getString("username", "未登录");
        int uid = userInfo.getInt("uid", -1);
        boolean isPro = userInfo.getBoolean("pro", false) && userInfo.getBoolean("pro_valid", false);
        int points = userInfo.getInt("point", 0);
        int diamonds = userInfo.getInt("diamond", 0);
        tvUsername.setText(username);
        tvMeta.setText(String.format(java.util.Locale.getDefault(),
                "UID: %d\nPro：%s\n积分：%d · 钻石：%d",
                uid, isPro ? "已开通" : "未开通", points, diamonds));
    }

    /** 使用当前账户 Token 打开网站并完成快速登录。 */
    private void openBrowserLogin() {
        String token = authInfo.getString(KEY_TOKEN, "");
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://simpfun.cn/auth?autologin=" + token)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void openQqBindPage() {
        if (getActivity() == null) return;
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.fragment_container, new QqBindFragment())
                .addToBackStack("qq_bind")
                .commit();
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账户吗？退出后会取消所有设备的登录状态")
                .setPositiveButton("退出", (dialog, which) -> {
                    clearAccountData();
                    Intent intent = new Intent(requireContext(), AuthActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearAccountData() {
        requireContext().getSharedPreferences(SP_TOKEN, 0).edit().remove(KEY_TOKEN).apply();
        requireContext().getSharedPreferences(SP_USER_INFO, 0).edit().clear().apply();
        requireContext().getSharedPreferences(SP_SERVER_DATA, 0).edit().clear().apply();
        requireContext().getSharedPreferences(SP_DEVICE_ID, 0).edit().clear().apply();
        PageDataStore.getInstance().clearAll();
        InstanceDetailStore.getInstance().clearAll();
    }

    private void bindEntryRow(View row, int iconRes, String title, String subtitle) {
        SettingsRowUtils.hideLeadingIcon(row);
        ImageView icon = row.findViewById(R.id.iv_entry_icon);
        icon.setImageResource(iconRes);
        TextView tvTitle = row.findViewById(R.id.tv_entry_title);
        tvTitle.setText(title);
        TextView tvSubtitle = row.findViewById(R.id.tv_entry_subtitle);
        tvSubtitle.setText(subtitle);
    }
}
