package cn.jdnjk.simpfun.ui.files;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileListFragment extends Fragment {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;


    public boolean isFabMenuOpen = false;

    // 文件选择请求码
    private static final int REQUEST_PICK_FILE = 1001;

    public final List<FileItem> fileList = new ArrayList<>();
    public String currentPath = "/";
    private static final String PARENT_DIR_NAME = "..";

    // 剪贴板相关
    public final List<String> cutFilesPaths = new ArrayList<>();
    public String cutSourcePath = "";

    public final Handler mainHandler = new Handler(Looper.getMainLooper());
    public final Map<String, FileWatcher> fileWatchers = new HashMap<>();
    public final Map<String, Long> fileLastModified = new HashMap<>();
    private static final String TAG = "FileListFragment";

    // 添加这些新类的实例
    private FileCopy fileCopy;
    private FileDelete fileDelete;
    private FileExtract fileExtract;
    private FileUpdate fileUpdate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_files);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        // 初始化辅助类
        fileCopy = new FileCopy(this);
        fileDelete = new FileDelete(this);
        fileExtract = new FileExtract(this);
        fileUpdate = new FileUpdate(this);

        FileFab fileFab = new FileFab();
        fileFab.initFabButtons(this, view);

        adapter = new FileAdapter(fileList, this::onFileItemClick, this::onFileItemSelectionChanged);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        loadFileList();

        return view;
    }

    public void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("新建文件夹");

        final EditText input = new EditText(requireContext());
        input.setHint("请输入文件夹名称");
        builder.setView(input);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                createFolder(folderName);
            } else {
                Toast.makeText(requireContext(), "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public void showCreateFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("新建文件");

        final EditText input = new EditText(requireContext());
        input.setHint("请输入文件名称");
        builder.setView(input);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                createFile(fileName);
            } else {
                Toast.makeText(requireContext(), "文件名称不能为空", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                uploadFileFromUri(uri);
            }
        }
    }

    private void createFolder(String folderName) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileApi().createFileOrFolder(requireContext(), deviceId, "folder", currentPath, folderName, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "文件夹创建成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void createFile(String fileName) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileApi().createFileOrFolder(requireContext(), deviceId, "file", currentPath, fileName, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "文件创建成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void uploadFileFromUri(Uri uri) {
        try {
            String fileName = getFileNameFromUri(uri);
            if (fileName == null) {
                Toast.makeText(requireContext(), "无法获取文件名", Toast.LENGTH_SHORT).show();
                return;
            }

            File tempFile = new File(requireContext().getCacheDir(), fileName);
            copyUriToFile(uri, tempFile);

            SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            int deviceId = sp.getInt("device_id", -1);

            if (deviceId == -1) {
                Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
                return;
            }

            new FileApi().uploadFile(requireContext(), deviceId, currentPath, tempFile, new FileApi.Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "文件上传成功", Toast.LENGTH_SHORT).show();
                        loadFileList();
                        //noinspection ResultOfMethodCallIgnored
                        tempFile.delete();
                    });
                }

                @Override
                public void onFailure(String errorMsg) {
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                        //noinspection ResultOfMethodCallIgnored
                        tempFile.delete();
                    });
                }
            });

        } catch (Exception e) {
            Toast.makeText(requireContext(), "文件处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String filePath) {
        String mimeType = null;
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filePath.length() - 1) {
            String fileExtension = filePath.substring(dotIndex + 1).toLowerCase();
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
        }
        return mimeType;
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void copyUriToFile(Uri uri, File destFile) throws Exception {
        try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destFile)) {

            if (inputStream != null) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
        }
    }

    // 修改这些方法为public，以便其他类可以调用
    public void clearAllSelections() {
        for (FileItem item : fileList) {
            item.setSelected(false);
        }
        adapter.notifyDataSetChanged();
        // updateFabMenu(); // 这个方法在FileFab中处理
    }

    public void loadFileList() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        // 添加调试日志和错误检查
        Log.d("FileListFragment", "Loading file list for device ID: " + deviceId + ", path: " + currentPath);

        if (deviceId == -1) {
            showError("设备ID无效，请重新选择服务器");
            return;
        }

        showLoading(true);
        new FileApi().getFileList(requireContext(), deviceId, currentPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        Log.d("FileListFragment", "API response: " + data.toString());

                        if (data.has("list")) {
                            JSONArray list = data.getJSONArray("list");
                            updateFileList(list);
                            Log.d("FileListFragment", "File list updated with " + list.length() + " items");
                        } else {
                            showError("响应数据格式错误：缺少文件列表");
                        }
                    } catch (Exception e) {
                        Log.e("FileListFragment", "解析文件列表失败", e);
                        showError("解析文件列表失败: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Log.e("FileListFragment", "API调用失败: " + errorMsg);
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);

                    String detailedError = "加载失败: " + errorMsg;
                    if (errorMsg.contains("404")) {
                        detailedError = "文件路径不存在，请检查服务器状态";
                    } else if (errorMsg.contains("403")) {
                        detailedError = "权限不足，请检查登录状态，或是有任务正在运行";
                    } else if (errorMsg.contains("网络")) {
                        detailedError = "网络连接失败，请检查网络设置";
                    }

                    showError(detailedError);
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
                        fileObj.optLong("size", 0),
                        fileObj.getString("mime"),
                        fileObj.getString("modified_at")
                );
                fileList.add(item);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing file item", e);
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
            downloadAndEditFile(item);
        } else {
            currentPath = appendPath(currentPath, item.getName());
            loadFileList();
        }
    }

    private void onFileItemSelectionChanged() {
        updateFabMenu();
    }

    // 使这些方法可以从其他类访问
    public String getParentPath(String path) {
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

    public String appendPath(String base, String path) {
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

    public void updateFabMenu() {
        // 空实现，如果需要可以添加逻辑
    }

    public static class FileWatcher extends FileObserver {
        private final FileListFragment fragment;
        private final String filePath;
        private final String remotePath;
        private final String fileName;
        private final Handler checkHandler = new Handler(Looper.getMainLooper());
        private Runnable periodicCheck;
        private static final int CHECK_INTERVAL = 5000; // 5秒检查一次

        @SuppressWarnings("deprecation")
        public FileWatcher(FileListFragment fragment, String path, String remotePath, String fileName) {
            super(path, FileObserver.MODIFY | FileObserver.CLOSE_WRITE |
                    FileObserver.MOVED_TO | FileObserver.CREATE | FileObserver.DELETE);
            this.fragment = fragment;
            this.filePath = path;
            this.remotePath = remotePath;
            this.fileName = fileName;

            startPeriodicCheck();
        }

        @Override
        public void onEvent(int event, String path) {
            Log.d(TAG, "FileWatcher event: " + event + ", path: " + path + ", watching: " + filePath);

            if (event == FileObserver.MODIFY || event == FileObserver.CLOSE_WRITE ||
                    event == FileObserver.MOVED_TO || event == FileObserver.CREATE) {

                checkAndUploadFile();
            }
        }

        private void startPeriodicCheck() {
            periodicCheck = new Runnable() {
                @Override
                public void run() {
                    checkAndUploadFile();
                    checkHandler.postDelayed(this, CHECK_INTERVAL);
                }
            };
            checkHandler.postDelayed(periodicCheck, CHECK_INTERVAL);
        }

        private void checkAndUploadFile() {
            File file = new File(filePath);
            if (file.exists()) {
                long currentModified = file.lastModified();
                Long lastModified = fragment.fileLastModified.get(filePath);

                if (lastModified == null || currentModified > lastModified) {
                    Log.d(TAG, "File modified detected: " + fileName + ", last: " + lastModified + ", current: " + currentModified);
                    fragment.fileLastModified.put(filePath, currentModified);

                    fragment.mainHandler.removeCallbacksAndMessages(null);
                    fragment.mainHandler.postDelayed(() -> {
                        if (file.exists() && file.canRead()) {
                            Log.d(TAG, "Uploading modified file: " + fileName);
                            fragment.fileUpdate.uploadModifiedFile(file, remotePath, fileName);
                        }
                    }, 1000); // 修改为1秒等待时间
                }
            } else {
                Log.w(TAG, "Watched file no longer exists: " + filePath);
            }
        }

        @Override
        public void stopWatching() {
            super.stopWatching();
            if (checkHandler != null && periodicCheck != null) {
                checkHandler.removeCallbacks(periodicCheck);
            }
            Log.d(TAG, "Stopped watching file: " + filePath);
        }
    }

    @SuppressWarnings("deprecation")
    private void downloadAndEditFile(FileItem item) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.size >= 5242880) { // 5MB
            Toast.makeText(requireContext(), "文件过大，请使用其他方式打开", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("正在下载文件...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        String remoteFilePath = appendPath(currentPath, item.getName());
        File localFile = new File(requireContext().getExternalFilesDir("downloads"), item.getName());

        if (localFile.getParentFile() != null && !localFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            localFile.getParentFile().mkdirs();
        }

        new FileApi().downloadFileToLocal(requireContext(), deviceId, remoteFilePath, localFile, new FileApi.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                mainHandler.post(() -> progressDialog.setProgress(progress));
            }

            @Override
            public void onSuccess(File file) {
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    fileLastModified.put(file.getAbsolutePath(), file.lastModified());
                    fileUpdate.startFileWatcher(file.getAbsolutePath(), remoteFilePath, item.getName());
                    String ext = getFileExtension(file.getName());
                    openFileWithInternalEditor(file, remoteFilePath);
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "下载失败: " + errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openFileWithInternalEditor(File file, String remotePath) {
        try {
            android.content.Intent intent = new android.content.Intent(requireContext(), FileEditorActivity.class);
            android.content.SharedPreferences sp = requireContext().getSharedPreferences("deviceid", android.content.Context.MODE_PRIVATE);
            int deviceId = sp.getInt("device_id", -1);
            intent.putExtra("local_path", file.getAbsolutePath());
            intent.putExtra("remote_path", remotePath);
            intent.putExtra("file_name", file.getName());
            intent.putExtra("server_id", deviceId);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开内部编辑器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFileWithExternalEditor(File file) {
        if (!file.exists()) {
            Toast.makeText(requireContext(), "文件不存在：" + file.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Opening file: " + file.getName() + ", path: " + file.getAbsolutePath());

        try {
            String fileName = file.getName();
            String fileExtension = getFileExtension(fileName);

            openAsTextFile(file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file with external editor", e);
            Toast.makeText(requireContext(), "打开文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private boolean isTextFile(String extension) {
        return extension.matches("txt|log|json|xml|yml|yaml|properties|conf|cfg|ini|md|java|js|css|html|htm|php|py|sh|bat|sql|csv");
    }
    private void openAsTextFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            intent.setDataAndType(uri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(requireContext(), "文件已打开，编辑后会自动上传", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open as text file", e);
        }
    }

    private void forceOpenAsText(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            intent.setDataAndType(uri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "选择文本编辑器");
            startActivity(chooser);
            Toast.makeText(requireContext(), "文件已打开，编辑后会自动上传", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开文本编辑器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openWithSystemDefault(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            String mimeType = getMimeType(file.getAbsolutePath());
            if (mimeType == null) {
                mimeType = "*/*";
            }

            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "选择打开方式");
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开文件: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFileLocation(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            Uri folderUri = Uri.fromFile(file.getParentFile());
            Intent folderIntent = new Intent(Intent.ACTION_VIEW);
            folderIntent.setDataAndType(folderUri, "resource/folder");

            if (folderIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(folderIntent);
            } else {
                Toast.makeText(requireContext(), "文件位置: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "文件位置: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    public static class FileItem {
        private final String name;
        private final boolean file;
        private final long size;
        private final String mime;
        private final String modifiedAt;
        private boolean selected = false;

        public FileItem(String name, boolean file, long size, String mime, String modifiedAt) {
            this.name = name;
            this.file = file;
            this.size = size;
            this.mime = mime;
            this.modifiedAt = modifiedAt;
        }

        public String getName() {
            return name;
        }

        public boolean isFile() {
            return file;
        }

        public long getSize() {
            return size;
        }

        public String getMime() {
            return mime;
        }

        public String getModifiedAt() {
            return modifiedAt;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public boolean isArchive() {
            if (!isFile()) return false;
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".zip") || lowerName.endsWith(".7z") ||
                    lowerName.endsWith(".tar.gz") || lowerName.endsWith(".rar");
        }

        public boolean isExtractable() {
            if (!isFile()) return false;
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".zip") || lowerName.endsWith(".7z") ||
                    lowerName.endsWith(".tar.gz");
        }
    }

    private static class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private final List<FileItem> items;
        private final OnItemClickListener listener;
        private final OnItemSelectionChangedListener selectionListener;

        public interface OnItemClickListener {
            void onItemClick(FileItem item);
        }

        public interface OnItemSelectionChangedListener {
            void onSelectionChanged();
        }

        public FileAdapter(List<FileItem> items, OnItemClickListener listener, OnItemSelectionChangedListener selectionListener) {
            this.items = items;
            this.listener = listener;
            this.selectionListener = selectionListener;
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
            holder.bind(item, listener, selectionListener);
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

            public void bind(FileItem item, OnItemClickListener listener, OnItemSelectionChangedListener selectionListener) {
                checkBoxSelect.setChecked(item.isSelected());
                nameView.setText(item.getName());

                if (FileListFragment.PARENT_DIR_NAME.equals(item.getName())) {
                    iconView.setImageResource(R.drawable.folder);
                    infoView.setText("上级目录");
                    checkBoxSelect.setVisibility(View.GONE);
                } else {
                    checkBoxSelect.setVisibility(View.VISIBLE);
                    if (item.isFile()) {
                        iconView.setImageResource(R.drawable.files);
                        String sizeStr = formatFileSize(item.getSize());
                        infoView.setText(sizeStr + " • " + item.getModifiedAt());
                    } else {
                        iconView.setImageResource(R.drawable.folder);
                        infoView.setText("文件夹 • " + item.getModifiedAt());
                    }
                }

                checkBoxSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (!FileListFragment.PARENT_DIR_NAME.equals(item.getName())) {
                        item.setSelected(isChecked);
                        if (selectionListener != null) {
                            selectionListener.onSelectionChanged();
                        }
                    }
                });

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(item);
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    if (!FileListFragment.PARENT_DIR_NAME.equals(item.getName())) {
                        boolean newState = !item.isSelected();
                        item.setSelected(newState);
                        checkBoxSelect.setChecked(newState);
                        if (selectionListener != null) {
                            selectionListener.onSelectionChanged();
                        }
                        return true;
                    }
                    return false;
                });
            }

            private String formatFileSize(long size) {
                if (size <= 0) return "0 B";
                final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
                int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
                return new java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (FileWatcher watcher : fileWatchers.values()) {
            if (watcher != null) {
                watcher.stopWatching();
            }
        }
        fileWatchers.clear();
        fileLastModified.clear();
    }

    public int getSelectedItemsCount() {
        int count = 0;
        for (FileItem item : fileList) {
            if (item.isSelected() && !PARENT_DIR_NAME.equals(item.getName())) {
                count++;
            }
        }
        return count;
    }

    public List<FileItem> getSelectedItems() {
        List<FileItem> selectedItems = new ArrayList<>();
        for (FileItem item : fileList) {
            if (item.isSelected() && !PARENT_DIR_NAME.equals(item.getName())) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }



    // 在适当的地方调用辅助类的方法
    public void renameFile(FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("重命名");

        EditText input = new EditText(requireContext());
        input.setText(item.getName());
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(item.getName())) {
                executeRename(item, newName);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeRename(FileItem item, String newName) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        String originPath = appendPath(currentPath, item.getName());
        String targetPath = appendPath(currentPath, newName);

        new FileApi().renameFile(requireContext(), deviceId, originPath, targetPath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "重命名成功", Toast.LENGTH_SHORT).show();
                    clearAllSelections();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "重命名失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    public void showToolboxDialog() {
        String[] options = {"修复权限和中文名异常问题"};
        boolean[] checkedItems = {true};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("工具箱");

        builder.setMultiChoiceItems(options, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked);

        builder.setPositiveButton("确认", (dialog, which) -> {
            if (checkedItems[0]) {
                executeToolboxOperation("fix_permission_and_charset");
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }


    /**
     * 执行工具箱操作
     */
    private void executeToolboxOperation(String action) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileApi().toolboxOperation(requireContext(), deviceId, action, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    String message = data.optString("message", "工具箱操作执行成功");
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(requireContext(), "工具箱操作失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
