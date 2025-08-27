package cn.jdnjk.simpfun.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import cn.jdnjk.simpfun.BuyPointActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;
import cn.jdnjk.simpfun.ui.setting.SettingsActivity;
import com.tencent.bugly.crashreport.CrashReport;

public class ProfileFragment extends Fragment {

    private SharedPreferences UserInfo;
    private SharedPreferences AuthInfo;
    private SwipeRefreshLayout swipeRefresh;

    private TextView tvUsername, tvUid, tvQq, tvPoint, tvDiamond;
    private TextView tvAnnouncementTitle, tvAnnouncementText;

    private Context context;

    @Override
    public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        this.context = ctx;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);

        UserInfo = requireContext().getSharedPreferences("user_info", 0);
        AuthInfo = requireContext().getSharedPreferences("token", 0);

        swipeRefresh = root.findViewById(R.id.swipe_refresh);
        tvUsername = root.findViewById(R.id.tv_username);
        tvUid = root.findViewById(R.id.tv_uid);
        tvQq = root.findViewById(R.id.tv_qq);
        tvPoint = root.findViewById(R.id.tv_point);
        tvDiamond = root.findViewById(R.id.tv_diamond);
        tvAnnouncementTitle = root.findViewById(R.id.tv_announcement_title);
        tvAnnouncementText = root.findViewById(R.id.tv_announcement_text);
        Button btnSettings = root.findViewById(R.id.btn_settings);

        loadUserInfo();

        swipeRefresh.setOnRefreshListener(this::refreshUserInfo);

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SettingsActivity.class);
            startActivity(intent);
            //CrashReport.testJavaCrash();
        });
        root.findViewById(R.id.layout_click_point).setOnClickListener(v -> {
            Intent intent = new Intent(context, BuyPointActivity.class);
            startActivity(intent);
        });

        return root;
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

        tvAnnouncementTitle.setText(title);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            tvAnnouncementText.setText(android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT));
        } else {
            tvAnnouncementText.setText(android.text.Html.fromHtml(text));
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
        new Handler().postDelayed(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    loadUserInfo();
                    stopRefresh();
                });
            }
        }, 2000);

        new Handler().postDelayed(this::stopRefresh, 10000);
    }

    public void stopRefresh() {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void navigateToLogin() {
         Intent intent = new Intent(context, AuthActivity.class);
         intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
         context.startActivity(intent);
    }
}