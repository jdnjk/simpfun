package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.FileAdapter;
import cn.jdnjk.simpfun.model.FileItem;

class FilePaneViews {
    interface Callbacks {
        void onRefresh();
        void onRetry();
        void onAddClick();
        void onItemClick(FileItem item);
        void onItemLongClick(FileItem item, View anchor);
        void onItemMoreClick(FileItem item, View anchor);
        void onItemSwipe(FileItem item, int position);
        void onSelectionCancel();
        void onSelectionInvert();
        void onSelectionCut();
        void onSelectionCopy();
        void onSelectionArchive();
        void onSelectionDelete();
        void onMoveHere();
        void onMoveCancel();
        void onHistoryBack();
        void onHistoryForward();
        void onToolbarNewEntry();
        void onSwapPane();
        void onParentDirectory();
        void onPathClick(String path);
        void onPathLongClick();
        void onPaneTouched();
    }

    private final FilePaneState state;
    private final Callbacks callbacks;
    private final FileAdapter adapter;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private TextView emptyTitle;
    private View emptyStateLayout;
    private View emptyRetryButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout pathContainer;
    private HorizontalScrollView pathScrollView;
    private View bottomToolbarScrollView;
    private FloatingActionButton fabAdd;
    private View selectionBar;
    private TextView selectionCountText;
    private View selectionInvertButton;
    private View selectionCutButton;
    private View selectionCopyButton;
    private View selectionArchiveButton;
    private View selectionDeleteButton;
    private View selectionCancelButton;
    private View moveBar;
    private TextView movePendingText;
    private View moveHereButton;
    private View moveCancelButton;
    private TextView historyBackButton;
    private TextView historyForwardButton;
    private View toolbarNewButton;
    private View swapPaneButton;
    private View parentDirButton;
    private boolean showFab = true;
    private boolean showArchiveAction = true;
    private boolean selectionUiEnabled = true;
    private boolean selectionBarEnabled = true;
    private boolean quickSwipeSelection;
    private boolean destroyed;

