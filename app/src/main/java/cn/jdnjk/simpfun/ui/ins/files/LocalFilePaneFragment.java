package cn.jdnjk.simpfun.ui.ins.files;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.FilePathUtils;
import cn.jdnjk.simpfun.utils.StoragePermissionHelper;

public class LocalFilePaneFragment extends Fragment implements FilePaneViews.Callbacks, LocalFileListController.Host {
    private static final String LOCAL_ROOT = "/sdcard";
    private static final String ARG_INITIAL_PATH = "initial_path";

    static LocalFilePaneFragment newInstance(String initialPath) {
        LocalFilePaneFragment fragment = new LocalFilePaneFragment();
        Bundle args = new Bundle();
        if (initialPath != null && !initialPath.trim().isEmpty()) {
            args.putString(ARG_INITIAL_PATH, initialPath);
        }
        fragment.setArguments(args);
        return fragment;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService operationExecutor = Executors.newSingleThreadExecutor();
    private FilePaneState state;
    private FilePaneViews views;
    private LocalFileListController listController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ensureState();
        View root = inflater.inflate(R.layout.fragment_file_pane, container, false);
        views = new FilePaneViews(root, state, this);
        views.configureForLocalPane();
        views.configureForDualPane();
        listController = new LocalFileListController(state, this);
        renderNavigationState();
        loadFileList();
        return root;
    }

