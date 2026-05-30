package cn.jdnjk.simpfun.ui.ins.files;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.setting.FilePaneModeManager;

public class FilePaneHostFragment extends Fragment {
    private FilePaneModeManager modeManager;

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
        showSelectedFilePane();
    }

    @Override
    public void onResume() {
        super.onResume();
        showSelectedFilePane();
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
}
