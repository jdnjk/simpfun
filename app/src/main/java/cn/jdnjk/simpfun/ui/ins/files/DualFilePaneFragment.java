package cn.jdnjk.simpfun.ui.ins.files;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;

public class DualFilePaneFragment extends Fragment {
    private PaneSlot leftSlot;
    private PaneSlot rightSlot;
    private PaneSide activePane = PaneSide.LEFT;
    private SftpTransferCoordinator transferCoordinator;
    private View btnDualBack;
    private View btnDualForward;
    private View btnDualParent;
    private View btnDualNew;
    private View btnDualSwap;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed(this);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dual_file_pane, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int deviceId = getActivity() instanceof ServerManages activity ? activity.getDeviceId() : -1;
        transferCoordinator = new SftpTransferCoordinator(requireActivity(), deviceId);
        leftSlot = new PaneSlot(PaneSide.LEFT, view.findViewById(R.id.local_pane_container), R.id.local_pane_container, PaneKind.LOCAL, LocalFilePaneFragment.newInstance(null));
        rightSlot = new PaneSlot(PaneSide.RIGHT, view.findViewById(R.id.server_pane_container), R.id.server_pane_container, PaneKind.SERVER, FilePaneFragment.newEmbedded());
        getChildFragmentManager()
                .beginTransaction()
                .replace(leftSlot.containerId, leftSlot.fragment)
                .replace(rightSlot.containerId, rightSlot.fragment)
                .commitNow();
        setupToolbarPaneMenu();
        leftSlot.container.setOnClickListener(v -> activatePane(PaneSide.LEFT));
        rightSlot.container.setOnClickListener(v -> activatePane(PaneSide.RIGHT));

        btnDualBack = view.findViewById(R.id.btn_dual_back);
        btnDualForward = view.findViewById(R.id.btn_dual_forward);
        btnDualParent = view.findViewById(R.id.btn_dual_parent);
        btnDualNew = view.findViewById(R.id.btn_dual_new);
        btnDualSwap = view.findViewById(R.id.btn_dual_swap);

        btnDualBack.setOnClickListener(v -> handleToolbarAction("back"));
        btnDualForward.setOnClickListener(v -> handleToolbarAction("forward"));
        btnDualParent.setOnClickListener(v -> handleToolbarAction("parent"));
        btnDualNew.setOnClickListener(v -> handleToolbarAction("new"));
        btnDualSwap.setOnClickListener(v -> swapPanes());

