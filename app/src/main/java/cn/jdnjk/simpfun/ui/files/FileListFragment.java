package cn.jdnjk.simpfun.ui.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FileListFragment extends Fragment {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;

    private List<FileItem> fileList = new ArrayList<>();
    private String currentPath = "/";
    private static final String PARENT_DIR_NAME = "..";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_files);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        adapter = new FileAdapter(fileList, this::onFileItemClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        loadFileList();

        return view;
    }
    private void loadFileList() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        showLoading(true);
        new FileApi().getFileList(requireContext(), deviceId, currentPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        JSONArray list = data.getJSONArray("list");
                        updateFileList(list);
                    } catch (Exception e) {
                        showError("解析文件列表失败: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    showError("加载失败: " + errorMsg);
                });
            }
        });
    }
    private void updateFileList(JSONArray list) {
        fileList.clear();
        if (!"/".equals(currentPath)) {
            FileItem parentItem = new FileItem(PARENT_DIR_NAME, false, 0, "", "");
            fileList.add(parentItem);
        }
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject fileObj = list.getJSONObject(i);
                FileItem item = new FileItem(
                        fileObj.getString("name"),
                        fileObj.getBoolean("file"),
                        (long) fileObj.optInt("size", 0),
                        fileObj.getString("mime"),
                        fileObj.getString("modified_at")
                );
                fileList.add(item);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyView();
    }
    private void onFileItemClick(FileItem item) {
        if (PARENT_DIR_NAME.equals(item.getName())) {
            currentPath = getParentPath(currentPath);
            loadFileList();
        } else if (item.isFile()) {
            Toast.makeText(requireContext(), "打开文件: " + item.getName(), Toast.LENGTH_SHORT).show();
        } else {
            currentPath = appendPath(currentPath, item.getName());
            loadFileList();
        }
    }
    private String getParentPath(String path) {
        if ("/".equals(path)) {
            return "/";
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlashIndex = path.lastIndexOf("/");
        if (lastSlashIndex == -1 || lastSlashIndex == 0) {
            return "/";
        }
        return path.substring(0, lastSlashIndex);
    }
    private String appendPath(String base, String path) {
        if (base.endsWith("/")) {
            return base + path;
        } else {
            return base + "/" + path;
        }
    }
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }
    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setText("错误: " + message);
        emptyView.setVisibility(View.VISIBLE);
    }
    private void updateEmptyView() {
        if (fileList.isEmpty()) {
            emptyView.setText("此目录为空");
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    public static class FileItem {
        private final String name;
        private final boolean file;
        private final long size;
        private final String mime;
        private final String modifiedAt;

        public FileItem(String name, boolean file, long size, String mime, String modifiedAt) {
            this.name = name;
            this.file = file;
            this.size = size;
            this.mime = mime;
            this.modifiedAt = modifiedAt;
        }

        // Getters
        public String getName() { return name; }
        public boolean isFile() { return file; }
        public long getSize() { return size; }
        public String getMime() { return mime; }
        public String getModifiedAt() { return modifiedAt; }

        public boolean isSelected() {
            return false;
        }
    }
    private static class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private final List<FileItem> items;
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(FileItem item);
        }

        public FileAdapter(List<FileItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileItem item = items.get(position);
            holder.bind(item, listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final CheckBox checkBoxSelect;
            private final ImageView iconView;
            private final TextView nameView;
            private final TextView infoView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBoxSelect = itemView.findViewById(R.id.check_box_select);
                iconView = itemView.findViewById(R.id.image_view_icon);
                nameView = itemView.findViewById(R.id.text_view_name);
                infoView = itemView.findViewById(R.id.text_view_info);
            }

            public void bind(FileItem item, OnItemClickListener listener) {
                checkBoxSelect.setChecked(item.isSelected());
                nameView.setText(item.getName());

                if (FileListFragment.PARENT_DIR_NAME.equals(item.getName())) {
                    iconView.setImageResource(R.drawable.folder); // 你需要准备一个向上的箭头图标
                    infoView.setText("上级目录");
                } else if (item.isFile()) {
                    iconView.setImageResource(R.drawable.files);
                    String sizeStr = formatFileSize(item.getSize());
                    infoView.setText(sizeStr + " • " + item.getModifiedAt());
                } else {
                    iconView.setImageResource(R.drawable.folder);
                    infoView.setText("文件夹 • " + item.getModifiedAt());
                }
                itemView.setOnClickListener(v -> {
//                    // 切换选中状态
//                    boolean newSelectedState = !item.isSelected();
//                    item.setSelected(newSelectedState); // 需要在 FileItem 类中实现 setSelected 方法
//                    checkBoxSelect.setChecked(newSelectedState);
//                    //可以在这里通知 Activity/Fragment 有项目被选中
//                    if (listener != null) {
//                        listener.onItemClick(item);
//                    }
                });

                itemView.setOnClickListener(v -> listener.onItemClick(item));
            }

            private String formatFileSize(long size) {
                if (size <= 0) return "0 B";
                final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
                int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
                return new java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
            }
        }
    }
}