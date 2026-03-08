package cn.jdnjk.simpfun.ui.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.MainApi;
import cn.jdnjk.simpfun.model.ServerItem;
import cn.jdnjk.simpfun.model.ServerStatsSnapshot;
import cn.jdnjk.simpfun.service.ServerStatsListener;
import cn.jdnjk.simpfun.service.ServerStatsService;
import cn.jdnjk.simpfun.ui.create.CreateServer;
import cn.jdnjk.simpfun.ui.setting.ServerCardStyleManager;

public class ServerFragment extends Fragment implements ServerStatsListener {

    private RecyclerView recyclerView;
    private ServerAdapter adapter;
    private List<ServerItem> serverItems;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyStateLayout;
    private ServerCardStyleManager cardStyleManager;
    private final ServerStatsService statsService = ServerStatsService.getInstance();
    private final Set<Integer> subscribedIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_server, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        recyclerView = root.findViewById(R.id.recycler_view_servers);
        emptyStateLayout = root.findViewById(R.id.empty_state_layout);
        cardStyleManager = new ServerCardStyleManager(requireContext());

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (getActivity() instanceof cn.jdnjk.simpfun.MainActivity mainActivity) {
                    boolean atTop = !recyclerView.canScrollVertically(-1);
                    mainActivity.onPrimaryScroll(dy, atTop);
                }
            }
        });

        serverItems = new ArrayList<>();
        adapter = new ServerAdapter(serverItems, (MainActivity) requireActivity(), cardStyleManager.isModernServerCardEnabled());
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::refreshInstanceList);

        FloatingActionButton fab = root.findViewById(R.id.fab_add_server);
        if (fab == null) {
            Log.w("ServerFragment", "FAB未在布局中找到, 动态创建");
            if (root instanceof SwipeRefreshLayout) {
                View inner = ((SwipeRefreshLayout) root).getChildAt(0);
                if (inner instanceof FrameLayout fl) {
                    Context ctx = getContext();
                    if (ctx != null) {
                        fab = new FloatingActionButton(ctx);
                        fab.setId(R.id.fab_add_server);
                        fab.setImageResource(android.R.drawable.ic_input_add);
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.gravity = Gravity.END | Gravity.BOTTOM;
                        lp.setMargins(0,0,20,20);
                        fab.setLayoutParams(lp);
                        fl.addView(fab);
                    } else {
                        Log.w("ServerFragment", "上下文为空，暂不创建FAB");
                    }
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
            BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
            if (nav != null) {
                FloatingActionButton finalFab = fab;
                nav.post(() -> {
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }
                    int h = nav.getHeight();
                    ViewGroup.LayoutParams lp0 = finalFab.getLayoutParams();
                    if (lp0 instanceof FrameLayout.LayoutParams lp) {
                        int base = dpSafe(16);
                        lp.bottomMargin = h + base;
                        lp.rightMargin = Math.max(lp.rightMargin, base);
                        finalFab.setLayoutParams(lp);
                    }
                    if (recyclerView != null && recyclerView.getPaddingBottom() < h) {
                        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(), recyclerView.getPaddingRight(), h + dpSafe(80));
                        recyclerView.setClipToPadding(false);
                    }
                });
            }
        }

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        statsService.addListener(this);
        resubscribeStats();
    }

    @Override
    public void onStop() {
        super.onStop();
        statsService.removeListener(this);
        unsubscribeStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCachedDataIfAvailable();
        if (adapter != null && cardStyleManager != null) {
            adapter.setUseModernStyle(cardStyleManager.isModernServerCardEnabled());
        }
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

        unsubscribeStats();
        serverItems.clear();
        JSONArray instanceList = list != null ? list : activity.getInstanceList();

        if (instanceList != null) {
            for (int i = 0; i < instanceList.length(); i++) {
                try {
                    JSONObject obj = instanceList.getJSONObject(i);
                    String name = obj.isNull("name") || obj.optString("name").trim().isEmpty()
                            ? "未命名实例" : obj.getString("name");
                    ServerItem item = new ServerItem(
                            obj.getInt("id"),
                            name,
                            obj.optString("cpu", "0"),
                            obj.optString("ram", "0"),
                            obj.optString("disk", "0")
                    );
                    serverItems.add(item);
                } catch (Exception e) {
                    Log.e("ServerFragment", "解析实例数据失败: " + e.getMessage());
                }
            }
        }

        if (serverItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            resubscribeStats();
        }

        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onStatsUpdated(int deviceId, ServerStatsSnapshot stats) {
        for (int i = 0; i < serverItems.size(); i++) {
            ServerItem item = serverItems.get(i);
            if (item.getId() == deviceId) {
                item.setStats(stats);
                if (adapter != null) {
                    adapter.notifyItemChanged(i);
                }
                break;
            }
        }
    }

    @Override
    public void onStatsDisconnected(int deviceId, String reason) {
        Log.d("ServerFragment", "stats disconnected for " + deviceId + ": " + reason);
    }

    private void resubscribeStats() {
        if (!isAdded()) return;
        for (ServerItem item : serverItems) {
            if (subscribedIds.add(item.getId())) {
                statsService.subscribe(requireContext(), item.getId());
            }
        }
    }

    private void unsubscribeStats() {
        for (Integer deviceId : subscribedIds) {
            statsService.unsubscribe(deviceId);
        }
        subscribedIds.clear();
    }

    private String getToken() {
        SharedPreferences sp = requireContext().getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", null);
    }

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

    private int dpSafe(int v) {
        Resources res = isAdded() ? getResources() : Resources.getSystem();
        return (int) (v * res.getDisplayMetrics().density + 0.5f);
    }
}
