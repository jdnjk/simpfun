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

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.auth.AuthActivity;

import java.io.File;

public class SettingsFragment extends Fragment {

    private SharedPreferences sp;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = requireContext().getSharedPreferences("token", 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        SharedPreferences sp = requireContext().getSharedPreferences("token", 0);

        View optionCheckUpdate = root.findViewById(R.id.option_check_update);
        TextView tvCheckUpdate = optionCheckUpdate.findViewById(R.id.tv_title);
        tvCheckUpdate.setText("检查更新");
        optionCheckUpdate.findViewById(R.id.iv_arrow).setVisibility(View.VISIBLE);
        TextView tvCheckUpdateSubtitle = optionCheckUpdate.findViewById(R.id.tv_subtitle);
        String currentVersion = BuildConfig.VERSION_NAME;
        tvCheckUpdateSubtitle.setVisibility(View.VISIBLE);
        tvCheckUpdateSubtitle.setText("当前版本：" + currentVersion);

        optionCheckUpdate.setOnClickListener(v -> {
            final UpdateHelper[] helperRef = new UpdateHelper[1];

            UpdateHelper.UpdateListener listener = new UpdateHelper.UpdateListener() {
                @Override
                public void onChecking() {
                    Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onNoUpdate() {
                    Toast.makeText(requireContext(), "当前已是最新版本", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onFoundNewVersion(String version, String downloadUrl) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("发现新版本")
                            .setMessage("检测到新版本：" + version + "\n\n是否下载更新？\n\n文件将保存到缓存目录。")
                            .setPositiveButton("立即下载", (d, w) -> {
                                if (helperRef[0] != null) {
                                    helperRef[0].downloadApk(downloadUrl);
                                }
                            })
                            .setNegativeButton("稍后再说", null)
                            .show();
                }
                @Override
                public void onDownloadStart() {
                    Toast.makeText(requireContext(), "开始下载...", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onDownloadSuccess(File apkFile) {
                    Toast.makeText(requireContext(), "下载完成，准备安装", Toast.LENGTH_SHORT).show();
                    UpdateHelper.installApk(requireContext(), apkFile);
                }
                @Override
                public void onDownloadError(String error) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                }
            };
            helperRef[0] = new UpdateHelper(requireContext(), listener);

            String updateChannel = "github";
            helperRef[0].checkForUpdate(currentVersion, updateChannel);
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
}