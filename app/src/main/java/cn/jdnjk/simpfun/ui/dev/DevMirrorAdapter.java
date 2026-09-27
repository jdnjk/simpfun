package cn.jdnjk.simpfun.ui.dev;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.DevMirrorItem;

public class DevMirrorAdapter extends RecyclerView.Adapter<DevMirrorAdapter.MirrorViewHolder> {

    public interface OnMirrorClickListener {
        void onMirrorClick(DevMirrorItem item);
    }

    private final List<DevMirrorItem> allItems = new ArrayList<>();
    private final List<DevMirrorItem> shownItems = new ArrayList<>();
    private final OnMirrorClickListener listener;

    public DevMirrorAdapter(OnMirrorClickListener listener) {
        this.listener = listener;
    }

    public void submitItems(List<DevMirrorItem> items) {
        allItems.clear();
        if (items != null) allItems.addAll(items);
        filter(null);
    }

    public void filter(String keyword) {
        shownItems.clear();
        if (keyword == null || keyword.trim().isEmpty()) {
            shownItems.addAll(allItems);
        } else {
            String key = keyword.trim().toLowerCase();
            for (DevMirrorItem item : allItems) {
                boolean matchName = item.getName() != null
                        && item.getName().toLowerCase().contains(key);
                boolean matchDescription = item.getDescription() != null
                        && item.getDescription().toLowerCase().contains(key);
                if (matchName || matchDescription) {
                    shownItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean isFiltering() {
        return shownItems.size() != allItems.size();
    }

    public int getAllItemCount() {
        return allItems.size();
    }

    @NonNull
    @Override
    public MirrorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dev_mirror, parent, false);
        return new MirrorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MirrorViewHolder holder, int position) {
        DevMirrorItem item = shownItems.get(position);
        holder.tvName.setText(item.getName());
        holder.tvId.setText("#" + item.getId());
        holder.tvDescription.setText(item.getDescription());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMirrorClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
    }

    static class MirrorViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId, tvDescription;

        MirrorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_mirror_name);
            tvId = itemView.findViewById(R.id.tv_mirror_id);
            tvDescription = itemView.findViewById(R.id.tv_mirror_description);
        }
    }
}
