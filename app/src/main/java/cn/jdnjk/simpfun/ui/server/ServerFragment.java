package cn.jdnjk.simpfun.ui.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import cn.jdnjk.simpfun.api.MainApi;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.ServerItem;
import cn.jdnjk.simpfun.ui.create.CreateServer;

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

        FloatingActionButton fab = root.findViewById(R.id.fab_add_server);
        if (fab == null) {
            Log.w("ServerFragment", "FAB未在布局中找到, 动态创建");
            if (root instanceof SwipeRefreshLayout) {
                View inner = ((SwipeRefreshLayout) root).getChildAt(0);
                if (inner instanceof FrameLayout fl) {
                    fab = new FloatingActionButton(requireContext());
                    fab.setId(R.id.fab_add_server);
                    fab.setImageResource(android.R.drawable.ic_input_add);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.END | Gravity.BOTTOM;
                    lp.setMargins(0,0,20,20);
                    fab.setLayoutParams(lp);
                    fl.addView(fab);
                }
            }
        }
        if (fab != null) {
            fab.setVisibility(View.VISIBLE);
            fab.bringToFront();
            fab.setOnClickListener(v -> {
                Log.d("ServerFragment", "FAB点击, 跳转创建");
                Intent intent = new Intent(requireContext(), CreateServer.class);
                startActivity(intent);
            });
            // 动态上移避免被底栏遮挡
            BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
            if (nav != null) {
                FloatingActionButton finalFab = fab;
                nav.post(() -> {
                    int h = nav.getHeight();
                    ViewGroup.LayoutParams lp0 = finalFab.getLayoutParams();
                    if (lp0 instanceof FrameLayout.LayoutParams lp) {
                        int base = dp(16);
                        lp.bottomMargin = h + base; // 底栏高度 + 16dp
                        lp.rightMargin = Math.max(lp.rightMargin, base);
                        finalFab.setLayoutParams(lp);
                    }
                    // 列表底部留出空间
                    if (recyclerView != null && recyclerView.getPaddingBottom() < h) {
                        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(), recyclerView.getPaddingRight(), h + dp(80));
                        recyclerView.setClipToPadding(false);
                    }
                });
            }
        } else {
            Log.e("ServerFragment", "仍未能创建FAB");
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCachedDataIfAvailable();
        // 创建新实例后回来刷新一次
        refreshInstanceList();
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

    private int dp(int v){ return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}