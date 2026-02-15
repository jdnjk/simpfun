package cn.jdnjk.simpfun.ui.ins.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import cn.jdnjk.simpfun.FileEditorActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import androidx.appcompat.widget.Toolbar;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.api.ins.file.FileTransferApi;
import cn.jdnjk.simpfun.api.ins.file.FileManageApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.graphics.Color;

/**
 * 单个文件浏览面板 Fragment，可被 DualFileBrowserFragment 两次实例化实现双栏浏览。
 * FileListFragment：保留基础列表、导航与文件打开功能。
 */
public class FilePaneFragment extends Fragment {
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout pathContainer; // 新增面包屑容器
    private HorizontalScrollView pathScrollView;
    private Toolbar toolbar;
    private FloatingActionButton fabAdd;
    private View actionRefresh, actionSearch;

    private final List<FileItem> fileList = new ArrayList<>();
    private FileAdapter adapter;
    private String currentPath = "/";
    private static final String PARENT_DIR_NAME = "..";
    private static final String ARG_INITIAL_PATH = "initial_path";
    private static final String TAG = "FilePaneFragment";

    private ActivityResultLauncher<android.content.Intent> editorLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_file_pane, container, false);
        fabAdd = v.findViewById(R.id.fab_add);

        // 初始化原组件
        recyclerView = v.findViewById(R.id.recycler_view_files);
        progressBar = v.findViewById(R.id.progress_bar);
        emptyView = v.findViewById(R.id.empty_view);
        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_layout);

        // 初始化面包屑导航
        pathContainer = v.findViewById(R.id.layout_path_container);
        pathScrollView = v.findViewById(R.id.scroll_view_path);

        // 配置 Toolbar 导航：当不在根目录时先返回上级，否则交由 Activity 返回
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v1 -> {
                if (!"/".equals(currentPath)) {
                    // 返回上一级目录
                    currentPath = getParentPath(currentPath);
                    updatePathView(); // 更新面包屑
                    loadFileList();
                } else {
                    // 已在根目录，退出
                    requireActivity().onBackPressed();
                }
            });
        }

        // 顶部操作：刷新
        if (actionRefresh != null) {
            actionRefresh.setOnClickListener(v12 -> loadFileList());
        }
        // 顶部操作：搜索（本地过滤）
        if (actionSearch != null) {
            actionSearch.setOnClickListener(v13 -> showSearchDialog());
        }

        // 初始化路径与列表
        if (getArguments() != null) {
            String init = getArguments().getString(ARG_INITIAL_PATH, "/");
            if (init != null && !init.trim().isEmpty()) {
                currentPath = sanitizePath(init.trim());
            }
        }

        adapter = new FileAdapter(fileList, this::onFileItemClick, this::onFileItemLongClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        // 路径栏点击可编辑 -> 移至面包屑末尾或长按面包屑
        // pathView.setOnClickListener(v1 -> showEditPathDialog());

        updatePathView(); // 初始化面包屑
        loadFileList();

        // 注册文件选择器回调
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handleSelectedFile(uri);
            }
        });

        // 注册编辑器结果回调（保持原逻辑）
        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                android.content.Intent data = result.getData();
                String localPath = data.getStringExtra("local_path");
                String remotePath = data.getStringExtra("remote_path");
                int serverId = data.getIntExtra("server_id", -1);
                if (localPath != null && remotePath != null && serverId > 0) {
                    java.io.File file = new java.io.File(localPath);
                    // 提取目录部分作为上传目标目录
                    String remoteDir = getParentPath(remotePath);
                    new FileTransferApi().uploadFile(requireContext(), serverId, remoteDir, file, new FileCallback() {
                        @Override public void onSuccess(org.json.JSONObject resp) {
                            Toast.makeText(requireContext(), "上传成功", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(String errorMsg) {
                            Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    Toast.makeText(requireContext(), "保存结果无上传配置", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // FAB 操作：弹出新建选项
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v14 -> showCreateOptionsDialog());
        }

        return v;
    }

    /**
     * 显示新建选项对话框（替代原底部按钮）
     */
    private void showCreateOptionsDialog() {
        String[] options = {getString(R.string.new_file), getString(R.string.new_folder), "上传文件"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_file_title) // 复用标题，或新建通用标题
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCreateEntryDialog(true);
                    } else if (which == 1) {
                        showCreateEntryDialog(false);
                    } else {
                        filePickerLauncher.launch("*/*");
                    }
                })
                .show();
    }

    private void handleSelectedFile(android.net.Uri uri) {
        try {
            Context context = requireContext();
            String fileName = getFileName(uri);
            if (fileName == null) fileName = "uploaded_file";

            java.io.File tempFile = new java.io.File(context.getCacheDir(), fileName);
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                if (is == null) throw new IOException("无法打开输入流");
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            uploadSelectedFile(tempFile);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "准备上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(android.net.Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void uploadSelectedFile(java.io.File file) {
        // 禁止上传超过 1000MB 的文件
        long maxSizeInBytes = 1000L * 1024L * 1024L;
        if (file.length() > maxSizeInBytes) {
            Toast.makeText(requireContext(), "文件超过1000MB，请使用SFTP上传较大文件", Toast.LENGTH_LONG).show();
            if (!file.delete()) Log.w(TAG, "Failed to delete oversized temp file: " + file.getName());
            return;
        }

        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int serverId = sp.getInt("device_id", -1);
        if (serverId <= 0) {
            Toast.makeText(requireContext(), "未找到服务器ID", Toast.LENGTH_SHORT).show();
            if (!file.delete()) Log.w(TAG, "Failed to delete temp file (no server ID): " + file.getName());
            return;
        }

        LinearProgressIndicator progressIndicator = new LinearProgressIndicator(requireContext());
        progressIndicator.setIndeterminate(true);
        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("正在上传文件...")
                .setView(progressIndicator)
                .setCancelable(false)
                .show();

        new FileTransferApi().uploadFile(requireContext(), serverId, currentPath, file, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "上传成功", Toast.LENGTH_SHORT).show();
                if (!file.delete()) Log.w(TAG, "Failed to delete temp file: " + file.getName());
                loadFileList(); // 刷新列表
            }

            @Override
            public void onFailure(String errorMsg) {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                if (!file.delete()) Log.w(TAG, "Failed to delete temp file: " + file.getName());
            }
        });
    }

    /**
     * 显示搜索对话框，本地过滤名称包含关键字。
     * Compose 风格：简洁交互，无需新页面
     */
    private void showSearchDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext());
        b.setTitle(getString(R.string.search_title));
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.search_hint);
        input.setSingleLine(true);
        b.setView(input);
        b.setPositiveButton(R.string.confirm, (d, w) -> {
            String key = input.getText().toString().trim();
            if (key.isEmpty()) {
                adapter.notifyDataSetChanged();
                return;
            }
            // 简易过滤：生成一个临时列表并展示
            List<FileItem> filtered = new ArrayList<>();
            for (FileItem fi : fileList) {
                if (fi.name.toLowerCase().contains(key.toLowerCase())) {
                    filtered.add(fi);
                }
            }
            recyclerView.setAdapter(new FileAdapter(filtered, this::onFileItemClick, this::onFileItemLongClick));
        });
        b.setNegativeButton(R.string.cancel, (d, w) -> {
            // 取消恢复原列表适配器
            recyclerView.setAdapter(adapter);
        });
        b.show();
    }

    /**
     * 新建文件或文件夹对话框
     * 若无服务端创建 API，则在本地创建空文件并提示；可后续由上传逻辑补齐。
     */
    private void showCreateEntryDialog(boolean file) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext());
        b.setTitle(file ? getString(R.string.create_file_title) : getString(R.string.create_folder_title));
        final EditText input = new EditText(requireContext());
        input.setHint(file ? getString(R.string.create_file_hint) : getString(R.string.create_folder_hint));
        input.setSingleLine(true);
        b.setView(input);
        b.setPositiveButton(R.string.confirm, (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            String target = appendPath(currentPath, name);
            // TODO: 如有服务端 API，可在此调用创建接口；以下为本地占位实现
            try {
                File localDir = requireContext().getExternalFilesDir("temp_create");
                if (localDir != null && !localDir.exists()) localDir.mkdirs();
                File f = new File(localDir, name);
                if (file) {
                    if (f.createNewFile()) {
                        Toast.makeText(requireContext(), "已在本地创建占位文件", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "创建失败或已存在", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (f.mkdirs()) {
                        Toast.makeText(requireContext(), "已在本地创建占位文件夹", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "创建失败或已存在", Toast.LENGTH_SHORT).show();
                    }
                }
                // 列表刷新（实际环境应从服务端拉取）
                loadFileList();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "创建失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private void showEditPathDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(requireContext());
        b.setTitle(getString(R.string.jump_path_title));
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(currentPath);
        input.setSingleLine(true);
        b.setView(input);
        b.setPositiveButton(R.string.confirm, (d, w) -> {
            String p = input.getText().toString().trim();
            if (p.isEmpty()) p = "/";
            currentPath = sanitizePath(p);
            updatePathView(); // 更新面包屑
            loadFileList();
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private String sanitizePath(String p) {
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }

    private void onFileItemClick(FileItem item) {
        if (PARENT_DIR_NAME.equals(item.name)) {
            currentPath = getParentPath(currentPath);
            updatePathView(); // 更新面包屑
            loadFileList();
        } else if (item.file) {
            downloadAndOpenFile(item);
        } else {
            currentPath = appendPath(currentPath, item.name);
            updatePathView(); // 更新面包屑
            loadFileList();
        }
    }

    private void onFileItemLongClick(FileItem item) {
        if (PARENT_DIR_NAME.equals(item.name)) return;
        if (!isAdded()) return;
        showFileActionDialog(item);
    }

    private void showFileActionDialog(FileItem item) {
        if (!isAdded()) return;
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_Simpfun_BottomSheet);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_file_actions, null, false);

        TextView title = view.findViewById(R.id.text_view_title);
        if (title != null) title.setText(item.name);

        View deleteAction = view.findViewById(R.id.action_delete);
        if (deleteAction != null) {
            deleteAction.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteConfirmDialog(item);
            });
        }

        View renameAction = view.findViewById(R.id.action_rename);
        if (renameAction != null) {
            renameAction.setOnClickListener(v -> {
                dialog.dismiss();
                showRenameDialog(item);
            });
        }

        // 更多操作可在此添加，如复制、移动、压缩等

        dialog.setContentView(view);

        if (!activity.isFinishing() && !activity.isDestroyed()) {
            dialog.show();
        }
    }


    private void showDeleteConfirmDialog(FileItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除确认")
                .setMessage("确定要删除 " + item.name + " 吗？")
                .setPositiveButton("删除", (d, w) -> {
                    deleteFile(item);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteFile(FileItem item) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId == -1) return;

        List<String> paths = new ArrayList<>();
        paths.add(appendPath(currentPath, item.name));

        new FileManageApi().deleteFileOrFolderBatch(requireContext(), deviceId, paths, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "删除失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showRenameDialog(FileItem item) {
        EditText input = new EditText(requireContext());
        input.setText(item.name);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(item.name)) {
                        renameFile(item, newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameFile(FileItem item, String newName) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId == -1) return;

        String origin = appendPath(currentPath, item.name);
        String target = appendPath(currentPath, newName);

        new FileManageApi().renameFile(requireContext(), deviceId, origin, target, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "重命名失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadFileList() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId == -1) {
            showError("设备ID无效");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        showLoading(true);
        new FileApi().getFileList(requireContext(), deviceId, currentPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                    try {
                        JSONArray list = data.getJSONArray("list");
                        updateFileList(list);
                    } catch (Exception e) {
                        showError("解析失败:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                    showError(errorMsg);
                });
            }
        });
    }

    private void updateFileList(JSONArray list) {
        fileList.clear();
        if (!"/".equals(currentPath)) {
            fileList.add(new FileItem(PARENT_DIR_NAME, false, 0, "", ""));
        }
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject obj = list.getJSONObject(i);
                String name = obj.getString("name");
                // 过滤重复或无效的上级目录项
                if ("..".equals(name) || ".".equals(name)) continue;
                fileList.add(new FileItem(
                        name,
                        obj.getBoolean("file"),
                        obj.optLong("size", 0L),
                        obj.optString("mime", ""),
                        obj.optString("modified_at", "")
                ));
            } catch (Exception e) {
                Log.e(TAG, "文件解析失败", e);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void downloadAndOpenFile(FileItem item) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.size > 5 * 1024 * 1024) { // >5MB
            Toast.makeText(requireContext(), "文件过大，暂不支持", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download_progress, null);
        final LinearProgressIndicator progressIndicator = dialogView.findViewById(R.id.progress_download);
        final TextView textPercent = dialogView.findViewById(R.id.text_download_percent);
        final androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        String remotePath = appendPath(currentPath, item.name);
        File local = new File(requireContext().getExternalFilesDir("downloads"), item.name);
        if (local.getParentFile() != null && !local.getParentFile().exists()) {
            boolean mk = local.getParentFile().mkdirs();
            if (!mk && !local.getParentFile().exists()) {
                Toast.makeText(requireContext(), getString(R.string.download_failed_format, "无法创建本地目录"), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
                return;
            }
        }

        new FileApi().downloadFileToLocal(requireContext(), deviceId, remotePath, local, new FileApi.DownloadCallback() {
            @Override public void onProgress(int progress) {
                requireActivity().runOnUiThread(() -> {
                    progressIndicator.setIndeterminate(false);
                    // 使用兼容方法更新进度
                    try {
                        progressIndicator.setProgressCompat(progress, true);
                    } catch (Throwable t) {
                        progressIndicator.setProgress(progress);
                    }
                    textPercent.setText(getString(R.string.percent_format, progress));
                });
            }
             @Override public void onSuccess(File file) {
                progressDialog.dismiss();
                 openInternalEditor(file, remotePath, deviceId);
             }
             @Override public void onFailure(String errorMsg) {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.download_failed_format, errorMsg), Toast.LENGTH_SHORT).show();
             }
         });
    }

    private void openInternalEditor(File file, String remotePath, int deviceId) {
        try {
            android.content.Intent intent = new android.content.Intent(requireContext(), FileEditorActivity.class);
            intent.putExtra("local_path", file.getAbsolutePath());
            intent.putExtra("remote_path", remotePath);
            intent.putExtra("file_name", file.getName());
            intent.putExtra("server_id", deviceId);
            editorLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.open_editor_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String getParentPath(String path) {
        if ("/".equals(path)) return "/";
        if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        int idx = path.lastIndexOf('/');
        if (idx <= 0) return "/";
        return path.substring(0, idx);
    }

    private String appendPath(String base, String name) {
        if ("/".equals(base)) return base + name;
        return base + "/" + name;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setText(getString(R.string.error_format, msg));
        emptyView.setVisibility(View.VISIBLE);
    }

    private void updateEmptyView() {
        if (fileList.isEmpty()) {
            emptyView.setText(getString(R.string.empty_directory));
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新面包屑导航视图
     */
    private void updatePathView() {
        if (pathContainer == null) return;
        pathContainer.removeAllViews();

        // 添加根目录图标
        TextView rootNode = new TextView(requireContext());
        rootNode.setText("/");
        rootNode.setTextColor(Color.WHITE);
        rootNode.setTextSize(16);
        rootNode.setPadding(16, 8, 16, 8);
        rootNode.setGravity(Gravity.CENTER);
        rootNode.setBackgroundResource(android.R.color.transparent); // 可选：添加点击背景
        rootNode.setOnClickListener(v -> {
            if (!"/".equals(currentPath)) {
                currentPath = "/";
                updatePathView();
                loadFileList();
            }
        });
        pathContainer.addView(rootNode);

        if ("/".equals(currentPath)) return;

        String[] parts = currentPath.split("/");
        StringBuilder builtPath = new StringBuilder("/");

        for (String part : parts) {
            if (part.isEmpty()) continue;

            // 添加分隔符
            TextView divider = new TextView(requireContext());
            divider.setText(">");
            divider.setTextColor(Color.LTGRAY);
            divider.setTextSize(14);
            divider.setPadding(0, 0, 0, 0);
            pathContainer.addView(divider);

            builtPath.append(part);
            String thisPath = builtPath.toString();
            builtPath.append("/");

            // 添加路径节点
            TextView node = new TextView(requireContext());
            node.setText(part);
            node.setTextColor(Color.WHITE);
            node.setTextSize(16);
            node.setPadding(16, 8, 16, 8);
            node.setGravity(Gravity.CENTER);

            // 当前路径加粗或高亮
            if (thisPath.equals(currentPath)) {
                node.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            node.setOnClickListener(v -> {
                if (!thisPath.equals(currentPath)) {
                    currentPath = thisPath;
                    updatePathView();
                    loadFileList();
                }
            });

            // 长按最后一个节点可编辑路径
            if (thisPath.equals(currentPath)) {
                 node.setOnLongClickListener(v -> {
                     showEditPathDialog();
                     return true;
                 });
            }

            pathContainer.addView(node);
        }

        // 滚动到最右侧
        pathScrollView.post(() -> pathScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    // 数据模型
        record FileItem(String name, boolean file, long size, String mime, String modifiedAt) {
    }

    // 适配器
    static class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        interface Click { void onClick(FileItem item); }
        interface LongClick { void onLongClick(FileItem item); }
        private final List<FileItem> data;
        private final Click click;
        private final LongClick longClick;

        FileAdapter(List<FileItem> d, Click c, LongClick lc){ data=d; click=c; longClick=lc; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos){ h.bind(data.get(pos), click, longClick); }
        @Override public int getItemCount(){ return data.size(); }
        static class VH extends RecyclerView.ViewHolder {
            private final android.widget.ImageView icon; private final android.widget.TextView name; private final android.widget.TextView info;
            VH(@NonNull View itemView){ super(itemView); icon=itemView.findViewById(R.id.image_view_icon); name=itemView.findViewById(R.id.text_view_name); info=itemView.findViewById(R.id.text_view_info); }
            void bind(FileItem it, Click c, LongClick lc){
                name.setText(it.name);
                if (PARENT_DIR_NAME.equals(it.name)) {
                    icon.setImageResource(R.drawable.ic_folder_material);
                    info.setText(R.string.parent_directory);
                } else if (it.file) {
                    icon.setImageResource(R.drawable.ic_file_material);
                    // 向量图标使用布局中的 tint，信息更精简
                    info.setText(itemView.getContext().getString(R.string.file_info_format, formatSize(it.size), it.modifiedAt));
                } else {
                    icon.setImageResource(R.drawable.ic_folder_material);
                    info.setText(itemView.getContext().getString(R.string.folder_info_format, it.modifiedAt));
                }
                itemView.setOnClickListener(v -> c.onClick(it));
                itemView.setOnLongClickListener(v -> {
                    lc.onLongClick(it);
                    return true;
                });
            }
            private String formatSize(long size){ if(size<=0) return "0 B"; String[] u={"B","KB","MB","GB","TB"}; int g=(int)(Math.log10(size)/Math.log10(1024)); return new java.text.DecimalFormat("#,##0.#").format(size/Math.pow(1024,g))+" "+u[g]; }
        }
    }
}