    FilePaneViews(@NonNull View root, @NonNull FilePaneState state, @NonNull Callbacks callbacks) {
        this.state = state;
        this.callbacks = callbacks;
        fabAdd = root.findViewById(R.id.fab_add);
        recyclerView = root.findViewById(R.id.recycler_view_files);
        progressBar = root.findViewById(R.id.progress_bar);
        emptyView = root.findViewById(R.id.empty_view);
        emptyTitle = root.findViewById(R.id.text_empty_title);
        emptyStateLayout = root.findViewById(R.id.layout_empty_state);
        emptyRetryButton = root.findViewById(R.id.button_empty_retry);
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        pathContainer = root.findViewById(R.id.layout_path_container);
        pathScrollView = root.findViewById(R.id.scroll_view_path);
        bottomToolbarScrollView = root.findViewById(R.id.layout_file_bottom_toolbar);
        selectionBar = root.findViewById(R.id.layout_selection_bar);
        selectionCountText = root.findViewById(R.id.text_selection_count);
        selectionInvertButton = root.findViewById(R.id.button_selection_invert);
        selectionCutButton = root.findViewById(R.id.button_selection_cut);
        selectionCopyButton = root.findViewById(R.id.button_selection_copy);
        selectionArchiveButton = root.findViewById(R.id.button_selection_archive);
        selectionDeleteButton = root.findViewById(R.id.button_selection_delete);
        selectionCancelButton = root.findViewById(R.id.button_selection_cancel);
        moveBar = root.findViewById(R.id.layout_move_bar);
        movePendingText = root.findViewById(R.id.text_move_pending);
        moveHereButton = root.findViewById(R.id.button_move_here);
        moveCancelButton = root.findViewById(R.id.button_move_cancel);
        historyBackButton = root.findViewById(R.id.button_history_back);
        historyForwardButton = root.findViewById(R.id.button_history_forward);
        toolbarNewButton = root.findViewById(R.id.button_toolbar_new);
        swapPaneButton = root.findViewById(R.id.button_swap_pane);
        parentDirButton = root.findViewById(R.id.button_parent_dir);

        adapter = new FileAdapter(state.getFileList(), callbacks::onItemClick, callbacks::onItemLongClick, callbacks::onItemMoreClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(root.getContext()));
        recyclerView.setAdapter(adapter);
        attachSwipeSelection();
        attachPaneTouchListeners(root);

        swipeRefreshLayout.setOnRefreshListener(callbacks::onRefresh);
        if (emptyRetryButton != null) {
            emptyRetryButton.setOnClickListener(v -> callbacks.onRetry());
        }
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> callbacks.onAddClick());
        }
        if (selectionCancelButton != null) {
            selectionCancelButton.setOnClickListener(v -> callbacks.onSelectionCancel());
        }
        if (selectionInvertButton != null) {
            selectionInvertButton.setOnClickListener(v -> callbacks.onSelectionInvert());
        }
        if (selectionCutButton != null) {
            selectionCutButton.setOnClickListener(v -> callbacks.onSelectionCut());
        }
        if (selectionCopyButton != null) {
            selectionCopyButton.setOnClickListener(v -> callbacks.onSelectionCopy());
        }
        if (selectionArchiveButton != null) {
            selectionArchiveButton.setOnClickListener(v -> callbacks.onSelectionArchive());
        }
        if (selectionDeleteButton != null) {
            selectionDeleteButton.setOnClickListener(v -> callbacks.onSelectionDelete());
        }
        if (moveHereButton != null) {
            moveHereButton.setOnClickListener(v -> callbacks.onMoveHere());
        }
        if (moveCancelButton != null) {
            moveCancelButton.setOnClickListener(v -> callbacks.onMoveCancel());
        }
        if (historyBackButton != null) {
            historyBackButton.setOnClickListener(v -> callbacks.onHistoryBack());
        }
        if (historyForwardButton != null) {
            historyForwardButton.setOnClickListener(v -> callbacks.onHistoryForward());
        }
        if (toolbarNewButton != null) {
            toolbarNewButton.setOnClickListener(v -> callbacks.onToolbarNewEntry());
        }
        if (swapPaneButton != null) {
            swapPaneButton.setOnClickListener(v -> callbacks.onSwapPane());
        }
        if (parentDirButton != null) {
            parentDirButton.setOnClickListener(v -> callbacks.onParentDirectory());
        }
    }

    private void attachSwipeSelection() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            private int selectedPosition = RecyclerView.NO_POSITION;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getBindingAdapterPosition();
                if (!selectionUiEnabled || position == RecyclerView.NO_POSITION || position >= state.getFileList().size() || state.getFileList().get(position).isParentEntry()) {
                    return 0;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (quickSwipeSelection && actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
                    int position = viewHolder.getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position < state.getFileList().size()
                            && !state.getFileList().get(position).isParentEntry()
                            && selectedPosition != position
                            && Math.abs(dX) >= getQuickSwipeThreshold(viewHolder.itemView)) {
                        selectedPosition = position;
                        callbacks.onItemSwipe(state.getFileList().get(position), position);
                        adapter.notifyItemChanged(position);
                    }
                    super.onChildDraw(c, recyclerView, viewHolder, dX * 0.35f, dY, actionState, isCurrentlyActive);
                    return;
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return quickSwipeSelection ? 1f : super.getSwipeThreshold(viewHolder);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getBindingAdapterPosition();
                super.clearView(recyclerView, viewHolder);
                if (quickSwipeSelection && selectedPosition == position) {
                    adapter.notifyItemChanged(position);
                }
                selectedPosition = RecyclerView.NO_POSITION;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < state.getFileList().size()) {
                    if (!quickSwipeSelection) {
                        callbacks.onItemSwipe(state.getFileList().get(position), position);
                    }
                    adapter.notifyItemChanged(position);
                }
            }
        }).attachToRecyclerView(recyclerView);
    }

    private float getQuickSwipeThreshold(View itemView) {
        return Math.min(itemView.getWidth() * 0.28f, dp(itemView.getContext(), 96));
    }

    private int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void attachPaneTouchListeners(View root) {
        View.OnTouchListener touchListener = (v, event) -> {
            callbacks.onPaneTouched();
            return false;
        };
        root.setOnTouchListener(touchListener);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnTouchListener(touchListener);
        }
        if (recyclerView != null) {
            recyclerView.setOnTouchListener(touchListener);
        }
    }

    void configureForLocalPane() {
        showFab = false;
        showArchiveAction = false;
        if (fabAdd != null) {
            fabAdd.setVisibility(View.GONE);
        }
        if (selectionArchiveButton != null) {
            selectionArchiveButton.setVisibility(View.GONE);
        }
    }

    void configureForDualPane() {
        showFab = false;
        selectionBarEnabled = false;
        quickSwipeSelection = true;
        adapter.setSelectionPresentation(false, true);
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
        }
        if (pathScrollView != null) {
            pathScrollView.setVisibility(View.GONE);
        }
        if (bottomToolbarScrollView != null) {
            bottomToolbarScrollView.setVisibility(View.GONE);
        }
    }

    void showLoading(boolean show) {
        if (destroyed || progressBar == null || recyclerView == null || emptyStateLayout == null) {
            return;
        }
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    void showError(String msg) {
        if (destroyed || progressBar == null || recyclerView == null || emptyView == null || emptyTitle == null || emptyStateLayout == null) {
            return;
        }
        Context context = emptyView.getContext();
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyTitle.setText(R.string.file_error_title);
        emptyView.setText(context.getString(R.string.error_format, msg));
        if (emptyRetryButton != null) {
            emptyRetryButton.setVisibility(View.VISIBLE);
        }
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    void updateEmptyView() {
        if (destroyed || emptyView == null || recyclerView == null || emptyTitle == null || emptyStateLayout == null) {
            return;
        }
        if (state.getFileList().isEmpty()) {
            emptyTitle.setText(R.string.file_empty_title);
            emptyView.setText(R.string.file_empty_desc);
            if (emptyRetryButton != null) {
                emptyRetryButton.setVisibility(View.GONE);
            }
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    void renderPath() {
        if (destroyed || pathContainer == null) {
            return;
        }
        Context context = pathContainer.getContext();
        String currentPath = state.getCurrentPath();
        String rootPath = state.getRootPath();
        pathContainer.removeAllViews();

        TextView rootNode = createPathNode(context, rootPath, rootPath.equals(currentPath));
        rootNode.setOnClickListener(v -> {
            if (!rootPath.equals(state.getCurrentPath())) {
                callbacks.onPathClick(rootPath);
            }
        });
        rootNode.setOnLongClickListener(v -> {
            callbacks.onPathLongClick();
            return true;
        });
        pathContainer.addView(rootNode);

        if (rootPath.equals(currentPath)) {
            return;
        }

        String suffix = "/".equals(rootPath) ? currentPath.substring(1) : currentPath.substring(rootPath.length());
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        String[] parts = suffix.split("/");
        String builtPath = rootPath;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            TextView divider = new TextView(context);
            divider.setText(">");
            divider.setTextColor(Color.LTGRAY);
            divider.setTextSize(14);
            pathContainer.addView(divider);

            builtPath = "/".equals(builtPath) ? builtPath + part : builtPath + "/" + part;
            String thisPath = builtPath;

            TextView node = createPathNode(context, part, thisPath.equals(currentPath));
            if (thisPath.equals(currentPath)) {
                node.setOnLongClickListener(v -> {
                    callbacks.onPathLongClick();
                    return true;
                });
            }
            node.setOnClickListener(v -> {
                if (!thisPath.equals(state.getCurrentPath())) {
                    callbacks.onPathClick(thisPath);
                }
            });
            pathContainer.addView(node);
        }

        if (pathScrollView != null) {
            pathScrollView.post(() -> pathScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
        }
    }

    void renderSelection() {
        if (destroyed) {
            return;
        }
        boolean selectionMode = selectionUiEnabled && state.isSelectionMode();
        if (selectionBar != null && selectionBarEnabled) {
            selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        }
        if (selectionCountText != null) {
            selectionCountText.setText(selectionCountText.getContext().getString(R.string.file_selection_count, state.getSelectedPaths().size()));
        }
        if (selectionInvertButton != null) {
            selectionInvertButton.setEnabled(state.hasSelectableItems());
        }
        if (selectionCutButton != null) {
            selectionCutButton.setEnabled(state.hasSelection());
        }
        if (selectionArchiveButton != null) {
            selectionArchiveButton.setVisibility(showArchiveAction ? View.VISIBLE : View.GONE);
            selectionArchiveButton.setEnabled(state.hasSelection());
        }
        if (selectionDeleteButton != null) {
            selectionDeleteButton.setEnabled(state.hasSelection());
        }
        if (fabAdd != null) {
            fabAdd.setVisibility(showFab && !selectionMode ? View.VISIBLE : View.GONE);
        }
        adapter.setSelectionState(selectionMode, selectionMode ? state.getSelectedPaths() : java.util.Collections.emptySet(), state::getItemPath);
    }

    void renderMoveBar() {
        if (destroyed || moveBar == null) {
            return;
        }
        boolean hasPendingMove = state.hasPendingMove();
        moveBar.setVisibility(hasPendingMove ? View.VISIBLE : View.GONE);
        if (movePendingText != null && hasPendingMove) {
            String label = state.getPendingMoveLabel() == null ? "" : state.getPendingMoveLabel();
            movePendingText.setText(movePendingText.getContext().getString(R.string.file_move_pending, label));
        }
    }

    void renderBottomToolbar() {
        if (destroyed) {
            return;
        }
        if (historyBackButton != null) {
            String target = state.getBackHistoryTarget();
            historyBackButton.setText(target == null ? "返回" : "返回到" + target);
            historyBackButton.setEnabled(target != null);
        }
        if (historyForwardButton != null) {
            String target = state.getForwardHistoryTarget();
            historyForwardButton.setText(target == null ? "前进" : "前进到" + target);
            historyForwardButton.setEnabled(target != null);
        }
        if (parentDirButton != null) {
            parentDirButton.setEnabled(!state.isAtRoot());
        }
    }

    void notifyFileListChanged() {
        if (!destroyed) {
            adapter.notifyDataSetChanged();
        }
    }

    void stopRefreshing() {
        if (!destroyed && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    void requestPaneFocus() {
        if (!destroyed && recyclerView != null) {
            recyclerView.requestFocus();
        }
    }

    void destroy() {
        destroyed = true;
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        recyclerView = null;
        progressBar = null;
        emptyView = null;
        emptyTitle = null;
        emptyStateLayout = null;
        emptyRetryButton = null;
        swipeRefreshLayout = null;
        pathContainer = null;
        pathScrollView = null;
        bottomToolbarScrollView = null;
        fabAdd = null;
        selectionBar = null;
        selectionCountText = null;
        selectionInvertButton = null;
        selectionCutButton = null;
        selectionArchiveButton = null;
        selectionDeleteButton = null;
        selectionCancelButton = null;
        moveBar = null;
        movePendingText = null;
        moveHereButton = null;
        moveCancelButton = null;
        historyBackButton = null;
        historyForwardButton = null;
        toolbarNewButton = null;
        swapPaneButton = null;
        parentDirButton = null;
    }

    private TextView createPathNode(Context context, String text, boolean current) {
        TextView node = new TextView(context);
        node.setText(text);
        node.setTextColor(Color.WHITE);
        node.setTextSize(16);
        node.setPadding(16, 8, 16, 8);
        node.setGravity(Gravity.CENTER);
        node.setBackgroundResource(android.R.color.transparent);
        if (current) {
            node.setTypeface(null, Typeface.BOLD);
        }
        return node;
    }
}
