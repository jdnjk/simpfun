package cn.jdnjk.simpfun.ui.ins.manage;

import android.graphics.Paint;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;



import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;

public class DiamondPlanAdapter extends RecyclerView.Adapter<DiamondPlanAdapter.ViewHolder> {

    private final List<JSONObject> plans = new ArrayList<>();
    private final OnPlanSelectListener listener;
    private int selectedPosition = -1;

    private String currentAreaGrade = "";

    public interface OnPlanSelectListener {
        void onPlanSelected(JSONObject plan);
    }

    public DiamondPlanAdapter(OnPlanSelectListener listener) {
        this.listener = listener;
    }

    public void setPlans(JSONArray array, String currentAreaGrade) {
        plans.clear();
        this.currentAreaGrade = currentAreaGrade;
        this.selectedPosition = -1;
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    plans.add(obj);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_diamond_plan, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject plan = plans.get(position);

        int originalPoint = plan.optInt("original_point", 0);
        int discountPoint = plan.optInt("point_discount", 0);
        int subsidizedPoint = originalPoint - discountPoint;

        holder.tvDiscountPoint.setText(subsidizedPoint + " 积分/天");
        holder.tvOriginalPoint.setText(originalPoint + "积分/天");
        holder.tvOriginalPoint.setPaintFlags(holder.tvOriginalPoint.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

        String grade = plan.optString("grade", "-");
        String vendor = plan.optString("vendor", "-");
        String spec = plan.optString("spec", "-");
        boolean isWindows = plan.optBoolean("is_windows", false);
        String fullPlanGrade = String.format("%s.%s.%s.%s", grade, vendor, spec, isWindows ? "W" : "L");
        holder.tvBadgeType.setText(fullPlanGrade);

        holder.tvBadgeDiamond.setText(plan.optInt("diamond", 0) + "钻石");

        int pCpu = plan.optInt("cpu", 0);
        int pRam = plan.optInt("ram", 0);
        int pDisk = plan.optInt("disk", 0);

        boolean needsChange = !fullPlanGrade.equals(currentAreaGrade);

        if (needsChange) {
            holder.tvBadgeChange.setVisibility(View.VISIBLE);
        } else {
            holder.tvBadgeChange.setVisibility(View.GONE);
        }

        holder.tvSpec1.setText(String.format(Locale.getDefault(), "CPU:%d核 内存:%dG", pCpu, pRam));
        holder.tvSpec2.setText(String.format(Locale.getDefault(), "硬盘:%dGB 流量:%dGB", pDisk, plan.optInt("traffic", 0)));

        boolean isSelected = (position == selectedPosition);
        TypedValue typedValue = new TypedValue();
        if (isSelected) {
            holder.layPlanRoot.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        } else {
            holder.layPlanRoot.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        }
        holder.layPlanRoot.setBackgroundColor(typedValue.data);

        // If it needs change, it cannot be clicked according to requirement
        if (needsChange) {
            holder.itemView.setAlpha(0.6f);
            holder.itemView.setOnClickListener(null);
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.itemView.setOnClickListener(v -> {
                int oldSelected = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (oldSelected != -1) {
                    notifyItemChanged(oldSelected);
                }
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onPlanSelected(plan);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return plans.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layPlanRoot;
        TextView tvDiscountPoint, tvOriginalPoint;
        TextView tvBadgeType, tvBadgeDiamond, tvBadgeChange;
        TextView tvSpec1, tvSpec2;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layPlanRoot = itemView.findViewById(R.id.lay_plan_root);
            tvDiscountPoint = itemView.findViewById(R.id.tv_discount_point);
            tvOriginalPoint = itemView.findViewById(R.id.tv_original_point);
            tvBadgeType = itemView.findViewById(R.id.tv_badge_type);
            tvBadgeDiamond = itemView.findViewById(R.id.tv_badge_diamond);
            tvBadgeChange = itemView.findViewById(R.id.tv_badge_change);
            tvSpec1 = itemView.findViewById(R.id.tv_spec_1);
            tvSpec2 = itemView.findViewById(R.id.tv_spec_2);
        }
    }
}
