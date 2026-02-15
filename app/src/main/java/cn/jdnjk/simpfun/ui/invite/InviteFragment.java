package cn.jdnjk.simpfun.ui.invite;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.model.InviteData;

public class InviteFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvInviteCode, tvRegisterTimes, tvRegisterVerifyTimes, tvTotalIncome, tvProIncome;
    private Button btnCopyCode, btnShareLink;
    private String inviteCode = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_invite, container, false);

        swipeRefresh = root.findViewById(R.id.swipe_refresh);
        tvInviteCode = root.findViewById(R.id.tv_invite_code);
        tvRegisterTimes = root.findViewById(R.id.tv_register_times);
        tvRegisterVerifyTimes = root.findViewById(R.id.tv_register_verify_times);
        tvTotalIncome = root.findViewById(R.id.tv_total_income);
        tvProIncome = root.findViewById(R.id.tv_pro_income);
        btnCopyCode = root.findViewById(R.id.btn_copy_code);
        btnShareLink = root.findViewById(R.id.btn_share_link);

        swipeRefresh.setOnRefreshListener(this::loadInviteData);

        btnCopyCode.setOnClickListener(v -> {
            if (!inviteCode.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Invite Code", inviteCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "推荐码已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });

        btnShareLink.setOnClickListener(v -> {
            if (!inviteCode.isEmpty()) {
                String shareUrl = String.format("https://simpfun.cn/auth?type=register&code=%s", inviteCode);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "我在 Simpfun 发现了好玩的东西，快来和我一起玩吧！注册时填写我的推荐码：" + inviteCode + "，或者直接点击链接注册：" + shareUrl);
                startActivity(Intent.createChooser(shareIntent, "分享邀请链接"));
            }
        });

        loadInviteData();

        return root;
    }

    private void loadInviteData() {
        swipeRefresh.setRefreshing(true);
        SharedPreferences authInfo = requireContext().getSharedPreferences("token", Context.MODE_PRIVATE);
        String token = authInfo.getString("token", null);

        if (token == null) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        UserApi userApi = new UserApi(requireContext());
        userApi.getInviteData(token, new UserApi.InviteCallback() {
            @Override
            public void onSuccess(InviteData data) {
                if (isAdded()) {
                    inviteCode = data.getInviteCode();
                    tvInviteCode.setText(inviteCode);
                    tvRegisterTimes.setText(String.valueOf(data.getRegisterTimes()));
                    tvRegisterVerifyTimes.setText(String.valueOf(data.getRegisterVerifyTimes()));
                    tvTotalIncome.setText(String.valueOf(data.getRegisterTotalIncome()));
                    tvProIncome.setText(String.valueOf(data.getRegisterTotalIncomeFromPro()));
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onFailure(String message) {
                if (isAdded()) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    swipeRefresh.setRefreshing(false);
                }
            }
        });
    }
}
