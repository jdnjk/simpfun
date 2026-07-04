package cn.jdnjk.simpfun.ui.ins.plans;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeSet;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.PlanAPI;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.model.PlanItem;

public class PlansFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ExtendedFloatingActionButton batchDeleteFab;
    private PlansAdapter adapter;
    private AlertDialog activeDialog;

    private boolean isLoading = false;
    private int requestGeneration = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_plans, container, false);
        requestGeneration++;

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_plans);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);
        batchDeleteFab = root.findViewById(R.id.fab_batch_delete);

        root.findViewById(R.id.fab_add_plan).setOnClickListener(v -> showCreatePlanDialog());
        batchDeleteFab.setOnClickListener(v -> showBatchDeleteConfirmDialog());

        recyclerView.setLayoutManager(new LinearLayoutManager(root.getContext()));
        adapter = new PlansAdapter(new PlansAdapter.OnPlanActionListener() {
            @Override
            public void onDeleteClick(PlanItem item) {
                showDeleteConfirmDialog(item);
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateBatchDeleteFab(selectedCount);
            }
        });
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadPlans(true));

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadPlans(true);
    }

    @Override
    public void onDestroyView() {
        requestGeneration++;
        isLoading = false;
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
        dismissChildDialog("plan_specific_date");
        dismissChildDialog("plan_specific_time");
        dismissChildDialog("plan_multi_date_range");
        dismissChildDialog("plan_multi_time");
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        swipeRefreshLayout = null;
        recyclerView = null;
        emptyStateLayout = null;
        batchDeleteFab = null;
        adapter = null;
        super.onDestroyView();
    }

    private boolean isViewAvailable() {
        return isAdded() && getView() != null && getContext() != null;
    }

    private boolean isCurrentRequest(int generation) {
        return generation == requestGeneration && isViewAvailable();
    }

    private void showToast(String message) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDialog(AlertDialog dialog) {
        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) {
                activeDialog = null;
            }
        });
        dialog.show();
    }

    private void dismissChildDialog(String tag) {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof DialogFragment dialogFragment) {
            dialogFragment.dismissAllowingStateLoss();
        }
    }

    private PlanAPI createPlanApi() {
        return new PlanAPI();
    }

    private void updateBatchDeleteFab(int selectedCount) {
        if (batchDeleteFab == null) return;
        if (selectedCount > 0) {
            batchDeleteFab.setVisibility(View.VISIBLE);
            batchDeleteFab.setText("批量删除(" + selectedCount + ")");
        } else {
            batchDeleteFab.setVisibility(View.GONE);
        }
    }

    private void loadPlans(boolean showSpinner) {
        if (!isViewAvailable() || isLoading) return;
        isLoading = true;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        String token = getToken();
        if (token == null) {
            finishLoading();
            showToast("尚未登录");
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            finishLoading();
            showToast("设备ID无效");
            return;
        }

        int generation = requestGeneration;
        createPlanApi().listPlans(token, deviceId, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isCurrentRequest(generation)) return;
                finishLoading();
                List<PlanItem> plansList = new ArrayList<>();
                JSONArray listArr = response.optJSONArray("list");
                if (listArr != null) {
                    for (int i = 0; i < listArr.length(); i++) {
                        JSONObject obj = listArr.optJSONObject(i);
                        if (obj == null) continue;
                        plansList.add(new PlanItem(
                                obj.optInt("id"),
                                obj.optString("command"),
                                obj.optString("scheduled_time"),
                                obj.optBoolean("repeated"),
                                obj.optInt("interval")
                        ));
                    }
                }

                if (adapter != null) {
                    adapter.setData(plansList);
                }
                updateEmptyState(plansList.isEmpty());
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentRequest(generation)) return;
                finishLoading();
                showToast("获取计划失败: " + errorMsg);
            }
        });
    }

    private void finishLoading() {
        isLoading = false;
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (recyclerView == null || emptyStateLayout == null) return;
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showCreatePlanDialog() {
        if (!isViewAvailable()) return;
        Context context = getContext();
        if (context == null) return;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_plan, null, false);

        TextInputEditText etCommand = view.findViewById(R.id.et_command);
        TextInputEditText etIntervalValue = view.findViewById(R.id.et_interval_value);
        TextInputEditText etSpecificTime = view.findViewById(R.id.et_specific_time);
        TextInputEditText etMultiDateRange = view.findViewById(R.id.et_multi_date_range);
        TextInputEditText etMultiTime = view.findViewById(R.id.et_multi_time);
        AutoCompleteTextView actIntervalUnit = view.findViewById(R.id.act_interval_unit);

        MaterialButtonToggleGroup toggleMode = view.findViewById(R.id.toggle_execution_mode);
        View layoutSpecific = view.findViewById(R.id.layout_mode_specific);
        View layoutMulti = view.findViewById(R.id.layout_mode_multi);
        CheckBox cbRepeat = view.findViewById(R.id.cb_repeat);
        View layoutInterval = view.findViewById(R.id.layout_interval);

        String[] units = new String[]{"分钟", "小时", "天"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, units);
        actIntervalUnit.setAdapter(unitAdapter);
        actIntervalUnit.setText(units[0], false);

        view.findViewById(R.id.btn_power_on).setOnClickListener(v -> etCommand.setText("<POWER_ON>"));
        view.findViewById(R.id.btn_power_off).setOnClickListener(v -> etCommand.setText("<POWER_OFF>"));
        view.findViewById(R.id.btn_restart).setOnClickListener(v -> etCommand.setText("<RESTART>"));

        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_mode_specific) {
                layoutSpecific.setVisibility(View.VISIBLE);
                layoutMulti.setVisibility(View.GONE);
            } else if (checkedId == R.id.btn_mode_multi) {
                layoutSpecific.setVisibility(View.GONE);
                layoutMulti.setVisibility(View.VISIBLE);
            }
        });

        cbRepeat.setOnCheckedChangeListener((btn, checked) -> layoutInterval.setVisibility(checked ? View.VISIBLE : View.GONE));

        final Calendar[] specificDateHolder = {null};
        final List<Pair<Long, Long>> multiDateRanges = new ArrayList<>();
        final int[] multiTimeHolder = {-1, -1};

        SimpleDateFormat sdfDisplay = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        SimpleDateFormat sdfDateOnly = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        etSpecificTime.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("选择日期")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(new CalendarConstraints.Builder()
                            .setValidator(DateValidatorPointForward.now())
                            .build())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                if (!isViewAvailable()) return;
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_24H)
                        .setTitleText("选择时间")
                        .build();

                timePicker.addOnPositiveButtonClickListener(tv -> {
                    if (!isViewAvailable()) return;
                    Calendar utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    utcDate.setTimeInMillis(selection);

                    Calendar local = Calendar.getInstance();
                    local.set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH),
                            timePicker.getHour(), timePicker.getMinute(), 0);
                    local.set(Calendar.MILLISECOND, 0);

                    if (local.before(Calendar.getInstance())) {
                        showToast("执行时间不能早于当前时间");
                        return;
                    }

                    specificDateHolder[0] = local;
                    etSpecificTime.setText(sdfDisplay.format(local.getTime()));
                });
                if (isViewAvailable()) {
                    timePicker.show(getChildFragmentManager(), "plan_specific_time");
                }
            });

            if (isViewAvailable()) {
                datePicker.show(getChildFragmentManager(), "plan_specific_date");
            }
        });

        etMultiDateRange.setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(multiDateRanges.isEmpty() ? "选择日期段" : "继续添加日期段")
                    .setCalendarConstraints(new CalendarConstraints.Builder()
                            .setValidator(DateValidatorPointForward.now())
                            .build())
                    .build();

            rangePicker.addOnPositiveButtonClickListener(selection -> {
                if (!isViewAvailable()) return;
                if (selection == null || selection.first == null || selection.second == null) return;
                multiDateRanges.add(selection);
                updateMultiDateRangeText(etMultiDateRange, multiDateRanges, sdfDateOnly);
            });
            if (isViewAvailable()) {
                rangePicker.show(getChildFragmentManager(), "plan_multi_date_range");
            }
        });
        etMultiDateRange.setOnLongClickListener(v -> {
            multiDateRanges.clear();
            etMultiDateRange.setText("");
            showToast("已清空日期段");
            return true;
        });

        etMultiTime.setOnClickListener(v -> {
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setTitleText("选择每天执行时间")
                    .build();

            timePicker.addOnPositiveButtonClickListener(tv -> {
                if (!isViewAvailable()) return;
                multiTimeHolder[0] = timePicker.getHour();
                multiTimeHolder[1] = timePicker.getMinute();
                etMultiTime.setText(String.format(Locale.getDefault(), "%02d:%02d", multiTimeHolder[0], multiTimeHolder[1]));
            });
            if (isViewAvailable()) {
                timePicker.show(getChildFragmentManager(), "plan_multi_time");
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("新建计划任务")
                .setView(view)
                .setPositiveButton("创建", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cmd = etCommand.getText() == null ? "" : etCommand.getText().toString().trim();
            if (cmd.isEmpty()) {
                showToast("命令不能为空");
                return;
            }

            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            apiFormat.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));

            if (toggleMode.getCheckedButtonId() == R.id.btn_mode_specific) {
                if (specificDateHolder[0] == null) {
                    showToast("请选择执行时间");
                    return;
                }

                Integer intervalSeconds = null;
                if (cbRepeat.isChecked()) {
                    String valueText = etIntervalValue.getText() == null ? "" : etIntervalValue.getText().toString().trim();
                    if (valueText.isEmpty()) {
                        showToast("请填写循环间隔值");
                        return;
                    }

                    long value;
                    try {
                        value = Long.parseLong(valueText);
                    } catch (NumberFormatException e) {
                        showToast("循环间隔值格式错误");
                        return;
                    }
                    if (value <= 0) {
                        showToast("循环间隔值必须大于0");
                        return;
                    }

                    String unit = actIntervalUnit.getText() == null ? "分钟" : actIntervalUnit.getText().toString();
                    long seconds;
                    if ("小时".equals(unit)) {
                        seconds = value * 3600L;
                    } else if ("天".equals(unit)) {
                        seconds = value * 86400L;
                    } else {
                        seconds = value * 60L;
                    }

                    if (seconds < 1800L || seconds > 8640000L) {
                        showToast("循环间隔必须在30分钟到100天之间");
                        return;
                    }
                    intervalSeconds = (int) seconds;
                }

                createPlan(cmd, apiFormat.format(specificDateHolder[0].getTime()), intervalSeconds);
                dialog.dismiss();
                return;
            }

            if (multiDateRanges.isEmpty()) {
                showToast("请至少选择一个日期段");
                return;
            }
            if (multiTimeHolder[0] < 0 || multiTimeHolder[1] < 0) {
                showToast("请选择每天执行时间");
                return;
            }

            List<String> sendTimes = buildMultiPlanTimes(multiDateRanges, multiTimeHolder[0], multiTimeHolder[1], apiFormat);
            if (sendTimes.isEmpty()) {
                showToast("所选时间均早于当前时间");
                return;
            }

            dialog.dismiss();
            sendMultiPlans(cmd, sendTimes, 0, 0, 0, requestGeneration);
        }));

        showDialog(dialog);
    }

    private void updateMultiDateRangeText(TextInputEditText editText, List<Pair<Long, Long>> ranges, SimpleDateFormat sdfDateOnly) {
        List<String> labels = new ArrayList<>();
        for (Pair<Long, Long> range : ranges) {
            if (range == null || range.first == null || range.second == null) continue;
            labels.add(formatDateRange(range, sdfDateOnly));
        }
        editText.setText(String.join("，", labels));
    }

    private String formatDateRange(Pair<Long, Long> range, SimpleDateFormat sdfDateOnly) {
        String start = sdfDateOnly.format(new Date(range.first));
        String end = sdfDateOnly.format(new Date(range.second));
        return start.equals(end) ? start : start + " ~ " + end;
    }

    private List<String> buildMultiPlanTimes(List<Pair<Long, Long>> ranges, int hour, int minute, SimpleDateFormat apiFormat) {
        Calendar now = Calendar.getInstance();
        TreeSet<Long> timeMillisSet = new TreeSet<>();

        for (Pair<Long, Long> range : ranges) {
            if (range == null || range.first == null || range.second == null) continue;

            Calendar startUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startUtc.setTimeInMillis(range.first);
            Calendar endUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endUtc.setTimeInMillis(range.second);

            Calendar iter = Calendar.getInstance();
            iter.set(startUtc.get(Calendar.YEAR), startUtc.get(Calendar.MONTH), startUtc.get(Calendar.DAY_OF_MONTH), hour, minute, 0);
            iter.set(Calendar.MILLISECOND, 0);

            Calendar end = Calendar.getInstance();
            end.set(endUtc.get(Calendar.YEAR), endUtc.get(Calendar.MONTH), endUtc.get(Calendar.DAY_OF_MONTH), hour, minute, 0);
            end.set(Calendar.MILLISECOND, 0);

            while (!iter.after(end)) {
                if (!iter.before(now)) timeMillisSet.add(iter.getTimeInMillis());
                iter.add(Calendar.DAY_OF_YEAR, 1);
            }
        }

        List<String> sendTimes = new ArrayList<>();
        for (Long millis : timeMillisSet) {
            sendTimes.add(apiFormat.format(new Date(millis)));
        }
        return sendTimes;
    }

    private void sendMultiPlans(String cmd, List<String> times, int index, int success, int failed, int generation) {
        if (!isCurrentRequest(generation)) return;
        if (index >= times.size()) {
            showToast("多天任务发送完成: 成功" + success + "，失败" + failed);
            loadPlans(true);
            return;
        }

        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            showToast("登录状态或设备ID无效");
            return;
        }

        createPlanApi().createPlan(token, deviceId, cmd, times.get(index), null, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                sendMultiPlans(cmd, times, index + 1, success + 1, failed, generation);
            }

            @Override
            public void onFailure(String errorMsg) {
                sendMultiPlans(cmd, times, index + 1, success, failed + 1, generation);
            }
        });
    }

    private void createPlan(String command, String time, Integer interval) {
        if (!isViewAvailable()) return;
        String token = getToken();
        if (token == null) {
            showToast("尚未登录");
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            showToast("设备ID无效");
            return;
        }

        int generation = requestGeneration;
        createPlanApi().createPlan(token, deviceId, command, time, interval, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isCurrentRequest(generation)) return;
                showToast("创建成功");
                loadPlans(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentRequest(generation)) return;
                showToast("创建失败: " + errorMsg);
            }
        });
    }

    private void showDeleteConfirmDialog(PlanItem item) {
        Context context = getContext();
        if (context == null) return;
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("确认删除")
                .setMessage("确定要删除计划 #" + item.getId() + " 吗？")
                .setPositiveButton("删除", (d, which) -> deletePlan(item.getId()))
                .setNegativeButton("取消", null)
                .create();
        showDialog(dialog);
    }

    private void showBatchDeleteConfirmDialog() {
        if (adapter == null) return;
        List<Integer> selected = adapter.getSelectedIds();
        if (selected.isEmpty()) {
            showToast("请先勾选要删除的计划");
            return;
        }

        Context context = getContext();
        if (context == null) return;
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("批量删除")
                .setMessage("确定删除已勾选的 " + selected.size() + " 个计划吗？")
                .setPositiveButton("删除", (d, which) -> deletePlansSequentially(selected, 0, 0, 0, requestGeneration))
                .setNegativeButton("取消", null)
                .create();
        showDialog(dialog);
    }

    private void deletePlansSequentially(List<Integer> ids, int index, int success, int failed, int generation) {
        if (!isCurrentRequest(generation)) return;
        if (index >= ids.size()) {
            if (adapter != null) {
                adapter.clearSelection();
            }
            showToast("批量删除完成: 成功" + success + "，失败" + failed);
            loadPlans(true);
            return;
        }

        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            showToast("登录状态或设备ID无效");
            return;
        }

        int currentId = ids.get(index);
        createPlanApi().deletePlan(token, deviceId, currentId, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                deletePlansSequentially(ids, index + 1, success + 1, failed, generation);
            }

            @Override
            public void onFailure(String errorMsg) {
                deletePlansSequentially(ids, index + 1, success, failed + 1, generation);
            }
        });
    }

    private void deletePlan(int planId) {
        if (!isViewAvailable()) return;
        String token = getToken();
        if (token == null) {
            showToast("尚未登录");
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            showToast("设备ID无效");
            return;
        }

        int generation = requestGeneration;
        createPlanApi().deletePlan(token, deviceId, planId, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isCurrentRequest(generation)) return;
                showToast("删除成功");
                if (adapter != null) {
                    adapter.deleteItem(planId);
                    updateEmptyState(adapter.getItemCount() == 0);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentRequest(generation)) return;
                showToast("删除失败: " + errorMsg);
            }
        });
    }

    private int getDeviceId() {
        if (getActivity() instanceof ServerManages activity && activity.getDeviceId() > 0) {
            return activity.getDeviceId();
        }
        Context context = getContext();
        if (context == null) return -1;
        SharedPreferences sp = context.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    @Nullable
    private String getToken() {
        Context context = getContext();
        if (context == null) return null;
        return context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", null);
    }
}
