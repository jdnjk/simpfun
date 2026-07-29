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
        final com.google.android.material.card.MaterialCardView cardPlan;
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
            cardPlan = itemView.findViewById(R.id.card_plan);
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

            tvId.setText("ID: #" + item.getId());

            cbxPlan.setOnCheckedChangeListener(null);
            boolean isSelected = selectedIds.contains(item.getId());
            cbxPlan.setChecked(isSelected);
            cbxPlan.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedIds.add(item.getId());
                else selectedIds.remove(item.getId());
                notifyItemChanged(getBindingAdapterPosition());
                notifySelectionChanged();
            });

            // Update card appearance based on selection
            android.content.Context ctx = itemView.getContext();
            android.util.TypedValue typedValue = new android.util.TypedValue();
            int strokeColor;
            int bgColor;
            
            if (isSelected) {
                if (ctx.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
                    strokeColor = typedValue.data;
                } else {
                    strokeColor = Color.BLUE;
                }
                if (ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)) {
                    bgColor = typedValue.data;
                } else {
                    bgColor = 0xFFE0E0E0;
                }
                cardPlan.setStrokeWidth(4);
            } else {
                if (ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)) {
                    strokeColor = typedValue.data;
                } else {
                    strokeColor = Color.GRAY;
                }
                if (ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                    bgColor = typedValue.data;
                } else {
                    bgColor = Color.WHITE;
                }
                cardPlan.setStrokeWidth(1);
            }
            
            cardPlan.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
            cardPlan.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
        }

        private void bindCommand(String cmd) {
            android.content.Context ctx = itemView.getContext();
            android.util.TypedValue typedValue = new android.util.TypedValue();
            
            int bgColorAttr;
            int textColorAttr;
            String text;

            if ("<POWER_ON>".equals(cmd)) {
                text = "开机";
                bgColorAttr = com.google.android.material.R.attr.colorTertiaryContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnTertiaryContainer;
            } else if ("<POWER_OFF>".equals(cmd)) {
                text = "关机";
                bgColorAttr = com.google.android.material.R.attr.colorErrorContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnErrorContainer;
            } else if ("<RESTART>".equals(cmd)) {
                text = "重启";
                bgColorAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                textColorAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
            } else {
                text = cmd;
                bgColorAttr = com.google.android.material.R.attr.colorSurfaceVariant;
                textColorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
            }

            chipCommand.setText(text);
            
            int bgColor;
            if (ctx.getTheme().resolveAttribute(bgColorAttr, typedValue, true)) {
                bgColor = typedValue.data;
            } else {
                bgColor = Color.LTGRAY;
            }
            chipCommand.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));

            int textColor;
            if (ctx.getTheme().resolveAttribute(textColorAttr, typedValue, true)) {
                textColor = typedValue.data;
            } else {
                textColor = Color.BLACK;
            }
            chipCommand.setTextColor(textColor);
        }

        private String formatInterval(int intervalSecs) {
            if (intervalSecs % 86400 == 0) return "每 " + (intervalSecs / 86400) + " 天";
            if (intervalSecs % 3600 == 0) return "每 " + (intervalSecs / 3600) + " 小时";
            if (intervalSecs % 60 == 0) return "每 " + (intervalSecs / 60) + " 分钟";
            return "每 " + intervalSecs + " 秒";
        }
    }
}

