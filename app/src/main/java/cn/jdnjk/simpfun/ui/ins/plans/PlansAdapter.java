package cn.jdnjk.simpfun.ui.ins.plans;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.PlanItem;

public class PlansAdapter extends RecyclerView.Adapter<PlansAdapter.PlanViewHolder> {

    private final List<PlanItem> plans = new ArrayList<>();
    private final Set<Integer> selectedIds = new HashSet<>();
    private final OnPlanActionListener listener;

    private final SimpleDateFormat utcFormat;
    private final SimpleDateFormat localFormat;

    public interface OnPlanActionListener {
        void onDeleteClick(PlanItem item);
        void onSelectionChanged(int selectedCount);
    }

    public PlansAdapter(OnPlanActionListener listener) {
        this.listener = listener;

        utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        localFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        localFormat.setTimeZone(TimeZone.getDefault());
    }

    public void setData(List<PlanItem> list) {
        plans.clear();
        if (list != null) plans.addAll(list);

        Set<Integer> validIds = new HashSet<>();
        for (PlanItem plan : plans) validIds.add(plan.getId());
        selectedIds.retainAll(validIds);

        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void deleteItem(int id) {
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).getId() == id) {
                plans.remove(i);
                selectedIds.remove(id);
                notifyItemRemoved(i);
                notifySelectionChanged();
                break;
            }
        }
    }

    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public void clearSelection() {
        if (selectedIds.isEmpty()) return;
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        holder.bind(plans.get(position));
    }

    @Override
    public int getItemCount() {
        return plans.size();
    }

    private void notifySelectionChanged() {
        if (listener != null) listener.onSelectionChanged(selectedIds.size());
    }

    class PlanViewHolder extends RecyclerView.ViewHolder {
        final CheckBox cbxPlan;
        final Chip chipCommand;
        final TextView tvTime;
        final TextView tvRepeated;
        final TextView tvNextTime;
        final TextView tvId;
        final LinearLayout repeatRow;
        final ImageButton btnDelete;

        PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            cbxPlan = itemView.findViewById(R.id.cbx_plan);
            chipCommand = itemView.findViewById(R.id.chip_command);
            tvTime = itemView.findViewById(R.id.tv_scheduled_time);
            tvRepeated = itemView.findViewById(R.id.tv_repeated);
            tvNextTime = itemView.findViewById(R.id.tv_next_time);
            tvId = itemView.findViewById(R.id.tv_plan_id);
            repeatRow = itemView.findViewById(R.id.layout_repeat_row);
            btnDelete = itemView.findViewById(R.id.btn_delete);

            btnDelete.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDeleteClick(plans.get(pos));
                }
            });
        }

        void bind(PlanItem item) {
            bindCommand(item.getCommand());

            long baseMillis = 0L;
            try {
                Date date = utcFormat.parse(item.getScheduledTime());
                if (date != null) {
                    baseMillis = date.getTime();
                    tvTime.setText(localFormat.format(date));
                } else {
                    tvTime.setText(item.getScheduledTime());
                }
            } catch (ParseException e) {
                tvTime.setText(item.getScheduledTime());
            }

            if (item.isRepeated() && item.getInterval() > 0) {
                repeatRow.setVisibility(View.VISIBLE);
                tvRepeated.setText(formatInterval(item.getInterval()));

                if (baseMillis > 0) {
                    Date nextDate = new Date(baseMillis + (long) item.getInterval() * 1000L);
                    tvNextTime.setText("下次: " + localFormat.format(nextDate));
                } else {
                    tvNextTime.setText("下次: --");
                }
            } else {
                repeatRow.setVisibility(View.GONE);
            }

            tvId.setText("#" + item.getId());

            cbxPlan.setOnCheckedChangeListener(null);
            cbxPlan.setChecked(selectedIds.contains(item.getId()));
            cbxPlan.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedIds.add(item.getId());
                else selectedIds.remove(item.getId());
                notifySelectionChanged();
            });
        }

        private void bindCommand(String cmd) {
            if ("<POWER_ON>".equals(cmd)) {
                chipCommand.setText("开机");
                chipCommand.setChipBackgroundColorResource(android.R.color.holo_green_dark);
                chipCommand.setTextColor(Color.WHITE);
            } else if ("<POWER_OFF>".equals(cmd)) {
                chipCommand.setText("关机");
                chipCommand.setChipBackgroundColorResource(android.R.color.holo_red_dark);
                chipCommand.setTextColor(Color.WHITE);
            } else if ("<RESTART>".equals(cmd)) {
                chipCommand.setText("重启");
                chipCommand.setChipBackgroundColorResource(android.R.color.holo_orange_dark);
                chipCommand.setTextColor(Color.WHITE);
            } else {
                chipCommand.setText(cmd);
                chipCommand.setChipBackgroundColorResource(android.R.color.darker_gray);
                chipCommand.setTextColor(Color.WHITE);
            }
        }

        private String formatInterval(int intervalSecs) {
            if (intervalSecs % 86400 == 0) return "每 " + (intervalSecs / 86400) + " 天";
            if (intervalSecs % 3600 == 0) return "每 " + (intervalSecs / 3600) + " 小时";
            if (intervalSecs % 60 == 0) return "每 " + (intervalSecs / 60) + " 分钟";
            return "每 " + intervalSecs + " 秒";
        }
    }
}

