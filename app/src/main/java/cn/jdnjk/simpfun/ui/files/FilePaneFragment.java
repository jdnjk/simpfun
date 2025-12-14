package cn.jdnjk.simpfun.ui.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.api.ins.file.FileTransferApi;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个文件浏览面板 Fragment，可被 DualFileBrowserFragment 两次实例化实现双栏浏览。
 * FileListFragment：保留基础列表、导航与文件打开功能。
 */
public class FilePaneFragment extends Fragment {
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView pathView;

    private final List<FileItem> fileList = new ArrayList<>();
    private FileAdapter adapter;
    private String currentPath = "/";
    private static final String PARENT_DIR_NAME = "..";
    private static final String ARG_INITIAL_PATH = "initial_path";
    private static final String TAG = "FilePaneFragment";

    private ActivityResultLauncher<android.content.Intent> editorLauncher;

    public static FilePaneFragment newInstance(String initialPath) {
        FilePaneFragment f = new FilePaneFragment();
        Bundle b = new Bundle();
        b.putString(ARG_INITIAL_PATH, initialPath);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_file_pane, container, false);
        recyclerView = v.findViewById(R.id.recycler_view_files);
        progressBar = v.findViewById(R.id.progress_bar);
        emptyView = v.findViewById(R.id.empty_view);
        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_layout);
        pathView = v.findViewById(R.id.text_view_path);

        if (getArguments() != null) {
            String init = getArguments().getString(ARG_INITIAL_PATH, "/");
            if (init != null && !init.trim().isEmpty()) {
                currentPath = sanitizePath(init.trim());
            }
        }

        adapter = new FileAdapter(fileList, this::onFileItemClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        // 路径栏点击可编辑
        pathView.setOnClickListener(v1 -> showEditPathDialog());

        pathView.setText(currentPath);
        loadFileList();

        // 注册编辑器结果回调
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

        return v;
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
            pathView.setText(currentPath);
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
            pathView.setText(currentPath);
            loadFileList();
        } else if (item.file) {
            downloadAndOpenFile(item);
        } else {
            currentPath = appendPath(currentPath, item.name);
            pathView.setText(currentPath);
            loadFileList();
        }
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
            android.content.Intent intent = new android.content.Intent(requireContext(), cn.jdnjk.simpfun.ui.files.FileEditorActivity.class);
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

    // 数据模型
        record FileItem(String name, boolean file, long size, String mime, String modifiedAt) {
    }

    // 适配器
    static class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        interface Click { void onClick(FileItem item); }
        private final List<FileItem> data; private final Click click;
        FileAdapter(List<FileItem> d, Click c){ data=d; click=c; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos){ h.bind(data.get(pos), click); }
        @Override public int getItemCount(){ return data.size(); }
        static class VH extends RecyclerView.ViewHolder {
            private final android.widget.ImageView icon; private final android.widget.TextView name; private final android.widget.TextView info;
            VH(@NonNull View itemView){ super(itemView); icon=itemView.findViewById(R.id.image_view_icon); name=itemView.findViewById(R.id.text_view_name); info=itemView.findViewById(R.id.text_view_info); }
            void bind(FileItem it, Click c){
                name.setText(it.name);
                if (PARENT_DIR_NAME.equals(it.name)) {
                    icon.setImageResource(R.drawable.folder);
                    info.setText(R.string.parent_directory);
                } else if (it.file) {
                    icon.setImageResource(R.drawable.ic_document);
                    // 向量图标使用布局中的 tint，信息更精简
                    info.setText(itemView.getContext().getString(R.string.file_info_format, formatSize(it.size), it.modifiedAt));
                } else {
                    icon.setImageResource(R.drawable.folder);
                    info.setText(itemView.getContext().getString(R.string.folder_info_format, it.modifiedAt));
                }
                itemView.setOnClickListener(v -> c.onClick(it));
            }
            private String formatSize(long size){ if(size<=0) return "0 B"; String[] u={"B","KB","MB","GB","TB"}; int g=(int)(Math.log10(size)/Math.log10(1024)); return new java.text.DecimalFormat("#,##0.#").format(size/Math.pow(1024,g))+" "+u[g]; }
        }
    }
}

