package cn.jdnjk.simpfun.ui.setting;

import android.content.Intent;
import android.net.Uri;
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
import cn.jdnjk.simpfun.SWebView;
import cn.jdnjk.simpfun.utils.UpdateChecker;

/**
 * 关于应用：版本信息、更新日志、反馈、源码与更新检查。
 */
public class AboutFragment extends Fragment {

    private static final String CHANGELOG_URL = "https://github.com/jdnjk/simpfun/releases";
    private static final String GITHUB_URL = "https://github.com/jdnjk/simpfun";
    private static final String QQ_GROUP_KEY = "fwu-tO7d0-7xx1FucAlhA-dnYpUaNWut";

    private View rowCheckUpdate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_about, container, false);

        TextView tvVersion = root.findViewById(R.id.tv_about_version);
        tvVersion.setText(String.format("V%s.%d", BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE));

        bindEntryRow(root.findViewById(R.id.row_changelog), R.drawable.ic_history,
                "更新日志", "查看本次版本的新增功能和问题修复");
        bindEntryRow(root.findViewById(R.id.row_feedback), R.drawable.ic_profile,
                "反馈", "加入反馈群");
        bindEntryRow(root.findViewById(R.id.row_github), R.drawable.ic_code,
                "Github", "查看本应用的源码");
        bindEntryRow(root.findViewById(R.id.row_check_update), R.drawable.ic_system_update,
                "检查更新", "获取软件更新");

        rowCheckUpdate = root.findViewById(R.id.row_check_update);
        setUpdateDotVisible(false);

        root.findViewById(R.id.row_changelog).setOnClickListener(v ->
                openInBrowser(CHANGELOG_URL));
        root.findViewById(R.id.row_feedback).setOnClickListener(v -> joinQQGroup());
        root.findViewById(R.id.row_github).setOnClickListener(v ->
                openInBrowser(GITHUB_URL));
        root.findViewById(R.id.row_check_update).setOnClickListener(v -> checkUpdate());

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("关于应用");
            activity.setHelpEnabled(false);
            activity.bindToolbarScroll(root.findViewById(R.id.scroll_about));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("关于应用");
            activity.setHelpEnabled(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    private void checkUpdate() {
        if (getActivity() != null) {
            UpdateChecker.checkUpdate(getActivity());
        }
        UpdateChecker.fetchLatestUpdate(info -> {
            if (!isAdded() || getView() == null) return;
            setUpdateDotVisible(info != null && info.isNewerThanCurrent());
        });
    }

    private void setUpdateDotVisible(boolean visible) {
        if (rowCheckUpdate != null) {
            View dot = rowCheckUpdate.findViewById(R.id.dot_entry_end);
            if (dot != null) dot.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void openInBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            try {
                Intent intent = new Intent(requireContext(), SWebView.class);
                intent.putExtra("url", url);
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void joinQQGroup() {
        Intent intent = new Intent();
        intent.setData(Uri.parse(
                "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr"
                        + "%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + QQ_GROUP_KEY));
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(),
                    "未安装手Q或安装的版本不支持", android.widget.Toast.LENGTH_SHORT).show();
        }
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
