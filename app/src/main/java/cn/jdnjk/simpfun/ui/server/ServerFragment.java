package cn.jdnjk.simpfun.ui.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.ServerItem;

public class ServerFragment extends Fragment {

    private RecyclerView recyclerView;
    private ServerAdapter adapter;
    private List<ServerItem> serverItems;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_server, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_servers);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        serverItems = new ArrayList<>();

        String token = getToken();
        adapter = new ServerAdapter(serverItems, token, (MainActivity) requireActivity());
        recyclerView.setAdapter(adapter);

        // 设置下拉刷新监听器
        swipeRefreshLayout.setOnRefreshListener(this::refreshInstanceList);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
    }
    private void refreshInstanceList() {
        swipeRefreshLayout.setRefreshing(true);

        serverItems.clear();
        adapter.notifyDataSetChanged();

        updateInstanceList(null);
    }

    public void updateInstanceList(@Nullable JSONArray list) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null || adapter == null) {
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        serverItems.clear();

        JSONArray instanceList = list != null ? list : activity.getInstanceList();

        if (instanceList != null) {
            for (int i = 0; i < instanceList.length(); i++) {
                try {
                    JSONObject obj = instanceList.getJSONObject(i);
                    ServerItem item = new ServerItem(
                            obj.getInt("id"),
                            obj.optString("name", "实例" + obj.getInt("id")),
                            obj.getString("cpu"),
                            obj.getString("ram"),
                            obj.getString("disk")
                    );
                    serverItems.add(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }

        adapter.notifyDataSetChanged();
    }

    private String getToken() {
        SharedPreferences sp = requireContext().getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", null);
    }
}