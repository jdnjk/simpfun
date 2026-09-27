package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.dev.DevMirrorDetailActivity;

public class DevSettingsFragment extends Fragment {

    private SharedPreferences devSettings;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        devSettings = requireContext().getSharedPreferences(DevMirrorDetailActivity.SP_DEV_SETTINGS, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dev_settings, container, false);

        View row = root.findViewById(R.id.row_prefill_version);
        MaterialSwitch switchPrefill = row.findViewById(R.id.switch_entry);
        bindToggleRow(row, R.drawable.ic_code,
                "自动载入版本配置",
                "更新版本时默认载入其他版本的建议配置、一键指令和一键配置");
        switchPrefill.setChecked(devSettings.getBoolean(DevMirrorDetailActivity.KEY_PREFILL_VERSION_INFO, true));
        switchPrefill.setOnCheckedChangeListener((buttonView, isChecked) ->
                devSettings.edit()
                        .putBoolean(DevMirrorDetailActivity.KEY_PREFILL_VERSION_INFO, isChecked)
                        .apply());

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("开发者");
            activity.setHelpEnabled(true);
            activity.bindToolbarScroll(root.findViewById(R.id.scroll_dev_settings));
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("开发者");
            activity.setHelpEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    private void bindToggleRow(View row, int iconRes, String title, String subtitle) {
        SettingsRowUtils.hideLeadingIcon(row);
        android.widget.ImageView icon = row.findViewById(R.id.iv_entry_icon);
        icon.setImageResource(iconRes);
        android.widget.TextView tvTitle = row.findViewById(R.id.tv_entry_title);
        tvTitle.setText(title);
        android.widget.TextView tvSubtitle = row.findViewById(R.id.tv_entry_subtitle);
        tvSubtitle.setText(subtitle);
    }
}
