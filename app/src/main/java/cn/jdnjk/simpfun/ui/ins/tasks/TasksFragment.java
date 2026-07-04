package cn.jdnjk.simpfun.ui.ins.tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
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

    private int cancelableTaskId = -1;
    private int proQueueTaskId = -1;
    private boolean isTaskActionRunning = false;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupToolbarTaskMenu();
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

    private void setupToolbarTaskMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem cancelItem = menu.add(Menu.NONE, R.id.action_task_cancel, 0, R.string.task_cancel);
                cancelItem.setIcon(R.drawable.ic_task_cancel_24);
                cancelItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

                MenuItem proItem = menu.add(Menu.NONE, R.id.action_task_pro_queue, 1, R.string.task_move_to_pro_queue);
                proItem.setIcon(R.drawable.ic_rocket_24);
                proItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

                updateTaskActionMenu(menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                updateTaskActionMenu(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_task_cancel) {
                    showCancelTaskConfirmDialog(cancelableTaskId);
                    return true;
                }
                if (itemId == R.id.action_task_pro_queue) {
                    showProQueueConfirmDialog(proQueueTaskId);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void updateTaskActionMenu(Menu menu) {
        MenuItem cancelItem = menu.findItem(R.id.action_task_cancel);
        if (cancelItem != null) {
            cancelItem.setVisible(cancelableTaskId > 0);
            cancelItem.setEnabled(!isTaskActionRunning);
        }

        MenuItem proItem = menu.findItem(R.id.action_task_pro_queue);
        if (proItem != null) {
            proItem.setVisible(proQueueTaskId > 0);
            proItem.setEnabled(!isTaskActionRunning);
        }
    }

    private void invalidateToolbarMenu() {
        if (isAdded()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void loadTasks() { loadTasks(true); }

    private void loadTasks(boolean showSpinner) {
        if (isLoading) return; // 防止并发请求

        Context context = getContext();
        if (context == null) return;

        isLoading = true;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        Context appContext = context.getApplicationContext();
        SharedPreferences sp = appContext.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId <= 0) {
            if (showSpinner && swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
            isLoading = false;
            Toast.makeText(appContext, "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new TasksApi().getTasks(appContext, deviceId, new TasksApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    if (!isViewActive()) return;

                    int running = data.optInt("running", 0);
                    int waiting = data.optInt("waiting", 0);
                    int firstWaiting = data.optInt("num_first_waiting", -1);
                    int runningPro = data.optInt("running_pro", 0);
                    int waitingPro = data.optInt("waiting_pro", 0);
                    int firstWaitingPro = data.optInt("num_first_waiting_pro", -1);
                    boolean isPro = data.optBoolean("is_pro", false);

                    String countsText = getString(R.string.tasks_counts, running, waiting, firstWaiting);
                    if (textCounts != null) textCounts.setText(countsText);
                    String proSuffix = isPro ? getString(R.string.tasks_pro_suffix) : "";
                    String countsProText = getString(R.string.tasks_counts_pro, runningPro, waitingPro, firstWaitingPro, proSuffix);
                    if (textCountsPro != null) textCountsPro.setText(countsProText);

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
                    if (adapter != null) adapter.setData(items);
                    updateTaskActionState(items, running, isPro);

                    if (items.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        emptyStateLayout.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyStateLayout.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Toast.makeText(appContext, "解析任务数据失败", Toast.LENGTH_SHORT).show();
                } finally {
                    if (showSpinner && swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (isViewActive()) {
                    Toast.makeText(appContext, "获取任务失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                    if (showSpinner && swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
                }
                isLoading = false;
            }
        });
    }

    private void updateTaskActionState(List<TaskItem> items, int runningCount, boolean isPro) {
        int newCancelableTaskId = -1;
        int newProQueueTaskId = -1;
        boolean hasRunningTask = false;

        for (TaskItem item : items) {
            if (item.getStatus() != -1) continue;
            hasRunningTask = true;
            if (newProQueueTaskId <= 0) {
                newProQueueTaskId = item.getId();
            }
            String comment = item.getComment() == null ? "" : item.getComment();
            if (newCancelableTaskId <= 0 && comment.contains("解压缩文件")) {
                newCancelableTaskId = item.getId();
            }
        }

        cancelableTaskId = newCancelableTaskId;
        proQueueTaskId = !isPro && (runningCount > 0 || hasRunningTask) ? newProQueueTaskId : -1;
        invalidateToolbarMenu();
    }

    private void showCancelTaskConfirmDialog(int taskId) {
        if (taskId <= 0 || isTaskActionRunning) return;
        Context context = getContext();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle("取消任务")
                .setMessage("确定取消正在运行的解压缩任务 #" + taskId + " 吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> cancelTask(taskId))
                .show();
    }

    private void showProQueueConfirmDialog(int taskId) {
        if (taskId <= 0 || isTaskActionRunning) return;
        Context context = getContext();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle("变更Pro队列")
                .setMessage("确定将正在运行的任务 #" + taskId + " 变更至Pro队列吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> moveTaskToProQueue(taskId))
                .show();
    }

    private void cancelTask(int taskId) {
        runTaskAction(taskId, true);
    }

    private void moveTaskToProQueue(int taskId) {
        runTaskAction(taskId, false);
    }

    private void runTaskAction(int taskId, boolean cancel) {
        Context context = getContext();
        if (context == null || isTaskActionRunning) return;

        Context appContext = context.getApplicationContext();
        SharedPreferences sp = appContext.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId <= 0) {
            Toast.makeText(appContext, "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        isTaskActionRunning = true;
        invalidateToolbarMenu();
        TasksApi api = new TasksApi();
        TasksApi.Callback callback = new TasksApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewActive()) return;
                isTaskActionRunning = false;
                Toast.makeText(appContext, data.optString("msg", cancel ? "取消任务成功" : "变更至Pro队列成功"), Toast.LENGTH_SHORT).show();
                loadTasks(true);
                invalidateToolbarMenu();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isViewActive()) return;
                isTaskActionRunning = false;
                Toast.makeText(appContext, (cancel ? "取消任务失败: " : "变更Pro队列失败: ") + errorMsg, Toast.LENGTH_LONG).show();
                invalidateToolbarMenu();
            }
        };

        if (cancel) {
            api.cancelTask(appContext, deviceId, taskId, callback);
        } else {
            api.moveTaskToProQueue(appContext, deviceId, taskId, callback);
        }
    }

    private boolean isViewActive() {
        return isAdded() && getView() != null;
    }
}