package cn.jdnjk.simpfun.ui.ins.overview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.databinding.FragmentOverviewBinding;
import cn.jdnjk.simpfun.model.ServerStatsSnapshot;
import cn.jdnjk.simpfun.service.ServerStatsListener;
import cn.jdnjk.simpfun.service.ServerStatsService;
import cn.jdnjk.simpfun.ui.setting.OverviewDisplayManager;
import cn.jdnjk.simpfun.utils.ClipboardUtils;
import cn.jdnjk.simpfun.utils.ServerStatsFormatter;

/**
 * 总览页：展示服务器电源状态、地址、剩余流量线路，
 * 以及实时的 CPU / 内存 / 上下行网速。
 * 默认卡片 + 进度条模式，设置中可切换为实时折线统计图。
 */
public class OverviewFragment extends Fragment implements ServerStatsListener {
    /** 折线图只保留最近 30 秒内的数据点。 */
    private static final long CHART_WINDOW_MS = 30_000L;
    private static final int MAX_CHART_POINTS = 60;
    private static final long CHART_REDRAW_DELAY_MS = 100L;

    private FragmentOverviewBinding binding;
    private final ServerStatsService statsService = ServerStatsService.getInstance();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OverviewDisplayManager displayManager;

    private final List<ServerStatsSnapshot> chartBuffer = new ArrayList<>();
    private final List<Long> chartTimes = new ArrayList<>();
    private ServerStatsSnapshot latestSnapshot;
    private boolean chartRedrawScheduled;

    private int coreCount = 1;
    private int detailGeneration;
    private boolean detailRefreshRunning;

    private String currentAddress = "";
    private OverviewMarkerView markerView;

