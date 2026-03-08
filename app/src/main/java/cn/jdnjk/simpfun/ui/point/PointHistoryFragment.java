package cn.jdnjk.simpfun.ui.point;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.PointRecordAdapter;
import cn.jdnjk.simpfun.api.PointApi;
import cn.jdnjk.simpfun.model.PointRecord;

public class PointHistoryFragment extends Fragment {

    public static final String ARG_TYPE = "type";
    public static final String TYPE_POINTS = "points";
    public static final String TYPE_DIAMONDS = "diamonds";

    private String type;
    private PointRecordAdapter adapter;
    private final List<PointRecord> records = new ArrayList<>();
    private final PointApi pointApi = new PointApi();

    private FrameLayout rechargeContainer;
    private RecyclerView recyclerView;
    private View titleLayout;
    private boolean isRechargeLoaded = false;

    public boolean isRechargeVisible() {
        return rechargeContainer != null && rechargeContainer.getVisibility() == View.VISIBLE;
    }

    public static PointHistoryFragment newInstance(String type) {
        PointHistoryFragment fragment = new PointHistoryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_point_history, container, false);

        titleLayout = view.findViewById(R.id.tv_history_title).getParent() instanceof View ? (View) view.findViewById(R.id.tv_history_title).getParent() : null;
        TextView tvTitle = view.findViewById(R.id.tv_history_title);
        ImageView btnPlus = view.findViewById(R.id.btn_recharge_plus);
        recyclerView = view.findViewById(R.id.recycler_history);
        rechargeContainer = view.findViewById(R.id.recharge_container);

        if (TYPE_DIAMONDS.equals(type)) {
            tvTitle.setText("钻石历史记录");
            btnPlus.setVisibility(View.GONE);
        } else {
            tvTitle.setText("积分历史记录");
            btnPlus.setVisibility(View.VISIBLE);
            btnPlus.setOnClickListener(v -> toggleRecharge());
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PointRecordAdapter(records);
        recyclerView.setAdapter(adapter);

        loadHistoryData();

        return view;
    }

    public void toggleRecharge() {
        boolean show = rechargeContainer.getVisibility() != View.VISIBLE;
        if (!show) {
            Fragment f = getChildFragmentManager().findFragmentById(R.id.recharge_container);
            if (f != null) {
                getChildFragmentManager().beginTransaction()
                        .remove(f)
                        .commit();
            }
            rechargeContainer.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            if (titleLayout != null) titleLayout.setVisibility(View.VISIBLE);
        } else {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.recharge_container, new RechargeFragment())
                    .commit();
            isRechargeLoaded = true;
            rechargeContainer.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            if (titleLayout != null) titleLayout.setVisibility(View.GONE);
        }

        if (getActivity() instanceof PointManageActivity) {
            ((PointManageActivity) getActivity()).showRecharge(show);
        }
    }

    private void loadHistoryData() {
        if (getContext() == null) return;
        String token = getContext().getSharedPreferences("token", android.content.Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) return;

        PointApi.Callback callback = new PointApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                parseRecordResponse(response);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("PointHistoryFragment", "History Error: " + errorMsg);
            }
        };

        if (TYPE_DIAMONDS.equals(type)) {
            pointApi.getDiamondHistory(token, callback);
        } else {
            pointApi.getPointHistory(token, callback);
        }
    }

    private void parseRecordResponse(JSONObject response) {
        try {
            JSONArray list = response.optJSONArray("list");
            if (list == null) list = response.optJSONArray("data");
            if (list == null) list = response.optJSONArray("rows");
            if (list == null) return;

            records.clear();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;

                String desc = item.optString("comment", "");
                if (desc.trim().isEmpty()) {
                    desc = item.optString("description", item.optString("reason", "变动"));
                }

                String amount = "0";
                if (item.has("point")) {
                    amount = String.valueOf(item.optInt("point", 0));
                } else if (item.has("diamond")) {
                    amount = String.valueOf(item.optInt("diamond", 0));
                } else if (item.has("amount")) {
                    amount = item.optString("amount", "0");
                } else if (item.has("num")) {
                    amount = item.optString("num", "0");
                } else if (item.has("change")) {
                    amount = item.optString("change", "0");
                }

                String rawTime = item.optString("create_time", "");
                if (rawTime.trim().isEmpty()) {
                    rawTime = item.optString("created_at", item.optString("time", ""));
                }
                String time = PointRecord.formatTime(rawTime);

                int left = 0;
                if (item.has("point_left")) left = item.optInt("point_left", 0);
                else if (item.has("diamond_left")) left = item.optInt("diamond_left", 0);
                else if (item.has("left")) left = item.optInt("left", 0);
                else if (item.has("balance")) left = item.optInt("balance", 0);

                records.add(new PointRecord(desc, amount, time, left));
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(adapter::notifyDataSetChanged);
            }
        } catch (Exception e) {
            Log.e("PointHistoryFragment", "parseRecordResponse failed", e);
        }
    }
}
