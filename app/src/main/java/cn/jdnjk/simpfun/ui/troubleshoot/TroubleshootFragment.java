package cn.jdnjk.simpfun.ui.troubleshoot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import cn.jdnjk.simpfun.R;

public class TroubleshootFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_troubleshoot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.option_firewall).setOnClickListener(v -> {
            if (getActivity() instanceof TroubleshootActivity activity) {
                activity.openFirewallPage();
            }
        });
        view.findViewById(R.id.option_status_monitor).setOnClickListener(v -> {
            if (getActivity() instanceof TroubleshootActivity activity) {
                activity.openStatusMonitorPage();
            }
        });
    }
}
