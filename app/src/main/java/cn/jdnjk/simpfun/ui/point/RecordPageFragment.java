package cn.jdnjk.simpfun.ui.point;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.RecordAdapter;
import cn.jdnjk.simpfun.api.PointApi;
import cn.jdnjk.simpfun.databinding.FragmentRecordPageBinding;

/**
 * 单一货币（积分/钻石）的流水列表页。
 */
public class RecordPageFragment extends Fragment {

    public static final String TYPE_POINTS = "points";
    public static final String TYPE_DIAMONDS = "diamonds";
    private static final String ARG_TYPE = "type";
    private static final String TAG = "RecordPageFragment";

    private String type;
    private FragmentRecordPageBinding binding;
    private RecordAdapter adapter;
    private final PointApi pointApi = new PointApi();

    public static RecordPageFragment newInstance(String type) {
        RecordPageFragment fragment = new RecordPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE, TYPE_POINTS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecordPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.recyclerRecords.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RecordAdapter();
        binding.recyclerRecords.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        load();
    }

    /** 充值成功后刷新当前流水。 */
    public void refresh() {
        if (binding != null && isAdded()) load();
    }

    private void load() {
        Context context = getContext();
        if (context == null) return;
        String token = context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) {
            binding.swipeRefresh.setRefreshing(false);
            showState(false, true);
            return;
        }

        PointApi.Callback callback = new PointApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isViewAlive()) return;
                binding.swipeRefresh.setRefreshing(false);
                render(response);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "load " + type + " history failed: " + errorMsg);
                if (!isViewAlive()) return;
                binding.swipeRefresh.setRefreshing(false);
                if (adapter.getItemCount() == 0) showState(false, true);
            }
        };

        if (TYPE_DIAMONDS.equals(type)) {
            pointApi.getDiamondHistory(token, callback);
        } else {
            pointApi.getPointHistory(token, callback);
        }
    }

    private void render(JSONObject response) {
        List<RecordAdapter.Row> rows = new ArrayList<>();
        JSONArray list = response.optJSONArray("list");
        if (list == null) list = response.optJSONArray("data");
        if (list == null) list = response.optJSONArray("rows");

        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;

                String reason = RecordAdapter.cleanComment(item.optString("comment", ""));
                if (reason.isEmpty()) {
                    reason = RecordAdapter.cleanComment(item.optString("description",
                            item.optString("reason", "变动")));
                }

                int delta = 0;
                if (item.has("point")) {
                    delta = item.optInt("point", 0);
                } else if (item.has("diamond")) {
                    delta = item.optInt("diamond", 0);
                } else if (item.has("amount")) {
                    delta = parseSafe(item.optString("amount", "0"));
                } else if (item.has("num")) {
                    delta = parseSafe(item.optString("num", "0"));
                } else if (item.has("change")) {
                    delta = parseSafe(item.optString("change", "0"));
                }

                int left = 0;
                if (item.has("point_left")) {
                    left = item.optInt("point_left", 0);
                } else if (item.has("diamond_left")) {
                    left = item.optInt("diamond_left", 0);
                } else if (item.has("left")) {
                    left = item.optInt("left", 0);
                } else if (item.has("balance")) {
                    left = item.optInt("balance", 0);
                }

                long epoch = parseTimeToMillis(item.optString("create_time",
                        item.optString("created_at", item.optString("time", ""))));
                String time = epoch > 0 ? RecordAdapter.formatShortTime(epoch / 1000L) : "";

                rows.add(new RecordAdapter.Row(reason, delta, left, time));
            }
        }

        adapter.submit(rows);
        showState(rows.isEmpty(), false);
    }

    private void showState(boolean empty, boolean error) {
        binding.tvEmpty.setVisibility(empty && !error ? View.VISIBLE : View.GONE);
        binding.tvError.setVisibility(error ? View.VISIBLE : View.GONE);
        binding.recyclerRecords.setVisibility(error || empty ? View.GONE : View.VISIBLE);
    }

    private static int parseSafe(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** ISO-8601 / "yyyy-MM-dd HH:mm:ss" → epoch millis；解析失败返回 0。 */
    private static long parseTimeToMillis(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        String t = raw.trim();
        try {
            return Instant.parse(t).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            return Instant.from(fmt.parse(t.replace('T', ' '))).toEpochMilli();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private boolean isViewAlive() {
        return binding != null && isAdded();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
