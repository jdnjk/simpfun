package cn.jdnjk.simpfun.ui.dev;

import android.content.res.ColorStateList;
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
import cn.jdnjk.simpfun.model.DevVersionItem;

public class DevVersionAdapter extends RecyclerView.Adapter<DevVersionAdapter.VersionViewHolder> {

    public interface OnVersionClickListener {
        void onVersionClick(DevVersionItem item);
    }

    public interface OnVersionDeleteListener {
        void onVersionDelete(DevVersionItem item);
    }

    private final List<DevVersionItem> items = new ArrayList<>();
    private final OnVersionClickListener clickListener;
    private final OnVersionDeleteListener deleteListener;

    public DevVersionAdapter(OnVersionClickListener clickListener, OnVersionDeleteListener deleteListener) {
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    public void submitItems(List<DevVersionItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public List<DevVersionItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public VersionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dev_version, parent, false);
        return new VersionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VersionViewHolder holder, int position) {
        DevVersionItem item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvDescription.setText(item.getDescription());

        holder.tvState.setText(item.isState() ? "公开" : "未公开");
        holder.tvState.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
                item.isState() ? "#1F4CAF50" : "#1F9E9E9E")));
        holder.tvState.setTextColor(Color.parseColor(item.isState() ? "#2E7D32" : "#757575"));

        StringBuilder meta = new StringBuilder()
                .append("大小 ").append(formatSize(item.getSize()))
                .append(" · 安装 ").append(item.getRestoreTimes())
                .append(" · 赞 ").append(item.getLike())
                .append(" · 踩 ").append(item.getDislike());
        String createTime = trimTime(item.getCreateTime());
        if (!createTime.isEmpty()) {
            meta.append(" · ").append(createTime);
        }
        holder.tvMeta.setText(meta.toString());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onVersionClick(item);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onVersionDelete(item);
        });
    }

    private String formatSize(String sizeStr) {
        try {
            long size = Long.parseLong(sizeStr.trim());
            double mb = size / 1024.0 / 1024.0;
            if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
            return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
        } catch (Exception e) {
            return sizeStr.isEmpty() ? "--" : sizeStr;
        }
    }

    private String trimTime(String time) {
        if (time == null) return "";
        if (time.length() > 16) return time.substring(0, 16);
        return time;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VersionViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvState, tvDescription, tvMeta;
        View btnDelete;

        VersionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_version_name);
            tvState = itemView.findViewById(R.id.tv_version_state);
            tvDescription = itemView.findViewById(R.id.tv_version_description);
            tvMeta = itemView.findViewById(R.id.tv_version_meta);
            btnDelete = itemView.findViewById(R.id.btn_version_delete);
        }
    }
}
