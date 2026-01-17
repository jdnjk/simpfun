package cn.jdnjk.simpfun.ui.ins.tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.TasksApi;
import cn.jdnjk.simpfun.model.TaskItem;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class TasksFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private TextView textCounts;
    private TextView textCountsPro;
    private TasksAdapter adapter;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private boolean isPolling = false;
    private boolean isLoading = false;
    private static final long POLL_INTERVAL_MS = 5000L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_tasks, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_tasks);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);
        textCounts = root.findViewById(R.id.text_counts);
        textCountsPro = root.findViewById(R.id.text_counts_pro);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TasksAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadTasks(true));

        loadTasks(true);

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                loadTasks(false);
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        startPolling();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPolling();
        pollHandler.removeCallbacksAndMessages(null);
    }

    private void startPolling() {
        if (isPolling || pollRunnable == null) return;
        isPolling = true;
        // 下次在5秒后触发（避免与初次加载重叠）
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (!isPolling) return;
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void loadTasks() { loadTasks(true); }

    private void loadTasks(boolean showSpinner) {
        if (isLoading) return; // 防止并发请求
        isLoading = true;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId <= 0) {
            if (showSpinner && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
            isLoading = false;
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new TasksApi().getTasks(requireContext(), deviceId, new TasksApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    int running = data.optInt("running", 0);
                    int waiting = data.optInt("waiting", 0);
                    int firstWaiting = data.optInt("num_first_waiting", -1);
                    int runningPro = data.optInt("running_pro", 0);
                    int waitingPro = data.optInt("waiting_pro", 0);
                    int firstWaitingPro = data.optInt("num_first_waiting_pro", -1);
                    boolean isPro = data.optBoolean("is_pro", false);

                    String countsText = getString(R.string.tasks_counts, running, waiting, firstWaiting);
                    textCounts.setText(countsText);
                    String proSuffix = isPro ? getString(R.string.tasks_pro_suffix) : "";
                    String countsProText = getString(R.string.tasks_counts_pro, runningPro, waitingPro, firstWaitingPro, proSuffix);
                    textCountsPro.setText(countsProText);

                    JSONArray list = data.optJSONArray("list");
                    List<TaskItem> items = new ArrayList<>();
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject obj = list.optJSONObject(i);
                            if (obj == null) continue;
                            items.add(new TaskItem(
                                    obj.optInt("id", 0),
                                    obj.optInt("status", 0),
                                    obj.optString("comment", ""),
                                    obj.optString("create_time", "")
                            ));
                        }
                    }
                    adapter.setData(items);

                    if (items.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        emptyStateLayout.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyStateLayout.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "解析任务数据失败", Toast.LENGTH_SHORT).show();
                } finally {
                    if (showSpinner && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "获取任务失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                if (showSpinner && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
                isLoading = false;
            }
        });
    }
}