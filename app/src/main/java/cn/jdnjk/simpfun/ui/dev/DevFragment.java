package cn.jdnjk.simpfun.ui.dev;

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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

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
import java.util.List;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.dev.DevApi;
import cn.jdnjk.simpfun.model.DevMirrorItem;

public class DevFragment extends Fragment {

    private DevApi devApi;
    private DevMirrorAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View emptyLayout;
    private View forbiddenLayout;
    private MaterialToolbar toolbar;
    private View searchBar;
    private EditText etSearch;

    private boolean searchMode = false;
    private boolean forbidden = false;
    private boolean firstResume = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dev, container, false);

        devApi = new DevApi();
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_dev);
        recyclerView = root.findViewById(R.id.recycler_dev_mirrors);
        emptyLayout = root.findViewById(R.id.empty_dev_layout);
        forbiddenLayout = root.findViewById(R.id.forbidden_dev_layout);
        toolbar = root.findViewById(R.id.toolbar_dev);
        searchBar = root.findViewById(R.id.search_bar_dev);
        etSearch = root.findViewById(R.id.et_search_mirror);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (getActivity() instanceof MainActivity mainActivity) {
                    boolean atTop = !recyclerView.canScrollVertically(-1);
                    mainActivity.onPrimaryScroll(dy, atTop);
                }
            }
        });

        adapter = new DevMirrorAdapter(this::openMirrorDetail);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadMirrorList(true));

        toolbar.setOnMenuItemClickListener(this::handleToolbarMenuItem);

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                adapter.filter(s.toString());
                updateListVisibility();
            }
        });
        root.findViewById(R.id.iv_search_close).setOnClickListener(v -> exitSearchMode());
        root.findViewById(R.id.iv_search_clear).setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.requestFocus();
        });

        adjustRecyclerBottomPadding();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadMirrorList(false);
    }

    private boolean handleToolbarMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_mirror) {
            startActivity(new Intent(requireContext(), DevCreateMirrorActivity.class));
            return true;
        }
        if (id == R.id.action_search_mirror) {
            enterSearchMode();
            return true;
        }
        return false;
    }

    private void enterSearchMode() {
        searchMode = true;
        toolbar.setTitle(null);
        toolbar.getMenu().findItem(R.id.action_add_mirror).setVisible(false);
        toolbar.getMenu().findItem(R.id.action_search_mirror).setVisible(false);
        searchBar.setVisibility(View.VISIBLE);
        etSearch.requestFocus();
        showKeyboard(true);
    }

    private void exitSearchMode() {
        searchMode = false;
        toolbar.setTitle("开发者");
        toolbar.getMenu().findItem(R.id.action_add_mirror).setVisible(true);
        toolbar.getMenu().findItem(R.id.action_search_mirror).setVisible(true);
        searchBar.setVisibility(View.GONE);
        etSearch.setText("");
        showKeyboard(false);
        updateListVisibility();
    }

    private void showKeyboard(boolean show) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (show) {
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (!forbidden) {
            loadMirrorList(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (searchMode) {
            exitSearchMode();
        }
    }

    private void loadMirrorList(boolean showSwipe) {
        String token = getToken();
        if (token == null || token.trim().isEmpty()) {
            stopRefreshing();
            return;
        }
        if (showSwipe) {
            swipeRefreshLayout.setRefreshing(true);
        }

        devApi.getMirrorList(token, new DevApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                forbidden = false;
                JSONArray rawList = response.optJSONArray("list");
                List<DevMirrorItem> items = new ArrayList<>();
                if (rawList != null) {
                    for (int i = 0; i < rawList.length(); i++) {
                        JSONObject obj = rawList.optJSONObject(i);
                        DevMirrorItem item = DevMirrorItem.fromJson(obj);
                        if (item != null && item.getId() > 0) items.add(item);
                    }
                }
                adapter.submitItems(items);
                updateListVisibility();
                stopRefreshing();
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("DevFragment", "获取镜像列表失败: " + errorMsg);
                updateListVisibility();
                stopRefreshing();
            }

            @Override
            public void onForbidden() {
                forbidden = true;
                adapter.submitItems(new ArrayList<>());
                updateListVisibility();
                stopRefreshing();
            }
        });
    }

    private void updateListVisibility() {
        if (!isAdded() || emptyLayout == null || forbiddenLayout == null || recyclerView == null) return;
        if (forbidden) {
            forbiddenLayout.setVisibility(View.VISIBLE);
            emptyLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
        } else {
            forbiddenLayout.setVisibility(View.GONE);
            boolean empty = adapter.getItemCount() == 0;
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            emptyLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    private void openMirrorDetail(DevMirrorItem item) {
        Intent intent = new Intent(requireContext(), DevMirrorDetailActivity.class);
        intent.putExtra(DevMirrorDetailActivity.EXTRA_MIRROR_ID, item.getId());
        intent.putExtra(DevMirrorDetailActivity.EXTRA_MIRROR_NAME, item.getName());
        startActivity(intent);
    }

    private void adjustRecyclerBottomPadding() {
        BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
        if (nav == null) return;
        nav.post(() -> {
            if (!isAdded() || recyclerView == null) return;
            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    nav.getHeight() + dpSafe());
            recyclerView.setClipToPadding(false);
        });
    }

    private int dpSafe() {
        Resources res = isAdded() ? getResources() : Resources.getSystem();
        return (int) (16 * res.getDisplayMetrics().density + 0.5f);
    }

    private String getToken() {
        SharedPreferences sp = requireContext().getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", null);
    }

    private void stopRefreshing() {
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}
