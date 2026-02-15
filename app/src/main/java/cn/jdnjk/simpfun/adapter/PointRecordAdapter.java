package cn.jdnjk.simpfun.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.PointRecord;

public class PointRecordAdapter extends RecyclerView.Adapter<PointRecordAdapter.ViewHolder> {
    private List<PointRecord> list;

    // Use runtime-generated ids to avoid requiring an R.id entry for programmatic views
    private static final int ID_AMOUNT = View.generateViewId();
    private static final int ID_LEFT = View.generateViewId();

    public PointRecordAdapter(List<PointRecord> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout layout = new LinearLayout(parent.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        layout.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(parent.getContext());
        tvTitle.setId(android.R.id.text1);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.md_theme_onSurface));
        layout.addView(tvTitle);

        TextView tvTime = new TextView(parent.getContext());
        tvTime.setId(android.R.id.text2);
        tvTime.setTextSize(12);
        tvTime.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.md_theme_onSurfaceVariant));
        layout.addView(tvTime);

        LinearLayout sub = new LinearLayout(parent.getContext());
        sub.setOrientation(LinearLayout.HORIZONTAL);

        ViewGroup.LayoutParams subParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sub.setLayoutParams(subParams);

        TextView tvLeft = new TextView(parent.getContext());
        tvLeft.setId(ID_LEFT);
        tvLeft.setTextSize(12);
        tvLeft.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.md_theme_onSurfaceVariant));
        tvLeft.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sub.addView(tvLeft);

        TextView tvAmount = new TextView(parent.getContext());
        tvAmount.setId(ID_AMOUNT);
        tvAmount.setTextSize(14);
        tvAmount.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.md_theme_primary));
        sub.addView(tvAmount);

        layout.addView(sub);

        return new ViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PointRecord record = list.get(position);

        // 1) description
        holder.tvDesc.setText(record.getDescription());

        // 2) time
        holder.tvTime.setText(record.getTime());

        // 3) amount with sign if numeric
        String rawAmount = record.getAmount() == null ? "" : record.getAmount().trim();
        try {
            double val = Double.parseDouble(rawAmount);
            holder.tvAmount.setText((val > 0 ? "+" : "") + rawAmount);
        } catch (NumberFormatException e) {
            holder.tvAmount.setText(rawAmount);
        }

        // 4) remaining
        if (record.getLeft() > 0) {
            holder.tvLeft.setVisibility(View.VISIBLE);
            holder.tvLeft.setText("剩余：" + record.getLeft());
        } else {
            holder.tvLeft.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDesc, tvTime, tvAmount, tvLeft;

        ViewHolder(View itemView) {
            super(itemView);
            tvDesc = itemView.findViewById(android.R.id.text1);
            tvTime = itemView.findViewById(android.R.id.text2);
            tvAmount = itemView.findViewById(ID_AMOUNT);
            tvLeft = itemView.findViewById(ID_LEFT);
        }
    }
}
