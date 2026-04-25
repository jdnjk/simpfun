package cn.jdnjk.simpfun.ui.ins.backups;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.BackupItem;

public class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.BackupVH> {

    public interface Listener {
        void onSelectionChanged(int selectedCount);
    }

    public interface ActionListener {
        void onActionMenuClick(BackupItem item, View anchor);
    }

    private final List<BackupItem> backups = new ArrayList<>();
    private final Set<Integer> selectedIds = new HashSet<>();
    private final Listener listener;
    private final ActionListener actionListener;

    private boolean multiSelectMode = false;

    public BackupAdapter(Listener listener, ActionListener actionListener) {
        this.listener = listener;
        this.actionListener = actionListener;
    }

    public void setData(List<BackupItem> list) {
        backups.clear();
        if (list != null) {
            backups.addAll(list);
        }
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void setMultiSelectMode(boolean enabled) {
        if (this.multiSelectMode == enabled) {
            return;
        }
        this.multiSelectMode = enabled;
        if (!enabled && selectedIds.size() > 1) {
            Integer first = selectedIds.iterator().next();
            selectedIds.clear();
            selectedIds.add(first);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public void clearSelection() {
        if (selectedIds.isEmpty()) {
            return;
        }
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public List<BackupItem> getSelectedItems() {
        List<BackupItem> result = new ArrayList<>();
        for (BackupItem item : backups) {
            if (selectedIds.contains(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    @NonNull
    @Override
    public BackupVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_backup, parent, false);
        return new BackupVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BackupVH holder, int position) {
        BackupItem item = backups.get(position);
        holder.tvTag.setText(buildTagText(item));
        holder.tvSize.setText(formatSize(item.getSize()));
        holder.tvExpire.setText("预计" + formatExpireDate(item.getValidTime()) + "释放");
        holder.tvId.setText("ID=" + item.getId());

        holder.tvWindows.setVisibility(item.isWindows() ? View.VISIBLE : View.GONE);

        boolean selected = selectedIds.contains(item.getId());
        holder.cbSelected.setVisibility(multiSelectMode ? View.VISIBLE : View.GONE);
        holder.cbSelected.setChecked(selected);
        holder.btnMore.setVisibility(multiSelectMode ? View.GONE : View.VISIBLE);

        int strokeColor = holder.itemView.getResources().getColor(
                selected ? R.color.md_theme_primary : R.color.md_theme_outlineVariant,
                null
        );
        holder.cardBackup.setStrokeColor(ColorStateList.valueOf(strokeColor));
        holder.cardBackup.setStrokeWidth(1);

        holder.itemView.setOnClickListener(v -> {
            if (multiSelectMode) {
                toggleSelection(item.getId());
                notifyItemChanged(position);
            } else if (actionListener != null) {
                actionListener.onActionMenuClick(item, holder.btnMore);
            }
        });
        holder.btnMore.setOnClickListener(v -> {
            if (multiSelectMode) {
                toggleSelection(item.getId());
                notifyItemChanged(position);
            } else if (actionListener != null) {
                actionListener.onActionMenuClick(item, holder.btnMore);
            }
        });
    }

    @Override
    public int getItemCount() {
        return backups.size();
    }

    private void toggleSelection(int backupId) {
        if (selectedIds.contains(backupId)) {
            selectedIds.remove(backupId);
        } else {
            selectedIds.add(backupId);
        }
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(selectedIds.size());
        }
    }

    private String buildTagText(BackupItem item) {
        String tag = item.getTag() == null ? "" : item.getTag().trim();
        if (!tag.isEmpty()) {
            return tag;
        }
        return "ID=" + item.getId() + " 7天未开服保存";
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes < 0) {
            return "0B";
        }
        double value = sizeBytes;
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024.0;
            idx++;
        }
        if (idx == 0) {
            return String.format(Locale.US, "%d%s", (long) value, units[idx]);
        }
        return String.format(Locale.US, "%.2f%s", value, units[idx]);
    }

    private String formatExpireDate(String utcTime) {
        if (utcTime == null || utcTime.trim().isEmpty()) {
            return "-";
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(utcTime);
            return odt.atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA));
        } catch (Exception ignored) {
            return utcTime;
        }
    }

    static class BackupVH extends RecyclerView.ViewHolder {
        final MaterialCardView cardBackup;
        final TextView tvTag;
        final TextView tvSize;
        final TextView tvWindows;
        final TextView tvExpire;
        final TextView tvId;
        final TextView btnMore;
        final CheckBox cbSelected;

        BackupVH(@NonNull View itemView) {
            super(itemView);
            cardBackup = itemView.findViewById(R.id.card_backup);
            tvTag = itemView.findViewById(R.id.tv_backup_tag);
            tvSize = itemView.findViewById(R.id.tv_backup_size);
            tvWindows = itemView.findViewById(R.id.tv_backup_windows);
            tvExpire = itemView.findViewById(R.id.tv_backup_expire);
            tvId = itemView.findViewById(R.id.tv_backup_id);
            btnMore = itemView.findViewById(R.id.btn_backup_more);
            cbSelected = itemView.findViewById(R.id.cb_selected);
        }
    }
}