    private final Runnable chartRedrawRunnable = () -> {
        chartRedrawScheduled = false;
        if (binding != null && isAdded()
                && displayManager != null && displayManager.isLineChartEnabled()) {
            renderChart();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOverviewBinding.inflate(inflater, container, false);
        displayManager = new OverviewDisplayManager(requireContext());
        setupChart(binding.chartOverview);
        renderStaticDetail();
        applyMode();
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        statsService.addListener(this);
        int deviceId = getDeviceId();
        if (deviceId > 0) {
            statsService.subscribe(requireContext(), deviceId);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        statsService.removeListener(this);
        int deviceId = getDeviceId();
        if (deviceId > 0) {
            statsService.unsubscribe(deviceId);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) return;
        // 设置页可能已改动显示模式
        applyMode();
        renderStaticDetail();
        renderLatestSnapshot();
    }

    @Override
    public void onDestroyView() {
        detailGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        chartRedrawScheduled = false;
        chartBuffer.clear();
        chartTimes.clear();
        latestSnapshot = null;
        markerView = null;
        binding = null;
        super.onDestroyView();
    }

    // ---------- WS 回调（主线程） ----------

    @Override
    public void onStatsUpdated(int deviceId, ServerStatsSnapshot stats) {
        if (deviceId != getDeviceId() || binding == null || !isAdded()) return;
        latestSnapshot = stats;
        renderPowerState(stats.getState());
        boolean offline = "offline".equalsIgnoreCase(stats.getState());
        if (displayManager != null && displayManager.isLineChartEnabled()) {
            if (!offline) {
                chartBuffer.add(stats);
                chartTimes.add(SystemClock.elapsedRealtime());
                // 只保留最近 30 秒内的点（同时按数量兜底）
                while (!chartTimes.isEmpty()
                        && chartTimes.get(0) < SystemClock.elapsedRealtime() - CHART_WINDOW_MS) {
                    chartTimes.remove(0);
                    chartBuffer.remove(0);
                }
                while (chartBuffer.size() > MAX_CHART_POINTS) {
                    chartBuffer.remove(0);
                    chartTimes.remove(0);
                }
                scheduleChartRedraw();
            } else {
                showChartEmpty();
            }
        } else {
            renderProgress(stats);
        }
    }

    @Override
    public void onStatsDisconnected(int deviceId, String reason) {
        if (deviceId != getDeviceId() || binding == null || !isAdded()) return;
        String detailStatus = detailStatus();
        renderPowerState(detailStatus);
        binding.textUptime.setText("--");
        if (displayManager != null && displayManager.isLineChartEnabled()) {
            showChartEmpty();
        } else {
            resetProgressToZero();
        }
    }

    // ---------- 静态详情 ----------

    private void renderStaticDetail() {
        if (binding == null) return;
        JSONObject detail = cachedDetail();
        if (detail == null) {
            currentAddress = "";
            binding.textAddress.setText("服务器地址：--");
            binding.textTrafficRemain.setText("剩余上行流量：--");
            binding.textCpuCoreHint.setText(getString(R.string.overview_cpu_core_hint, coreCount, coreCount * 100));
            refreshDetailFromHost();
            return;
        }

        int cpu = detail.optInt("cpu", 0);
        if (cpu > 0) {
            coreCount = cpu;
        }
        binding.textCpuCoreHint.setText(getString(R.string.overview_cpu_core_hint, coreCount, coreCount * 100));

        String addr = extractAddress(detail);
        currentAddress = addr;
        binding.textAddress.setText("服务器地址：" + (addr.isEmpty() ? "--" : addr));
        binding.textAddress.setOnClickListener(v -> {
            if (!currentAddress.isEmpty()) {
                ClipboardUtils.copyPlainText(requireContext(), "Server IP", currentAddress, "IP已复制");
            }
        });

        JSONObject traffic = detail.optJSONObject("traffic");
        long remain = traffic != null ? traffic.optLong("remain_bytes", -1) : -1;
        boolean moreTraffic = traffic != null && traffic.optBoolean("more_traffic", false);
        String gb = remain < 0 ? "--" : String.format(Locale.US, "%.2f GB", remain / 1024d / 1024d / 1024d);
        String line = moreTraffic ? "普通线路" : "精品线路";
        binding.textTrafficRemain.setText(getString(R.string.overview_traffic_remain, gb, line));
    }

    @Nullable
    private JSONObject cachedDetail() {
        if (!(getActivity() instanceof ServerManages activity)) return null;
        return activity.getCachedInstanceDetailData();
    }

    private String detailStatus() {
        JSONObject detail = cachedDetail();
        if (detail == null) return "offline";
        return detail.optString("status", "offline");
    }

    private void refreshDetailFromHost() {
        if (!(getActivity() instanceof ServerManages activity) || detailRefreshRunning) return;
        detailRefreshRunning = true;
        int generation = ++detailGeneration;
        activity.refreshInstanceDetail(new ServerManages.InstanceDetailCallback() {
            @Override
            public void onSuccess(@Nullable JSONObject detail) {
                detailRefreshRunning = false;
                if (generation == detailGeneration && isAdded()) {
                    renderStaticDetail();
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                detailRefreshRunning = false;
            }
        });
    }

    /** 复用 ServerManages.applyServerDetail 的地址提取逻辑：优先 is_default，否则取第一个。 */
    private String extractAddress(JSONObject detail) {
        JSONArray allocations = detail.optJSONArray("allocations");
        if (allocations == null) return "";
        for (int i = 0; i < allocations.length(); i++) {
            JSONObject alloc = allocations.optJSONObject(i);
            if (alloc != null && alloc.optBoolean("is_default")) {
                return alloc.optString("ip") + ":" + alloc.optInt("port");
            }
        }
        if (allocations.length() > 0) {
            JSONObject alloc = allocations.optJSONObject(0);
            if (alloc != null) {
                return alloc.optString("ip") + ":" + alloc.optInt("port");
            }
        }
        return "";
    }

    // ---------- 电源状态 ----------

    private void renderPowerState(String state) {
        if (binding == null) return;
        binding.dotStatus.setColorFilter(statusColor(state));
        binding.textStatus.setText(toStatusText(state));
    }

    private String toStatusText(String status) {
        return switch (status) {
            case "running" -> "运行中";
            case "offline" -> "已离线";
            case "installing" -> "安装中";
            case "starting" -> "启动中";
            case "stopping" -> "停止中";
            default -> "未知状态";
        };
    }

    private int statusColor(String status) {
        return switch (status) {
            case "running" -> Color.rgb(76, 175, 80);
            case "offline" -> Color.rgb(158, 158, 158);
            case "starting" -> Color.rgb(255, 152, 0);
            case "installing" -> Color.rgb(156, 39, 176);
            case "stopping" -> Color.rgb(244, 67, 54);
            default -> getColor(R.color.md_theme_onSurfaceVariant);
        };
    }

    // ---------- 进度条模式 ----------

    private void renderProgress(ServerStatsSnapshot stats) {
        if (binding == null) return;
        boolean offline = "offline".equalsIgnoreCase(stats.getState());
        if (offline) {
            resetProgressToZero();
            return;
        }

        int cpuLimit = coreCount * 100;
        int cpuBar = ServerStatsFormatter.toCpuPercent(stats.getCpuAbsolute(), cpuLimit);
        int memPct = ServerStatsFormatter.toMemoryPercent(stats.getMemoryBytes(), stats.getMemoryLimitBytes());

        binding.progressCpu.setProgressCompat(cpuBar, false);
        // 大百分比按最大 100% 归一化显示（满 1 核即 100%），数量行显示原始百分比
        binding.textCpuPercent.setText(ServerStatsFormatter.formatPercentText(cpuBar));
        binding.textCpuAmount.setText(formatCpuAmount(stats.getCpuAbsolute()));
        binding.progressMemory.setProgressCompat(memPct, false);
        binding.textMemoryPercent.setText(ServerStatsFormatter.formatPercentText(memPct));
        binding.textMemoryAmount.setText(ServerStatsFormatter.formatBytes(stats.getMemoryBytes())
                + "/" + ServerStatsFormatter.formatBytes(stats.getMemoryLimitBytes()));
        binding.textDownloadSpeed.setText(ServerStatsFormatter.formatSpeed(stats.getDownloadBytesPerSecond()));
        binding.textUploadSpeed.setText(ServerStatsFormatter.formatSpeed(stats.getUploadBytesPerSecond()));
        binding.textUptime.setText(getString(R.string.overview_uptime, ServerStatsFormatter.formatUptime(stats.getUptimeMillis())));
    }

    private void resetProgressToZero() {
        if (binding == null) return;
        binding.progressCpu.setProgressCompat(0, false);
        binding.textCpuPercent.setText("--");
        binding.textCpuAmount.setText("--");
        binding.progressMemory.setProgressCompat(0, false);
        binding.textMemoryPercent.setText("--");
        binding.textMemoryAmount.setText("--");
        binding.textDownloadSpeed.setText("--");
        binding.textUploadSpeed.setText("--");
        binding.textUptime.setText("--");
    }

    /** CPU 当前百分比/总百分比：如 cpu_absolute=150、核数=2 → "150%/200%"。 */
    private String formatCpuAmount(double cpuAbsolute) {
        int current = (int) Math.round(cpuAbsolute);
        int total = coreCount * 100;
        return current + "%/" + total + "%";
    }

    private void renderLatestSnapshot() {
        if (latestSnapshot != null) {
            renderPowerState(latestSnapshot.getState());
            if (displayManager != null && displayManager.isLineChartEnabled()) {
                renderChart();
            } else {
                renderProgress(latestSnapshot);
            }
        }
    }

    // ---------- 模式切换 ----------

    private void applyMode() {
        if (binding == null) return;
        boolean chart = displayManager != null && displayManager.isLineChartEnabled();
        binding.cardChart.setVisibility(chart ? View.VISIBLE : View.GONE);
        binding.cardCpu.setVisibility(chart ? View.GONE : View.VISIBLE);
        binding.cardMemory.setVisibility(chart ? View.GONE : View.VISIBLE);
        binding.cardNetwork.setVisibility(chart ? View.GONE : View.VISIBLE);
        if (chart) {
            renderChart();
        } else {
            renderLatestSnapshot();
        }
    }

    // ---------- 折线图 ----------

    private void setupChart(LineChart chart) {
        int labelColor = getColor(R.color.md_theme_onSurfaceVariant);
        int axisColor = getColor(R.color.md_theme_outlineVariant);

        chart.getDescription().setEnabled(false);
        chart.setNoDataText(getString(R.string.overview_chart_empty));
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(8f, 8f, 8f, 12f);
        chart.getLegend().setTextColor(labelColor);
        chart.getLegend().setWordWrapEnabled(true);
        markerView = new OverviewMarkerView(requireContext());
        chart.setMarker(markerView);
        setupChartTouchHandling(chart);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(labelColor);
        xAxis.setAxisLineColor(axisColor);
        xAxis.setGridColor(axisColor);
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(true);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(labelColor);
        leftAxis.setAxisLineColor(axisColor);
        leftAxis.setGridColor(axisColor);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(Math.max(100f, (float) coreCount * 100f));
        leftAxis.setValueFormatter(new PercentAxisFormatter());

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setTextColor(labelColor);
        rightAxis.setAxisLineColor(axisColor);
        rightAxis.setGridColor(axisColor);
        rightAxis.setAxisMinimum(0f);
        rightAxis.setValueFormatter(new SpeedAxisFormatter());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupChartTouchHandling(LineChart chart) {
        chart.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked() != MotionEvent.ACTION_UP
                    && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
            return false;
        });
    }

    private void scheduleChartRedraw() {
        if (binding == null || !isAdded()) return;
        if (chartRedrawScheduled) return;
        chartRedrawScheduled = true;
        mainHandler.postDelayed(chartRedrawRunnable, CHART_REDRAW_DELAY_MS);
    }

    private void renderChart() {
        if (binding == null) return;
        LineChart chart = binding.chartOverview;
        if (chartBuffer.isEmpty()) {
            showChartEmpty();
            return;
        }

        List<Entry> cpuEntries = new ArrayList<>(chartBuffer.size());
        List<Entry> memEntries = new ArrayList<>(chartBuffer.size());
        List<Entry> downEntries = new ArrayList<>(chartBuffer.size());
        List<Entry> upEntries = new ArrayList<>(chartBuffer.size());
        for (int i = 0; i < chartBuffer.size(); i++) {
            ServerStatsSnapshot s = chartBuffer.get(i);
            float x = i;
            cpuEntries.add(new Entry(x, (float) s.getCpuAbsolute()));
            memEntries.add(new Entry(x, ServerStatsFormatter.toMemoryPercent(s.getMemoryBytes(), s.getMemoryLimitBytes())));
            downEntries.add(new Entry(x, (float) s.getDownloadBytesPerSecond()));
            upEntries.add(new Entry(x, (float) s.getUploadBytesPerSecond()));
        }

        LineData data = new LineData();
        data.addDataSet(createDataSet(cpuEntries, getString(R.string.stats_metric_cpu),
                getColor(R.color.md_theme_primary), YAxis.AxisDependency.LEFT));
        data.addDataSet(createDataSet(memEntries, getString(R.string.stats_metric_memory),
                getColor(R.color.md_theme_secondary), YAxis.AxisDependency.LEFT));
        data.addDataSet(createDataSet(downEntries, getString(R.string.stats_traffic_in),
                getColor(R.color.md_theme_tertiary), YAxis.AxisDependency.RIGHT));
        data.addDataSet(createDataSet(upEntries, getString(R.string.stats_traffic_out),
                getColor(R.color.md_theme_error), YAxis.AxisDependency.RIGHT));

        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(Math.max(1f, chartBuffer.size() - 1f));
        chart.getXAxis().setLabelCount(Math.min(5, Math.max(2, chartBuffer.size())), true);
        chart.getXAxis().setValueFormatter(new TimeAxisFormatter(chartTimes));
        chart.getAxisLeft().setAxisMaximum(Math.max(100f, (float) coreCount * 100f));

        if (markerView != null) {
            markerView.setBuffers(chartBuffer, chartTimes);
        }

        binding.textChartEmpty.setVisibility(View.GONE);
        binding.chartOverview.setVisibility(View.VISIBLE);
        chart.setData(data);
        chart.invalidate();
    }

    private void showChartEmpty() {
        if (binding == null) return;
        LineChart chart = binding.chartOverview;
        chart.setData(null);
        binding.chartOverview.setVisibility(View.GONE);
        binding.textChartEmpty.setVisibility(View.VISIBLE);
        chart.invalidate();
    }

    private LineDataSet createDataSet(List<Entry> entries, String label, int color, YAxis.AxisDependency axisDependency) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setAxisDependency(axisDependency);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setHighLightColor(color);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(18);
        return dataSet;
    }

    // ---------- 工具 ----------

    private int getDeviceId() {
        if (getActivity() instanceof ServerManages serverManages) {
            return serverManages.getDeviceId();
        }
        return -1;
    }

    private int getColor(int colorRes) {
        Context context = getContext();
        if (context != null) {
            return context.getResources().getColor(colorRes, context.getTheme());
        }
        return requireContext().getResources().getColor(colorRes, requireContext().getTheme());
    }

    /**
     * 按住折线图时展示该点的时间与四项指标数值。
     * 复用 view_stats_marker 布局（与统计页一致），并做水平边界裁剪。
     */
    private class OverviewMarkerView extends MarkerView {
        private final TextView textTime;
        private final TextView textValue;
        private List<ServerStatsSnapshot> buffer = new ArrayList<>();
        private List<Long> times = new ArrayList<>();

        OverviewMarkerView(Context context) {
            super(context, R.layout.view_stats_marker);
            textTime = findViewById(R.id.text_marker_time);
            textValue = findViewById(R.id.text_marker_value);
        }

        void setBuffers(List<ServerStatsSnapshot> buffer, List<Long> times) {
            this.buffer = buffer;
            this.times = times;
        }

        @Override
        public void refreshContent(Entry entry, Highlight highlight) {
            if (entry == null) {
                return;
            }
            int index = Math.min(buffer.size() - 1, Math.max(0, (int) entry.getX()));
            textTime.setText(formatMarkerTime(index));
            textValue.setText(formatMarkerValue(index));
            super.refreshContent(entry, highlight);
        }

        private String formatMarkerTime(int index) {
            if (index < 0 || index >= times.size()) return "";
            long elapsedMs = times.get(index) - times.get(0);
            long totalSeconds = Math.max(0L, elapsedMs / 1000L);
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return String.format(Locale.US, "-%d:%02d", minutes, seconds);
        }

        private String formatMarkerValue(int index) {
            if (index < 0 || index >= buffer.size()) return "";
            ServerStatsSnapshot s = buffer.get(index);
            List<String> values = new ArrayList<>();
            values.add(getString(R.string.stats_metric_cpu) + "："
                    + ServerStatsFormatter.formatPercentText(
                            ServerStatsFormatter.toCpuPercent(s.getCpuAbsolute(), coreCount * 100))
                    + " (" + (int) Math.round(s.getCpuAbsolute()) + "%)");
            values.add(getString(R.string.stats_metric_memory) + "：" + ServerStatsFormatter.formatBytes(s.getMemoryBytes()));
            values.add(getString(R.string.stats_traffic_in) + "：" + ServerStatsFormatter.formatSpeed(s.getDownloadBytesPerSecond()));
            values.add(getString(R.string.stats_traffic_out) + "：" + ServerStatsFormatter.formatSpeed(s.getUploadBytesPerSecond()));
            return String.join("\n", values);
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getWidth() / 2f), -getHeight() - 12f);
        }