    @Override
    public void onDestroyView() {
        if (views != null) {
            views.destroy();
            views = null;
        }
        if (listController != null) {
            listController.shutdown();
            listController = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        operationExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRefresh() {
        notifyHostTouched();
        loadFileList();
    }

    @Override
    public void onRetry() {
        Context context = getContext();
        if (context != null && !StoragePermissionHelper.hasLocalStorageAccess(context)) {
            openStoragePermissionSettings();
            return;
        }
        loadFileList();
    }

    @Override
    public void onAddClick() {
        onToolbarNewEntry();
    }

    @Override
    public void onItemClick(FileItem item) {
        notifyHostTouched();
        if (item.isParentEntry()) {
            navigateToPath(state.getParentPath());
            return;
        }
        if (state.isSelectionMode()) {
            state.toggleSelection(item);
            renderSelection();
            return;
        }
        if (item.isFile()) {
            openLocalFile(item);
        } else {
            navigateToPath(FilePathUtils.appendPath(state.getCurrentPath(), item.getName()));
        }
    }

    @Override
    public void onItemLongClick(FileItem item, View anchor) {
        notifyHostTouched();
        if (!item.isParentEntry()) {
            showDualLocalPopup(item, anchor);
        }
    }

    @Override
    public void onItemMoreClick(FileItem item, View anchor) {
        notifyHostTouched();
        showDualLocalPopup(item, anchor);
    }

    @Override
    public void onItemSwipe(FileItem item, int position) {
        state.selectBySwipe(position);
        renderSelection();
    }

    @Override
    public void onSelectionCancel() {
        clearSelectionAndRender();
    }

    @Override
    public void onSelectionInvert() {
        state.invertSelection();
        renderSelection();
    }

    @Override
    public void onSelectionCut() {
        if (state.prepareMoveSelected()) {
            renderSelection();
            renderMoveBar();
            toast("请选择目标目录后点击移动到当前目录", Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onSelectionCopy() {
        if (!state.hasSelection()) {
            return;
        }
        if (getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.requestCrossCopy(this, state.copySelectedItems());
            return;
        }
        try {
            copyPaths(state.copySelectedItems());
        } catch (Exception e) {
            toast(e.getMessage() == null ? "复制失败" : e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onSelectionArchive() {
    }

    @Override
    public void onSelectionDelete() {
        if (state.hasSelection()) {
            showDeleteConfirmDialog(state.copySelectedPaths(), state.getSelectedPaths().size() + " 项");
        }
    }

    @Override
    public void onMoveHere() {
        movePendingToCurrentPath();
    }

    @Override
    public void onMoveCancel() {
        state.clearPendingMove();
        renderMoveBar();
    }

    @Override
    public void onHistoryBack() {
        if (state.goBackInHistory()) {
            onPathChangedAfterStateUpdate();
        }
    }

    @Override
    public void onHistoryForward() {
        if (state.goForwardInHistory()) {
            onPathChangedAfterStateUpdate();
        }
    }

    @Override
    public void onToolbarNewEntry() {
        showToolbarNewEntryDialog();
    }

    @Override
    public void onSwapPane() {
        if (getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.swapPanesFromChild(this);
        }
    }

    @Override
    public void onParentDirectory() {
        if (!state.isAtRoot()) {
            navigateToPath(state.getParentPath());
        }
    }

    @Override
    public void onPathClick(String path) {
        navigateToPath(path);
    }

    @Override
    public void onPathLongClick() {
        showEditPathDialog();
    }

    @Override
    public Context getContextOrNull() {
        return getContext();
    }

    @Override
    public boolean isActive() {
        return isAdded() && views != null;
    }

    @Override
    public void showLoading(boolean show) {
        if (views != null) {
            views.showLoading(show);
        }
    }

    @Override
    public void showError(String message) {
        if (views != null) {
            views.showError(message);
        }
    }

    @Override
    public void stopRefreshing() {
        if (views != null) {
            views.stopRefreshing();
        }
    }

    @Override
    public void onFileListChanged() {
        if (views == null) {
            return;
        }
        views.renderSelection();
        views.notifyFileListChanged();
        views.updateEmptyView();
    }

    boolean clearSelectionForHost() {
        if (!state.isSelectionMode()) {
            return false;
        }
        clearSelectionAndRender();
        return true;
    }

    boolean clearPendingMoveForHost() {
        if (!state.hasPendingMove()) {
            return false;
        }
        state.clearPendingMove();
        renderMoveBar();
        toast("已取消移动", Toast.LENGTH_SHORT);
        return true;
    }

    boolean navigateUpForHost() {
        if (state.isAtRoot()) {
            return false;
        }
        navigateToPath(state.getParentPath());
        return true;
    }

    void requestPaneFocus() {
        if (views != null) {
            views.requestPaneFocus();
        }
    }

    String getCurrentPathForHost() {
        ensureState();
        return state == null ? LOCAL_ROOT : state.getCurrentPath();
    }

    String getItemPathForHost(FileItem item) {
        return state.getItemPath(item);
    }

    File resolveLocalPathForHost(String virtualPath) throws Exception {
        return requireLocalFile(virtualPath);
    }

    File resolveChildInCurrentPathForHost(String childName) throws Exception {
        return new File(requireLocalFile(state.getCurrentPath()), childName);
    }

    void reloadForHost() {
        loadFileList();
    }

    void showEditPathDialogForHost() {
        showEditPathDialog();
    }

    void deleteLocalPathForHost(String virtualPath) throws Exception {
        deleteRecursively(requireLocalFile(virtualPath));
    }

    private void showToolbarNewEntryDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.file_name_placeholder);
        new MaterialAlertDialogBuilder(context)
                .setTitle("新建")
                .setView(input)
                .setNeutralButton(R.string.cancel, null)
                .setNegativeButton(R.string.file, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (!FilePathUtils.isSafeEntryName(name)) {
                        toast("名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT);
                        return;
                    }
                    createLocalEntry(true, name);
                })
                .setPositiveButton(R.string.folder, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (!FilePathUtils.isSafeEntryName(name)) {
                        toast("名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT);
                        return;
                    }
                    createLocalEntry(false, name);
                })
                .show();
    }

    private void createLocalEntry(boolean createFile, String name) {
        runLocalOperation("创建完成", () -> {
            File target = new File(requireLocalFile(state.getCurrentPath()), name);
            ensureTargetAvailable(target);
            if (createFile) {
                if (!target.createNewFile()) {
                    throw new IOException("创建文件失败");
                }
            } else if (!target.mkdirs() && !target.isDirectory()) {
                throw new IOException("创建文件夹失败");
            }
        }, true, false);
    }

    private void showDualLocalPopup(FileItem item, View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        DualFilePaneFragment dualFilePaneFragment = getParentFragment() instanceof DualFilePaneFragment parent ? parent : null;
        boolean canCrossTransfer = dualFilePaneFragment != null && dualFilePaneFragment.canTransferToOppositePane(this);
        popupMenu.getMenu().add(dualFilePaneFragment == null ? "复制到另一页" : dualFilePaneFragment.getCrossTransferMenuLabel(this, false))
                .setEnabled(canCrossTransfer)
                .setOnMenuItemClickListener(menuItem -> {
                    if (dualFilePaneFragment != null) {
                        dualFilePaneFragment.requestCrossCopy(this, getSelectedItemsOrSingle(item));
                    }
                    return true;
                });
        popupMenu.getMenu().add(dualFilePaneFragment == null ? "移动到另一页" : dualFilePaneFragment.getCrossTransferMenuLabel(this, true))
                .setEnabled(canCrossTransfer)
                .setOnMenuItemClickListener(menuItem -> {
                    if (dualFilePaneFragment != null) {
                        dualFilePaneFragment.requestCrossMove(this, getSelectedItemsOrSingle(item));
                    }
                    return true;
                });
        popupMenu.getMenu().add(R.string.file_action_delete).setOnMenuItemClickListener(menuItem -> {
            showDeleteConfirmDialog(state.singlePathList(item), item.getName());
            return true;
        });
        popupMenu.getMenu().add(R.string.file_action_rename).setOnMenuItemClickListener(menuItem -> {
            showRenameDialog(item);
            return true;
        });
        popupMenu.getMenu().add("属性").setOnMenuItemClickListener(menuItem -> {
            if (dualFilePaneFragment != null) {
                dualFilePaneFragment.requestProperties(this, item);
            }
            return true;
        });
        popupMenu.show();
    }

    private List<FileItem> getSelectedItemsOrSingle(FileItem item) {
        if (state.isSelectionMode() && state.getSelectedPaths().contains(state.getItemPath(item))) {
            return state.copySelectedItems();
        }
        return java.util.Collections.singletonList(item);
    }

    private void showLocalActionDialog(FileItem item) {
        if (item.isParentEntry() || getActivity() == null) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(requireActivity(), R.style.ThemeOverlay_Simpfun_BottomSheet);
        View view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_file_actions, null, false);

        TextView title = view.findViewById(R.id.text_view_title);
        if (title != null) {
            title.setText(item.getName());
        }

        TextView openAction = view.findViewById(R.id.action_open);
        View downloadAction = view.findViewById(R.id.action_download);
        View renameAction = view.findViewById(R.id.action_rename);
        View deleteAction = view.findViewById(R.id.action_delete);
        View copyAction = view.findViewById(R.id.action_copy);
        View moveAction = view.findViewById(R.id.action_move);
        TextView selectAction = view.findViewById(R.id.action_archive);
        View unarchiveAction = view.findViewById(R.id.action_unarchive);

        if (openAction != null) {
            openAction.setText(item.isFile() ? R.string.file_action_open_local : R.string.file_action_open_folder);
            openAction.setOnClickListener(v -> {
                dialog.dismiss();
                if (item.isFile()) {
                    openLocalFile(item);
                } else {
                    navigateToPath(FilePathUtils.appendPath(state.getCurrentPath(), item.getName()));
                }
            });
        }
        if (downloadAction != null) {
            downloadAction.setVisibility(View.GONE);
        }
        if (renameAction != null) {
            renameAction.setOnClickListener(v -> {
                dialog.dismiss();
                showRenameDialog(item);
            });
        }
        if (deleteAction != null) {
            deleteAction.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteConfirmDialog(state.singlePathList(item), item.getName());
            });
        }
        if (copyAction != null) {
            copyAction.setOnClickListener(v -> {
                dialog.dismiss();
                copyAsSibling(item);
            });
        }
        if (moveAction != null) {
            moveAction.setOnClickListener(v -> {
                dialog.dismiss();
                state.prepareMove(item);
                renderNavigationState();
                toast("请选择目标目录后点击移动到当前目录", Toast.LENGTH_LONG);
            });
        }
        if (selectAction != null) {
            selectAction.setVisibility(View.VISIBLE);
            selectAction.setText("选中");
            selectAction.setOnClickListener(v -> {
                dialog.dismiss();
                state.toggleSelection(item);
                renderSelection();
            });
        }
        if (unarchiveAction != null) {
            unarchiveAction.setVisibility(View.GONE);
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private void openLocalFile(FileItem item) {
        Context context = getContext();
        if (context == null || listController == null) {
            return;
        }
        try {
            File file = listController.resolveLocalFile(state.getItemPath(item));
            Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, item.getMime() == null || item.getMime().isEmpty() ? "*/*" : item.getMime());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, item.getName()));
        } catch (ActivityNotFoundException e) {
            toast("没有可打开此文件的应用", Toast.LENGTH_SHORT);
        } catch (Exception e) {
            toast(e.getMessage() == null ? "打开文件失败" : e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void showRenameDialog(FileItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText input = new EditText(context);
        input.setText(item.getName());
        input.setSingleLine(true);
        input.setSelection(input.getText().length());
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.file_action_rename)
                .setView(input)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.equals(item.getName())) {
                        return;
                    }
                    if (!FilePathUtils.isSafeEntryName(newName)) {
                        toast("名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT);
                        return;
                    }
                    renameItem(item, newName);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog(List<String> paths, String label) {
        Context context = getContext();
        if (context == null || paths.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("删除确认")
                .setMessage("确定要删除 " + label + " 吗？")
                .setPositiveButton("删除", (d, w) -> deletePaths(paths))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEditPathDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText input = new EditText(context);
        input.setText(state.getCurrentPath());
        input.setSingleLine(true);
        input.setSelection(input.getText().length());
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.jump_path_title)
                .setView(input)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String path = input.getText().toString().trim();
                    navigateToPath(path.isEmpty() ? LOCAL_ROOT : path);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    private void renameItem(FileItem item, String newName) {
        runLocalOperation("重命名完成", () -> {
            File source = requireLocalFile(state.getItemPath(item));
            File target = new File(source.getParentFile(), newName);
            ensureTargetAvailable(target);
            if (!source.renameTo(target)) {
                throw new IOException("重命名失败");
            }
        }, true, false);
    }

    private void copyAsSibling(FileItem item) {
        runLocalOperation("复制完成", () -> {
            File source = requireLocalFile(state.getItemPath(item));
            File target = buildCopyTarget(source);
            copyRecursively(source, target);
        }, true, false);
    }

    private void copyPaths(List<FileItem> items) {
        if (items.isEmpty()) {
            return;
        }
        runLocalOperation("复制完成", () -> {
            for (FileItem item : items) {
                File source = requireLocalFile(state.getItemPath(item));
                File target = buildCopyTarget(source);
                copyRecursively(source, target);
            }
        }, true, true);
    }

    private void deletePaths(List<String> paths) {
        runLocalOperation("删除完成", () -> {
            for (String path : paths) {
                deleteRecursively(requireLocalFile(path));
            }
        }, true, true);
    }

    private void movePendingToCurrentPath() {
        String error = state.validateMoveTarget(state.getCurrentPath());
        if (error != null) {
            toast(error, Toast.LENGTH_SHORT);
            return;
        }
        List<String> paths = state.copyPendingMovePaths();
        if (paths.isEmpty()) {
            return;
        }
        runLocalOperation("移动完成", () -> {
            File targetDirectory = requireLocalFile(state.getCurrentPath());
            for (String path : paths) {
                File source = requireLocalFile(path);
                File target = new File(targetDirectory, source.getName());
                ensureTargetAvailable(target);
                if (!source.renameTo(target)) {
                    copyRecursively(source, target);
                    deleteRecursively(source);
                }
            }
        }, true, true);
    }

    private void runLocalOperation(String successMessage, LocalOperation operation, boolean reload, boolean clearSelectionAndMove) {
        operationExecutor.execute(() -> {
            try {
                operation.run();
                mainHandler.post(() -> {
                    if (!isActive()) {
                        return;
                    }
                    if (clearSelectionAndMove) {
                        state.clearSelection();
                        state.clearPendingMove();
                    }
                    if (reload) {
                        renderNavigationState();
                        loadFileList();
                    }
                    toast(successMessage, Toast.LENGTH_SHORT);
                });
            } catch (Exception e) {
                mainHandler.post(() -> toast(e.getMessage() == null ? "本地文件操作失败" : e.getMessage(), Toast.LENGTH_LONG));
            }
        });
    }

    private File requireLocalFile(String path) throws Exception {
        if (listController == null) {
            throw new IllegalStateException("本地文件列表未初始化");
        }
        return listController.resolveLocalFile(path);
    }

    private void ensureTargetAvailable(File target) throws IOException {
        if (target.exists()) {
            throw new IOException("目标已存在：" + target.getName());
        }
    }

    private File buildCopyTarget(File source) throws IOException {
        File parent = source.getParentFile();
        if (parent == null) {
            throw new IOException("无法确定目标目录");
        }
        String name = source.getName();
        String base = name;
        String extension = "";
        if (source.isFile()) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                base = name.substring(0, dot);
                extension = name.substring(dot);
            }
        }
        for (int i = 1; i < 1000; i++) {
            String suffix = i == 1 ? " - 副本" : " - 副本 (" + i + ")";
            File candidate = new File(parent, base + suffix + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        throw new IOException("无法创建副本名称");
    }

    private void copyRecursively(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.mkdirs() && !target.isDirectory()) {
                throw new IOException("无法创建目录：" + target.getName());
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursively(child, new File(target, child.getName()));
                }
            }
            return;
        }
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("无法删除：" + file.getName());
        }
    }

    private void openStoragePermissionSettings() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            startActivity(StoragePermissionHelper.createManageAllFilesIntent(context));
        } catch (ActivityNotFoundException e) {
            startActivity(StoragePermissionHelper.createManageAllFilesFallbackIntent());
        }
    }

