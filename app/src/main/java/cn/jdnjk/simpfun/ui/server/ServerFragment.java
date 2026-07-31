package cn.jdnjk.simpfun.ui.server;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.model.ServerItem;
import cn.jdnjk.simpfun.model.ServerStatsSnapshot;
import cn.jdnjk.simpfun.service.ServerStatsListener;
import cn.jdnjk.simpfun.service.ServerStatsService;
import cn.jdnjk.simpfun.ui.create.CreateServer;
import cn.jdnjk.simpfun.utils.PageDataStore;
import cn.jdnjk.simpfun.ui.setting.ServerCardStyleManager;

public class ServerFragment extends Fragment implements ServerStatsListener {
    private static final String ARG_FORCE_REFRESH = "arg_force_refresh";

    public static ServerFragment newInstance(boolean forceRefresh) {
        ServerFragment fragment = new ServerFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_FORCE_REFRESH, forceRefresh);
        fragment.setArguments(args);
        return fragment;
    }

    private RecyclerView recyclerView;
    private ServerAdapter adapter;
    private List<ServerItem> serverItems;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyStateLayout;
    private ServerCardStyleManager cardStyleManager;
    private final ServerStatsService statsService = ServerStatsService.getInstance();
    private final Set<Integer> subscribedIds = new HashSet<>();

    private final ActivityResultLauncher<Intent> createServerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) return;
                Intent data = result.getData();
                if (data != null && data.getBooleanExtra(CreateServer.EXTRA_RESULT_CREATED, false)) {
                    loadInstanceList(true);
                }
            });

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

        swipeRefreshLayout.setOnRefreshListener(() -> loadInstanceList(true));

        MaterialToolbar toolbar = root.findViewById(R.id.toolbar_server);
        toolbar.setOnMenuItemClickListener(this::handleToolbarMenuItem);
        adjustRecyclerBottomPadding();

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
        if (adapter != null && cardStyleManager != null) {
            adapter.setUseModernStyle(cardStyleManager.isModernServerCardEnabled());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean forceRefresh = getArguments() != null && getArguments().getBoolean(ARG_FORCE_REFRESH, false);
        loadInstanceList(forceRefresh);
    }

    private boolean handleToolbarMenuItem(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_server) {
            openCreateServer();
            return true;
        }
        return false;
    }

    private void openCreateServer() {
        Log.d("ServerFragment", "创建服务器入口点击");
        Intent intent = new Intent(requireContext(), CreateServer.class);
        createServerLauncher.launch(intent);
    }

    private void adjustRecyclerBottomPadding() {
        BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
        if (nav == null) return;
        nav.post(() -> {
            if (!isAdded() || recyclerView == null) return;
            int bottomPadding = nav.getHeight() + dpSafe(16);
            recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(), recyclerView.getPaddingRight(), bottomPadding);
            recyclerView.setClipToPadding(false);
        });
    }

    private void loadInstanceList(boolean forceRefresh) {
        String token = getToken();
        if (token == null || token.trim().isEmpty()) {
            stopRefreshing();
            return;
        }

        if (!forceRefresh) {
            PageDataStore.ServerData cachedData = PageDataStore.getInstance().getServerData(token);
            if (cachedData != null) {
                renderInstanceList(cachedData.getInstanceList(), isDeveloperUser() ? cachedData.getSupportList() : null);
                stopRefreshing();
                return;
            }
        }

        swipeRefreshLayout.setRefreshing(true);
        UserApi api = new UserApi(requireContext());
        api.getInstanceList(token, new UserApi.InstanceCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONArray rawList = data.optJSONArray("list");
                final JSONArray instanceList = rawList == null ? new JSONArray() : rawList;
                if (isDeveloperUser()) {
                    fetchSupportInstanceList(api, token, instanceList);
                } else {
                    PageDataStore.getInstance().putServerData(token, instanceList, null);
                    renderInstanceList(instanceList, null);
                    stopRefreshing();
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("ServerFragment", "刷新实例列表失败: " + errorMsg);
                stopRefreshing();
            }
        });
    }

    private void fetchSupportInstanceList(UserApi api, String token, @NonNull JSONArray instanceList) {
        api.getSupportInstanceList(token, new UserApi.InstanceCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONArray rawSupportList = data.optJSONArray("list");
                final JSONArray supportList = rawSupportList == null ? new JSONArray() : rawSupportList;
                PageDataStore.getInstance().putServerData(token, instanceList, supportList);
                renderInstanceList(instanceList, supportList);
                stopRefreshing();
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("ServerFragment", "技术支持实例刷新失败: " + errorMsg);
                PageDataStore.getInstance().putServerData(token, instanceList, null);
                renderInstanceList(instanceList, null);
                stopRefreshing();
            }
        });
    }

    public void updateInstanceList(@Nullable JSONArray list) {
        if (list != null) {
            renderInstanceList(list, null);
            return;
        }

        String token = getToken();
        if (token == null || token.trim().isEmpty()) {
            renderInstanceList(null, null);
            return;
        }

        PageDataStore.ServerData cachedData = PageDataStore.getInstance().getServerData(token);
        if (cachedData != null) {
            renderInstanceList(cachedData.getInstanceList(), cachedData.getSupportList());
            return;
        }

        renderInstanceList(null, null);
    }

    private void renderInstanceList(@Nullable JSONArray list, @Nullable JSONArray supportList) {
        if (!isAdded() || adapter == null || recyclerView == null || emptyStateLayout == null) {
            stopRefreshing();
            return;
        }

        List<ServerItem> updatedItems = new ArrayList<>();
        Set<Integer> updatedIds = new HashSet<>();

        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject obj = list.getJSONObject(i);
                    int id = obj.getInt("id");
                    String name = obj.isNull("name") || obj.optString("name").trim().isEmpty()
                            ? "未命名实例" : obj.getString("name");
                    ServerItem item = new ServerItem(
                            id,
                            name,
                            obj.optString("cpu", "0"),
                            obj.optString("ram", "0"),
                            obj.optString("disk", "0")
                    );
                    item.setStats(findExistingStats(id));
                    updatedItems.add(item);
                    updatedIds.add(id);
                } catch (Exception e) {
                    Log.e("ServerFragment", "解析实例数据失败: " + e.getMessage());
                }
            }
        }

        appendSupportInstances(supportList, updatedItems, updatedIds);

        unsubscribeRemovedStats(updatedIds);
        serverItems.clear();
        serverItems.addAll(updatedItems);

        if (serverItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            resubscribeStats();
        }

        adapter.notifyDataSetChanged();
        stopRefreshing();
    }

    private void appendSupportInstances(@Nullable JSONArray supportList, List<ServerItem> updatedItems, Set<Integer> updatedIds) {
        if (supportList == null) return;
        for (int i = 0; i < supportList.length(); i++) {
            JSONObject obj = supportList.optJSONObject(i);
            if (obj == null) continue;
            int id = parseSupportInstanceId(obj.optString("ins_id", ""));
            if (id <= 0 || updatedIds.contains(id)) continue;

            String targetUid = obj.optString("target_uid", "未知");
            if (targetUid.trim().isEmpty()) targetUid = "未知";
            String targetQq = obj.isNull("target_qq") ? "未绑定" : obj.optString("target_qq", "未绑定");
            if (targetQq.trim().isEmpty()) targetQq = "未绑定";
            String comment = obj.optString("comment", "").trim();
            String createTime = obj.optString("create_time", "").trim();

            StringBuilder info = new StringBuilder()
                    .append("目标UID: ")
                    .append(targetUid)
                    .append(" · QQ: ")
                    .append(targetQq);
            if (!comment.isEmpty()) {
                info.append(" · 备注: ").append(comment);
            }
            if (!createTime.isEmpty()) {
                info.append(" · 时间: ").append(createTime);
            }

            updatedItems.add(ServerItem.supportInstance(id, "技术支持实例 #" + id, info.toString()));
            updatedIds.add(id);
        }
    }

    private int parseSupportInstanceId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return -1;
        }
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
            if (item.isSupportInstance()) continue;
            if (subscribedIds.add(item.getId())) {
                statsService.subscribe(requireContext(), item.getId());
            }
        }
    }

    private void unsubscribeRemovedStats(Set<Integer> activeIds) {
        List<Integer> removedIds = new ArrayList<>();
        for (Integer deviceId : subscribedIds) {
            if (!activeIds.contains(deviceId)) {
                statsService.unsubscribe(deviceId);
                removedIds.add(deviceId);
            }
        }
        subscribedIds.removeAll(removedIds);
    }

    @Nullable
    private ServerStatsSnapshot findExistingStats(int deviceId) {
        for (ServerItem item : serverItems) {
            if (item.getId() == deviceId) {
                return item.getStats();
            }
        }
        return null;
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

    private boolean isDeveloperUser() {
        Context context = getContext();
        return context != null && context.getSharedPreferences("user_info", Context.MODE_PRIVATE).getBoolean("dev", false);
    }

    private int dpSafe(int v) {
        Resources res = isAdded() ? getResources() : Resources.getSystem();
        return (int) (v * res.getDisplayMetrics().density + 0.5f);
    }

    private void stopRefreshing() {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}
