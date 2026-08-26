package cn.jdnjk.simpfun.ui.ins.files;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import org.json.JSONObject;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.PropertiesApi;
import cn.jdnjk.simpfun.model.ConfigFileDef;
import cn.jdnjk.simpfun.ui.setting.FilePaneModeManager;

public class FilePaneHostFragment extends Fragment implements VisualConfigFragment.Host {
    private static final String STATE_CONFIG_MODE = "config_mode";

    private FilePaneModeManager modeManager;
    private boolean configMode;
    private FrameLayout configContainer;
    /** 服务器是否下发了可用的可视化配置定义（为空时不显示入口按钮） */
    private boolean hasConfigDefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        modeManager = new FilePaneModeManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_pane_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        configContainer = view.findViewById(R.id.visual_config_container);
        showSelectedFilePane();
        setupToolbarConfigMenu();
        checkConfigDefinitions();

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_CONFIG_MODE, false)) {
            configMode = true;
            if (configContainer != null) {
                configContainer.setVisibility(View.VISIBLE);
            }
            requireActivity().invalidateOptionsMenu();
        }
    }

    /** 请求服务器配置定义：非空才显示入口按钮；空/失败则隐藏。 */
    private void checkConfigDefinitions() {
        int deviceId = getActivity() instanceof ServerManages a ? a.getDeviceId() : -1;
        if (deviceId <= 0) return;
        new PropertiesApi().getConfigDefinitions(requireContext(), deviceId, new PropertiesApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                hasConfigDefs = !ConfigFileDef.parse(data.optJSONArray("list")).isEmpty();
                requireActivity().invalidateOptionsMenu();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                hasConfigDefs = false;
                requireActivity().invalidateOptionsMenu();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        showSelectedFilePane();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_CONFIG_MODE, configMode);
    }

    private void showSelectedFilePane() {
        if (getView() == null || modeManager == null) {
            return;
        }
        boolean dualMode = modeManager.isDualFilePaneEnabled();
        Fragment current = getChildFragmentManager().findFragmentById(R.id.file_pane_host_container);
        if ((dualMode && current instanceof DualFilePaneFragment) || (!dualMode && current instanceof FilePaneFragment)) {
            return;
        }
        Fragment fragment = dualMode ? new DualFilePaneFragment() : new FilePaneFragment();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.file_pane_host_container, fragment)
                .commit();
    }

    // ---------- 顶栏"可视化配置"按钮 ----------

    private void setupToolbarConfigMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                MenuItem item = menu.add(Menu.NONE, R.id.action_visual_config, 0, R.string.visual_config);
                item.setIcon(R.drawable.ic_tune);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuItem item = menu.findItem(R.id.action_visual_config);
                if (item != null) {
                    // 服务器未下发配置定义时隐藏入口按钮
                    item.setVisible(hasConfigDefs);
                    item.setIcon(configMode ? R.drawable.ic_arrow_back : R.drawable.ic_tune);
                    item.setTitle(configMode ? R.string.visual_config_back : R.string.visual_config);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_visual_config) {
                    toggleConfigMode();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void toggleConfigMode() {
        if (configMode) {
            exitConfigMode();
        } else {
            enterConfigMode();
        }
    }

    private void enterConfigMode() {
        if (configMode) return;
        configMode = true;
        int deviceId = getActivity() instanceof ServerManages a ? a.getDeviceId() : -1;
        VisualConfigFragment f = VisualConfigFragment.newInstance(deviceId);
        getChildFragmentManager()
                .beginTransaction()
                .add(R.id.visual_config_container, f)
                .commit();
        if (configContainer != null) {
            configContainer.setVisibility(View.VISIBLE);
        }
        requireActivity().invalidateOptionsMenu();
    }

    @Override
    public void exitConfigMode() {
        if (!configMode) return;
        VisualConfigFragment cf = getConfigFragment();
        if (cf != null && cf.isDirty()) {
            cf.showUnsavedDialog(this::doExitConfigMode);
            return;
        }
        doExitConfigMode();
    }

    @Override
    public void onConfigSaved() {
        // 保存成功后无需立即处理；退出时会统一刷新文件列表。
    }

    private void doExitConfigMode() {
        configMode = false;
        VisualConfigFragment cf = getConfigFragment();
        if (cf != null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .remove(cf)
                    .commit();
        }
        if (configContainer != null) {
            configContainer.setVisibility(View.GONE);
        }
        refreshFileList();
        requireActivity().invalidateOptionsMenu();
    }

    private VisualConfigFragment getConfigFragment() {
        return (VisualConfigFragment) getChildFragmentManager().findFragmentById(R.id.visual_config_container);
    }

    private void refreshFileList() {
        Fragment f = getChildFragmentManager().findFragmentById(R.id.file_pane_host_container);
        if (f instanceof FilePaneFragment p) {
            p.reloadFileList();
        } else if (f instanceof DualFilePaneFragment d) {
            d.reloadServerPaneList();
        }
    }
}
