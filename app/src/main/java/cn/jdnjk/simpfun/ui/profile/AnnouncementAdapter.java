package cn.jdnjk.simpfun.ui.profile;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.AnnouncementItem;
import cn.jdnjk.simpfun.utils.AnnouncementHelper;

public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.ViewHolder> {

    private final List<AnnouncementItem> items = new ArrayList<>();
    private int expandedPosition = -1;

    public void submitItems(List<AnnouncementItem> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        expandedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AnnouncementItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvTime.setText(formatTime(item.getCreateTime()));
        holder.tvReadTimes.setText("阅读 " + item.getReadTimes());

        boolean isExpanded = position == expandedPosition;
        holder.tvText.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        if (isExpanded) {
            String text = item.getText();
            if (text == null || text.isEmpty()) {
                holder.tvText.setText("（无内容）");
            } else {
                holder.tvText.setText(AnnouncementHelper.formatAnnouncementText(text));
            }
            holder.tvText.setMovementMethod(LinkMovementMethod.getInstance());
            holder.tvText.setLinksClickable(true);
        }

        holder.itemView.setOnClickListener(v -> {
            int prev = expandedPosition;
            expandedPosition = (expandedPosition == position) ? -1 : position;
            if (prev != -1 && prev != position) {
                notifyItemChanged(prev);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        try {
            String t = isoTime.replace("T", " ").replace("Z", "");
            int dot = t.indexOf('.');
            return dot > 0 ? t.substring(0, dot) : t;
        } catch (Exception e) {
            return isoTime;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvTime;
        final TextView tvReadTimes;
        final TextView tvText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_announcement_title);
            tvTime = itemView.findViewById(R.id.tv_announcement_time);
            tvReadTimes = itemView.findViewById(R.id.tv_announcement_read_times);
            tvText = itemView.findViewById(R.id.tv_announcement_text);
        }
    }
}
