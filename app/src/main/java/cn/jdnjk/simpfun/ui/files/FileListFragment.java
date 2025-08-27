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

    // FAB相关视图
    private FloatingActionButton fabMain;
    private FloatingActionButton fabNewFolder;
    private FloatingActionButton fabNewFile;
    private FloatingActionButton fabUploadFile;
    private TextView tvNewFolder;
    private TextView tvNewFile;
    private TextView tvUploadFile;
    private boolean isFabMenuOpen = false;

    // 文件选择请求码
    private static final int REQUEST_PICK_FILE = 1001;

    private List<FileItem> fileList = new ArrayList<>();
    private String currentPath = "/";
    private static final String PARENT_DIR_NAME = "..";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, FileWatcher> fileWatchers = new HashMap<>();
    private Map<String, Long> fileLastModified = new HashMap<>();
    private static final String TAG = "FileListFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_files);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        initFabButtons(view);

        adapter = new FileAdapter(fileList, this::onFileItemClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadFileList);

        loadFileList();

        return view;
    }

    private void initFabButtons(View view) {
        fabMain = view.findViewById(R.id.fab_main);
        fabNewFolder = view.findViewById(R.id.fab_new_folder);
        fabNewFile = view.findViewById(R.id.fab_new_file);
        fabUploadFile = view.findViewById(R.id.fab_upload_file);

        tvNewFolder = view.findViewById(R.id.tv_new_folder);
        tvNewFile = view.findViewById(R.id.tv_new_file);
        tvUploadFile = view.findViewById(R.id.tv_upload_file);

        fabMain.setOnClickListener(v -> toggleFabMenu());

        fabNewFolder.setOnClickListener(v -> {
            closeFabMenu();
            showCreateFolderDialog();
        });

        fabNewFile.setOnClickListener(v -> {
            closeFabMenu();
            showCreateFileDialog();
        });

        fabUploadFile.setOnClickListener(v -> {
            closeFabMenu();
            openFileChooser();
        });
    }

    private void toggleFabMenu() {
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            openFabMenu();
        }
    }

    private void openFabMenu() {
        isFabMenuOpen = true;

        fabNewFolder.setVisibility(View.VISIBLE);
        fabNewFile.setVisibility(View.VISIBLE);
        fabUploadFile.setVisibility(View.VISIBLE);
        tvNewFolder.setVisibility(View.VISIBLE);
        tvNewFile.setVisibility(View.VISIBLE);
        tvUploadFile.setVisibility(View.VISIBLE);

        animateButton(fabNewFolder, true, 100);
        animateButton(fabNewFile, true, 150);
        animateButton(fabUploadFile, true, 200);
        animateTextLabel(tvNewFolder, true, 100);
        animateTextLabel(tvNewFile, true, 150);
        animateTextLabel(tvUploadFile, true, 200);

        fabMain.animate().rotation(45f).setDuration(300).start();
    }

    private void closeFabMenu() {
        isFabMenuOpen = false;

        animateButton(fabNewFolder, false, 0);
        animateButton(fabNewFile, false, 50);
        animateButton(fabUploadFile, false, 100);
        animateTextLabel(tvNewFolder, false, 0);
        animateTextLabel(tvNewFile, false, 50);
        animateTextLabel(tvUploadFile, false, 100);

        fabMain.animate().rotation(0f).setDuration(300).start();
    }

    private void animateButton(FloatingActionButton button, boolean show, long delay) {
        if (show) {
            button.setScaleX(0f);
            button.setScaleY(0f);
            button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setStartDelay(delay)
                    .start();
        } else {
            button.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(200)
                    .setStartDelay(delay)
                    .withEndAction(() -> button.setVisibility(View.GONE))
                    .start();
        }
    }

    private void animateTextLabel(TextView textView, boolean show, long delay) {
        if (show) {
            textView.setAlpha(0f);
            textView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setStartDelay(delay)
                    .start();
        } else {
            textView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setStartDelay(delay)
                    .withEndAction(() -> textView.setVisibility(View.GONE))
                    .start();
        }
    }

    private void showCreateFolderDialog() {
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

    private void showCreateFileDialog() {
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

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_FILE && resultCode == getActivity().RESULT_OK && data != null) {
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
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
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
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "创建失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
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
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                    });
                }

                @Override
                public void onFailure(String errorMsg) {
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "上传失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Toast.makeText(requireContext(), "文件处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void createFileIntent(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        intent.setDataAndType(uri, getMimeType(file.getAbsolutePath()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
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

    private void loadFileList() {
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
                        detailedError = "权限不足，请检查登录状态";
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
            downloadAndEditFile(item);
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

    private class FileWatcher extends FileObserver {
        private String filePath;
        private String remotePath;
        private String fileName;

        public FileWatcher(String path, String remotePath, String fileName) {
            super(path, FileObserver.MODIFY | FileObserver.CLOSE_WRITE);
            this.filePath = path;
            this.remotePath = remotePath;
            this.fileName = fileName;
        }

        @Override
        public void onEvent(int event, String path) {
            if (event == FileObserver.MODIFY || event == FileObserver.CLOSE_WRITE) {
                File file = new File(filePath);
                if (file.exists()) {
                    long currentModified = file.lastModified();
                    Long lastModified = fileLastModified.get(filePath);

                    // 检查文件是否真的被修改了（避免重复上传）
                    if (lastModified == null || currentModified > lastModified) {
                        fileLastModified.put(filePath, currentModified);
                        // 延迟一点时间再上传，避免文件正在写入时就上传
                        mainHandler.postDelayed(() -> {
                            uploadModifiedFile(file, remotePath, fileName);
                        }, 2000);
                    }
                }
            }
        }
    }

    // 下载并打开文件进行编辑
    private void downloadAndEditFile(FileItem item) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(requireContext(), "文件下载成功", Toast.LENGTH_SHORT).show();

                    fileLastModified.put(file.getAbsolutePath(), file.lastModified());

                    startFileWatcher(file.getAbsolutePath(), remoteFilePath, item.getName());

                    openFileWithExternalEditor(file);
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

    private void startFileWatcher(String localFilePath, String remoteFilePath, String fileName) {
        stopFileWatcher(localFilePath);
        FileWatcher watcher = new FileWatcher(localFilePath, remoteFilePath, fileName);
        watcher.startWatching();
        fileWatchers.put(localFilePath, watcher);

        Log.d(TAG, "Started watching file: " + localFilePath);
    }

    private void stopFileWatcher(String localFilePath) {
        FileWatcher watcher = fileWatchers.remove(localFilePath);
        if (watcher != null) {
            watcher.stopWatching();
            Log.d(TAG, "Stopped watching file: " + localFilePath);
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

            if (isTextFile(fileExtension)) {
                openAsTextFile(file);
            } else if (isImageFile(fileExtension)) {
                openAsImageFile(file);
            } else if (isVideoFile(fileExtension)) {
                openAsVideoFile(file);
            } else {
                openAsTextFile(file);
            }
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

    private boolean isImageFile(String extension) {
        return extension.matches("jpg|jpeg|png|gif|bmp|webp|ico");
    }

    private boolean isVideoFile(String extension) {
        return extension.matches("mp4|avi|mkv|mov|wmv|flv|webm|3gp");
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
            } else {
                openWithGenericIntent(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open as text file", e);
            showFileOpenDialog(file);
        }
    }

    private void openAsImageFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            intent.setDataAndType(uri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(requireContext(), "图片已打开", Toast.LENGTH_SHORT).show();
            } else {
                openWithGenericIntent(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open as image file", e);
            showFileOpenDialog(file);
        }
    }

    private void openAsVideoFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            intent.setDataAndType(uri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(requireContext(), "视频已打开", Toast.LENGTH_SHORT).show();
            } else {
                openWithGenericIntent(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open as video file", e);
            showFileOpenDialog(file);
        }
    }

    private void openWithGenericIntent(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);

            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent chooser = Intent.createChooser(intent, "选择打开方式");
            if (chooser.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(chooser);
                Toast.makeText(requireContext(), "请选择合适的应用打开文件", Toast.LENGTH_SHORT).show();
            } else {
                showFileOpenDialog(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open with generic intent", e);
            showFileOpenDialog(file);
        }
    }

    private void showFileOpenDialog(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("打开文件: " + file.getName());

        String[] options = {"文本编辑器", "系统默认", "文件管理器", "取消"};

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    forceOpenAsText(file);
                    break;
                case 1:
                    openWithSystemDefault(file);
                    break;
                case 2:
                    openFileLocation(file);
                    break;
                case 3:
                    dialog.dismiss();
                    break;
            }
        });

        builder.show();
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

        public FileItem(String name, boolean file, long size, String mime, String modifiedAt) {
            this.name = name;
            this.file = file;
            this.size = size;
            this.mime = mime;
            this.modifiedAt = modifiedAt;
        }

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
    private void uploadModifiedFile(File localFile, String remotePath, String fileName) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Log.e(TAG, "设备ID无效，无法上传文件");
            return;
        }

        if (!localFile.exists()) {
            Log.w(TAG, "本地文件不存在，跳过上传: " + localFile.getAbsolutePath());
            return;
        }

        Log.d(TAG, "开始上传修改后的文件: " + fileName);

        Toast.makeText(requireContext(), "正在上传文件: " + fileName, Toast.LENGTH_SHORT).show();

        new FileApi().uploadFile(requireContext(), deviceId, getParentPath(remotePath), localFile, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "文件 " + fileName + " 上传成功", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "文件上传成功: " + fileName);
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "文件上传失败: " + errorMsg, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "文件上传失败: " + fileName + ", 错误: " + errorMsg);
                });
            }
        });
    }
}
