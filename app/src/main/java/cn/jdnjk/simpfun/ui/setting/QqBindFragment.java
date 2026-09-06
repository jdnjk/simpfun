package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.utils.ClipboardUtils;

/**
 * 「QQ 绑定与交流群」子页面。
 *
 * <p>从设置页「绑定 QQ 号」进入：上半区输入 QQ 号完成绑定 / 改绑；一旦已绑定（本地
 * user_info 里 qq 非 0）即在下方显示交流群列表——点群号用 mqq 协议加群，长按复制群号。
 */
public class QqBindFragment extends Fragment {

    private static final String SP_TOKEN = "token";
    private static final String SP_USER_INFO = "user_info";

    private static final String QQ_GROUP_JOIN_SCHEME =
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=%s&card_type=group&source=qrcode";

    private static final class QqGroup {
        final String section;      // 分组标题
        final String groupName;    // 群名
        final String number;       // 群号

        QqGroup(String section, String groupName, String number) {
            this.section = section;
            this.groupName = groupName;
            this.number = number;
        }
    }

    private static final List<QqGroup> QQ_GROUPS = new ArrayList<>();

    static {
        QQ_GROUPS.add(new QqGroup("综合交流QQ群（活跃）", "1群", "710735670"));
        QQ_GROUPS.add(new QqGroup(null, "10群", "874338645"));
        QQ_GROUPS.add(new QqGroup("第三方镜像交流群", "2群", "1020961156"));
        QQ_GROUPS.add(new QqGroup("问题答疑QQ群", null, "465468467"));
    }

    private TextInputEditText qqInput;
    private TextView bindHint;
    private MaterialButton btnBind;
    private LinearLayout groupsContainer;
    private LinearLayout groupsList;

    private SharedPreferences spToken;
    private SharedPreferences userInfo;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();
        spToken = context.getSharedPreferences(SP_TOKEN, Context.MODE_PRIVATE);
        userInfo = context.getSharedPreferences(SP_USER_INFO, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_qq_bind, container, false);

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("QQ 绑定与交流群");
            // 顶栏问号本页指向「加入 QQ 群」帮助文档，离开时复原。
            activity.overrideHelpUrl(SettingsActivity.QQ_GROUP_HELP_URL);
        }

        qqInput = root.findViewById(R.id.qq_input);
        bindHint = root.findViewById(R.id.qq_bind_hint);
        btnBind = root.findViewById(R.id.btn_bind_qq);
        groupsContainer = root.findViewById(R.id.groups_container);
        groupsList = root.findViewById(R.id.groups_list);

        // 已绑定则预填当前 QQ，用于改绑。
        long currentQq = userInfo.getLong("qq", 0L);
        if (currentQq != 0L) {
            qqInput.setText(String.valueOf(currentQq));
            bindHint.setText("当前绑定：" + currentQq + "，可直接修改后重新绑定");
        } else {
            bindHint.setText("尚未绑定 QQ 号");
        }

        btnBind.setText(currentQq != 0L ? "改绑" : "绑定");
        btnBind.setOnClickListener(v -> doBind());

        renderGroups(currentQq != 0L);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("设置");
            activity.restoreDefaultHelpUrl();
        }
    }

    // ---------- 绑定 ----------

    private void doBind() {
        String qqStr = qqInput.getText() == null ? "" : qqInput.getText().toString().trim();
        if (qqStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入 QQ 号码", Toast.LENGTH_SHORT).show();
            return;
        }
        long qq;
        try {
            qq = Long.parseLong(qqStr);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "请输入有效的 QQ 号码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (qq <= 0) {
            Toast.makeText(requireContext(), "请输入有效的 QQ 号码", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = spToken.getString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        btnBind.setEnabled(false);
        new UserApi(requireContext()).bindQQ(token, qq, new UserApi.InstanceCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (getView() == null) return;
                btnBind.setEnabled(true);
                Toast.makeText(requireContext(), "绑定成功", Toast.LENGTH_SHORT).show();
                userInfo.edit().putLong("qq", qq).apply();
                bindHint.setText("当前绑定：" + qq + "，可直接修改后重新绑定");
                btnBind.setText("改绑");
                renderGroups(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (getView() == null) return;
                btnBind.setEnabled(true);
                Toast.makeText(requireContext(), "绑定失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- 交流群列表 ----------

    private void renderGroups(boolean bound) {
        if (groupsContainer == null || groupsList == null) return;
        groupsContainer.setVisibility(bound ? View.VISIBLE : View.GONE);
        if (!bound) return;

        groupsList.removeAllViews();
        String currentSection = null;
        for (QqGroup group : QQ_GROUPS) {
            if (group.section != null && !group.section.equals(currentSection)) {
                currentSection = group.section;
                groupsList.addView(buildSectionHeader(currentSection));
            }
            groupsList.addView(buildGroupRow(group));
        }
    }

    /** 分组小标题，如「综合交流QQ群（活跃）」。 */
    private View buildSectionHeader(String section) {
        TextView header = new TextView(requireContext());
        header.setText(section);
        header.setTextSize(14);
        header.setTextColor(getColorOnSurfaceVariant());
        header.setPadding(dp(4), dp(16), dp(4), dp(4));
        return header;
    }

    /** 群号行：显示群名（若有）+ 群号，点击加群、长按复制。 */
    private View buildGroupRow(QqGroup group) {
        TextView row = new TextView(requireContext());
        String label = group.groupName != null
                ? group.groupName + ": " + group.number
                : group.number;
        row.setText(label);
        row.setTextSize(16);
        row.setTextColor(getColorOnSurface());
        row.setPadding(dp(4), dp(12), dp(4), dp(12));

        row.setOnClickListener(v -> joinGroup(group.number));
        row.setOnLongClickListener(v -> {
            ClipboardUtils.copyPlainText(requireContext(), "QQ群号", group.number,
                    "群号 " + group.number + " 已复制");
            return true;
        });
        return row;
    }

    /** 用 mqq 协议拉起 QQ 加群；设备无 QQ 时提示。 */
    private void joinGroup(String number) {
        try {
            String url = String.format(Locale.ROOT, QQ_GROUP_JOIN_SCHEME, number);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "未找到 QQ，请先安装 QQ", Toast.LENGTH_SHORT).show();
        }
    }

    /** 解析主题里的 colorOnSurface / colorOnSurfaceVariant，与布局里的 ?attr 保持一致。 */
    private int themeColor(int attrRes) {
        return com.google.android.material.color.MaterialColors.getColor(requireContext(), attrRes, 0xFF000000);
    }

    private int getColorOnSurface() {
        return themeColor(com.google.android.material.R.attr.colorOnSurface);
    }

    private int getColorOnSurfaceVariant() {
        return themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
