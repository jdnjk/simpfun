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
    private FloatingActionButton fabToolbox;

    private FloatingActionButton fabCompress;
    private FloatingActionButton fabExtract;
    private FloatingActionButton fabRename;
    private FloatingActionButton fabDelete;
    private FloatingActionButton fabCut;
    private FloatingActionButton fabCopy;
    private FloatingActionButton fabPaste;

    // 文本标签
    private TextView tvNewFolder;
    private TextView tvNewFile;
    private TextView tvUploadFile;
    private TextView tvToolbox;
    private TextView tvCompress;
    private TextView tvExtract;
    private TextView tvRename;
    private TextView tvDelete;
    private TextView tvCut;
    private TextView tvCopy;
    private TextView tvPaste;

    private boolean isFabMenuOpen = false;

    // 文件选择请求码
    private static final int REQUEST_PICK_FILE = 1001;

    private final List<FileItem> fileList = new ArrayList<>();
    private String currentPath = "/";
    private static final String PARENT_DIR_NAME = "..";

    // 剪贴板相关
    private final List<String> cutFilesPaths = new ArrayList<>();
    private String cutSourcePath = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, FileWatcher> fileWatchers = new HashMap<>();
    private final Map<String, Long> fileLastModified = new HashMap<>();
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

        adapter = new FileAdapter(fileList, this::onFileItemClick, this::onFileItemSelectionChanged);
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
        fabToolbox = view.findViewById(R.id.fab_toolbox);

        fabCompress = view.findViewById(R.id.fab_compress);
        fabExtract = view.findViewById(R.id.fab_extract);
        fabRename = view.findViewById(R.id.fab_rename);
        fabDelete = view.findViewById(R.id.fab_delete);
        fabCut = view.findViewById(R.id.fab_cut);
        fabCopy = view.findViewById(R.id.fab_copy);
        fabPaste = view.findViewById(R.id.fab_paste);

        tvNewFolder = view.findViewById(R.id.tv_new_folder);
        tvNewFile = view.findViewById(R.id.tv_new_file);
        tvUploadFile = view.findViewById(R.id.tv_upload_file);
        tvToolbox = view.findViewById(R.id.tv_toolbox);
        tvCompress = view.findViewById(R.id.tv_compress);
        tvExtract = view.findViewById(R.id.tv_extract);
        tvRename = view.findViewById(R.id.tv_rename);
        tvDelete = view.findViewById(R.id.tv_delete);
        tvCut = view.findViewById(R.id.tv_cut);
        tvCopy = view.findViewById(R.id.tv_copy);
        tvPaste = view.findViewById(R.id.tv_paste);

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

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            showToolboxDialog();
        });

        // 添加选择模式相关的点击监听器
        fabCompress.setOnClickListener(v -> {
            closeFabMenu();
            compressFiles(getSelectedItems());
        });

        fabExtract.setOnClickListener(v -> {
            closeFabMenu();
            List<FileItem> selectedItems = getSelectedItems();
            if (!selectedItems.isEmpty()) {
                extractFile(selectedItems.get(0));
            }
        });

        fabRename.setOnClickListener(v -> {
            closeFabMenu();
            List<FileItem> selectedItems = getSelectedItems();
            if (!selectedItems.isEmpty()) {
                renameFile(selectedItems.get(0));
            }
        });

        fabDelete.setOnClickListener(v -> {
            closeFabMenu();
            deleteFiles(getSelectedItems());
        });

        fabCut.setOnClickListener(v -> {
            closeFabMenu();
            cutFiles(getSelectedItems());
        });

        fabCopy.setOnClickListener(v -> {
            closeFabMenu();
            List<FileItem> selectedItems = getSelectedItems();
            if (!selectedItems.isEmpty()) {
                copyFile(selectedItems.get(0));
            }
        });

        fabPaste.setOnClickListener(v -> {
            closeFabMenu();
            pasteFiles();
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

        int selectedCount = getSelectedItemsCount();

        if (selectedCount == 0) {
            fabNewFolder.setVisibility(View.VISIBLE);
            fabNewFile.setVisibility(View.VISIBLE);
            fabUploadFile.setVisibility(View.VISIBLE);
            fabToolbox.setVisibility(View.VISIBLE);
            tvNewFolder.setVisibility(View.VISIBLE);
            tvNewFile.setVisibility(View.VISIBLE);
            tvUploadFile.setVisibility(View.VISIBLE);
            tvToolbox.setVisibility(View.VISIBLE);

            // 如果有剪贴文件，显示粘贴按钮
            if (!cutFilesPaths.isEmpty()) {
                fabPaste.setVisibility(View.VISIBLE);
                tvPaste.setVisibility(View.VISIBLE);
            }

            long delay = 100;
            animateButton(fabNewFolder, true, delay);
            animateTextLabel(tvNewFolder, true, delay);
            delay += 56;

            animateButton(fabNewFile, true, delay);
            animateTextLabel(tvNewFile, true, delay);
            delay += 56;

            animateButton(fabUploadFile, true, delay);
            animateTextLabel(tvUploadFile, true, delay);
            delay += 56;

            if (!cutFilesPaths.isEmpty()) {
                animateButton(fabPaste, true, delay);
                animateTextLabel(tvPaste, true, delay);
                delay += 56;
            }

            animateButton(fabToolbox, true, delay);
            animateTextLabel(tvToolbox, true, delay);

        } else if (selectedCount == 1) {
            List<FileItem> selectedItems = getSelectedItems();
            if (!selectedItems.isEmpty()) {
                FileItem selectedItem = selectedItems.get(0);
                fabCopy.setVisibility(View.VISIBLE);
                fabCompress.setVisibility(View.VISIBLE);
                fabRename.setVisibility(View.VISIBLE);
                fabDelete.setVisibility(View.VISIBLE);
                fabCut.setVisibility(View.VISIBLE);
                tvCopy.setVisibility(View.VISIBLE);
                tvCompress.setVisibility(View.VISIBLE);
                tvRename.setVisibility(View.VISIBLE);
                tvDelete.setVisibility(View.VISIBLE);
                tvCut.setVisibility(View.VISIBLE);

                long delay = 100;
                animateButton(fabCopy, true, delay);
                animateTextLabel(tvCopy, true, delay);
                delay += 56;

                animateButton(fabCompress, true, delay);
                animateTextLabel(tvCompress, true, delay);
                delay += 56;

                if (selectedItem.isExtractable()) {
                    fabExtract.setVisibility(View.VISIBLE);
                    tvExtract.setVisibility(View.VISIBLE);
                    animateButton(fabExtract, true, delay);
                    animateTextLabel(tvExtract, true, delay);
                    delay += 56;
                }

                animateButton(fabRename, true, delay);
                animateTextLabel(tvRename, true, delay);
                delay += 56;

                animateButton(fabDelete, true, delay);
                animateTextLabel(tvDelete, true, delay);
                delay += 56;

                animateButton(fabCut, true, delay);
                animateTextLabel(tvCut, true, delay);
            }
        } else {
            fabCopy.setVisibility(View.VISIBLE);
            fabCompress.setVisibility(View.VISIBLE);
            fabDelete.setVisibility(View.VISIBLE);
            fabCut.setVisibility(View.VISIBLE);
            tvCopy.setVisibility(View.VISIBLE);
            tvCompress.setVisibility(View.VISIBLE);
            tvDelete.setVisibility(View.VISIBLE);
            tvCut.setVisibility(View.VISIBLE);

            animateButton(fabCopy, true, 100);
            animateButton(fabCompress, true, 156);
            animateButton(fabDelete, true, 212);
            animateButton(fabCut, true, 268);
            animateTextLabel(tvCopy, true, 100);
            animateTextLabel(tvCompress, true, 156);
            animateTextLabel(tvDelete, true, 212);
            animateTextLabel(tvCut, true, 268);
        }

        fabMain.animate().rotation(45f).setDuration(300).start();
    }

    private void closeFabMenu() {
        isFabMenuOpen = false;

        List<FloatingActionButton> allFabs = List.of(
                fabNewFolder, fabNewFile, fabUploadFile, fabToolbox,
                fabCompress, fabExtract, fabRename, fabDelete, fabCut, fabCopy, fabPaste
        );

        List<TextView> allLabels = List.of(
                tvNewFolder, tvNewFile, tvUploadFile, tvToolbox,
                tvCompress, tvExtract, tvRename, tvDelete, tvCut, tvCopy, tvPaste
        );

        long delay = 0;
        for (FloatingActionButton fab : allFabs) {
            if (fab.getVisibility() == View.VISIBLE) {
                animateButton(fab, false, delay);
                delay += 25;
            }
        }

        delay = 0;
        for (TextView label : allLabels) {
            if (label.getVisibility() == View.VISIBLE) {
                animateTextLabel(label, false, delay);
                delay += 25;
            }
        }

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

        // ���加调试日志和错误检查
        Log.d("FileListFragment", "Loading file list for device ID: " + deviceId + ", path: " + currentPath);

        if (deviceId == -1) {
            showError("设备ID无效，请重新选择服��器");
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
        private final String filePath;
        private final String remotePath;
        private final String fileName;
        private final Handler checkHandler = new Handler(Looper.getMainLooper());
        private Runnable periodicCheck;
        private static final int CHECK_INTERVAL = 5000; // 5秒检查一次

        @SuppressWarnings("deprecation")
        public FileWatcher(String path, String remotePath, String fileName) {
            super(path, FileObserver.MODIFY | FileObserver.CLOSE_WRITE |
                    FileObserver.MOVED_TO | FileObserver.CREATE | FileObserver.DELETE);
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
                Long lastModified = fileLastModified.get(filePath);

                if (lastModified == null || currentModified > lastModified) {
                    Log.d(TAG, "File modified detected: " + fileName + ", last: " + lastModified + ", current: " + currentModified);
                    fileLastModified.put(filePath, currentModified);

                    mainHandler.removeCallbacksAndMessages(null);
                    mainHandler.postDelayed(() -> {
                        if (file.exists() && file.canRead()) {
                            Log.d(TAG, "Uploading modified file: " + fileName);
                            uploadModifiedFile(file, remotePath, fileName);
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
                Toast.makeText(requireContext(), "请选择合适的方式打开文件", Toast.LENGTH_SHORT).show();
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
                    checkBoxSelect.setVisibility(View.GONE); // 上级目录不显���选择框
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

    private int getSelectedItemsCount() {
        int count = 0;
        for (FileItem item : fileList) {
            if (item.isSelected() && !PARENT_DIR_NAME.equals(item.getName())) {
                count++;
            }
        }
        return count;
    }

    private List<FileItem> getSelectedItems() {
        List<FileItem> selectedItems = new ArrayList<>();
        for (FileItem item : fileList) {
            if (item.isSelected() && !PARENT_DIR_NAME.equals(item.getName())) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    private void clearAllSelections() {
        for (FileItem item : fileList) {
            item.setSelected(false);
        }
        adapter.notifyDataSetChanged();
        updateFabMenu();
    }

    private void updateFabMenu() {
        int selectedCount = getSelectedItemsCount();
        boolean isSelectionMode = selectedCount > 0;

        if (selectedCount == 0) {
            setupDefaultFab();
        } else if (selectedCount == 1) {
            setupSingleSelectionFab();
        } else {
            setupMultiSelectionFab();
        }

        if (isFabMenuOpen) {
            showCurrentModeButtons();
        }
    }

    private void setupDefaultFab() {
        hideSelectionFabs();

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

        fabPaste.setOnClickListener(v -> {
            closeFabMenu();
            pasteFiles();
        });

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            showToolboxDialog();
        });
    }

    private void setupSingleSelectionFab() {
        List<FileItem> selectedItems = getSelectedItems();
        if (selectedItems.isEmpty()) return;

        FileItem selectedItem = selectedItems.get(0);

        hideDefaultFabs();

        fabCompress.setOnClickListener(v -> {
            closeFabMenu();
            compressFiles(selectedItems);
        });

        if (selectedItem.isExtractable()) {
            fabExtract.setVisibility(View.VISIBLE);
            tvExtract.setVisibility(View.VISIBLE);
            fabExtract.setOnClickListener(v -> {
                closeFabMenu();
                extractFile(selectedItem);
            });
        }

        fabRename.setOnClickListener(v -> {
            closeFabMenu();
            renameFile(selectedItem);
        });

        fabDelete.setOnClickListener(v -> {
            closeFabMenu();
            deleteFiles(selectedItems);
        });

        fabCut.setOnClickListener(v -> {
            closeFabMenu();
            cutFiles(selectedItems);
        });

        if (selectedItem.isFile()) {
            fabCopy.setVisibility(View.VISIBLE);
            tvCopy.setVisibility(View.VISIBLE);
            fabCopy.setOnClickListener(v -> {
                closeFabMenu();
                copyFile(selectedItem);
            });
        }

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            showToolboxDialog();
        });
    }

    private void setupMultiSelectionFab() {
        List<FileItem> selectedItems = getSelectedItems();

        hideDefaultFabs();
        fabExtract.setVisibility(View.GONE);
        tvExtract.setVisibility(View.GONE);
        fabRename.setVisibility(View.GONE);
        tvRename.setVisibility(View.GONE);
        fabCopy.setVisibility(View.GONE);
        tvCopy.setVisibility(View.GONE);

        fabCompress.setOnClickListener(v -> {
            closeFabMenu();
            compressFiles(selectedItems);
        });

        fabDelete.setOnClickListener(v -> {
            closeFabMenu();
            deleteFiles(selectedItems);
        });

        fabCut.setOnClickListener(v -> {
            closeFabMenu();
            cutFiles(selectedItems);
        });

        fabToolbox.setOnClickListener(v -> {
            closeFabMenu();
            showToolboxDialog();
        });
    }

    private void hideDefaultFabs() {
        fabNewFolder.setVisibility(View.GONE);
        fabNewFile.setVisibility(View.GONE);
        fabUploadFile.setVisibility(View.GONE);
        tvNewFolder.setVisibility(View.GONE);
        tvNewFile.setVisibility(View.GONE);
        tvUploadFile.setVisibility(View.GONE);
    }

    private void hideSelectionFabs() {
        fabCompress.setVisibility(View.GONE);
        fabExtract.setVisibility(View.GONE);
        fabRename.setVisibility(View.GONE);
        fabDelete.setVisibility(View.GONE);
        fabCut.setVisibility(View.GONE);
        fabCopy.setVisibility(View.GONE);
        fabPaste.setVisibility(View.GONE);
        tvCompress.setVisibility(View.GONE);
        tvExtract.setVisibility(View.GONE);
        tvRename.setVisibility(View.GONE);
        tvDelete.setVisibility(View.GONE);
        tvCut.setVisibility(View.GONE);
        tvCopy.setVisibility(View.GONE);
        tvPaste.setVisibility(View.GONE);
    }

    private void showCurrentModeButtons() {
        hideAllButtons();
        int selectedCount = getSelectedItemsCount();

        if (selectedCount == 0) {
            fabNewFolder.setVisibility(View.VISIBLE);
            fabNewFile.setVisibility(View.VISIBLE);
            fabUploadFile.setVisibility(View.VISIBLE);
            fabToolbox.setVisibility(View.VISIBLE);
            tvNewFolder.setVisibility(View.VISIBLE);
            tvNewFile.setVisibility(View.VISIBLE);
            tvUploadFile.setVisibility(View.VISIBLE);
            tvToolbox.setVisibility(View.VISIBLE);

            if (!cutFilesPaths.isEmpty()) {
                fabPaste.setVisibility(View.VISIBLE);
                tvPaste.setVisibility(View.VISIBLE);
                fabPaste.setScaleX(1f);
                fabPaste.setScaleY(1f);
                tvPaste.setAlpha(1f);
            }

            fabNewFolder.setScaleX(1f);
            fabNewFolder.setScaleY(1f);
            fabNewFile.setScaleX(1f);
            fabNewFile.setScaleY(1f);
            fabUploadFile.setScaleX(1f);
            fabUploadFile.setScaleY(1f);
            fabToolbox.setScaleX(1f);
            fabToolbox.setScaleY(1f);

            tvNewFolder.setAlpha(1f);
            tvNewFile.setAlpha(1f);
            tvUploadFile.setAlpha(1f);
            tvToolbox.setAlpha(1f);

        } else if (selectedCount == 1) {
            List<FileItem> selectedItems = getSelectedItems();
            if (!selectedItems.isEmpty()) {
                FileItem selectedItem = selectedItems.get(0);

                fabCompress.setVisibility(View.VISIBLE);
                fabRename.setVisibility(View.VISIBLE);
                fabDelete.setVisibility(View.VISIBLE);
                fabCut.setVisibility(View.VISIBLE);
                fabToolbox.setVisibility(View.VISIBLE);
                tvCompress.setVisibility(View.VISIBLE);
                tvRename.setVisibility(View.VISIBLE);
                tvDelete.setVisibility(View.VISIBLE);
                tvCut.setVisibility(View.VISIBLE);
                tvToolbox.setVisibility(View.VISIBLE);

                fabCompress.setScaleX(1f);
                fabCompress.setScaleY(1f);
                fabRename.setScaleX(1f);
                fabRename.setScaleY(1f);
                fabDelete.setScaleX(1f);
                fabDelete.setScaleY(1f);
                fabCut.setScaleX(1f);
                fabCut.setScaleY(1f);
                fabToolbox.setScaleX(1f);
                fabToolbox.setScaleY(1f);

                tvCompress.setAlpha(1f);
                tvRename.setAlpha(1f);
                tvDelete.setAlpha(1f);
                tvCut.setAlpha(1f);
                tvToolbox.setAlpha(1f);

                if (selectedItem.isExtractable()) {
                    fabExtract.setVisibility(View.VISIBLE);
                    tvExtract.setVisibility(View.VISIBLE);
                    fabExtract.setScaleX(1f);
                    fabExtract.setScaleY(1f);
                    tvExtract.setAlpha(1f);
                }

                if (selectedItem.isFile()) {
                    fabCopy.setVisibility(View.VISIBLE);
                    tvCopy.setVisibility(View.VISIBLE);
                    fabCopy.setScaleX(1f);
                    fabCopy.setScaleY(1f);
                    tvCopy.setAlpha(1f);
                }
            }
        } else {
            fabCompress.setVisibility(View.VISIBLE);
            fabDelete.setVisibility(View.VISIBLE);
            fabCut.setVisibility(View.VISIBLE);
            fabToolbox.setVisibility(View.VISIBLE);
            tvCompress.setVisibility(View.VISIBLE);
            tvDelete.setVisibility(View.VISIBLE);
            tvCut.setVisibility(View.VISIBLE);
            tvToolbox.setVisibility(View.VISIBLE);

            fabCompress.setScaleX(1f);
            fabCompress.setScaleY(1f);
            fabDelete.setScaleX(1f);
            fabDelete.setScaleY(1f);
            fabCut.setScaleX(1f);
            fabCut.setScaleY(1f);
            fabToolbox.setScaleX(1f);
            fabToolbox.setScaleY(1f);

            tvCompress.setAlpha(1f);
            tvDelete.setAlpha(1f);
            tvCut.setAlpha(1f);
            tvToolbox.setAlpha(1f);
        }
    }

    private void hideAllButtons() {
        List<FloatingActionButton> allFabs = List.of(
                fabNewFolder, fabNewFile, fabUploadFile, fabToolbox,
                fabCompress, fabExtract, fabRename, fabDelete, fabCut, fabCopy, fabPaste
        );

        List<TextView> allLabels = List.of(
                tvNewFolder, tvNewFile, tvUploadFile, tvToolbox,
                tvCompress, tvExtract, tvRename, tvDelete, tvCut, tvCopy, tvPaste
        );

        for (FloatingActionButton fab : allFabs) {
            fab.setVisibility(View.GONE);
        }

        for (TextView label : allLabels) {
            label.setVisibility(View.GONE);
        }
    }

    private void compressFiles(List<FileItem> items) {
        String[] formats = {"ZIP", "7Z", "TAR.GZ"};
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择压缩格式");
        builder.setItems(formats, (dialog, which) -> {
            String format = formats[which].toLowerCase().replace(".", "");
            showCompressNameDialog(items, format);
        });
        builder.show();
    }

    private void showCompressNameDialog(List<FileItem> items, String format) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("压缩文件");

        EditText input = new EditText(requireContext());
        input.setHint("请输入压缩包名称");
        if (items.size() == 1) {
            String baseName = items.get(0).getName();
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            input.setText(baseName);
        } else {
            input.setText(getString(R.string.compressed_files_default_name));
        }
        builder.setView(input);

        builder.setPositiveButton("压缩", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                executeCompress(items, fileName, format);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeCompress(List<FileItem> items, String fileName, String format) {
        Toast.makeText(requireContext(), "正在压缩文件...", Toast.LENGTH_SHORT).show();

        List<String> fileNames = new ArrayList<>();
        for (FileItem item : items) {
            fileNames.add(item.getName());
        }

        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder fileListJson = new StringBuilder("[");
        for (int i = 0; i < fileNames.size(); i++) {
            if (i > 0) fileListJson.append(",");
            fileListJson.append("\"").append(fileNames.get(i)).append("\"");
        }
        fileListJson.append("]");

        new FileApi().zipFileOrFolder(requireContext(), deviceId, currentPath, fileListJson.toString(), format, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "压缩完成: " + fileName + "." + format, Toast.LENGTH_SHORT).show();
                    clearAllSelections();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "压缩失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void extractFile(FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("解压文件");
        builder.setMessage("确定要解压 " + item.getName() + " 吗？");
        builder.setPositiveButton("解压", (dialog, which) -> executeExtract(item));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeExtract(FileItem item) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new FileApi().unzipFile(requireContext(), deviceId, currentPath, item.getName(), new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    clearAllSelections();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "解压失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void renameFile(FileItem item) {
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
                    Toast.makeText(requireContext(), "重命���失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void deleteFiles(List<FileItem> items) {
        String message = items.size() == 1 ?
                "确定要删除 " + items.get(0).getName() + " 吗？" :
                "确定要删除选中的 " + items.size() + " 个项目吗？";

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("删除确认");
        builder.setMessage(message);
        builder.setPositiveButton("删除", (dialog, which) -> executeDelete(items));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeDelete(List<FileItem> items) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.List<String> filePaths = new java.util.ArrayList<>();
        for (FileItem item : items) {
            String filePath = appendPath(currentPath, item.getName());
            filePaths.add(filePath);
        }

        new FileApi().deleteFileOrFolderBatch(requireContext(), deviceId, filePaths, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "删除完成", Toast.LENGTH_SHORT).show();
                    clearAllSelections();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "删除失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                    clearAllSelections();
                    loadFileList();
                });
            }
        });
    }

    private void cutFiles(List<FileItem> items) {
        cutFilesPaths.clear();
        for (FileItem item : items) {
            String filePath = appendPath(currentPath, item.getName());
            cutFilesPaths.add(filePath);
        }
        cutSourcePath = currentPath;

        Toast.makeText(requireContext(), "已剪切 " + items.size() + " 个项目，请到目标文件夹粘贴", Toast.LENGTH_SHORT).show();
        clearAllSelections();
    }

    private void copyFile(FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("创建副本");

        EditText input = new EditText(requireContext());
        String baseName = item.getName();
        int dotIndex = baseName.lastIndexOf('.');
        String newName = dotIndex > 0 ?
                baseName.substring(0, dotIndex) + "_副本" + baseName.substring(dotIndex) :
                baseName + "_副本";
        input.setText(newName);
        builder.setView(input);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                executeCopyFile(item, fileName);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void executeCopyFile(FileItem item, String newName) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        String sourceFilePath = appendPath(currentPath, item.getName());

        new FileApi().copyFileOrFolder(requireContext(), deviceId, sourceFilePath, new FileApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "创建副本成功", Toast.LENGTH_SHORT).show();
                    clearAllSelections();
                    loadFileList();
                });
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "创建副本失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showToolboxDialog() {
        String[] options = {"修复权限和中文名异常问题"};
        boolean[] checkedItems = {true};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("工具箱");

        builder.setMultiChoiceItems(options, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

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
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(requireContext(), "工具箱操作失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 粘贴剪切的文件到当前目录
     */
    private void pasteFiles() {
        if (cutFilesPaths.isEmpty()) {
            Toast.makeText(requireContext(), "没有可粘贴的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentPath.equals(cutSourcePath)) {
            Toast.makeText(requireContext(), "不能粘贴到同一文件夹", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);

        if (deviceId == -1) {
            Toast.makeText(requireContext(), "设备ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建JSON数组字符串
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(cutFilesPaths);
            String listString = jsonArray.toString();

            new FileApi().moveFileOrFolder(requireContext(), deviceId, listString, currentPath, new FileApi.Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "粘贴完成", Toast.LENGTH_SHORT).show();
                        cutFilesPaths.clear();
                        cutSourcePath = "";
                        loadFileList();
                    });
                }

                @Override
                public void onFailure(String errorMsg) {
                    mainHandler.post(() -> {
                        if (errorMsg.contains("500")) {
                            Toast.makeText(requireContext(), "不能粘贴到同一文件夹", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "粘贴失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "构建请求数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startFileWatcher(String localFilePath, String remoteFilePath, String fileName) {
        if (fileWatchers.containsKey(localFilePath)) {
            FileWatcher existingWatcher = fileWatchers.get(localFilePath);
            if (existingWatcher != null) {
                existingWatcher.stopWatching();
            }
        }
    }
}
