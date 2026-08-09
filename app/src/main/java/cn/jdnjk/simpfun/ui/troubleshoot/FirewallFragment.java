package cn.jdnjk.simpfun.ui.troubleshoot;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.regex.Pattern;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.FirewallApi;
import cn.jdnjk.simpfun.model.FirewallCheckResult;

public class FirewallFragment extends Fragment {
    private static final Pattern IP_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final int GREEN = 0xFF43A047;
    private static final int GRAY = 0xFF9E9E9E;

    private final FirewallApi api = new FirewallApi();

    private TextInputEditText inputIp;
    private MaterialButton buttonQuery;
    private MaterialButton buttonUnblock;
    private ProgressBar progressBar;
    private MaterialCardView cardResult;
    private TextView tvResultTitle;
    private LinearLayout resultDetails;

    private int dailyQueriesLimit = 15;
    private int dailyUnblocksLimit = 3;
    private FirewallCheckResult lastResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_firewall, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        inputIp = view.findViewById(R.id.input_ip);
        buttonQuery = view.findViewById(R.id.button_query);
        buttonUnblock = view.findViewById(R.id.button_unblock);
        progressBar = view.findViewById(R.id.progress_bar);
        cardResult = view.findViewById(R.id.card_result);
        tvResultTitle = view.findViewById(R.id.tv_result_title);
        resultDetails = view.findViewById(R.id.result_details);

        buttonQuery.setOnClickListener(v -> onQueryClicked());
        buttonUnblock.setOnClickListener(v -> onUnblockClicked());

        setBusy(true);
        loadSession(null);
    }

    private void loadSession(@Nullable Runnable onReady) {
        api.fetchSession(new FirewallApi.SessionCallback() {
            @Override
            public void onSuccess(FirewallApi.FirewallSession session) {
                if (!isAdded()) return;
                dailyQueriesLimit = session.dailyQueriesLimit;
                dailyUnblocksLimit = session.dailyUnblocksLimit;
                if (!TextUtils.isEmpty(session.defaultIp) && TextUtils.isEmpty(currentIpText())) {
                    inputIp.setText(session.defaultIp);
                    inputIp.setSelection(inputIp.getText().length());
                }
                setBusy(false);
                if (onReady != null) onReady.run();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                setBusy(false);
                toast(errorMsg == null ? "连接防火墙服务失败" : errorMsg, Toast.LENGTH_LONG);
            }
        });
    }

    private void onQueryClicked() {
        String ip = currentIpText().trim();
        if (!IP_PATTERN.matcher(ip).matches()) {
            toast("请输入有效的 IP 地址，如 192.168.1.1", Toast.LENGTH_SHORT);
            return;
        }
        if (!api.isSessionReady()) {
            setBusy(true);
            loadSession(this::checkIp);
            return;
        }
        checkIp();
    }

    private void checkIp() {
        String ip = currentIpText().trim();
        if (!IP_PATTERN.matcher(ip).matches()) {
            toast("请输入有效的 IP 地址，如 192.168.1.1", Toast.LENGTH_SHORT);
            return;
        }
        setBusy(true);
        api.checkIp(ip, new FirewallApi.CheckCallback() {
            @Override
            public void onSuccess(FirewallCheckResult result) {
                if (!isAdded()) return;
                lastResult = result;
                setBusy(false);
                renderResult(result);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                setBusy(false);
                cardResult.setVisibility(View.GONE);
                toast(errorMsg == null ? "查询失败，请稍后再试" : errorMsg, Toast.LENGTH_LONG);
            }
        });
    }

    private void renderResult(FirewallCheckResult result) {
        cardResult.setVisibility(View.VISIBLE);
        resultDetails.removeAllViews();

        boolean blocked = result.isBlocked();
        tvResultTitle.setText(blocked ? "IP 已被封禁" : "IP 未被封禁");
        tvResultTitle.setTextColor(themeColorAttr(blocked
                ? android.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurface));

        addDetailRow("IP 地址", result.getIp());
        addDetailRow("封禁状态", blocked ? "已封禁" : "未封禁");

        if (blocked) {
            if (!TextUtils.isEmpty(result.getBlockType())) {
                addDetailRow("封禁类型", result.getBlockType());
            }
            if (!TextUtils.isEmpty(result.getBlockTime())) {
                addDetailRow("封禁时间", result.getBlockTime());
            }
            if (!TextUtils.isEmpty(result.getTimeout())) {
                addDetailRow("剩余封禁时间", result.getTimeout());
            }
        }

        addDetailRow("今日剩余查询次数",
                formatRemaining(result.getDailyQueriesRemaining(), dailyQueriesLimit));
        addDetailRow("今日剩余解封次数",
                formatRemaining(result.getDailyUnblocksRemaining(), dailyUnblocksLimit));

        buttonUnblock.setVisibility(blocked ? View.VISIBLE : View.GONE);
    }

    private String formatRemaining(int remaining, int limit) {
        if (remaining < 0) return String.valueOf(limit);
        return remaining + " / " + limit;
    }

    private void onUnblockClicked() {
        if (lastResult == null || !lastResult.isBlocked()) {
            toast("没有可解封的 IP", Toast.LENGTH_SHORT);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认解封")
                .setMessage("确定要解封 IP " + lastResult.getIp() + " 吗？\n封禁类型：" + lastResult.getBlockType())
                .setPositiveButton("立即解封", (d, w) -> performUnblock())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performUnblock() {
        if (lastResult == null) return;
        String ip = lastResult.getIp();
        String blockType = lastResult.getBlockType();
        setBusy(true);
        api.unblockIp(ip, blockType, new FirewallApi.UnblockCallback() {
            @Override
            public void onSuccess(int dailyUnblocksRemaining) {
                if (!isAdded()) return;
                setBusy(false);
                toast("解封成功，今日剩余解封次数：" + dailyUnblocksRemaining, Toast.LENGTH_SHORT);
                checkIp();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                setBusy(false);
                toast(errorMsg == null ? "解封失败，请稍后再试" : errorMsg, Toast.LENGTH_LONG);
            }
        });
    }

    private void setBusy(boolean busy) {
        if (progressBar != null) progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (buttonQuery != null) buttonQuery.setEnabled(!busy);
        if (buttonUnblock != null) buttonUnblock.setEnabled(!busy);
    }

    private void addDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(15);
        tvLabel.setTextColor(themeColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(tvLabel, labelParams);

        TextView tvValue = new TextView(requireContext());
        tvValue.setText(value == null ? "" : value);
        tvValue.setTextSize(15);
        tvValue.setTextColor(themeColorAttr(com.google.android.material.R.attr.colorOnSurface));
        tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvValue.setMaxLines(2);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tvValue, valueParams);

        resultDetails.addView(row);
    }

    private int themeColorAttr(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (requireContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(requireContext(), value.resourceId);
            }
            if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return 0xFF808080;
    }

    private String currentIpText() {
        return inputIp == null || inputIp.getText() == null ? "" : inputIp.getText().toString();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message, int length) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, length).show();
        }
    }

    @Override
    public void onDestroyView() {
        inputIp = null;
        buttonQuery = null;
        buttonUnblock = null;
        progressBar = null;
        cardResult = null;
        tvResultTitle = null;
        resultDetails = null;
        super.onDestroyView();
    }
}
