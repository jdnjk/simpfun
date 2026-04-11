package cn.jdnjk.simpfun.ui.ins.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.FileEditorActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.FileAdapter;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.api.ins.file.FileManageApi;
import cn.jdnjk.simpfun.api.ins.file.FileTransferApi;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.FilePathUtils;

public class FilePaneFragment extends Fragment {
    private static final String ARG_INITIAL_PATH = "initial_path";
    private static final String TAG = "FilePaneFragment";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout pathContainer;
    private HorizontalScrollView pathScrollView;
    private FloatingActionButton fabAdd;

    private final List<FileItem> fileList = new ArrayList<>();
    private FileAdapter adapter;
    private String currentPath = "/";

    private ActivityResultLauncher<android.content.Intent> editorLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_file_pane, container, false);

        fabAdd = root.findViewById(R.id.fab_add);
        recyclerView = root.findViewById(R.id.recycler_view_files);
        progressBar = root.findViewById(R.id.progress_bar);
        emptyView = root.findViewById(R.id.empty_view);
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        pathContainer = root.findViewById(R.id.layout_path_container);
        pathScrollView = root.findViewById(R.id.scroll_view_path);

        if (getArguments() != null) {
            String init = getArguments().getString(ARG_INITIAL_PATH, "/");
            if (init != null && !init.trim().isEmpty()) {
                currentPath = FilePathUtils.sanitizePath(init.trim());
            }
        }

        adapter = new FileAdapter(fileList, this::onFileItemClick, this::onFileItemLongClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handleSelectedFile(uri);
            }
        });

        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                android.content.Intent data = result.getData();
                String localPath = data.getStringExtra("local_path");
                String remotePath = data.getStringExtra("remote_path");
                int serverId = data.getIntExtra("server_id", -1);
                if (localPath != null && remotePath != null && serverId > 0) {
                    File file = new File(localPath);
                    String remoteDir = FilePathUtils.getParentPath(remotePath);
                    new FileTransferApi().uploadFile(requireContext(), serverId, remoteDir, file, new FileCallback() {
                        @Override
                        public void onSuccess(JSONObject resp) {
                            Toast.makeText(requireContext(), "上传成功", Toast.LENGTH_SHORT).show();
                            loadFileList();
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    Toast.makeText(requireContext(), "保存结果无上传配置", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showCreateOptionsDialog());
        }

        updatePathView();
        loadFileList();
        return root;
    }

    private void showCreateOptionsDialog() {
        String[] options = {getString(R.string.new_file), getString(R.string.new_folder), "上传文件"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_file_title)
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

    private void showCreateEntryDialog(boolean createFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle(createFile ? getString(R.string.create_file_title) : getString(R.string.create_folder_title));
        final EditText input = new EditText(requireContext());
        input.setHint(createFile ? getString(R.string.create_file_hint) : getString(R.string.create_folder_hint));
        input.setSingleLine(true);
        builder.setView(input);

        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            createEntryOnServer(createFile ? "file" : "folder", name);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void createEntryOnServer(String mode, String name) {
        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileManageApi().createFileOrFolder(requireContext(), deviceId, mode, currentPath, name, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), "创建成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleSelectedFile(Uri uri) {
        try {
            Context context = requireContext();
            String fileName = getFileName(uri);
            if (fileName == null) {
                fileName = "uploaded_file";
            }

            File tempFile = new File(context.getCacheDir(), fileName);
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                if (is == null) {
                    throw new IOException("无法打开输入流");
                }
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

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }

        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }

    private void uploadSelectedFile(File file) {
        long maxSizeInBytes = 1000L * 1024L * 1024L;
        if (file.length() > maxSizeInBytes) {
            Toast.makeText(requireContext(), "文件超过1000MB，请使用SFTP上传较大文件", Toast.LENGTH_LONG).show();
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete oversized temp file: " + file.getName());
            }
            return;
        }

        int serverId = getDeviceId();
        if (serverId <= 0) {
            Toast.makeText(requireContext(), "未找到服务器ID", Toast.LENGTH_SHORT).show();
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete temp file (no server ID): " + file.getName());
            }
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
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete temp file: " + file.getName());
                }
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete temp file: " + file.getName());
                }
            }
        });
    }

    private void onFileItemClick(FileItem item) {
        if (item.isParentEntry()) {
            currentPath = FilePathUtils.getParentPath(currentPath);
            updatePathView();
            loadFileList();
        } else if (item.isFile()) {
            downloadAndOpenFile(item);
        } else {
            currentPath = FilePathUtils.appendPath(currentPath, item.getName());
            updatePathView();
            loadFileList();
        }
    }

    private void onFileItemLongClick(FileItem item) {
        if (item.isParentEntry() || !isAdded()) {
            return;
        }
        showFileActionDialog(item);
    }

    private void showFileActionDialog(FileItem item) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_Simpfun_BottomSheet);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_file_actions, null, false);

        TextView title = view.findViewById(R.id.text_view_title);
        if (title != null) {
            title.setText(item.getName());
        }

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

        dialog.setContentView(view);
        dialog.show();
    }

    private void showDeleteConfirmDialog(FileItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除确认")
                .setMessage("确定要删除 " + item.getName() + " 吗？")
                .setPositiveButton("删除", (d, w) -> deleteFile(item))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteFile(FileItem item) {
        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> paths = new ArrayList<>();
        paths.add(FilePathUtils.appendPath(currentPath, item.getName()));

        new FileManageApi().deleteFileOrFolderBatch(requireContext(), deviceId, paths, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "删除失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRenameDialog(FileItem item) {
        EditText input = new EditText(requireContext());
        input.setText(item.getName());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(item.getName())) {
                        renameFile(item, newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameFile(FileItem item, String newName) {
        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        String origin = FilePathUtils.appendPath(currentPath, item.getName());
        String target = FilePathUtils.appendPath(currentPath, newName);

        new FileManageApi().renameFile(requireContext(), deviceId, origin, target, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), "重命名成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "重命名失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFileList() {
        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            showError("设备ID无效");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading(true);
        new FileApi().getFileList(requireContext(), deviceId, currentPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) {
                    return;
                }
                swipeRefreshLayout.setRefreshing(false);
                showLoading(false);
                try {
                    JSONArray list = data.getJSONArray("list");
                    updateFileList(list);
                } catch (Exception e) {
                    showError("解析失败:" + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) {
                    return;
                }
                swipeRefreshLayout.setRefreshing(false);
                showLoading(false);
                showError(errorMsg);
            }
        });
    }

    private void updateFileList(JSONArray list) {
        fileList.clear();
        if (!"/".equals(currentPath)) {
            fileList.add(new FileItem(FileItem.PARENT_DIR_NAME, false, 0, "", ""));
        }

        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject obj = list.getJSONObject(i);
                String name = obj.getString("name");
                if ("..".equals(name) || ".".equals(name)) {
                    continue;
                }
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
        int deviceId = getDeviceId();
        if (deviceId <= 0) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.getSize() > 5 * 1024 * 1024) {
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

        String remotePath = FilePathUtils.appendPath(currentPath, item.getName());
        File local = new File(requireContext().getExternalFilesDir("downloads"), item.getName());
        if (local.getParentFile() != null && !local.getParentFile().exists()) {
            boolean mk = local.getParentFile().mkdirs();
            if (!mk && !local.getParentFile().exists()) {
                Toast.makeText(requireContext(), getString(R.string.download_failed_format, "无法创建本地目录"), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
                return;
            }
        }

        new FileApi().downloadFileToLocal(requireContext(), deviceId, remotePath, local, new FileApi.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                if (!isAdded()) {
                    return;
                }
                progressIndicator.setIndeterminate(false);
                try {
                    progressIndicator.setProgressCompat(progress, true);
                } catch (Throwable t) {
                    progressIndicator.setProgress(progress);
                }
                textPercent.setText(getString(R.string.percent_format, progress));
            }

            @Override
            public void onSuccess(File file) {
                progressDialog.dismiss();
                openInternalEditor(file, remotePath, deviceId);
            }

            @Override
            public void onFailure(String errorMsg) {
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

    private int getDeviceId() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    private void showEditPathDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.jump_path_title));
        final EditText input = new EditText(requireContext());
        input.setText(currentPath);
        input.setSingleLine(true);
        builder.setView(input);
        builder.setPositiveButton(R.string.confirm, (d, w) -> {
            String p = input.getText().toString().trim();
            if (p.isEmpty()) {
                p = "/";
            }
            currentPath = FilePathUtils.sanitizePath(p);
            updatePathView();
            loadFileList();
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void updatePathView() {
        if (pathContainer == null) {
            return;
        }
        pathContainer.removeAllViews();

        TextView rootNode = new TextView(requireContext());
        rootNode.setText("/");
        rootNode.setTextColor(Color.WHITE);
        rootNode.setTextSize(16);
        rootNode.setPadding(16, 8, 16, 8);
        rootNode.setGravity(Gravity.CENTER);
        rootNode.setBackgroundResource(android.R.color.transparent);
        rootNode.setOnClickListener(v -> {
            if (!"/".equals(currentPath)) {
                currentPath = "/";
                updatePathView();
                loadFileList();
            }
        });
        pathContainer.addView(rootNode);

        if ("/".equals(currentPath)) {
            return;
        }

        String[] parts = currentPath.split("/");
        StringBuilder builtPath = new StringBuilder("/");

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            TextView divider = new TextView(requireContext());
            divider.setText(">");
            divider.setTextColor(Color.LTGRAY);
            divider.setTextSize(14);
            pathContainer.addView(divider);

            builtPath.append(part);
            String thisPath = builtPath.toString();
            builtPath.append("/");

            TextView node = new TextView(requireContext());
            node.setText(part);
            node.setTextColor(Color.WHITE);
            node.setTextSize(16);
            node.setPadding(16, 8, 16, 8);
            node.setGravity(Gravity.CENTER);

            if (thisPath.equals(currentPath)) {
                node.setTypeface(null, android.graphics.Typeface.BOLD);
                node.setOnLongClickListener(v -> {
                    showEditPathDialog();
                    return true;
                });
            }

            node.setOnClickListener(v -> {
                if (!thisPath.equals(currentPath)) {
                    currentPath = thisPath;
                    updatePathView();
                    loadFileList();
                }
            });

            pathContainer.addView(node);
        }

        pathScrollView.post(() -> pathScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }
}