        @Override
        public void draw(Canvas canvas, float posX, float posY) {
            MPPointF offset = getOffset();
            float drawX = posX + offset.x;
            float drawY = posY + offset.y;
            if (getChartView() != null) {
                float chartWidth = getChartView().getWidth();
                drawX = Math.max(0f, Math.min(drawX, chartWidth - getWidth()));
            }
            canvas.save();
            canvas.translate(drawX, drawY);
            draw(canvas);
            canvas.restore();
        }
    }

    private static class PercentAxisFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return Math.round(value) + "%";
        }
    }

    private static class SpeedAxisFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return ServerStatsFormatter.formatSpeed((long) value);
        }
    }

    private static class TimeAxisFormatter extends ValueFormatter {
        private final List<Long> chartTimes;

        TimeAxisFormatter(List<Long> chartTimes) {
            this.chartTimes = chartTimes;
        }

        @Override
        public String getFormattedValue(float value) {
            if (chartTimes.isEmpty()) return "";
            int index = Math.min(chartTimes.size() - 1, Math.max(0, (int) value));
            long elapsedMs = chartTimes.get(index) - chartTimes.get(0);
            long totalSeconds = Math.max(0L, elapsedMs / 1000L);
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return minutes + ":" + String.format(Locale.US, "%02d", seconds);
        }
    }
}
