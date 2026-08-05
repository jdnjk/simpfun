package cn.jdnjk.simpfun.ui.troubleshoot;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SWebView;
import cn.jdnjk.simpfun.api.StatusPageApi;
import cn.jdnjk.simpfun.model.MonitorLiveStatus;
import cn.jdnjk.simpfun.model.StatusMonitor;
import cn.jdnjk.simpfun.model.StatusMonitorGroup;
import cn.jdnjk.simpfun.model.StatusPageData;
import okhttp3.Call;

public class StatusMonitorFragment extends Fragment {
    private static final String STATUS_PAGE_WEB_URL = "https://simp.host/status/simpfun";
    private static final int AMBER = 0xFFE6A23C;
    private static final int GREEN = 0xFF43A047;
    private static final int GRAY = 0xFF9E9E9E;

    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView contentScroll;
    private View stateLayout;
    private TextView tvStateTitle;
    private TextView tvStateMessage;
    private View buttonRetry;
    private TextView tvStatusTitle;
    private TextView tvStatusDescription;
    private TextView tvStatusMeta;
    private MaterialCardView cardIncident;
    private LinearLayout monitorListContainer;

    private boolean isLoading = false;
    private Call call;
    private Map<Long, MonitorLiveStatus> liveMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status_monitor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        contentScroll = view.findViewById(R.id.content_scroll);
        stateLayout = view.findViewById(R.id.state_layout);
        tvStateTitle = view.findViewById(R.id.text_state_title);
        tvStateMessage = view.findViewById(R.id.text_state_message);
        buttonRetry = view.findViewById(R.id.button_retry);
        tvStatusTitle = view.findViewById(R.id.tv_status_title);
        tvStatusDescription = view.findViewById(R.id.tv_status_description);
        tvStatusMeta = view.findViewById(R.id.tv_status_meta);
        cardIncident = view.findViewById(R.id.card_incident);
        monitorListContainer = view.findViewById(R.id.monitor_list_container);

        swipeRefreshLayout.setOnRefreshListener(() -> loadData(true));
        buttonRetry.setOnClickListener(v -> loadData(false));
        view.findViewById(R.id.button_open_web).setOnClickListener(v -> openUrl(STATUS_PAGE_WEB_URL));

