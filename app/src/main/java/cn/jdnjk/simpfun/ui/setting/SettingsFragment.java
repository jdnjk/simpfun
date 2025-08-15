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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;

public class SettingsFragment extends Fragment {

    private SharedPreferences sp;
    private SharedPreferences updateSp;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = requireContext().getSharedPreferences("token", 0);
        updateSp = requireContext().getSharedPreferences("settings", 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        SharedPreferences sp = requireContext().getSharedPreferences("token", 0);
        SharedPreferences updateSp = requireContext().getSharedPreferences("settings", 0);

        View optionUpdateSource = root.findViewById(R.id.option_update_source);
        TextView tvUpdateSourceTitle = optionUpdateSource.findViewById(R.id.tv_title);
        TextView tvUpdateSourceSub = optionUpdateSource.findViewById(R.id.tv_subtitle);

        tvUpdateSourceTitle.setText("更新源");

        String currentSource = updateSp.getString("update_channel", "china");
        tvUpdateSourceSub.setText(currentSource.equals("github") ? "GitHub 源" : "国内源");

        optionUpdateSource.setOnClickListener(v -> showUpdateSourceDialog(tvUpdateSourceSub));

        View optionCheckUpdate = root.findViewById(R.id.option_check_update);
        TextView tvCheckUpdate = optionCheckUpdate.findViewById(R.id.tv_title);
        tvCheckUpdate.setText("检查更新");
        optionCheckUpdate.findViewById(R.id.iv_arrow).setVisibility(View.VISIBLE);
        optionCheckUpdate.findViewById(R.id.tv_subtitle).setVisibility(View.GONE);

        optionCheckUpdate.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show();
            // TODO: 实现检查更新逻辑
        });

        View optionLoginBrowser = root.findViewById(R.id.option_login_browser);
        TextView tvLoginBrowser = optionLoginBrowser.findViewById(R.id.tv_title);
        tvLoginBrowser.setText("在浏览器中登录");
        optionLoginBrowser.findViewById(R.id.iv_arrow).setVisibility(View.VISIBLE);
        optionLoginBrowser.findViewById(R.id.tv_subtitle).setVisibility(View.GONE);

        optionLoginBrowser.setOnClickListener(v -> {
            String token = sp.getString("token", "");
            if (token.isEmpty()) {
                Toast.makeText(requireContext(), "未登录", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = "https://simpfun.cn/auth?autologin=" + token;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });

        View optionLogout = root.findViewById(R.id.option_logout);
        TextView tvLogout = optionLogout.findViewById(R.id.tv_title);
        tvLogout.setText("退出登录");
        optionLogout.findViewById(R.id.iv_arrow).setVisibility(View.GONE);
        optionLogout.findViewById(R.id.tv_subtitle).setVisibility(View.GONE);

        optionLogout.setOnClickListener(v -> {
            sp.edit().remove("token").apply();
            Intent intent = new Intent(requireContext(), AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return root;
    }
    private void showUpdateSourceDialog(TextView tvUpdateSourceSub) {
        String[] sources = {"GitHub 源", "国内源"};
        String[] values = {"github", "china"};
        String current = updateSp.getString("update_channel", "china");
        int checkedItem = current.equals("china") ? 1 : 0;

        new AlertDialog.Builder(requireContext())
                .setTitle("选择更新源")
                .setSingleChoiceItems(sources, checkedItem, null)
                .setPositiveButton("确定", (dialog, which) -> {
                    int selectedPos = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    String selectedValue = values[selectedPos];
                    // 保存
                    updateSp.edit().putString("update_channel", selectedValue).apply();
                    // 更新 UI
                    tvUpdateSourceSub.setText(sources[selectedPos]);
                    Toast.makeText(requireContext(), "已切换到：" + sources[selectedPos], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}