    private void navigateToPath(String path) {
        state.navigateTo(path);
        onPathChangedAfterStateUpdate();
    }

    private void onPathChangedAfterStateUpdate() {
        state.clearSelection();
        renderSelection();
        renderPath();
        renderBottomToolbar();
        notifyHostPathChanged();
        loadFileList();
    }

    private void loadFileList() {
        if (listController != null) {
            listController.loadFileList();
        }
    }

    private void clearSelectionAndRender() {
        state.clearSelection();
        renderSelection();
    }

    private void renderNavigationState() {
        renderSelection();
        renderMoveBar();
        renderPath();
        renderBottomToolbar();
        notifyHostPathChanged();
    }

    private void renderSelection() {
        if (views != null) {
            views.renderSelection();
        }
    }

    private void renderMoveBar() {
        if (views != null) {
            views.renderMoveBar();
        }
    }

    private void renderPath() {
        if (views != null) {
            views.renderPath();
        }
    }

    private void renderBottomToolbar() {
        if (views != null) {
            views.renderBottomToolbar();
        }
    }

    private void notifyHostPathChanged() {
        if (getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.onChildPathChanged(this);
        }
    }

    private void notifyHostTouched() {
        if (getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.onChildTouched(this);
        }
    }

    private void toast(String message, int length) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, length).show();
        }
    }

    private void ensureState() {
        if (state == null) {
            String initialPath = LOCAL_ROOT;
            if (getArguments() != null) {
                String argPath = getArguments().getString(ARG_INITIAL_PATH, LOCAL_ROOT);
                if (argPath != null && !argPath.trim().isEmpty()) {
                    initialPath = argPath;
                }
            }
            state = new FilePaneState(initialPath, LOCAL_ROOT);
        }
    }

    private interface LocalOperation {
        void run() throws Exception;
    }
}