        loadData(false);
    }

    private void loadData(boolean fromUserRefresh) {
        if (isLoading) return;
        if (fromUserRefresh && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        } else {
            showLoading();
        }
        isLoading = true;

        call = new StatusPageApi().fetchStatusPage(new StatusPageApi.Callback() {
            @Override
            public void onSuccess(StatusPageData data) {
                if (!isAdded()) return;
                fetchLive(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                isLoading = false;
                stopRefresh();
                if (!isAdded()) return;
                showError(errorMsg);
            }
        });
    }

    private void fetchLive(StatusPageData data) {
        call = new StatusPageApi().fetchLiveStatus(new StatusPageApi.LiveCallback() {
            @Override
            public void onSuccess(Map<Long, MonitorLiveStatus> live) {
                isLoading = false;
                stopRefresh();
                if (!isAdded()) return;
                liveMap = live;
                if (data.getMonitorCount() == 0 || data.getGroups().isEmpty()) {
                    showEmpty();
                } else {
                    render(data);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                isLoading = false;
                stopRefresh();
                if (!isAdded()) return;
                liveMap = null;
                if (data.getMonitorCount() == 0 || data.getGroups().isEmpty()) {
                    showEmpty();
                } else {
                    render(data);
                    Toast.makeText(requireContext(), "实时状态获取失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void stopRefresh() {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showLoading() {
        setContentVisible(false);
        setStateVisible("正在获取状态信息…", "正在连接 simp.host 状态服务", false);
    }

    private void showEmpty() {
        setContentVisible(false);
        setStateVisible("暂无监控数据", "状态服务当前未公开任何监控项", false);
    }

    private void showError(String message) {
        setContentVisible(false);
        setStateVisible("加载失败", TextUtils.isEmpty(message) ? "请稍后再试" : message, true);
    }

    private void setStateVisible(String title, String message, boolean showRetry) {
        if (stateLayout == null) return;
        stateLayout.setVisibility(View.VISIBLE);
        if (tvStateTitle != null) tvStateTitle.setText(title);
        if (tvStateMessage != null) tvStateMessage.setText(message);
        if (buttonRetry != null) buttonRetry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
    }

    private void setContentVisible(boolean visible) {
        if (contentScroll != null) contentScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (stateLayout != null) stateLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void render(StatusPageData data) {
        setContentVisible(true);

        String title = TextUtils.isEmpty(data.getTitle()) ? "状态监控" : data.getTitle();
        tvStatusTitle.setText(title);

        String description = data.getDescription();
        if (TextUtils.isEmpty(description)) {
            tvStatusDescription.setVisibility(View.GONE);
        } else {
            tvStatusDescription.setVisibility(View.VISIBLE);
            tvStatusDescription.setText(description);
        }

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        if (liveMap != null) {
            int online = countOnline(data);
            tvStatusMeta.setText("在线 " + online + "/" + data.getMonitorCount() + " · 更新于 " + time);
        } else {
            tvStatusMeta.setText("共 " + data.getMonitorCount() + " 个监控项 · 更新于 " + time);
        }

        cardIncident.setVisibility(data.hasIncident() ? View.VISIBLE : View.GONE);

        buildMonitorList(data);
    }

    private int countOnline(StatusPageData data) {
        int online = 0;
        for (StatusMonitorGroup group : data.getGroups()) {
            for (StatusMonitor monitor : group.getMonitors()) {
                MonitorLiveStatus live = liveMap.get(monitor.getId());
                if (live != null && live.isOnline()) online++;
            }
        }
        return online;
    }

    private void buildMonitorList(StatusPageData data) {
        monitorListContainer.removeAllViews();
        boolean first = true;
        for (StatusMonitorGroup group : data.getGroups()) {
            if (group.getMonitors().isEmpty()) continue;
            addGroupHeader(group.getName(), first);
            addGroupCard(group);
            first = false;
        }
    }

    private void addGroupHeader(String name, boolean first) {
        TextView header = new TextView(requireContext());
        header.setText(name);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setTextColor(themeColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant));
        header.setPadding(dp(8), first ? dp(2) : dp(16), dp(8), dp(8));
        monitorListContainer.addView(header);
    }

    private void addGroupCard(StatusMonitorGroup group) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(themeColorAttr(com.google.android.material.R.attr.colorSurfaceContainerLow));
        card.setStrokeColor(themeColorAttr(com.google.android.material.R.attr.colorOutlineVariant));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(12));
        card.setUseCompatPadding(false);

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        List<StatusMonitor> monitors = group.getMonitors();
        for (int i = 0; i < monitors.size(); i++) {
            inner.addView(createMonitorRow(monitors.get(i)));
            if (i < monitors.size() - 1) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(themeColorAttr(com.google.android.material.R.attr.colorOutlineVariant));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dlp.setMargins(dp(24), 0, dp(16), 0);
                inner.addView(divider, dlp);
            }
        }

        card.addView(inner);
        monitorListContainer.addView(card);
    }

    private View createMonitorRow(StatusMonitor monitor) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_status_monitor_row, monitorListContainer, false);

        TextView tvName = row.findViewById(R.id.tv_monitor_name);
        TextView tvDetail = row.findViewById(R.id.tv_monitor_detail);
        TextView tvState = row.findViewById(R.id.tv_monitor_state);
        TextView tvType = row.findViewById(R.id.tv_monitor_type);
        ImageView ivDot = row.findViewById(R.id.iv_status_dot);
        ImageView ivArrow = row.findViewById(R.id.iv_monitor_arrow);
        HeartbeatStripView strip = row.findViewById(R.id.view_heartbeat_strip);

        MonitorLiveStatus live = liveMap == null ? null : liveMap.get(monitor.getId());

        tvName.setText(monitor.getName());

        String type = typeLabel(monitor.getType());
        if (TextUtils.isEmpty(type)) {
            tvType.setVisibility(View.GONE);
        } else {
            tvType.setText(type);
            tvType.setVisibility(View.VISIBLE);
        }

        int dotColor = GRAY;
        if (live != null) {
            if (live.isOnline()) {
                dotColor = GREEN;
            } else if (live.isPending()) {
                dotColor = AMBER;
            } else {
                dotColor = themeColorAttr(android.R.attr.colorError);
            }
        }
        ivDot.setColorFilter(dotColor, PorterDuff.Mode.SRC_IN);

        if (live != null && live.isPending()) {
            tvState.setVisibility(View.VISIBLE);
            tvState.setText("重试中");
            tvState.setTextColor(AMBER);
        } else if (live != null && !live.isOnline()) {
            tvState.setVisibility(View.VISIBLE);
            tvState.setText("离线");
            tvState.setTextColor(themeColorAttr(android.R.attr.colorError));
        } else {
            tvState.setVisibility(View.GONE);
        }

        SpannableStringBuilder detail = new SpannableStringBuilder();
        if (live != null && live.hasUptime()) {
            detail.append("24h 可用率 ").append(String.format(Locale.getDefault(), "%.2f%%", live.getUptime24h() * 100));
        }
        String cert = buildCertText(monitor);
        if (!cert.isEmpty()) {
            if (detail.length() > 0) detail.append(" · ");
            int textStart = detail.length();
            detail.append(cert);
            detail.setSpan(new ForegroundColorSpan(certColor(monitor.getCertExpiryDaysRemaining())),
                    textStart, detail.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (detail.length() == 0) {
            tvDetail.setVisibility(View.GONE);
        } else {
            tvDetail.setVisibility(View.VISIBLE);
            tvDetail.setText(detail);
        }

        if (live != null && !live.getHeartbeats().isEmpty()) {
            strip.setHeartbeats(live.getHeartbeats());
            strip.setVisibility(View.VISIBLE);
        } else {
            strip.setVisibility(View.GONE);
        }

        if (monitor.hasUrl()) {
            ivArrow.setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> openUrl(monitor.getUrl()));
        } else {
            ivArrow.setVisibility(View.GONE);
        }

        return row;
    }

    private static String typeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "http":
                return "HTTP";
            case "port":
                return "端口";
            case "push":
                return "推送";
            case "tcp":
                return "TCP";
            case "ping":
                return "Ping";
            case "dns":
                return "DNS";
            case "keyword":
                return "关键词";
            case "docker":
                return "Docker";
            case "group":
                return "分组";
            default:
                return type.toUpperCase(Locale.ROOT);
        }
    }

    private static String buildCertText(StatusMonitor monitor) {
        long days = monitor.getCertExpiryDaysRemaining();
        if (days >= 0) {
            return "证书 " + days + " 天后到期";
        }
        if (monitor.isValidCert()) {
            return "证书有效";
        }
        return "";
    }

    private int certColor(long days) {
        if (days >= 0 && days < 30) {
            return themeColorAttr(android.R.attr.colorError);
        }
        if (days >= 30 && days < 60) {
            return AMBER;
        }
        return themeColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            try {
                Intent intent = new Intent(requireContext(), SWebView.class);
                intent.putExtra("url", url);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(requireContext(), "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int themeColorAttr(int attr) {
        TypedValue value = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(requireContext(), value.resourceId);
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return 0xFF808080;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroyView() {
        if (call != null) {
            call.cancel();
            call = null;
        }
        swipeRefreshLayout = null;
        contentScroll = null;
        stateLayout = null;
        tvStateTitle = null;
        tvStateMessage = null;
        buttonRetry = null;
        tvStatusTitle = null;
        tvStatusDescription = null;
        tvStatusMeta = null;
        cardIncident = null;
        monitorListContainer = null;
        super.onDestroyView();
    }
}
