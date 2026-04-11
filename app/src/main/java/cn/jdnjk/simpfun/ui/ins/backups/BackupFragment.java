package cn.jdnjk.simpfun.ui.ins.backups;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.backup.MirrorApi;
import cn.jdnjk.simpfun.model.BackupItem;

public class BackupFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private Button btnToggleMulti;
    private Button btnRestore;
    private Button btnRename;
    private Button btnDownload;
    private Button btnDelete;

    private BackupAdapter adapter;
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_backup, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_backups);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);
        btnToggleMulti = root.findViewById(R.id.btn_toggle_multi);
        Button btnNewBackup = root.findViewById(R.id.btn_new_backup);
        btnRestore = root.findViewById(R.id.btn_restore);
        btnRename = root.findViewById(R.id.btn_rename);
        btnDownload = root.findViewById(R.id.btn_download);
        btnDelete = root.findViewById(R.id.btn_delete);

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), calculateSpanCount()));

        adapter = new BackupAdapter(this::updateActionButtons);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadBackups(true));

        btnToggleMulti.setOnClickListener(v -> toggleMultiMode());
        btnNewBackup.setOnClickListener(v -> showCreateBackupDialog());

        btnRestore.setOnClickListener(v -> restoreSelectedBackup());
        btnRename.setOnClickListener(v -> renameSelectedBackup());
        btnDownload.setOnClickListener(v -> downloadSelectedBackup());
        btnDelete.setOnClickListener(v -> deleteSelectedBackups());

        loadBackups(true);
        return root;
    }

    private void toggleMultiMode() {
        boolean nextMode = !adapter.isMultiSelectMode();
        adapter.setMultiSelectMode(nextMode);
        btnToggleMulti.setText(nextMode ? "完成" : "多选");
    }

    private void loadBackups(boolean showSpinner) {
        if (isLoading) {
            return;
        }
        isLoading = true;

        if (showSpinner && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        String token = getToken();
        if (token == null || token.trim().isEmpty()) {
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
                updateEmptyState(items.isEmpty());
            }

            @Override
            public void onFailure(String errorMsg) {
                finishLoading();
                showToast("获取备份失败: " + errorMsg);
            }
        });
    }

    private void restoreSelectedBackup() {
        BackupItem selected = getSingleSelected("还原");
        if (selected == null) {
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("确认还原")
                .setMessage("将还原到备份 #" + selected.getId() + "，确认继续？")
                .setPositiveButton("确认", (d, w) -> {
                    String token = getToken();
                    int deviceId = getDeviceId();
                    if (token == null || deviceId <= 0) {
                        showToast("登录状态或设备ID无效");
                        return;
                    }

                    new MirrorApi(requireContext()).restoreBackup(token, deviceId, selected.getId(), new MirrorApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            showToast("还原请求已提交");
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            showToast("还原失败: " + errorMsg);
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameSelectedBackup() {
        BackupItem selected = getSingleSelected("重命名");
        if (selected == null) {
            return;
        }

        final EditText input = new EditText(requireContext());
        input.setText(selected.getTag());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
                .setTitle("重命名备份")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newTag = input.getText() == null ? "" : input.getText().toString().trim();
                    if (newTag.isEmpty()) {
                        showToast("备份名称不能为空");
                        return;
                    }

                    String token = getToken();
                    int deviceId = getDeviceId();
                    if (token == null || deviceId <= 0) {
                        showToast("登录状态或设备ID无效");
                        return;
                    }

                    new MirrorApi(requireContext()).renameBackup(token, deviceId, selected.getId(), newTag, new MirrorApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            showToast("重命名成功");
                            loadBackups(false);
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            showToast("重命名失败: " + errorMsg);
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadSelectedBackup() {
        BackupItem selected = getSingleSelected("下载");
        if (selected == null) {
            return;
        }

        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            showToast("登录状态或设备ID无效");
            return;
        }

        new MirrorApi(requireContext()).getDownloadKey(token, deviceId, selected.getId(), new MirrorApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String uuid = response.optString("uuid", "");
                if (uuid.isEmpty()) {
                    showToast("下载密钥为空");
                    return;
                }
                enqueueSystemDownload(selected, uuid);
            }

            @Override
            public void onFailure(String errorMsg) {
                showToast("获取下载密钥失败: " + errorMsg);
            }
        });
    }

    private void enqueueSystemDownload(BackupItem item, String uuid) {
        try {
            DownloadManager downloadManager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                showToast("系统下载器不可用");
                return;
            }

            String downloadUrl = "https://sfe4-connect.simpfun.cn:1000/download?uuid=" + Uri.encode(uuid);
            String fileName = buildDownloadFileName(item);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle(fileName);
            request.setDescription("正在下载备份");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setMimeType("application/octet-stream");
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            downloadManager.enqueue(request);
            showToast("已加入系统下载器");
        } catch (Exception e) {
            showToast("下载失败: " + e.getMessage());
        }
    }

    private String buildDownloadFileName(BackupItem item) {
        String tag = item.getTag() == null ? "" : item.getTag().trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (tag.isEmpty()) {
            tag = "backup-" + item.getId();
        }
        return tag + ".bin";
    }

    private void deleteSelectedBackups() {
        List<BackupItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            showToast("请先选择备份");
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
        String token = getToken();
        int deviceId = getDeviceId();
        if (token == null || deviceId <= 0) {
            showToast("登录状态或设备ID无效");
            return;
        }

        AtomicInteger finished = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total = selected.size();
        MirrorApi api = new MirrorApi(requireContext());

        for (BackupItem item : selected) {
            new Thread(() -> api.deleteBackup(token, deviceId, item.getId(), new MirrorApi.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    success.incrementAndGet();
                    if (finished.incrementAndGet() == total) {
                        showToast("删除完成: 成功" + success.get() + "，失败" + failed.get());
                        loadBackups(false);
                    }
                }

                @Override
                public void onFailure(String errorMsg) {
                    failed.incrementAndGet();
                    if (finished.incrementAndGet() == total) {
                        showToast("删除完成: 成功" + success.get() + "，失败" + failed.get());
                        loadBackups(false);
                    }
                }
            })).start();
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
                        showToast("备份名称不能为空");
                        return;
                    }

                    String token = getToken();
                    int deviceId = getDeviceId();
                    if (token == null || deviceId <= 0) {
                        showToast("登录状态或设备ID无效");
                        return;
                    }

                    new MirrorApi(requireContext()).createBackup(token, deviceId, tag, new MirrorApi.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            String msg = response.optString("msg", "备份任务创建成功");
                            showToast(msg);
                            loadBackups(false);
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            showToast("创建备份失败: " + errorMsg);
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Nullable
    private BackupItem getSingleSelected(String actionName) {
        List<BackupItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            showToast("请先选择备份");
            return null;
        }
        if (selected.size() > 1) {
            showToast(actionName + "仅支持单个备份");
            return null;
        }
        return selected.get(0);
    }

    private void updateActionButtons(int selectedCount) {
        boolean single = selectedCount == 1;
        btnRestore.setEnabled(single);
        btnRename.setEnabled(single);
        btnDownload.setEnabled(single);
        btnDelete.setEnabled(selectedCount > 0);
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

    private void showToast(String msg) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int getDeviceId() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    private int calculateSpanCount() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 720) return 3;
        if (widthDp >= 360) return 2;
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
