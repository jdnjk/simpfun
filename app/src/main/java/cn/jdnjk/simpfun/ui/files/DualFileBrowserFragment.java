package cn.jdnjk.simpfun.ui.files;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import cn.jdnjk.simpfun.R;

public class DualFileBrowserFragment extends Fragment {
    private static final String ARG_LEFT_PATH = "left_path";
    private static final String ARG_RIGHT_PATH = "right_path";

    public static DualFileBrowserFragment newInstance(String leftPath, String rightPath){
        DualFileBrowserFragment f = new DualFileBrowserFragment();
        Bundle b = new Bundle();
        b.putString(ARG_LEFT_PATH, leftPath);
        b.putString(ARG_RIGHT_PATH, rightPath);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dual_file_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) return;

        String left = "/";
        String right = "/";
        if (getArguments()!=null){
            String lArg = getArguments().getString(ARG_LEFT_PATH);
            String rArg = getArguments().getString(ARG_RIGHT_PATH);
            if (lArg!=null && !lArg.isEmpty()) left = lArg;
            if (rArg!=null && !rArg.isEmpty()) right = rArg;
        }

        Fragment leftPane = FilePaneFragment.newInstance(left);
        Fragment rightPane = FilePaneFragment.newInstance(right);
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.left_container, leftPane, "left_pane");
        ft.replace(R.id.right_container, rightPane, "right_pane");
        ft.commitNowAllowingStateLoss();
    }
}

