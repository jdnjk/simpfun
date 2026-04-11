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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.PlanAPI;
import cn.jdnjk.simpfun.model.PlanItem;

public class PlansFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ExtendedFloatingActionButton batchDeleteFab;
    private PlansAdapter adapter;

    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_plans, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_plans);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);
        batchDeleteFab = root.findViewById(R.id.fab_batch_delete);

        root.findViewById(R.id.fab_add_plan).setOnClickListener(v -> showCreatePlanDialog());
        batchDeleteFab.setOnClickListener(v -> showBatchDeleteConfirmDialog());

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
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

        loadPlans(true);
        return root;
    }

    private void updateBatchDeleteFab(int selectedCount) {
        if (selectedCount > 0) {
            batchDeleteFab.setVisibility(View.VISIBLE);
            batchDeleteFab.setText("批量删除(" + selectedCount + ")");
        } else {
            batchDeleteFab.setVisibility(View.GONE);
        }
    }

    private void loadPlans(boolean showSpinner) {
        if (isLoading) return;
        isLoading = true;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        String token = getToken();
        if (token == null) {
            finishLoading();
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            finishLoading();
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new PlanAPI(requireContext()).listPlans(token, deviceId, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
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

                adapter.setData(plansList);
                updateEmptyState(plansList.isEmpty());
            }

            @Override
            public void onFailure(String errorMsg) {
                finishLoading();
                Toast.makeText(requireContext(), "获取计划失败: " + errorMsg, Toast.LENGTH_SHORT).show();
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
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showCreatePlanDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_plan, null, false);

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
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, units);
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
        final Pair<Long, Long>[] multiDateRangeHolder = new Pair[]{null};
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
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_24H)
                        .setTitleText("选择时间")
                        .build();

                timePicker.addOnPositiveButtonClickListener(tv -> {
                    Calendar utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    utcDate.setTimeInMillis(selection);

                    Calendar local = Calendar.getInstance();
                    local.set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH),
                            timePicker.getHour(), timePicker.getMinute(), 0);
                    local.set(Calendar.MILLISECOND, 0);

                    if (local.before(Calendar.getInstance())) {
                        Toast.makeText(requireContext(), "执行时间不能早于当前时间", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    specificDateHolder[0] = local;
                    etSpecificTime.setText(sdfDisplay.format(local.getTime()));
                });
                timePicker.show(getChildFragmentManager(), "plan_specific_time");
            });

            datePicker.show(getChildFragmentManager(), "plan_specific_date");
        });

        etMultiDateRange.setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("选择多天范围")
                    .setCalendarConstraints(new CalendarConstraints.Builder()
                            .setValidator(DateValidatorPointForward.now())
                            .build())
                    .build();

            rangePicker.addOnPositiveButtonClickListener(selection -> {
                multiDateRangeHolder[0] = selection;
                etMultiDateRange.setText(sdfDateOnly.format(selection.first) + " ~ " + sdfDateOnly.format(selection.second));
            });
            rangePicker.show(getChildFragmentManager(), "plan_multi_date_range");
        });

        etMultiTime.setOnClickListener(v -> {
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setTitleText("选择每天执行时间")
                    .build();

            timePicker.addOnPositiveButtonClickListener(tv -> {
                multiTimeHolder[0] = timePicker.getHour();
                multiTimeHolder[1] = timePicker.getMinute();
                etMultiTime.setText(String.format(Locale.getDefault(), "%02d:%02d", multiTimeHolder[0], multiTimeHolder[1]));
            });
            timePicker.show(getChildFragmentManager(), "plan_multi_time");
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("新建计划任务")
                .setView(view)
                .setPositiveButton("创建", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cmd = etCommand.getText() == null ? "" : etCommand.getText().toString().trim();
            if (cmd.isEmpty()) {
                Toast.makeText(requireContext(), "命令不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            apiFormat.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));

            if (toggleMode.getCheckedButtonId() == R.id.btn_mode_specific) {
                if (specificDateHolder[0] == null) {
                    Toast.makeText(requireContext(), "请选择执行时间", Toast.LENGTH_SHORT).show();
                    return;
                }

                Integer intervalSeconds = null;
                if (cbRepeat.isChecked()) {
                    String valueText = etIntervalValue.getText() == null ? "" : etIntervalValue.getText().toString().trim();
                    if (valueText.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写循环间隔值", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long value;
                    try {
                        value = Long.parseLong(valueText);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "循环间隔值格式错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (value <= 0) {
                        Toast.makeText(requireContext(), "循环间隔值必须大于0", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(requireContext(), "循环间隔必须在30分钟到100天之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    intervalSeconds = (int) seconds;
                }

                createPlan(cmd, apiFormat.format(specificDateHolder[0].getTime()), intervalSeconds);
                dialog.dismiss();
                return;
            }

            Pair<Long, Long> range = multiDateRangeHolder[0];
            if (range == null || range.first == null || range.second == null) {
                Toast.makeText(requireContext(), "请选择日期范围", Toast.LENGTH_SHORT).show();
                return;
            }
            if (multiTimeHolder[0] < 0 || multiTimeHolder[1] < 0) {
                Toast.makeText(requireContext(), "请选择每天执行时间", Toast.LENGTH_SHORT).show();
                return;
            }

            Calendar startUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            startUtc.setTimeInMillis(range.first);
            Calendar endUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            endUtc.setTimeInMillis(range.second);

            Calendar iter = Calendar.getInstance();
            iter.set(startUtc.get(Calendar.YEAR), startUtc.get(Calendar.MONTH), startUtc.get(Calendar.DAY_OF_MONTH),
                    multiTimeHolder[0], multiTimeHolder[1], 0);
            iter.set(Calendar.MILLISECOND, 0);

            Calendar end = Calendar.getInstance();
            end.set(endUtc.get(Calendar.YEAR), endUtc.get(Calendar.MONTH), endUtc.get(Calendar.DAY_OF_MONTH),
                    multiTimeHolder[0], multiTimeHolder[1], 0);
            end.set(Calendar.MILLISECOND, 0);

            Calendar now = Calendar.getInstance();
            List<String> sendTimes = new ArrayList<>();
            while (!iter.after(end)) {
                if (!iter.before(now)) sendTimes.add(apiFormat.format(iter.getTime()));
                iter.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (sendTimes.isEmpty()) {
                Toast.makeText(requireContext(), "所选时间均早于当前时间", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            sendMultiPlans(cmd, sendTimes, 0);
        }));

        dialog.show();
    }

    private void sendMultiPlans(String cmd, List<String> times, int index) {
        if (index >= times.size()) {
            Toast.makeText(requireContext(), "多天任务发送完成", Toast.LENGTH_SHORT).show();
            loadPlans(true);
            return;
        }

        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) return;

        new PlanAPI(requireContext()).createPlan(token, deviceId, cmd, times.get(index), null, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                sendMultiPlans(cmd, times, index + 1);
            }

            @Override
            public void onFailure(String errorMsg) {
                sendMultiPlans(cmd, times, index + 1);
            }
        });
    }

    private void createPlan(String command, String time, Integer interval) {
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new PlanAPI(requireContext()).createPlan(token, deviceId, command, time, interval, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(requireContext(), "创建成功", Toast.LENGTH_SHORT).show();
                loadPlans(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteConfirmDialog(PlanItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除计划 #" + item.getId() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> deletePlan(item.getId()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBatchDeleteConfirmDialog() {
        List<Integer> selected = adapter.getSelectedIds();
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "请先勾选要删除的计划", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("批量删除")
                .setMessage("确定删除已勾选的 " + selected.size() + " 个计划吗？")
                .setPositiveButton("删除", (dialog, which) -> deletePlansSequentially(selected, 0, 0, 0))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deletePlansSequentially(List<Integer> ids, int index, int success, int failed) {
        if (index >= ids.size()) {
            adapter.clearSelection();
            Toast.makeText(requireContext(), "批量删除完成: 成功" + success + "，失败" + failed, Toast.LENGTH_SHORT).show();
            loadPlans(true);
            return;
        }

        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            Toast.makeText(requireContext(), "登录状态或设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentId = ids.get(index);
        new PlanAPI(requireContext()).deletePlan(token, deviceId, currentId, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                deletePlansSequentially(ids, index + 1, success + 1, failed);
            }

            @Override
            public void onFailure(String errorMsg) {
                deletePlansSequentially(ids, index + 1, success, failed + 1);
            }
        });
    }

    private void deletePlan(int planId) {
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new PlanAPI(requireContext()).deletePlan(token, deviceId, planId, new PlanAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show();
                adapter.deleteItem(planId);
                updateEmptyState(adapter.getItemCount() == 0);
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "删除失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int getDeviceId() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    @Nullable
    private String getToken() {
        Context context = getContext();
        if (context == null) return null;
        return context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", null);
    }
}
