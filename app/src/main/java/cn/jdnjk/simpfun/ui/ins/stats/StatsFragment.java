package cn.jdnjk.simpfun.ui.ins.stats;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.StatsApi;
import cn.jdnjk.simpfun.databinding.FragmentStatsBinding;
import cn.jdnjk.simpfun.model.InstanceStatPoint;
import cn.jdnjk.simpfun.utils.ServerStatsFormatter;
import okhttp3.Call;

public class StatsFragment extends Fragment {
    private static final int MAX_POINTS_PER_LINE = 360;

    private FragmentStatsBinding binding;
    private final StatsApi statsApi = new StatsApi();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService chartExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat shortTimeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);

    private Call currentCall;
    private List<InstanceStatPoint> currentPoints = new ArrayList<>();
    private final EnumSet<StatMetric> selectedMetrics = EnumSet.of(StatMetric.CPU);
    private TimeRange selectedRange = TimeRange.ONE_DAY;
    private long customStartTimestamp = 0L;
    private long customEndTimestamp = 0L;
    private StatsMarkerView markerView;
    private int requestGeneration = 0;
    private int chartGeneration = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        setupChart(binding.chartStats);
        setupInteractions();
        showLoadingState();
        loadStats();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        requestGeneration++;
        chartGeneration++;
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
        if (binding != null) {
            binding.buttonRetry.setOnClickListener(null);
            binding.buttonCustomRange.setOnClickListener(null);
        }
        markerView = null;
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        chartExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupInteractions() {
        binding.buttonRetry.setOnClickListener(v -> loadStats());
        binding.buttonCustomRange.setOnClickListener(v -> showCustomRangeDialog());
        binding.chipGroupRanges.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            boolean hadCustomRange = hasValidCustomRange();
            if (checkedId == R.id.chip_range_1h) {
                selectedRange = TimeRange.ONE_HOUR;
            } else if (checkedId == R.id.chip_range_1d) {
                selectedRange = TimeRange.ONE_DAY;
            } else if (checkedId == R.id.chip_range_3d) {
                selectedRange = TimeRange.THREE_DAYS;
            } else if (checkedId == R.id.chip_range_custom) {
                selectedRange = TimeRange.CUSTOM;
            }
            updateCustomRangeButton();
            if (selectedRange == TimeRange.CUSTOM && !hadCustomRange) {
                ensureCustomRangeDefaults();
                updateCustomRangeButton();
                showCustomRangeDialog();
                return;
            }
            renderChartAsync();
        });
        binding.chipGroupMetrics.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) {
                binding.chipCpu.setChecked(true);
                return;
            }
            EnumSet<StatMetric> nextMetrics = EnumSet.noneOf(StatMetric.class);
            for (int checkedId : checkedIds) {
                if (checkedId == R.id.chip_cpu) {
                    nextMetrics.add(StatMetric.CPU);
                } else if (checkedId == R.id.chip_memory) {
                    nextMetrics.add(StatMetric.MEMORY);
                } else if (checkedId == R.id.chip_traffic) {
                    nextMetrics.add(StatMetric.TRAFFIC);
                } else if (checkedId == R.id.chip_remain_traffic) {
                    nextMetrics.add(StatMetric.REMAIN_TRAFFIC);
                }
            }
            if (nextMetrics.isEmpty()) {
                binding.chipCpu.setChecked(true);
                return;
            }
            selectedMetrics.clear();
            selectedMetrics.addAll(nextMetrics);
            renderChartAsync();
        });
        updateCustomRangeButton();
    }

    private void setupChart(LineChart chart) {
        int labelColor = getColor(R.color.md_theme_onSurfaceVariant);
        int axisColor = getColor(R.color.md_theme_outlineVariant);

        chart.getDescription().setEnabled(false);
        chart.setNoDataText(getString(R.string.stats_empty));
        chart.setTouchEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setHighlightPerDragEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setAutoScaleMinMaxEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(8f, 8f, 8f, 12f);
        chart.getLegend().setTextColor(labelColor);
        chart.getLegend().setWordWrapEnabled(true);
        markerView = new StatsMarkerView(requireContext());
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

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
        rightAxis.setTextColor(labelColor);
        rightAxis.setAxisLineColor(axisColor);
        rightAxis.setGridColor(axisColor);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupChartTouchHandling(LineChart chart) {
        chart.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked() != MotionEvent.ACTION_UP
                    && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
            return false;
        });
    }

    private void loadStats() {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;

        int serverId = getServerId();
        if (serverId <= 0) {
            showErrorState(getString(R.string.invalid_device_id));
            return;
        }

        if (currentCall != null) {
            currentCall.cancel();
        }
        int generation = ++requestGeneration;
        showLoadingState();

        Context appContext = requireContext().getApplicationContext();
        currentCall = statsApi.getStats(appContext, serverId, new StatsApi.Callback() {
            @Override
            public void onSuccess(List<InstanceStatPoint> points) {
                if (!isCallbackActive(generation)) return;
                currentCall = null;
                currentPoints = points == null ? new ArrayList<>() : points;
                if (currentPoints.isEmpty()) {
                    showEmptyState();
                } else {
                    showContentState();
                    if (selectedRange == TimeRange.CUSTOM && !hasValidCustomRange()) {
                        ensureCustomRangeDefaults();
                        updateCustomRangeButton();
                    }
                    renderChartAsync();
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCallbackActive(generation)) return;
                currentCall = null;
                String message = errorMsg == null || errorMsg.isEmpty() ? getString(R.string.stats_load_failed) : errorMsg;
                showErrorState(message);
                if (isAdded()) {
                    Toast.makeText(requireContext(), getString(R.string.stats_load_failed_with_msg, message), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private int getServerId() {
        if (getActivity() instanceof ServerManages serverManages) {
            return serverManages.getDeviceId();
        }
        return -1;
    }

    private boolean isCallbackActive(int generation) {
        return binding != null && isAdded() && generation == requestGeneration;
    }

    private void showLoadingState() {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;
        currentBinding.contentScroll.setVisibility(View.GONE);
        currentBinding.stateLayout.setVisibility(View.VISIBLE);
        currentBinding.textStateTitle.setText(R.string.loading_text);
        currentBinding.textStateMessage.setText(R.string.stats_loading_desc);
        currentBinding.buttonRetry.setVisibility(View.GONE);
    }

    private void showContentState() {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;
        currentBinding.contentScroll.setVisibility(View.VISIBLE);
        currentBinding.stateLayout.setVisibility(View.GONE);
        currentBinding.buttonRetry.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;
        currentBinding.contentScroll.setVisibility(View.GONE);
        currentBinding.stateLayout.setVisibility(View.VISIBLE);
        currentBinding.textStateTitle.setText(R.string.stats_empty_title);
        currentBinding.textStateMessage.setText(R.string.stats_empty);
        currentBinding.buttonRetry.setVisibility(View.VISIBLE);
    }

    private void showErrorState(String message) {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;
        currentBinding.contentScroll.setVisibility(View.GONE);
        currentBinding.stateLayout.setVisibility(View.VISIBLE);
        currentBinding.textStateTitle.setText(R.string.stats_load_failed);
        currentBinding.textStateMessage.setText(message);
        currentBinding.buttonRetry.setVisibility(View.VISIBLE);
    }

    private void renderChartAsync() {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null || currentPoints.isEmpty() || selectedMetrics.isEmpty()) return;
        int generation = ++chartGeneration;
        EnumSet<StatMetric> metrics = selectedMetrics.clone();
        TimeRange range = selectedRange;
        long customStart = customStartTimestamp;
        long customEnd = customEndTimestamp;
        List<InstanceStatPoint> points = new ArrayList<>(currentPoints);
        ChartStyle style = createChartStyle();
        int chartWidth = currentBinding.chartStats.getWidth();

        chartExecutor.execute(() -> {
            ChartPayload payload = buildChartPayload(points, metrics, range, customStart, customEnd, style, chartWidth);
            mainHandler.post(() -> {
                if (binding == null
                        || generation != chartGeneration
                        || selectedRange != range
                        || customStartTimestamp != customStart
                        || customEndTimestamp != customEnd
                        || !selectedMetrics.equals(metrics)) {
                    return;
                }
                applyChartPayload(payload);
            });
        });
    }

    private ChartStyle createChartStyle() {
        return new ChartStyle(
                getString(R.string.stats_metric_cpu),
                getString(R.string.stats_metric_memory),
                getString(R.string.stats_traffic_in),
                getString(R.string.stats_traffic_out),
                getString(R.string.stats_metric_remain_traffic),
                getColor(R.color.md_theme_primary),
                getColor(R.color.md_theme_secondary),
                getColor(R.color.md_theme_tertiary),
                getColor(R.color.md_theme_error),
                getColor(R.color.md_theme_outline)
        );
    }

    private ChartPayload buildChartPayload(List<InstanceStatPoint> points,
                                           EnumSet<StatMetric> metrics,
                                           TimeRange range,
                                           long customStart,
                                           long customEnd,
                                           ChartStyle style,
                                           int chartWidth) {
        long latestTimestamp = findLastValidTimestamp(points);
        long baseTimestamp;
        long endTimestamp;
        if (range == TimeRange.CUSTOM && customStart > 0 && customEnd > customStart) {
            baseTimestamp = customStart;
            endTimestamp = customEnd;
        } else {
            baseTimestamp = Math.max(0L, latestTimestamp - range.durationSeconds);
            endTimestamp = latestTimestamp;
        }

        List<InstanceStatPoint> displayPoints = filterPointsByRange(points, baseTimestamp, endTimestamp);
        boolean hasCpu = metrics.contains(StatMetric.CPU);
        boolean hasByteMetric = metrics.contains(StatMetric.MEMORY)
                || metrics.contains(StatMetric.TRAFFIC)
                || metrics.contains(StatMetric.REMAIN_TRAFFIC);
        YAxis.AxisDependency byteAxis = hasCpu && hasByteMetric ? YAxis.AxisDependency.RIGHT : YAxis.AxisDependency.LEFT;
        float maxLeftY = 0f;
        float maxRightY = 0f;
        List<ILineDataSet> sets = new ArrayList<>();

        for (StatMetric metric : StatMetric.values()) {
            if (!metrics.contains(metric)) continue;
            switch (metric) {
                case CPU -> {
                    List<Entry> entries = buildEntries(displayPoints, baseTimestamp, InstanceStatPoint::getCpuPercent);
                    maxLeftY = Math.max(maxLeftY, findMax(entries));
                    sets.add(createDataSet(entries, style.cpuLabel, style.cpuColor, YAxis.AxisDependency.LEFT));
                }
                case MEMORY -> {
                    List<Entry> entries = buildEntries(displayPoints, baseTimestamp, InstanceStatPoint::getMemUsedBytes);
                    if (byteAxis == YAxis.AxisDependency.RIGHT) {
                        maxRightY = Math.max(maxRightY, findMax(entries));
                    } else {
                        maxLeftY = Math.max(maxLeftY, findMax(entries));
                    }
                    sets.add(createDataSet(entries, style.memoryLabel, style.memoryColor, byteAxis));
                }
                case TRAFFIC -> {
                    List<Entry> inEntries = buildEntries(displayPoints, baseTimestamp, InstanceStatPoint::getInBytes);
                    List<Entry> outEntries = buildEntries(displayPoints, baseTimestamp, InstanceStatPoint::getOutBytes);
                    float maxTraffic = Math.max(findMax(inEntries), findMax(outEntries));
                    if (byteAxis == YAxis.AxisDependency.RIGHT) {
                        maxRightY = Math.max(maxRightY, maxTraffic);
                    } else {
                        maxLeftY = Math.max(maxLeftY, maxTraffic);
                    }
                    sets.add(createDataSet(inEntries, style.inLabel, style.inColor, byteAxis));
                    sets.add(createDataSet(outEntries, style.outLabel, style.outColor, byteAxis));
                }
                case REMAIN_TRAFFIC -> {
                    List<Entry> entries = buildEntries(displayPoints, baseTimestamp, InstanceStatPoint::getOutRemainBytes);
                    if (byteAxis == YAxis.AxisDependency.RIGHT) {
                        maxRightY = Math.max(maxRightY, findMax(entries));
                    } else {
                        maxLeftY = Math.max(maxLeftY, findMax(entries));
                    }
                    sets.add(createDataSet(entries, style.remainLabel, style.remainColor, byteAxis));
                }
            }
        }

        long durationSeconds = Math.max(0L, endTimestamp - baseTimestamp);
        int labelCount = resolveXAxisLabelCount(range, chartWidth, durationSeconds);
        boolean includeDate = durationSeconds > 24L * 3600L;
        return new ChartPayload(metrics, new LineData(sets), baseTimestamp, endTimestamp, maxLeftY, maxRightY,
                labelCount, includeDate, displayPoints, hasCpu, hasByteMetric, byteAxis == YAxis.AxisDependency.RIGHT);
    }

    private List<InstanceStatPoint> filterPointsByRange(List<InstanceStatPoint> points, long startTimestamp, long endTimestamp) {
        if (startTimestamp <= 0 || endTimestamp <= 0) return points;
        List<InstanceStatPoint> filtered = new ArrayList<>();
        for (InstanceStatPoint point : points) {
            long timestamp = point.getCreateTimeTimestampSeconds();
            if (timestamp >= startTimestamp && timestamp <= endTimestamp) {
                filtered.add(point);
            }
        }
        return filtered;
    }

    private List<Entry> buildEntries(List<InstanceStatPoint> points, long baseTimestamp, StatValueProvider valueProvider) {
        if (baseTimestamp <= 0) return new ArrayList<>();
        List<Entry> entries = new ArrayList<>(points.size());
        for (InstanceStatPoint point : points) {
            long timestamp = point.getCreateTimeTimestampSeconds();
            if (timestamp <= 0) continue;
            float x = timestamp - baseTimestamp;
            float y = Math.max(0f, valueProvider.getValue(point));
            entries.add(new Entry(x, y));
        }
        return downsample(entries, MAX_POINTS_PER_LINE);
    }

    private List<Entry> downsample(List<Entry> entries, int maxPoints) {
        if (entries.size() <= maxPoints || maxPoints < 4) {
            return entries;
        }
        List<Entry> sampled = new ArrayList<>(maxPoints + 2);
        sampled.add(entries.get(0));

        int first = 1;
        int last = entries.size() - 2;
        int bucketCount = Math.max(1, (maxPoints - 2) / 2);
        double bucketSize = (last - first + 1) / (double) bucketCount;

        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int start = first + (int) Math.floor(bucket * bucketSize);
            int end = first + (int) Math.floor((bucket + 1) * bucketSize);
            if (bucket == bucketCount - 1) {
                end = last + 1;
            }
            start = Math.max(first, Math.min(start, last));
            end = Math.max(start + 1, Math.min(end, last + 1));

            Entry min = entries.get(start);
            Entry max = min;
            for (int i = start + 1; i < end; i++) {
                Entry entry = entries.get(i);
                if (entry.getY() < min.getY()) min = entry;
                if (entry.getY() > max.getY()) max = entry;
            }
            if (min.getX() <= max.getX()) {
                sampled.add(min);
                if (max != min) sampled.add(max);
            } else {
                sampled.add(max);
                sampled.add(min);
            }
        }
        sampled.add(entries.get(entries.size() - 1));
        return sampled;
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

    private void applyChartPayload(ChartPayload payload) {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;

        LineChart chart = currentBinding.chartStats;
        currentBinding.textChartTitle.setText(formatMetricTitle(payload.metrics));
        currentBinding.textChartRange.setText(getString(
                R.string.stats_chart_range,
                formatTimestamp(payload.baseTimestamp, true),
                formatTimestamp(payload.endTimestamp, true)
        ));
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(Math.max(1f, payload.endTimestamp - payload.baseTimestamp));
        chart.getXAxis().setLabelCount(payload.labelCount, true);
        chart.getXAxis().setValueFormatter(new TimeAxisFormatter(payload.baseTimestamp, payload.includeDate));
        configureAxes(chart, payload);
        chart.highlightValues(null);
        if (markerView != null) {
            markerView.setFormatter(payload.baseTimestamp, payload.includeDate, payload.metrics, payload.markerPoints);
        }
        chart.setData(payload.data);
        chart.highlightValues(null);
        chart.invalidate();
        chart.animateX(250);
    }

    private void configureAxes(LineChart chart, ChartPayload payload) {
        YAxis leftAxis = chart.getAxisLeft();
        YAxis rightAxis = chart.getAxisRight();
        leftAxis.resetAxisMinimum();
        leftAxis.resetAxisMaximum();
        rightAxis.resetAxisMinimum();
        rightAxis.resetAxisMaximum();

        if (payload.hasCpu) {
            leftAxis.setValueFormatter(StatMetric.CPU.axisFormatter);
            leftAxis.setAxisMinimum(0f);
            leftAxis.setAxisMaximum(100f);
        } else {
            leftAxis.setValueFormatter(new BytesAxisFormatter());
            leftAxis.setAxisMinimum(0f);
            if (payload.maxLeftY > 0f) {
                leftAxis.setAxisMaximum(payload.maxLeftY * 1.1f);
            }
        }

        rightAxis.setEnabled(payload.rightByteAxisEnabled);
        if (payload.rightByteAxisEnabled) {
            rightAxis.setValueFormatter(new BytesAxisFormatter());
            rightAxis.setAxisMinimum(0f);
            if (payload.maxRightY > 0f) {
                rightAxis.setAxisMaximum(payload.maxRightY * 1.1f);
            }
        }
    }

    private int resolveXAxisLabelCount(TimeRange range, int chartWidth, long durationSeconds) {
        int width = chartWidth <= 0 ? 1080 : chartWidth;
        int maxLabels = Math.max(2, Math.min(6, width / 180));
        if (range == TimeRange.ONE_HOUR) return Math.min(4, maxLabels);
        if (range == TimeRange.ONE_DAY) return Math.min(5, maxLabels);
        if (range == TimeRange.THREE_DAYS) return 3;
        if (durationSeconds <= 3600L) return Math.min(4, maxLabels);
        if (durationSeconds <= 86400L) return Math.min(5, maxLabels);
        long days = Math.max(1L, (durationSeconds + 86399L) / 86400L);
        return Math.max(2, Math.min(maxLabels, (int) Math.min(days + 1, 6)));
    }

    private float findMax(List<Entry> entries) {
        float max = 0f;
        for (Entry entry : entries) {
            max = Math.max(max, entry.getY());
        }
        return max;
    }

    private long findLastValidTimestamp(List<InstanceStatPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            long timestamp = points.get(i).getCreateTimeTimestampSeconds();
            if (timestamp > 0) {
                return timestamp;
            }
        }
        return 0L;
    }

    private String formatTimestamp(long timestampSeconds, boolean includeDate) {
        if (timestampSeconds <= 0) return "--";
        Date date = new Date(timestampSeconds * 1000L);
        return includeDate ? dateTimeFormat.format(date) : shortTimeFormat.format(date);
    }

    private String formatMetricTitle(EnumSet<StatMetric> metrics) {
        List<String> names = new ArrayList<>();
        for (StatMetric metric : StatMetric.values()) {
            if (metrics.contains(metric)) {
                names.add(getString(metric.titleRes));
            }
        }
        return String.join(" / ", names);
    }

    private void updateCustomRangeButton() {
        FragmentStatsBinding currentBinding = binding;
        if (currentBinding == null) return;
        if (selectedRange == TimeRange.CUSTOM) {
            currentBinding.buttonCustomRange.setVisibility(View.VISIBLE);
            if (hasValidCustomRange()) {
                currentBinding.buttonCustomRange.setText(getString(
                        R.string.stats_range_custom_value,
                        formatTimestamp(customStartTimestamp, true),
                        formatTimestamp(customEndTimestamp, true)
                ));
            } else {
                currentBinding.buttonCustomRange.setText(R.string.stats_range_custom_select);
            }
        } else {
            currentBinding.buttonCustomRange.setVisibility(View.GONE);
        }
    }

    private boolean hasValidCustomRange() {
        return customStartTimestamp > 0 && customEndTimestamp > customStartTimestamp;
    }

    private void ensureCustomRangeDefaults() {
        if (hasValidCustomRange()) return;
        long latestTimestamp = findLastValidTimestamp(currentPoints);
        if (latestTimestamp <= 0) return;
        customEndTimestamp = latestTimestamp;
        customStartTimestamp = Math.max(0L, latestTimestamp - TimeRange.ONE_DAY.durationSeconds);
    }

    private void showCustomRangeDialog() {
        if (!isAdded()) return;
        ensureCustomRangeDefaults();
        long start = customStartTimestamp > 0 ? customStartTimestamp : System.currentTimeMillis() / 1000L - TimeRange.ONE_DAY.durationSeconds;
        long end = customEndTimestamp > start ? customEndTimestamp : System.currentTimeMillis() / 1000L;
        pickDateTime(getString(R.string.stats_range_custom_start), start, pickedStart ->
                pickDateTime(getString(R.string.stats_range_custom_end), end, pickedEnd -> {
                    if (binding == null || !isAdded()) return;
                    if (pickedStart >= pickedEnd) {
                        Toast.makeText(requireContext(), R.string.stats_range_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedRange = TimeRange.CUSTOM;
                    customStartTimestamp = pickedStart;
                    customEndTimestamp = pickedEnd;
                    binding.chipRangeCustom.setChecked(true);
                    updateCustomRangeButton();
                    renderChartAsync();
                }));
    }

    private void pickDateTime(String title, long initialTimestamp, DateTimeCallback callback) {
        if (!isAdded()) return;
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(Math.max(1L, initialTimestamp) * 1000L);
        DatePickerDialog dateDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    picked.set(Calendar.HOUR_OF_DAY, initial.get(Calendar.HOUR_OF_DAY));
                    picked.set(Calendar.MINUTE, initial.get(Calendar.MINUTE));
                    picked.set(Calendar.SECOND, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    TimePickerDialog timeDialog = new TimePickerDialog(
                            requireContext(),
                            (timeView, hourOfDay, minute) -> {
                                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                picked.set(Calendar.MINUTE, minute);
                                callback.onPicked(picked.getTimeInMillis() / 1000L);
                            },
                            initial.get(Calendar.HOUR_OF_DAY),
                            initial.get(Calendar.MINUTE),
                            true
                    );
                    timeDialog.setTitle(title);
                    timeDialog.show();
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        );
        dateDialog.setTitle(title);
        dateDialog.show();
    }

    private int getColor(int colorRes) {
        Context context = getContext();
        if (context != null) {
            return context.getResources().getColor(colorRes, context.getTheme());
        }
        return requireContext().getResources().getColor(colorRes, requireContext().getTheme());
    }

    private enum TimeRange {
        ONE_HOUR(3600L),
        ONE_DAY(86400L),
        THREE_DAYS(3L * 86400L),
        CUSTOM(0L);

        private final long durationSeconds;

        TimeRange(long durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }

    private enum StatMetric {
        CPU(R.string.stats_metric_cpu, new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Math.round(value) + "%";
            }
        }),
        MEMORY(R.string.stats_metric_memory, new BytesAxisFormatter()),
        TRAFFIC(R.string.stats_metric_traffic, new BytesAxisFormatter()),
        REMAIN_TRAFFIC(R.string.stats_metric_remain_traffic, new BytesAxisFormatter());

        private final int titleRes;
        private final ValueFormatter axisFormatter;

        StatMetric(int titleRes, ValueFormatter axisFormatter) {
            this.titleRes = titleRes;
            this.axisFormatter = axisFormatter;
        }
    }

    private static class BytesAxisFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return ServerStatsFormatter.formatBytes((long) value);
        }
    }

    private class StatsMarkerView extends MarkerView {
        private final TextView textTime;
        private final TextView textValue;
        private long baseTimestamp;
        private boolean includeDate;
        private EnumSet<StatMetric> metrics = EnumSet.of(StatMetric.CPU);
        private List<InstanceStatPoint> markerPoints = new ArrayList<>();

        StatsMarkerView(Context context) {
            super(context, R.layout.view_stats_marker);
            textTime = findViewById(R.id.text_marker_time);
            textValue = findViewById(R.id.text_marker_value);
        }

        void setFormatter(long baseTimestamp,
                          boolean includeDate,
                          EnumSet<StatMetric> metrics,
                          List<InstanceStatPoint> markerPoints) {
            this.baseTimestamp = baseTimestamp;
            this.includeDate = includeDate;
            this.metrics = metrics == null || metrics.isEmpty() ? EnumSet.of(StatMetric.CPU) : metrics.clone();
            this.markerPoints = markerPoints == null ? new ArrayList<>() : markerPoints;
        }

        @Override
        public void refreshContent(Entry entry, Highlight highlight) {
            if (entry != null) {
                long timestamp = baseTimestamp + (long) entry.getX();
                textTime.setText(formatTimestamp(timestamp, includeDate));
                textValue.setText(formatMarkerValue(entry, timestamp));
            }
            super.refreshContent(entry, highlight);
        }

        private String formatMarkerValue(Entry entry, long timestamp) {
            InstanceStatPoint point = findNearestPoint(timestamp);
            if (point == null) {
                return String.valueOf(entry.getY());
            }
            List<String> values = new ArrayList<>();
            for (StatMetric metric : StatMetric.values()) {
                if (!metrics.contains(metric)) continue;
                switch (metric) {
                    case CPU -> values.add(getString(R.string.stats_metric_cpu) + "：" + point.getCpuPercent() + "%");
                    case MEMORY -> values.add(getString(R.string.stats_metric_memory) + "：" + ServerStatsFormatter.formatBytes(point.getMemUsedBytes()));
                    case TRAFFIC -> {
                        values.add(getString(R.string.stats_traffic_in) + "：" + ServerStatsFormatter.formatBytes(point.getInBytes()));
                        values.add(getString(R.string.stats_traffic_out) + "：" + ServerStatsFormatter.formatBytes(point.getOutBytes()));
                    }
                    case REMAIN_TRAFFIC -> values.add(getString(R.string.stats_metric_remain_traffic) + "：" + ServerStatsFormatter.formatBytes(point.getOutRemainBytes()));
                }
            }
            return String.join("\n", values);
        }

        private InstanceStatPoint findNearestPoint(long timestamp) {
            InstanceStatPoint nearest = null;
            long nearestDistance = Long.MAX_VALUE;
            for (InstanceStatPoint point : markerPoints) {
                long pointTimestamp = point.getCreateTimeTimestampSeconds();
                if (pointTimestamp <= 0) continue;
                long distance = Math.abs(pointTimestamp - timestamp);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = point;
                }
            }
            return nearest;
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getWidth() / 2f), -getHeight() - 12f);
        }
    }

    private class TimeAxisFormatter extends ValueFormatter {
        private final long baseTimestamp;
        private final boolean includeDate;

        TimeAxisFormatter(long baseTimestamp, boolean includeDate) {
            this.baseTimestamp = baseTimestamp;
            this.includeDate = includeDate;
        }

        @Override
        public String getFormattedValue(float value) {
            return formatTimestamp(baseTimestamp + (long) value, includeDate);
        }
    }

    private static class ChartPayload {
        private final EnumSet<StatMetric> metrics;
        private final LineData data;
        private final long baseTimestamp;
        private final long endTimestamp;
        private final float maxLeftY;
        private final float maxRightY;
        private final int labelCount;
        private final boolean includeDate;
        private final List<InstanceStatPoint> markerPoints;
        private final boolean hasCpu;
        private final boolean hasByteMetric;
        private final boolean rightByteAxisEnabled;

        ChartPayload(EnumSet<StatMetric> metrics,
                     LineData data,
                     long baseTimestamp,
                     long endTimestamp,
                     float maxLeftY,
                     float maxRightY,
                     int labelCount,
                     boolean includeDate,
                     List<InstanceStatPoint> markerPoints,
                     boolean hasCpu,
                     boolean hasByteMetric,
                     boolean rightByteAxisEnabled) {
            this.metrics = metrics;
            this.data = data;
            this.baseTimestamp = baseTimestamp;
            this.endTimestamp = endTimestamp;
            this.maxLeftY = maxLeftY;
            this.maxRightY = maxRightY;
            this.labelCount = labelCount;
            this.includeDate = includeDate;
            this.markerPoints = markerPoints;
            this.hasCpu = hasCpu;
            this.hasByteMetric = hasByteMetric;
            this.rightByteAxisEnabled = rightByteAxisEnabled;
        }
    }

    private static class ChartStyle {
        private final String cpuLabel;
        private final String memoryLabel;
        private final String inLabel;
        private final String outLabel;
        private final String remainLabel;
        private final int cpuColor;
        private final int memoryColor;
        private final int inColor;
        private final int outColor;
        private final int remainColor;

        ChartStyle(String cpuLabel,
                   String memoryLabel,
                   String inLabel,
                   String outLabel,
                   String remainLabel,
                   int cpuColor,
                   int memoryColor,
                   int inColor,
                   int outColor,
                   int remainColor) {
            this.cpuLabel = cpuLabel;
            this.memoryLabel = memoryLabel;
            this.inLabel = inLabel;
            this.outLabel = outLabel;
            this.remainLabel = remainLabel;
            this.cpuColor = cpuColor;
            this.memoryColor = memoryColor;
            this.inColor = inColor;
            this.outColor = outColor;
            this.remainColor = remainColor;
        }
    }

    private interface StatValueProvider {
        float getValue(InstanceStatPoint point);
    }

    private interface DateTimeCallback {
        void onPicked(long timestampSeconds);
    }
}
