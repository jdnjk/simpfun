package cn.jdnjk.simpfun.ui.ins.mirror.rollback;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.backup.RollbackApi;

public class RollbackFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View emptyStateLayout;
    private TextView tvRollbackSummary;
    private TextView tvEmptyTitle;
    private TextView tvEmptyMessage;

    private RollbackAdapter adapter;
    private boolean isLoading = false;
    private boolean isExecutingRollback = false;
    private int requestGeneration = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_rollback, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_rollback_periods);
        emptyStateLayout = root.findViewById(R.id.rollback_empty_state_layout);
        tvRollbackSummary = root.findViewById(R.id.tv_rollback_summary);
        tvEmptyTitle = root.findViewById(R.id.tv_rollback_empty_title);
        tvEmptyMessage = root.findViewById(R.id.tv_rollback_empty_message);

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), calculateSpanCount()));
        adapter = new RollbackAdapter(this::showRollbackConfirmDialog);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadRollbackPeriods(true));

        loadRollbackPeriods(true);
        return root;
    }

    @Override
    public void onDestroyView() {
        requestGeneration++;
        isLoading = false;
        isExecutingRollback = false;
        swipeRefreshLayout = null;
        recyclerView = null;
        emptyStateLayout = null;
        tvRollbackSummary = null;
        tvEmptyTitle = null;
        tvEmptyMessage = null;
        adapter = null;
        super.onDestroyView();
    }

    private void loadRollbackPeriods(boolean showSpinner) {
        if (isLoading) {
            return;
        }
        isLoading = true;
        int generation = ++requestGeneration;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        updateSummary("正在获取可回档时间点");

        String token = getToken();
        if (token == null || token.trim().isEmpty()) {
            finishLoading();
            showMessageState("尚未登录", "请重新登录后再尝试获取回档时间点");
            showToast("尚未登录");
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            finishLoading();
            showMessageState("设备ID无效", "无法获取当前实例ID");
            showToast("设备ID无效");
            return;
        }

        new RollbackApi(requireContext()).getRollback(token, deviceId, new RollbackApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isCurrentCallback(generation)) {
                    return;
                }
                finishLoading();

                List<String> periods = new ArrayList<>();
                JSONArray arr = response.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String rollbackTime = arr.optString(i, "").trim();
                        if (!rollbackTime.isEmpty()) {
                            periods.add(rollbackTime);
                        }
                    }
                }

                periods.sort(Comparator.comparingLong(RollbackAdapter::parseRollbackEpochSecond).reversed());

                if (periods.isEmpty()) {
                    if (adapter != null) {
                        adapter.setData(periods);
                    }
                    updateSummary("暂无可用回档时间点");
                    String message = response.optString("msg", "暂无可用回档时间点");
                    showMessageState("暂无可用回档", message);
                    return;
                }

                if (adapter != null) {
                    adapter.setData(periods);
                }
                updateSummary("共 " + periods.size() + " 个可回档时间点");
                showListState();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentCallback(generation)) {
                    return;
                }
                finishLoading();
                if (adapter != null) {
                    adapter.setData(new ArrayList<>());
                }
                updateSummary("回档时间加载失败");
                String message = errorMsg == null || errorMsg.trim().isEmpty() ? "获取回档时间失败" : errorMsg;
                showMessageState("加载失败", message);
                showToast("获取回档时间失败: " + message);
            }
        });
    }

    private void showRollbackConfirmDialog(String rawTime, String displayTime) {
        if (!isAdded() || rawTime == null || rawTime.trim().isEmpty()) {
            return;
        }
        if (isExecutingRollback) {
            showToast("已有回档请求正在提交");
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("确认回档")
                .setMessage("将服务器文件回档到 " + displayTime + "，此操作可能覆盖当前文件，确认继续？")
                .setPositiveButton("确认回档", (dialog, which) -> executeRollback(rawTime))
                .setNegativeButton("取消", null)
                .show();
    }

    private void executeRollback(String rawTime) {
        if (isExecutingRollback) {
            return;
        }

        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || token.trim().isEmpty() || deviceId <= 0) {
            showToast("登录状态或设备ID无效");
            return;
        }

        isExecutingRollback = true;
        new RollbackApi(requireContext()).executeRollback(token, deviceId, rawTime, new RollbackApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) {
                    return;
                }
                isExecutingRollback = false;
                showToast(response.optString("msg", "回档请求已提交"));
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) {
                    return;
                }
                isExecutingRollback = false;
                String message = errorMsg == null || errorMsg.trim().isEmpty() ? "操作失败" : errorMsg;
                showToast("回档失败: " + message);
            }
        });
    }

    private boolean isCurrentCallback(int generation) {
        return isAdded() && generation == requestGeneration;
    }

    private void finishLoading() {
        isLoading = false;
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showListState() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showMessageState(String title, String message) {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(View.VISIBLE);
        }
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(title);
        }
        if (tvEmptyMessage != null) {
            tvEmptyMessage.setText(message);
        }
    }

    private void updateSummary(String summary) {
        if (tvRollbackSummary != null) {
            tvRollbackSummary.setText(summary);
        }
    }

    private void showToast(String msg) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int getDeviceId() {
        if (getActivity() instanceof ServerManages activity && activity.getDeviceId() > 0) {
            return activity.getDeviceId();
        }
        Context context = getContext();
        if (context == null) {
            return -1;
        }
        SharedPreferences sp = context.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    private int calculateSpanCount() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 840) return 4;
        return 2;
    }

    @Nullable
    private String getToken() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", null);
    }
}
