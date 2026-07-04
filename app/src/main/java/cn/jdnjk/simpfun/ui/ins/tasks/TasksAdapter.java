package cn.jdnjk.simpfun.ui.ins.tasks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.TaskItem;
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
        String statusText = mapStatus(ctx, item.getStatus());
        holder.imageStatus.setContentDescription(statusText);
        applyStatusIcon(holder, item.getStatus());

        holder.textComment.setText(item.getComment() == null ? "" : item.getComment());
        holder.textTime.setText(ctx.getString(R.string.task_meta_format, item.getId(), formatUtcToBeijing(item.getCreateTime())));
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    public static class TaskVH extends RecyclerView.ViewHolder {
        final FrameLayout layoutStatusIcon;
        final ImageView imageStatus;
        final TextView textComment, textTime;
        public TaskVH(@NonNull View itemView) {
            super(itemView);
            layoutStatusIcon = itemView.findViewById(R.id.layout_task_status_icon);
            imageStatus = itemView.findViewById(R.id.image_task_status);
            textComment = itemView.findViewById(R.id.text_task_comment);
            textTime = itemView.findViewById(R.id.text_task_time);
        }
    }

    private String mapStatus(Context ctx, int status) {
        if (status == -2)
            return ctx.getString(R.string.task_status_running);
        if (status == -1)
            return ctx.getString(R.string.task_status_waiting);
        if (status == 0)
            return ctx.getString(R.string.task_status_done);
        return ctx.getString(R.string.task_status_error);
    }

    private void applyStatusIcon(TaskVH holder, int status) {
        Context ctx = holder.itemView.getContext();
        int bgRes;
        int tintRes;
        int iconRes;
        if (status == 0) { // done
            bgRes = R.drawable.bg_task_status_done;
            tintRes = R.color.task_status_done;
            iconRes = R.drawable.ic_check_circle_outline_24;
        } else if (status > 0) { // error
            bgRes = R.drawable.bg_task_status_error;
            tintRes = R.color.task_status_error;
            iconRes = R.drawable.ic_error_outline;
        } else if (status == -2) { // running
            bgRes = R.drawable.bg_task_status_running;
            tintRes = R.color.task_status_running;
            iconRes = R.drawable.ic_refresh;
        } else { // waiting
            bgRes = R.drawable.bg_task_status_waiting;
            tintRes = R.color.task_status_waiting;
            iconRes = R.drawable.tasks;
        }

        holder.layoutStatusIcon.setBackgroundResource(bgRes);
        holder.imageStatus.setImageResource(iconRes);
        holder.imageStatus.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, tintRes)));
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
