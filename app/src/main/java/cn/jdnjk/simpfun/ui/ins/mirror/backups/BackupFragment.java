package cn.jdnjk.simpfun.ui.ins.mirror.backups;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.utils.Feedback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.backup.MirrorApi;
import cn.jdnjk.simpfun.download.DownloadTaskController;
import cn.jdnjk.simpfun.download.FileDownloader;
import cn.jdnjk.simpfun.model.BackupItem;
import cn.jdnjk.simpfun.utils.FilePathUtils;

public class BackupFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View emptyStateLayout;
    private TextView tvBackupSummary;
    private Button btnToggleMulti;

    private BackupAdapter adapter;
    private DownloadTaskController downloadController;
    private boolean isLoading = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 必须在 onCreate 构造：内部要注册 ActivityResultLauncher（通知权限）。
        downloadController = new DownloadTaskController(this, new DownloadTaskController.Host() {
            @Override
            public Context getContextOrNull() {
                return getContext();
            }

            @Override
            public boolean isActive() {
                return !isViewDetached();
            }

            @Override
            public View getFeedbackRoot() {
                return getView();
            }

            @Override
            public int getDeviceId() {
                return BackupFragment.this.getDeviceId();
            }

            @Override
            public int getNotificationNavId() {
                return R.id.nav_backup;
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_backup, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_backups);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);
        tvBackupSummary = root.findViewById(R.id.tv_backup_summary);
        btnToggleMulti = root.findViewById(R.id.btn_toggle_multi);
        FloatingActionButton btnNewBackup = root.findViewById(R.id.btn_new_backup);

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), calculateSpanCount()));

        adapter = new BackupAdapter(this::updateActionButtons, this::showBackupActionMenu);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadBackups(true));

        btnToggleMulti.setOnClickListener(v -> handleSelectionButtonClick());
        btnNewBackup.setOnClickListener(v -> showCreateBackupDialog());

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 首次加载放在 onViewCreated：getView() 要等 onCreateView 返回后才有值，
        // 在 onCreateView 里调用会被 loadBackups 开头的 isViewDetached() 挡掉，
        // 结果备份列表首次进入永远是空的。
        loadBackups(true);
    }

    /** 返回 true 表示视图已销毁，调用点一律 {@code if (isViewDetached()) return;}。 */
    private boolean isViewDetached() {
        return !isAdded() || getView() == null || getContext() == null;
    }

    @Override
    public void onDestroyView() {
        isLoading = false;
        if (downloadController != null) {
            downloadController.onDestroyView();
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(null);
        }
        swipeRefreshLayout = null;
        recyclerView = null;
        emptyStateLayout = null;
        tvBackupSummary = null;
        btnToggleMulti = null;
        adapter = null;
        super.onDestroyView();
    }

    private void handleSelectionButtonClick() {
        if (adapter == null) return;
        if (adapter.isMultiSelectMode() && adapter.getSelectedCount() > 0) {
            showSelectionMenu();
            return;
        }
        toggleMultiMode();
    }

    private void toggleMultiMode() {
        if (adapter == null) return;
        boolean nextMode = !adapter.isMultiSelectMode();
        adapter.setMultiSelectMode(nextMode);
        updateActionButtons(adapter.getSelectedCount());
    }

    private void showSelectionMenu() {
        if (isViewDetached() || btnToggleMulti == null) return;
        PopupMenu popupMenu = new PopupMenu(requireContext(), btnToggleMulti);
        popupMenu.getMenu().add("删除所选");
        popupMenu.getMenu().add("完成选择");
        popupMenu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("删除所选".equals(title)) {
                deleteSelectedBackups();
            } else if ("完成选择".equals(title)) {
                toggleMultiMode();
            }
            return true;
        });
        popupMenu.show();
    }

    private void loadBackups(boolean showSpinner) {
        if (isLoading) {
            return;
        }
        if (isViewDetached()) {
            return;
        }
        isLoading = true;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        String token = getToken();
        if (token == null) {
            finishLoading();
            updateSummary(-1);
            showMessage("尚未登录", true);
            return;
        }

        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            finishLoading();
            updateSummary(-1);
            showMessage("设备ID无效", true);
            return;
        }

        new MirrorApi(requireContext()).getBackups(token, deviceId, new MirrorApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                finishLoading();

                List<BackupItem> items = new ArrayList<>();
                JSONArray arr = response.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj == null) {
                            continue;
                        }
                        items.add(new BackupItem(
                                obj.optInt("id"),
                                obj.optInt("status"),
                                obj.optLong("size", 0L),
                                obj.optString("valid_time", ""),
                                obj.optString("tag", ""),
                                obj.optBoolean("is_windows", false)
                        ));
                    }
                }

                adapter.setData(items);
                updateSummary(items.size());
                updateEmptyState(items.isEmpty());
            }

            @Override
            public void onFailure(String errorMsg) {
                finishLoading();
                updateSummary(-1);
                Feedback.error(getView(), "获取备份失败: " + errorMsg,
                        "重试", () -> loadBackups(true));
            }
        });
    }

    private void showBackupActionMenu(BackupItem item, View anchor) {
        if (isViewDetached()) return;
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.getMenu().add("还原");
        popupMenu.getMenu().add("重命名");
        popupMenu.getMenu().add("下载");
        popupMenu.getMenu().add("删除");
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            String title = String.valueOf(menuItem.getTitle());
            if ("还原".equals(title)) {
                restoreBackup(item);
            } else if ("重命名".equals(title)) {
                renameBackup(item);
            } else if ("下载".equals(title)) {
                downloadBackup(item);
            } else if ("删除".equals(title)) {
                confirmDeleteSingleBackup(item);
            }
            return true;
        });
        popupMenu.show();
    }

    private void restoreBackup(BackupItem selected) {
        if (isViewDetached()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("确认还原")
                .setMessage("将还原到备份 #" + selected.getId() + "，确认继续？")
                .setPositiveButton("确认", (d, w) -> {
                    String token = getToken();
                    int deviceId = getDeviceId();
                    if (token == null || deviceId <= 0) {
                        showMessage("登录状态或设备ID无效", true);
                        return;
                    }

                    new MirrorApi(requireContext()).restoreBackup(token, deviceId, selected.getId(), new MirrorApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            showMessage("还原请求已提交", false);
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            if (isViewDetached()) return;
                            Feedback.error(getView(), "还原失败: " + errorMsg,
                                    "重试", () -> restoreBackup(selected));
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameBackup(BackupItem selected) {
        if (isViewDetached()) return;
        final EditText input = new EditText(requireContext());
        input.setText(selected.getTag());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
                .setTitle("重命名备份")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newTag = input.getText() == null ? "" : input.getText().toString().trim();
                    if (newTag.isEmpty()) {
                        showMessage("备份名称不能为空", true);
                        return;
                    }

                    String token = getToken();
                    int deviceId = getDeviceId();
                    if (token == null || deviceId <= 0) {
                        showMessage("登录状态或设备ID无效", true);
                        return;
                    }

                    new MirrorApi(requireContext()).renameBackup(token, deviceId, selected.getId(), newTag, new MirrorApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            if (isViewDetached()) return;
                            showMessage("重命名成功", false);
                            loadBackups(false);
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            showMessage("重命名失败: " + errorMsg, true);
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadBackup(BackupItem selected) {
        if (isViewDetached()) {
            return;
        }
        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            showMessage("登录状态或设备ID无效", true);
            return;
        }
        if (downloadController.isBusy()) {
            showMessage(getString(R.string.download_busy), true);
            return;
        }

        // 备份下载分两步：先向 API 换一次性 uuid，再由下载引擎流式写入用户选定的位置。
        // 落盘名优先用服务器 Content-Disposition 下发的文件名（zip），拿不到再用回退名。
        downloadController.start(buildDownloadFileName(selected), (response, fallbackName) ->
                        FileDownloader.parseContentDispositionFilename(response),
                callback ->
                new MirrorApi(requireContext()).getDownloadKey(token, deviceId, selected.getId(),
                        new MirrorApi.Callback() {
                            @Override
                            public void onSuccess(JSONObject response) {
                                String uuid = response.optString("uuid", "");
                                if (uuid.isEmpty()) {
                                    callback.onFailure("下载密钥为空");
                                    return;
                                }
                                callback.onUrl(MirrorApi.buildBackupDownloadUrl(uuid));
                            }

                            @Override
                            public void onFailure(String errorMsg) {
                                callback.onFailure("获取下载密钥失败: " + errorMsg);
                            }
                        }));
    }

    private String buildDownloadFileName(BackupItem item) {
        String fallback = "backup-" + item.getId();
        String name = FilePathUtils.sanitizeFileName(item.getTag(), fallback);
        // 备份是 zip：若 tag 没带扩展名则补 .zip，避免存成打不开的无后缀/错误后缀文件。
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".zip") ? name : name + ".zip";
    }

    private void confirmDeleteSingleBackup(BackupItem item) {
        if (isViewDetached()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定删除备份 “" + getBackupDisplayName(item) + "” 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    List<BackupItem> selected = new ArrayList<>();
                    selected.add(item);
                    deleteBackupsConcurrent(selected);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteSelectedBackups() {
        if (isViewDetached() || adapter == null) return;
        List<BackupItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            showMessage("请先选择备份", true);
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定删除已选中的 " + selected.size() + " 个备份吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteBackupsConcurrent(selected))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteBackupsConcurrent(List<BackupItem> selected) {
        if (isViewDetached()) return;
        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            showMessage("登录状态或设备ID无效", true);
            return;
        }

        AtomicInteger finished = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total = selected.size();
        MirrorApi api = new MirrorApi(requireContext());

        for (BackupItem item : selected) {
            api.deleteBackup(token, deviceId, item.getId(), new MirrorApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    success.incrementAndGet();
                    if (finished.incrementAndGet() == total) {
                        if (isViewDetached()) return;
                        showMessage("删除完成: 成功" + success.get() + "，失败" + failed.get(), false);
                        loadBackups(false);
                    }
                }

                @Override
                public void onFailure(String errorMsg) {
                    failed.incrementAndGet();
                    if (finished.incrementAndGet() == total) {
                        if (isViewDetached()) return;
                        showMessage("删除完成: 成功" + success.get() + "，失败" + failed.get(), false);
                        loadBackups(false);
                    }
                }
            });
        }
    }

    private void showCreateBackupDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("请输入备份名称");

        new AlertDialog.Builder(requireContext())
                .setTitle("创建备份")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String tag = input.getText() == null ? "" : input.getText().toString().trim();
                    if (tag.isEmpty()) {
                        showMessage("备份名称不能为空",true);
                        return;
                    }

                    String token = getToken();
                    int deviceId = getDeviceId();
                    if (token == null || deviceId <= 0) {
                        showMessage("登录状态或设备ID无效", true);
                        return;
                    }

                    new MirrorApi(requireContext()).createBackup(token, deviceId, tag, new MirrorApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            if (isViewDetached()) return;
                            String msg = response.optString("msg", "备份任务创建成功");
                            showMessage(msg, false);
                            loadBackups(false);
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            showMessage("创建备份失败: " + errorMsg, true);
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getBackupDisplayName(BackupItem item) {
        String tag = item.getTag() == null ? "" : item.getTag().trim();
        if (!tag.isEmpty()) {
            return tag;
        }
        return "#" + item.getId();
    }

    private void updateActionButtons(int selectedCount) {
        if (btnToggleMulti == null) {
            return;
        }
        boolean selecting = adapter != null && adapter.isMultiSelectMode();
        btnToggleMulti.setText(selecting ? "完成" : "选择");
    }

    private void finishLoading() {
        isLoading = false;
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (recyclerView == null || emptyStateLayout == null) {
            return;
        }
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void updateSummary(int backupCount) {
        if (tvBackupSummary == null) {
            return;
        }
        if (backupCount < 0) {
            tvBackupSummary.setText("备份列表加载失败");
        } else if (backupCount == 0) {
            tvBackupSummary.setText("暂无备份");
        } else {
            tvBackupSummary.setText("共 " + backupCount + " 个备份");
        }
    }

    private void showMessage(String msg, boolean isError) {
        if (isViewDetached()) {
            return;
        }
        if (isError) {
            Feedback.error(getView(), msg);
        } else {
            Feedback.info(getView(), msg);
        }
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

    private int calculateSpanCount() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 840) return 3;
        if (widthDp >= 600) return 2;
        return 1;
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
