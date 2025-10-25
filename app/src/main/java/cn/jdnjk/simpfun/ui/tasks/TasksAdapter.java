package cn.jdnjk.simpfun.ui.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.TaskItem;
import com.google.android.material.chip.Chip;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskVH> {
    private final List<TaskItem> tasks = new ArrayList<>();

    public void setData(List<TaskItem> list) {
        tasks.clear();
        if (list != null) tasks.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskVH holder, int position) {
        TaskItem item = tasks.get(position);
        Context ctx = holder.itemView.getContext();
        holder.textId.setText(ctx.getString(R.string.task_id_format, item.getId()));

        String statusText = mapStatus(ctx, item.getStatus());
        holder.chipStatus.setText(statusText);
        applyStatusColors(holder.chipStatus, item.getStatus());

        holder.textComment.setText(item.getComment() == null ? "" : item.getComment());
        holder.textTime.setText(formatUtcToBeijing(item.getCreateTime()));
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    public static class TaskVH extends RecyclerView.ViewHolder {
        final TextView textId, textComment, textTime;
        final Chip chipStatus;
        public TaskVH(@NonNull View itemView) {
            super(itemView);
            textId = itemView.findViewById(R.id.text_task_id);
            chipStatus = itemView.findViewById(R.id.chip_task_status);
            textComment = itemView.findViewById(R.id.text_task_comment);
            textTime = itemView.findViewById(R.id.text_task_time);
        }
    }

    private String mapStatus(Context ctx, int status) {
        if (status == -2) return ctx.getString(R.string.task_status_running);
        if (status == -1) return ctx.getString(R.string.task_status_waiting);
        if (status == 0) return ctx.getString(R.string.task_status_done);
        return ctx.getString(R.string.task_status_error);
    }

    private void applyStatusColors(Chip chip, int status) {
        int bg;
        int fg;
        if (status == -2) { // running
            bg = chip.getResources().getColor(R.color.md_theme_primaryContainer, null);
            fg = chip.getResources().getColor(R.color.md_theme_onPrimaryContainer, null);
        } else if (status == -1) { // waiting
            bg = chip.getResources().getColor(R.color.md_theme_secondaryContainer, null);
            fg = chip.getResources().getColor(R.color.md_theme_onSecondaryContainer, null);
        } else if (status == 0) { // done
            bg = chip.getResources().getColor(R.color.md_theme_surfaceVariant, null);
            fg = chip.getResources().getColor(R.color.md_theme_onSurfaceVariant, null);
        } else { // error
            bg = chip.getResources().getColor(R.color.md_theme_errorContainer, null);
            fg = chip.getResources().getColor(R.color.md_theme_onErrorContainer, null);
        }
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bg));
        chip.setTextColor(fg);
    }

    private String formatUtcToBeijing(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        // Try common ISO-8601 patterns
        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        };
        Date parsed = null;
        for (String p : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(p, Locale.US);
                in.setTimeZone(TimeZone.getTimeZone("UTC"));
                parsed = in.parse(iso);
                if (parsed != null) break;
            } catch (ParseException ignored) {}
        }
        if (parsed == null) return iso;

        SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        out.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));
        return out.format(parsed);
    }
}
