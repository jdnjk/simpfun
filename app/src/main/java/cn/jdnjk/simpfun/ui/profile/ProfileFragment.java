package cn.jdnjk.simpfun.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.point.PointManageActivity;
import cn.jdnjk.simpfun.utils.BottomNavScrollHelper;

public class ProfileFragment extends Fragment {

    private SharedPreferences UserInfo;
    private SharedPreferences AuthInfo;
    private SwipeRefreshLayout swipeRefresh;

    private TextView tvUsername, tvUid, tvQq, tvPoint, tvDiamond;
    private TextView tvAnnouncementTitle, tvAnnouncementText;

    private Context context;
    private NestedScrollView scrollView;
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

        swipeRefresh = root.findViewById(R.id.swipe_refresh);
        scrollView = root.findViewById(R.id.scroll_profile);
        if (getActivity() instanceof MainActivity mainActivity) {
            bottomNavBinding.attach(scrollView, mainActivity::onPrimaryScroll);
        }
        tvUsername = root.findViewById(R.id.tv_username);
        tvUid = root.findViewById(R.id.tv_uid);
        tvQq = root.findViewById(R.id.tv_qq);
        tvPoint = root.findViewById(R.id.tv_point);
        tvDiamond = root.findViewById(R.id.tv_diamond);
        tvAnnouncementTitle = root.findViewById(R.id.tv_announcement_title);
        tvAnnouncementText = root.findViewById(R.id.tv_announcement_text);

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

    private void loadUserInfo() {
        String username = UserInfo.getString("username", "神秘用户");
        int uid = UserInfo.getInt("uid", -1);
        int point = UserInfo.getInt("point", 0);
        int diamond = UserInfo.getInt("diamond", 0);
        long qq = UserInfo.getLong("qq", 0);

        tvUsername.setText(username);
        tvUid.setText("UID: " + (uid == -1 ? "未知" : uid));
        tvPoint.setText(String.valueOf(point));
        tvDiamond.setText(String.valueOf(diamond));
        tvQq.setText("QQ: " + (qq == 0 ? "未绑定" : qq));

        String title = UserInfo.getString("announcement_title", "暂无公告");
        String text = UserInfo.getString("announcement_text", "");
        boolean show = UserInfo.getBoolean("announcement_show", false);

        tvAnnouncementTitle.setText(title);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            tvAnnouncementText.setText(android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT));
        } else {
            tvAnnouncementText.setText(android.text.Html.fromHtml(text));
        }

        if (show) {
            showAnnouncementDialog(title, text);
        }
    }

    private void showAnnouncementDialog(String title, String text) {
        Spanned styledText;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            styledText = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT);
        } else {
            styledText = android.text.Html.fromHtml(text);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(styledText)
                .setPositiveButton("我知道了", (dialog, which) -> {
                    markAnnouncementAsRead();
                    // Update preference to avoid showing again before next refresh
                    UserInfo.edit().putBoolean("announcement_show", false).apply();
                })
                .setCancelable(false)
                .show();
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
