package cn.jdnjk.simpfun.ui.ins.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.model.FileItem;
import cn.jdnjk.simpfun.utils.FilePathUtils;

public class FilePaneFragment extends Fragment implements
        FilePaneViews.Callbacks,
        FilePaneListController.Host,
        FilePaneOperations.Host,
        FileTransferController.Host {
    private static final String ARG_INITIAL_PATH = "initial_path";
    private static final String ARG_EMBEDDED = "embedded";

    static FilePaneFragment newEmbedded() {
        return newEmbedded(null);
    }

    static FilePaneFragment newEmbedded(String initialPath) {
        FilePaneFragment fragment = new FilePaneFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_EMBEDDED, true);
        if (initialPath != null && !initialPath.trim().isEmpty()) {
            args.putString(ARG_INITIAL_PATH, initialPath);
        }
        fragment.setArguments(args);
        return fragment;
    }

    private FilePaneState state;
    private FilePaneViews views;
    private FilePaneListController listController;
    private FilePaneOperations operations;
    private FileTransferController transferController;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureState();
        transferController = new FileTransferController(this, state, this);
        if (!isEmbedded()) {
            requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackPressed(this);
                }
            });
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ensureState();
        View root = inflater.inflate(R.layout.fragment_file_pane, container, false);
        views = new FilePaneViews(root, state, this);
        if (isEmbedded()) {
            views.configureForDualPane();
        }
        listController = new FilePaneListController(state, this);
        operations = new FilePaneOperations(state, this);
        renderNavigationState();
        loadFileList();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateActivityFileTitle();
    }

    @Override
    public void onDestroyView() {
        if (transferController != null) {
            transferController.onDestroyView();
        }
        if (views != null) {
            views.destroy();
            views = null;
        }
        listController = null;
        operations = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (transferController != null) {
            transferController.onDestroy();
            transferController = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRefresh() {
        notifyHostTouched();
        loadFileList();
    }

    @Override
    public void onRetry() {
        loadFileList();
    }

    @Override
    public void onAddClick() {
        showCreateOptionsDialog();
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
            if (transferController != null) {
                transferController.downloadAndOpenFile(item);
            }
        } else {
            navigateToPath(FilePathUtils.appendPath(state.getCurrentPath(), item.getName()));
        }
    }

    @Override
    public void onItemLongClick(FileItem item, View anchor) {
        notifyHostTouched();
        if (item.isParentEntry()) {
            return;
        }
        if (isEmbedded()) {
            showDualServerPopup(item, anchor);
        } else {
            showFileActionDialog(item);
        }
    }

    @Override
    public void onItemMoreClick(FileItem item, View anchor) {
        notifyHostTouched();
        if (isEmbedded()) {
            showDualServerPopup(item, anchor);
        } else {
            showFileActionDialog(item);
        }
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
        if (isEmbedded() && getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.requestCrossCopy(this, state.copySelectedItems());
            return;
        }
        if (operations != null) {
            operations.copyPaths(state.copySelectedPaths());
        }
    }

    @Override
    public void onSelectionArchive() {
        showArchiveFormatDialog(state.copySelectedPaths());
    }

    @Override
    public void onSelectionDelete() {
        showDeleteSelectedConfirmDialog();
    }

    @Override
    public void onMoveHere() {
        if (operations != null) {
            operations.movePendingToCurrentPath();
        }
    }

    @Override
    public void onMoveCancel() {
        clearPendingMoveAndRender();
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
        if (isEmbedded() && getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.swapPanesFromChild(this);
        } else {
            toast("单页模式不可互换", Toast.LENGTH_SHORT);
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
    public int getDeviceId(Context context) {
        if (getActivity() instanceof ServerManages activity && activity.getDeviceId() > 0) {
            return activity.getDeviceId();
        }
        SharedPreferences sp = context.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    @Override
    public boolean useSftpFileList() {
        return isEmbedded();
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

    @Override
    public void clearSelectionAndRender() {
        state.clearSelection();
        renderSelection();
    }

    @Override
    public void clearPendingMoveAndRender() {
        state.clearPendingMove();
        renderMoveBar();
    }

    @Override
    public void reloadFileList() {
        loadFileList();
    }

    @Override
    public void toast(String message, int length) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, length).show();
        }
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
        clearPendingMoveAndRender();
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
        return state.getCurrentPath();
    }

    String getItemPathForHost(FileItem item) {
        return state.getItemPath(item);
    }

    int getDeviceIdForHost(Context context) {
        return getDeviceId(context);
    }

    void reloadForHost() {
        loadFileList();
    }

    void showEditPathDialogForHost() {
        showEditPathDialog();
    }

    private void handleBackPressed(OnBackPressedCallback callback) {
        if (clearSelectionForHost()) {
            return;
        }
        if (clearPendingMoveForHost()) {
            return;
        }
        if (navigateUpForHost()) {
            return;
        }
        callback.setEnabled(false);
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
        callback.setEnabled(true);
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
                    if (operations != null) {
                        operations.createEntry("file", name);
                    }
                })
                .setPositiveButton(R.string.folder, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (!FilePathUtils.isSafeEntryName(name)) {
                        toast("名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT);
                        return;
                    }
                    if (operations != null) {
                        operations.createEntry("folder", name);
                    }
                })
                .show();
    }

    private void showCreateOptionsDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String[] options = {
                getString(R.string.new_file),
                getString(R.string.new_folder),
                getString(R.string.upload_file),
                getString(R.string.file_action_toolbox_fix)
        };
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.file_actions_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCreateEntryDialog(true);
                    } else if (which == 1) {
                        showCreateEntryDialog(false);
                    } else if (which == 2) {
                        if (transferController != null) {
                            transferController.pickFile();
                        }
                    } else {
                        showToolboxFixConfirmDialog();
                    }
                })
                .show();
    }

    private void showCreateEntryDialog(boolean createFile) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        final EditText input = new EditText(context);
        input.setHint(createFile ? getString(R.string.create_file_hint) : getString(R.string.create_folder_hint));
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(context)
                .setTitle(createFile ? getString(R.string.create_file_title) : getString(R.string.create_folder_title))
                .setView(input)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!FilePathUtils.isSafeEntryName(name)) {
                        toast("名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT);
                        return;
                    }
                    if (operations != null) {
                        operations.createEntry(createFile ? "file" : "folder", name);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDualServerPopup(FileItem item, View anchor) {
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
            showDeleteConfirmDialog(item);
            return true;
        });
        popupMenu.getMenu().add(R.string.file_action_rename).setOnMenuItemClickListener(menuItem -> {
            showRenameDialog(item);
            return true;
        });
        popupMenu.getMenu().add(R.string.file_action_archive).setOnMenuItemClickListener(menuItem -> {
            showArchiveFormatDialog(state.singlePathList(item));
            return true;
        });
        if (item.isFile()) {
            popupMenu.getMenu().add(R.string.file_action_unarchive).setOnMenuItemClickListener(menuItem -> {
                if (operations != null) {
                    operations.unarchiveFile(item);
                }
                return true;
            });
        }
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

        TextView openAction = view.findViewById(R.id.action_open);
        View downloadAction = view.findViewById(R.id.action_download);
        View renameAction = view.findViewById(R.id.action_rename);
        View deleteAction = view.findViewById(R.id.action_delete);
        View copyAction = view.findViewById(R.id.action_copy);
        View moveAction = view.findViewById(R.id.action_move);
        View archiveAction = view.findViewById(R.id.action_archive);
        View unarchiveAction = view.findViewById(R.id.action_unarchive);

        if (openAction != null) {
            openAction.setText(item.isFile() ? R.string.file_action_open : R.string.file_action_open_folder);
            openAction.setOnClickListener(v -> {
                dialog.dismiss();
                if (item.isFile()) {
                    if (transferController != null) {
                        transferController.downloadAndOpenFile(item);
                    }
                } else {
                    navigateToPath(FilePathUtils.appendPath(state.getCurrentPath(), item.getName()));
                }
            });
        }
        if (downloadAction != null) {
            downloadAction.setVisibility(item.isFile() ? View.VISIBLE : View.GONE);
            downloadAction.setOnClickListener(v -> {
                dialog.dismiss();
                if (transferController != null) {
                    transferController.downloadFileOnly(item);
                }
            });
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
                showDeleteConfirmDialog(item);
            });
        }
        if (copyAction != null) {
            copyAction.setOnClickListener(v -> {
                dialog.dismiss();
                if (state.isSelectionMode() && state.getSelectedPaths().contains(state.getItemPath(item))) {
                    onSelectionCopy();
                } else if (operations != null) {
                    operations.copyFileOrFolder(item);
                }
            });
        }
        if (moveAction != null) {
            moveAction.setOnClickListener(v -> {
                dialog.dismiss();
                prepareMove(item);
            });
        }
        if (archiveAction != null) {
            archiveAction.setOnClickListener(v -> {
                dialog.dismiss();
                showArchiveFormatDialog(state.singlePathList(item));
            });
        }
        if (unarchiveAction != null) {
            unarchiveAction.setVisibility(item.isFile() ? View.VISIBLE : View.GONE);
            unarchiveAction.setOnClickListener(v -> {
                dialog.dismiss();
                if (operations != null) {
                    operations.unarchiveFile(item);
                }
            });
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private void showDeleteConfirmDialog(FileItem item) {
        showDeleteConfirmDialog(state.singlePathList(item), item.getName());
    }

    private void showDeleteSelectedConfirmDialog() {
        if (!state.hasSelection()) {
            return;
        }
        showDeleteConfirmDialog(state.copySelectedPaths(), state.getSelectedPaths().size() + " 项");
    }

    private void showDeleteConfirmDialog(List<String> paths, String label) {
        Context context = getContext();
        if (context == null || paths.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("删除确认")
                .setMessage("确定要删除 " + label + " 吗？")
                .setPositiveButton("删除", (d, w) -> {
                    if (operations != null) {
                        operations.deletePaths(paths);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
                    if (!newName.equals(item.getName())) {
                        if (!FilePathUtils.isSafeEntryName(newName)) {
                            toast("名称不能包含路径分隔符或特殊目录", Toast.LENGTH_SHORT);
                            return;
                        }
                        if (operations != null) {
                            operations.renameFile(item, newName);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void prepareMove(FileItem item) {
        state.prepareMove(item);
        renderNavigationState();
        toast("请选择目标目录后点击移动到当前目录", Toast.LENGTH_LONG);
    }

    private void showArchiveFormatDialog(List<String> paths) {
        Context context = getContext();
        if (context == null || paths.isEmpty()) {
            return;
        }
        String[] formats = {"zip", "7z"};
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.file_action_archive)
                .setItems(formats, (dialog, which) -> {
                    if (operations != null) {
                        operations.archivePaths(paths, formats[which]);
                    }
                })
                .show();
    }

    private void showToolboxFixConfirmDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.file_action_toolbox_fix)
                .setMessage("确定要修复文件权限和中文文件名吗？")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (operations != null) {
                        operations.runToolboxFix();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void runToolboxFixForHost() {
        showToolboxFixConfirmDialog();
    }

    private void showEditPathDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        final EditText input = new EditText(context);
        input.setText(state.getCurrentPath());
        input.setSingleLine(true);
        input.setSelection(input.getText().length());
        new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.jump_path_title))
                .setView(input)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String p = input.getText().toString().trim();
                    navigateToPath(p.isEmpty() ? "/" : p);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
        updateActivityFileTitle();
        loadFileList();
    }

    private void loadFileList() {
        if (listController != null) {
            listController.loadFileList();
        }
    }

    private void renderNavigationState() {
        renderSelection();
        renderMoveBar();
        renderPath();
        renderBottomToolbar();
        updateActivityFileTitle();
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

    private void updateActivityFileTitle() {
        if (isEmbedded()) {
            notifyHostPathChanged();
            return;
        }
        if (getActivity() instanceof ServerManages activity) {
            activity.clearFilePathTitle();
        }
    }

    private void notifyHostTouched() {
        if (isEmbedded() && getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.onChildTouched(this);
        }
    }

    private void notifyHostPathChanged() {
        if (isEmbedded() && getParentFragment() instanceof DualFilePaneFragment dualFilePaneFragment) {
            dualFilePaneFragment.onChildPathChanged(this);
        }
    }

    private boolean isEmbedded() {
        return getArguments() != null && getArguments().getBoolean(ARG_EMBEDDED, false);
    }

    private void ensureState() {
        if (state != null) {
            return;
        }
        String initialPath = "/";
        if (getArguments() != null) {
            String init = getArguments().getString(ARG_INITIAL_PATH, "/");
            if (init != null && !init.trim().isEmpty()) {
                initialPath = init.trim();
            }
        }
        state = new FilePaneState(initialPath);
    }
}
