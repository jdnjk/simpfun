package cn.jdnjk.simpfun.ui.ins.mirror.rollback;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;

public class RollbackAdapter extends RecyclerView.Adapter<RollbackAdapter.RollbackVH> {

    public interface Listener {
        void onRollbackPeriodClick(String rawTime, String displayTime);
    }

    private final List<String> periods = new ArrayList<>();
    private final Listener listener;

    public RollbackAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<String> list) {
        periods.clear();
        if (list != null) {
            periods.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RollbackVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rollback_period, parent, false);
        return new RollbackVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RollbackVH holder, int position) {
        String rawTime = periods.get(position);
        String displayTime = formatRollbackTime(rawTime);
        holder.tvTime.setText(displayTime);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRollbackPeriodClick(rawTime, displayTime);
            }
        });
    }

    @Override
    public int getItemCount() {
        return periods.size();
    }

    public static long parseRollbackEpochSecond(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return Long.MIN_VALUE;
        }
        try {
            return OffsetDateTime.parse(rawTime).toEpochSecond();
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    private String formatRollbackTime(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return "-";
        }
        try {
            return OffsetDateTime.parse(rawTime)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault()));
        } catch (Exception ignored) {
            return rawTime;
        }
    }

    static class RollbackVH extends RecyclerView.ViewHolder {
        final TextView tvTime;

        RollbackVH(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_rollback_time);
        }
    }
}