        updateActivePane();
        updateActivityTitle();
    }

    private void handleToolbarAction(String action) {
        Fragment fragment = getActiveFragment();
        if (fragment instanceof LocalFilePaneFragment localFrag) {
            switch (action) {
                case "back": localFrag.onHistoryBack(); break;
                case "forward": localFrag.onHistoryForward(); break;
                case "parent": localFrag.onParentDirectory(); break;
                case "new": localFrag.onToolbarNewEntry(); break;
                case "add": localFrag.onAddClick(); break;
            }
        } else if (fragment instanceof FilePaneFragment fileFrag) {
            switch (action) {
                case "back": fileFrag.onHistoryBack(); break;
                case "forward": fileFrag.onHistoryForward(); break;
                case "parent": fileFrag.onParentDirectory(); break;
                case "new": fileFrag.onToolbarNewEntry(); break;
                case "add": fileFrag.onAddClick(); break;
                case "fix": fileFrag.runToolboxFixForHost(); break;
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (transferCoordinator != null) {
            transferCoordinator.shutdown();
            transferCoordinator = null;
        }
        if (getActivity() instanceof ServerManages activity) {
            activity.clearFilePathTitle();
        }
        leftSlot = null;
        rightSlot = null;
        btnDualBack = null;
        btnDualForward = null;
        btnDualParent = null;
        btnDualNew = null;
        btnDualSwap = null;
        super.onDestroyView();
    }

    void onChildTouched(Fragment fragment) {
        PaneSlot slot = findSlot(fragment);
        if (slot != null) {
            activatePane(slot.side);
        }
    }

    void onChildPathChanged(Fragment fragment) {
        PaneSlot slot = findSlot(fragment);
        if (slot != null && slot.side == activePane) {
            updateActivityTitle();
        }
    }

    void swapPanesFromChild(Fragment fragment) {
        PaneSlot slot = findSlot(fragment);
        if (slot != null) {
            activePane = slot.side;
        }
        swapPanes();
    }

    void requestCrossCopy(Fragment fragment, cn.jdnjk.simpfun.model.FileItem item) {
        startCrossTransfer(fragment, item, false);
    }

    void requestCrossMove(Fragment fragment, cn.jdnjk.simpfun.model.FileItem item) {
        startCrossTransfer(fragment, item, true);
    }

    void requestProperties(Fragment fragment, cn.jdnjk.simpfun.model.FileItem item) {
        PaneSlot slot = findSlot(fragment);
        if (slot == null || getActivity() == null) {
            return;
        }
        if (fragment instanceof LocalFilePaneFragment localFilePaneFragment) {
            FilePropertiesDialog.showLocal(requireActivity(), localFilePaneFragment, item);
        } else if (fragment instanceof FilePaneFragment filePaneFragment) {
            int deviceId = getActivity() instanceof ServerManages activity ? activity.getDeviceId() : -1;
            FilePropertiesDialog.showServer(requireActivity(), filePaneFragment, deviceId, item);
        }
    }

    private void startCrossTransfer(Fragment sourceFragment, cn.jdnjk.simpfun.model.FileItem item, boolean move) {
        PaneSlot source = findSlot(sourceFragment);
        PaneSlot target = source == leftSlot ? rightSlot : leftSlot;
        if (source == null || target == null || transferCoordinator == null) {
            return;
        }
        if (source.fragment instanceof LocalFilePaneFragment localSource && target.fragment instanceof FilePaneFragment serverTarget) {
            transferCoordinator.copyLocalToServer(localSource, serverTarget, item, move);
            return;
        }
        if (source.fragment instanceof FilePaneFragment serverSource && target.fragment instanceof LocalFilePaneFragment localTarget) {
            transferCoordinator.copyServerToLocal(serverSource, localTarget, item, move);
            return;
        }
        android.widget.Toast.makeText(requireContext(), "另一页不是对应的本地/服务器面板", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void setupToolbarPaneMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem localItem = menu.add(Menu.NONE, R.id.action_file_local_pane, 0, "本地存储");
                localItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                MenuItem serverItem = menu.add(Menu.NONE, R.id.action_file_server_pane, 1, "服务器");
                serverItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                MenuItem fixItem = menu.add(Menu.NONE, R.id.action_file_toolbox_fix, 2, R.string.file_action_toolbox_fix);
                fixItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_file_local_pane) {
                    switchActivePaneTo(PaneKind.LOCAL);
                    return true;
                }
                if (itemId == R.id.action_file_server_pane) {
                    switchActivePaneTo(PaneKind.SERVER);
                    return true;
                }
                if (itemId == R.id.action_file_toolbox_fix) {
                    handleToolbarAction("fix");
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void switchActivePaneTo(PaneKind kind) {
        PaneSlot slot = getActiveSlot();
        if (slot == null || slot.kind == kind) {
            return;
        }
        Fragment fragment = kind == PaneKind.LOCAL ? LocalFilePaneFragment.newInstance(null) : FilePaneFragment.newEmbedded();
        slot.kind = kind;
        slot.fragment = fragment;
        getChildFragmentManager().beginTransaction().replace(slot.containerId, fragment).commitNow();
        updateActivePane();
        updateActivityTitle();
    }

    private void swapPanes() {
        if (leftSlot == null || rightSlot == null) {
            return;
        }
        PaneKind leftKind = leftSlot.kind;
        String leftPath = getSlotPath(leftSlot);
        PaneKind rightKind = rightSlot.kind;
        String rightPath = getSlotPath(rightSlot);
        replaceSlot(leftSlot, rightKind, rightPath);
        replaceSlot(rightSlot, leftKind, leftPath);
        updateActivePane();
        updateActivityTitle();
    }

    private void replaceSlot(PaneSlot slot, PaneKind kind, String initialPath) {
        slot.kind = kind;
        slot.fragment = kind == PaneKind.LOCAL ? LocalFilePaneFragment.newInstance(initialPath) : FilePaneFragment.newEmbedded(initialPath);
        getChildFragmentManager().beginTransaction().replace(slot.containerId, slot.fragment).commitNow();
    }

    private void activatePane(PaneSide pane) {
        activePane = pane;
        updateActivePane();
        Fragment fragment = getActiveFragment();
        if (fragment instanceof LocalFilePaneFragment localFilePaneFragment) {
            localFilePaneFragment.requestPaneFocus();
        } else if (fragment instanceof FilePaneFragment filePaneFragment) {
            filePaneFragment.requestPaneFocus();
        }
        updateActivityTitle();
    }

    private void updateActivePane() {
        if (leftSlot != null) {
            leftSlot.container.setForeground(createPaneBorder(activePane == PaneSide.LEFT));
        }
        if (rightSlot != null) {
            rightSlot.container.setForeground(createPaneBorder(activePane == PaneSide.RIGHT));
        }
    }

    private void updateActivityTitle() {
        if (!(getActivity() instanceof ServerManages activity)) {
            return;
        }
        PaneSlot slot = getActiveSlot();
        String path = slot == null ? "/" : getSlotPath(slot);
        activity.setFilePathTitle(path, v -> showActivePathEditor());
    }

    private void showActivePathEditor() {
        Fragment fragment = getActiveFragment();
        if (fragment instanceof LocalFilePaneFragment localFilePaneFragment) {
            localFilePaneFragment.showEditPathDialogForHost();
        } else if (fragment instanceof FilePaneFragment filePaneFragment) {
            filePaneFragment.showEditPathDialogForHost();
        }
    }

    private GradientDrawable createPaneBorder(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        int color = getResources().getColor(active ? R.color.md_theme_primary : R.color.md_theme_outlineVariant, requireContext().getTheme());
        drawable.setStroke(dp(active ? 2 : 1), color);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void handleBackPressed(OnBackPressedCallback callback) {
        if (handlePaneBack(getActiveSlot())) {
            return;
        }
        PaneSlot inactive = activePane == PaneSide.LEFT ? rightSlot : leftSlot;
        if (clearPaneTransientState(inactive)) {
            return;
        }
        callback.setEnabled(false);
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
        callback.setEnabled(true);
    }

    private boolean handlePaneBack(PaneSlot slot) {
        if (slot == null) {
            return false;
        }
        Fragment fragment = slot.fragment;
        if (fragment instanceof LocalFilePaneFragment localFilePaneFragment) {
            return localFilePaneFragment.clearSelectionForHost() || localFilePaneFragment.clearPendingMoveForHost() || localFilePaneFragment.navigateUpForHost();
        }
        if (fragment instanceof FilePaneFragment filePaneFragment) {
            return filePaneFragment.clearSelectionForHost() || filePaneFragment.clearPendingMoveForHost() || filePaneFragment.navigateUpForHost();
        }
        return false;
    }

    private boolean clearPaneTransientState(PaneSlot slot) {
        if (slot == null) {
            return false;
        }
        Fragment fragment = slot.fragment;
        if (fragment instanceof LocalFilePaneFragment localFilePaneFragment) {
            return localFilePaneFragment.clearSelectionForHost() || localFilePaneFragment.clearPendingMoveForHost();
        }
        if (fragment instanceof FilePaneFragment filePaneFragment) {
            return filePaneFragment.clearSelectionForHost() || filePaneFragment.clearPendingMoveForHost();
        }
        return false;
    }

    private PaneSlot findSlot(Fragment fragment) {
        if (leftSlot != null && leftSlot.fragment == fragment) {
            return leftSlot;
        }
        if (rightSlot != null && rightSlot.fragment == fragment) {
            return rightSlot;
        }
        return null;
    }

    private PaneSlot getActiveSlot() {
        return activePane == PaneSide.LEFT ? leftSlot : rightSlot;
    }

    private Fragment getActiveFragment() {
        PaneSlot slot = getActiveSlot();
        return slot == null ? null : slot.fragment;
    }

    private String getSlotPath(PaneSlot slot) {
        if (slot.fragment instanceof LocalFilePaneFragment localFilePaneFragment) {
            return localFilePaneFragment.getCurrentPathForHost();
        }
        if (slot.fragment instanceof FilePaneFragment filePaneFragment) {
            return filePaneFragment.getCurrentPathForHost();
        }
        return slot.kind == PaneKind.LOCAL ? "/sdcard" : "/";
    }

    private static class PaneSlot {
        final PaneSide side;
        final FrameLayout container;
        final int containerId;
        PaneKind kind;
        Fragment fragment;

        PaneSlot(PaneSide side, FrameLayout container, int containerId, PaneKind kind, Fragment fragment) {
            this.side = side;
            this.container = container;
            this.containerId = containerId;
            this.kind = kind;
            this.fragment = fragment;
        }
    }

    private enum PaneSide {
        LEFT,
        RIGHT
    }

    private enum PaneKind {
        LOCAL,
        SERVER
    }
}
