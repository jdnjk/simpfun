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
import cn.jdnjk.simpfun.model.DevBackupItem;

public class DevBackupAdapter extends RecyclerView.Adapter<DevBackupAdapter.BackupViewHolder> {

    public interface OnBackupClickListener {
        void onBackupClick(DevBackupItem item);
    }

    private final List<DevBackupItem> items = new ArrayList<>();
    private final OnBackupClickListener listener;

    public DevBackupAdapter(OnBackupClickListener listener) {
        this.listener = listener;
    }

    public void submitItems(List<DevBackupItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public BackupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dev_backup, parent, false);
        return new BackupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BackupViewHolder holder, int position) {
        DevBackupItem item = items.get(position);
        holder.tvTag.setText(item.getTag().isEmpty() ? "备份 #" + item.getId() : item.getTag());
        String validTime = item.getValidTime();
        if (validTime.length() > 10) validTime = validTime.substring(0, 10);
        holder.tvMeta.setText("大小 " + item.getFormattedSize() + " · 有效期至 " + validTime + " · #" + item.getId());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBackupClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BackupViewHolder extends RecyclerView.ViewHolder {
        TextView tvTag, tvMeta;

        BackupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTag = itemView.findViewById(R.id.tv_backup_tag);
            tvMeta = itemView.findViewById(R.id.tv_backup_meta);
        }
    }
}
