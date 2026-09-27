package cn.jdnjk.simpfun.ui.dev;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.DevFeedbackItem;

public class DevFeedbackAdapter extends RecyclerView.Adapter<DevFeedbackAdapter.FeedbackViewHolder> {

    private final List<DevFeedbackItem> items = new ArrayList<>();

    public void submitItems(List<DevFeedbackItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FeedbackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dev_feedback, parent, false);
        return new FeedbackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedbackViewHolder holder, int position) {
        DevFeedbackItem item = items.get(position);

        holder.tvUser.setText(item.getUserQq().isEmpty() ? "匿名用户" : "QQ " + item.getUserQq());

        if (item.isLike()) {
            holder.tvRating.setText("👍 赞");
            holder.tvRating.setTextColor(Color.parseColor("#2E7D32"));
        } else if (item.isDislike()) {
            holder.tvRating.setText("👎 踩");
            holder.tvRating.setTextColor(Color.parseColor("#C62828"));
        } else {
            holder.tvRating.setText("未评价");
            holder.tvRating.setTextColor(Color.parseColor("#757575"));
        }

        holder.tvComment.setText(item.getComment().isEmpty() ? "（无评论内容）" : item.getComment());

        StringBuilder meta = new StringBuilder();
        if (!item.getVersionName().isEmpty()) {
            meta.append("版本 ").append(item.getVersionName());
        }
        String time = item.getFormattedTime();
        if (!time.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(time);
        }
        holder.tvMeta.setText(meta.toString());
        holder.tvMeta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FeedbackViewHolder extends RecyclerView.ViewHolder {
        TextView tvUser, tvRating, tvComment, tvMeta;

        FeedbackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUser = itemView.findViewById(R.id.tv_feedback_user);
            tvRating = itemView.findViewById(R.id.tv_feedback_rating);
            tvComment = itemView.findViewById(R.id.tv_feedback_comment);
            tvMeta = itemView.findViewById(R.id.tv_feedback_meta);
        }
    }
}
