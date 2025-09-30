package cn.jdnjk.simpfun.ui.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import cn.jdnjk.simpfun.api.MainApi;
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
    private View emptyStateLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_server, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_servers);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        serverItems = new ArrayList<>();

        String token = getToken();
        adapter = new ServerAdapter(serverItems, token, (MainActivity) requireActivity());
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::refreshInstanceList);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCachedDataIfAvailable();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadCachedDataIfAvailable();
    }

    private void refreshInstanceList() {
        swipeRefreshLayout.setRefreshing(true);

        MainApi api = new MainApi(requireContext());
        api.getInstanceList(getToken(), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONArray list = data.optJSONArray("list");
                updateInstanceList(list);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("ServerFragment", "刷新失败: " + errorMsg);
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });
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
                    String name;
                    if (obj.isNull("name") || obj.optString("name").trim().isEmpty()) {
                        name = "未命名实例";
                    } else {
                        name = obj.getString("name");
                    }
                    ServerItem item = new ServerItem(
                            obj.getInt("id"),
                            name,
                            obj.getString("cpu"),
                            obj.getString("ram"),
                            obj.getString("disk")
                    );
                    serverItems.add(item);
                } catch (Exception e) {
                    Log.e("ServerFragment", "解析实例数据失败: " + e.getMessage());
                }
            }
        }

        // 根据列表是否为空来显示相应的状态
        if (serverItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
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

    /**
     * 尝试从MainActivity或缓存中加载数据
     */
    private void loadCachedDataIfAvailable() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            JSONArray cachedList = activity.getInstanceList();
            if (cachedList != null && cachedList.length() > 0) {
                updateInstanceList(cachedList);
                return;
            }
        }

        loadFromSharedPreferences();
    }

    /**
     * 从SharedPreferences加载缓存的服务器数据
     */
    private void loadFromSharedPreferences() {
        Context context = getContext();
        if (context != null) {
            SharedPreferences sp = context.getSharedPreferences("server_data", Context.MODE_PRIVATE);
            String cachedJson = sp.getString("instance_list", null);
            if (cachedJson != null) {
                try {
                    JSONArray cachedList = new JSONArray(cachedJson);
                    updateInstanceList(cachedList);
                } catch (Exception e) {
                    Log.e("ServerFragment", "解析缓存数据失败", e);
                    updateInstanceList(null);
                }
            } else {
                updateInstanceList(null);
            }
        }
    }
}