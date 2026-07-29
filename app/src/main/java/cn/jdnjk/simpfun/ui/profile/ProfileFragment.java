package cn.jdnjk.simpfun.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.point.PointManageActivity;
import cn.jdnjk.simpfun.ui.setting.SettingsActivity;
import cn.jdnjk.simpfun.ui.setting.ThemeManager;
import cn.jdnjk.simpfun.utils.BottomNavScrollHelper;

public class ProfileFragment extends Fragment {

    private SharedPreferences UserInfo;
    private SharedPreferences AuthInfo;
    private SwipeRefreshLayout swipeRefresh;

    private TextView tvUsername;
    private TextView tvUserBadge;
    private TextView tvUidQq;
    private TextView tvPoint;
    private TextView tvDiamond;
    private TextView tvAnnouncementTitle;
    private TextView tvAnnouncementText;
    private ImageView ivThemeMode;

    private Context context;
    private NestedScrollView scrollView;
    private ThemeManager themeManager;
    private final BottomNavScrollHelper.Binding bottomNavBinding = new BottomNavScrollHelper.Binding();

    @Override
    public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        this.context = ctx;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);

        UserInfo = requireContext().getSharedPreferences("user_info", 0);
        AuthInfo = requireContext().getSharedPreferences("token", 0);
        themeManager = ThemeManager.getInstance(requireContext());

        swipeRefresh = root.findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeResources(
                R.color.md_theme_primary,
                R.color.md_theme_secondary);
        scrollView = root.findViewById(R.id.scroll_profile);
        if (getActivity() instanceof MainActivity mainActivity) {
            bottomNavBinding.attach(scrollView, mainActivity::onPrimaryScroll);
        }

        tvUsername = root.findViewById(R.id.tv_username);
        tvUserBadge = root.findViewById(R.id.tv_user_badge);
        tvUidQq = root.findViewById(R.id.tv_uid_qq);
        tvPoint = root.findViewById(R.id.tv_point);
        tvDiamond = root.findViewById(R.id.tv_diamond);
        tvAnnouncementTitle = root.findViewById(R.id.tv_announcement_title);
        tvAnnouncementText = root.findViewById(R.id.tv_announcement_text);
        ivThemeMode = root.findViewById(R.id.iv_theme_mode);

        root.findViewById(R.id.iv_theme_mode).setOnClickListener(v -> cycleThemeMode());
        root.findViewById(R.id.iv_settings).setOnClickListener(v ->
                startActivity(new Intent(context, SettingsActivity.class)));

        loadUserInfo();

        swipeRefresh.setOnRefreshListener(this::refreshUserInfo);

        root.findViewById(R.id.layout_click_point).setOnClickListener(v -> {
            Intent intent = new Intent(context, PointManageActivity.class);
            intent.putExtra(PointManageActivity.EXTRA_TAB, PointManageActivity.TAB_POINTS);
            startActivity(intent);
        });

        View diamondEntry = root.findViewById(R.id.layout_click_diamond);
        if (diamondEntry != null) {
            diamondEntry.setOnClickListener(v -> {
                Intent intent = new Intent(context, PointManageActivity.class);
                intent.putExtra(PointManageActivity.EXTRA_TAB, PointManageActivity.TAB_DIAMONDS);
                startActivity(intent);
            });
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        bottomNavBinding.detach(scrollView);
        scrollView = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) {
            updateThemeModeDisplay();
        }
    }

    private void loadUserInfo() {
        String username = UserInfo.getString("username", "神秘用户");
        int uid = UserInfo.getInt("uid", -1);
        int point = UserInfo.getInt("point", 0);
        int diamond = UserInfo.getInt("diamond", 0);
        long qq = UserInfo.getLong("qq", 0);
        boolean verified = UserInfo.getBoolean("verified", true);
        boolean isPro = UserInfo.getBoolean("pro", false);
        boolean proValid = UserInfo.getBoolean("pro_valid", false);

        tvUsername.setText(username);
        tvUidQq.setText("UID: " + (uid == -1 ? "未知" : uid) + "  QQ: " + (qq == 0 ? "未绑定" : qq));
        tvPoint.setText(String.valueOf(point));
        tvDiamond.setText(String.valueOf(diamond));

        updateUserBadge(verified, isPro, proValid);
        updateThemeModeDisplay();

        String title = UserInfo.getString("announcement_title", "暂无公告");
        String text = UserInfo.getString("announcement_text", "");
        if (TextUtils.isEmpty(text)) {
            text = "暂无内容";
        }
        boolean show = UserInfo.getBoolean("announcement_show", false);

        tvAnnouncementTitle.setText(title);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            tvAnnouncementText.setText(android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT));
        } else {
            tvAnnouncementText.setText(android.text.Html.fromHtml(text));
        }
        tvAnnouncementText.setMovementMethod(LinkMovementMethod.getInstance());
        tvAnnouncementText.setLinksClickable(true);
        tvAnnouncementText.setTextIsSelectable(false);

        if (show) {
            showAnnouncementDialog(title, text);
        }
    }

    private void updateUserBadge(boolean verified, boolean isPro, boolean proValid) {
        if (!verified) {
            tvUserBadge.setVisibility(View.VISIBLE);
            tvUserBadge.setText("未认证");
            tvUserBadge.setBackgroundResource(R.drawable.bg_profile_badge_neutral);
            return;
        }
        if (isPro) {
            tvUserBadge.setVisibility(View.VISIBLE);
            tvUserBadge.setText(proValid ? "Pro+" : "Pro");
            tvUserBadge.setBackgroundResource(R.drawable.bg_profile_badge_primary);
            return;
        }
        tvUserBadge.setVisibility(View.GONE);
    }

    private void cycleThemeMode() {
        int currentMode = themeManager.getThemeMode();
        int nextMode;
        if (currentMode == ThemeManager.THEME_SYSTEM) {
            nextMode = ThemeManager.THEME_LIGHT;
        } else if (currentMode == ThemeManager.THEME_LIGHT) {
            nextMode = ThemeManager.THEME_DARK;
        } else {
            nextMode = ThemeManager.THEME_SYSTEM;
        }
        themeManager.setThemeMode(nextMode);
        updateThemeModeDisplay();
    }

    private void updateThemeModeDisplay() {
        int mode = themeManager.getThemeMode();
        if (mode == ThemeManager.THEME_LIGHT) {
            ivThemeMode.setImageResource(R.drawable.ic_theme_light);
            ivThemeMode.setContentDescription("保持亮色");
        } else if (mode == ThemeManager.THEME_DARK) {
            ivThemeMode.setImageResource(R.drawable.ic_theme_dark);
            ivThemeMode.setContentDescription("保持暗色");
        } else {
            ivThemeMode.setImageResource(R.drawable.ic_theme_auto);
            ivThemeMode.setContentDescription("跟随系统");
        }
    }

    private void showAnnouncementDialog(String title, String text) {
        Spanned styledText;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            styledText = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT);
        } else {
            styledText = android.text.Html.fromHtml(text);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(styledText)
                .setPositiveButton("我知道了", (dialogInterface, which) -> {
                    markAnnouncementAsRead();
                    UserInfo.edit().putBoolean("announcement_show", false).apply();
                })
                .setCancelable(false)
                .show();
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setLinksClickable(true);
            messageView.setTextIsSelectable(false);
        }
    }

    private void markAnnouncementAsRead() {
        String token = AuthInfo.getString("token", null);
        if (token != null) {
            new UserApi(context).readAnnouncement(token);
        }
    }

    private void refreshUserInfo() {
        String token = AuthInfo.getString("token", null);
        if (token == null || token.isEmpty()) {
            Toast.makeText(context, "未登录，请重新登录", Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        UserApi userApi = new UserApi(context);
        userApi.UserInfo(token);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    loadUserInfo();
                    stopRefresh();
                });
            }
        }, 2000);

        new Handler(Looper.getMainLooper()).postDelayed(this::stopRefresh, 10000);
    }

    public void stopRefresh() {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(false);
        }
    }
}
