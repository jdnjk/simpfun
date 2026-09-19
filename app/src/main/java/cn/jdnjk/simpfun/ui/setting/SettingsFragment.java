package cn.jdnjk.simpfun.ui.setting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.utils.UpdateChecker;

/**
 * 设置首页：偏好设置与关于应用两组入口，具体配置分散到独立子页面。
 */
public class SettingsFragment extends Fragment {
    private static final String SP_USER_INFO = "user_info";
    private static final String SP_TOKEN = "token";

    private SharedPreferences userInfo;
    private View rowCheckUpdate;
    private boolean updateDotVisible = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userInfo = requireContext().getSharedPreferences(SP_USER_INFO, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        String username = userInfo.getString("username", getString(R.string.app_name));
        int uid = userInfo.getInt("uid", -1);

        bindEntry(root.findViewById(R.id.option_account), R.drawable.ic_user_outline,
                "账号与安全", username + "   UID: " + uid);
        bindEntry(root.findViewById(R.id.option_appearance), R.drawable.ic_palette,
                "外观与终端", "主题、字体和服务器总览");
        bindEntry(root.findViewById(R.id.option_files), R.drawable.ic_folder_material,
                "文件与传输", "双排浏览、下载位置和 SFTP");
        bindEntry(root.findViewById(R.id.option_services), R.drawable.ic_construction,
                "服务与工具", "MCP、排错和快捷指令");
        bindEntry(root.findViewById(R.id.option_check_update), R.drawable.ic_system_update,
                "检查更新", "当前版本 " + BuildConfig.VERSION_NAME);
        bindEntry(root.findViewById(R.id.option_about), R.drawable.ic_info,
                "关于应用", "查看应用信息、隐私政策和开源许可");

        rowCheckUpdate = root.findViewById(R.id.row_check_update);
        setUpdateDotVisible(updateDotVisible);

        root.findViewById(R.id.option_account).setOnClickListener(v ->
                navigateTo(new AccountSecurityFragment(), "account"));
        root.findViewById(R.id.option_appearance).setOnClickListener(v ->
                navigateTo(new AppearanceTerminalFragment(), "appearance"));
        root.findViewById(R.id.option_files).setOnClickListener(v ->
                navigateTo(new FilesTransferFragment(), "files"));
        root.findViewById(R.id.option_services).setOnClickListener(v ->
                navigateTo(new ServicesToolsFragment(), "services"));
        root.findViewById(R.id.option_about).setOnClickListener(v ->
                navigateTo(new AboutFragment(), "about"));
        root.findViewById(R.id.option_check_update).setOnClickListener(v -> checkUpdateManually());

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("设置");
            activity.setHelpEnabled(true);
            activity.bindToolbarScroll(root.findViewById(R.id.scroll_settings));
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("设置");
            activity.setHelpEnabled(true);
        }
        // 已知有新版本时不重复请求；否则静默查一次用于红点
        if (!updateDotVisible) {
            refreshUpdateDot();
        }
    }

    private void checkUpdateManually() {
        if (getActivity() != null) {
            UpdateChecker.checkUpdate(getActivity());
        }
        refreshUpdateDot();
    }

    private void refreshUpdateDot() {
        UpdateChecker.fetchLatestUpdate(info -> {
            if (!isAdded() || info == null || getView() == null) return;
            setUpdateDotVisible(info.isNewerThanCurrent());
        });
    }

    private void setUpdateDotVisible(boolean visible) {
        updateDotVisible = visible;
        if (rowCheckUpdate != null) {
            View dot = rowCheckUpdate.findViewById(R.id.dot_entry_end);
            if (dot != null) dot.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void bindEntry(View row, int iconRes, String title, String subtitle) {
        ImageView icon = row.findViewById(R.id.iv_entry_icon);
        icon.setImageResource(iconRes);
        TextView tvTitle = row.findViewById(R.id.tv_entry_title);
        tvTitle.setText(title);
        TextView tvSubtitle = row.findViewById(R.id.tv_entry_subtitle);
        tvSubtitle.setText(subtitle);
    }

    private void navigateTo(Fragment fragment, String tag) {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.navigateTo(fragment, tag);
        }
    }
}
