package cn.jdnjk.simpfun.adapter;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.FileItem;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
    public interface OnItemClickListener {
        void onClick(FileItem item);
    }

    public interface OnItemMoreClickListener {
        void onClick(FileItem item, View anchor);
    }

    public interface OnItemLongClickListener {
        void onLongClick(FileItem item, View anchor);
    }

    public interface PathResolver {
        String resolve(FileItem item);
    }

    private final List<FileItem> data;
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;
    private final OnItemMoreClickListener moreClickListener;
    private PathResolver pathResolver;
    private Set<String> selectedPaths = Collections.emptySet();
    private boolean selectionMode;
    private boolean showSelectionCheckbox = true;
    private boolean showMoreButton = true;
    private int selectedBackgroundColorRes = R.color.md_theme_secondaryContainer;

    public FileAdapter(List<FileItem> data, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
        this(data, clickListener, longClickListener, (item, anchor) -> clickListener.onClick(item));
    }

    public FileAdapter(List<FileItem> data, OnItemClickListener clickListener, OnItemLongClickListener longClickListener,
            OnItemMoreClickListener moreClickListener) {
        this.data = data;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.moreClickListener = moreClickListener;
    }

    public void setSelectionState(boolean selectionMode, Set<String> selectedPaths, PathResolver pathResolver) {
        this.selectionMode = selectionMode;
        this.selectedPaths = selectedPaths == null ? Collections.emptySet() : selectedPaths;
        this.pathResolver = pathResolver;
        notifyDataSetChanged();
    }

    public void setSelectionPresentation(boolean showSelectionCheckbox, boolean usePrimarySelectionColor) {
        this.showSelectionCheckbox = showSelectionCheckbox;
        this.selectedBackgroundColorRes = usePrimarySelectionColor
                ? R.color.md_theme_primary
                : R.color.md_theme_secondaryContainer;
        notifyDataSetChanged();
    }

    public void setShowMoreButton(boolean showMoreButton) {
        this.showMoreButton = showMoreButton;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = data.get(position);
        String path = pathResolver == null ? item.getName() : pathResolver.resolve(item);
        boolean selectable = selectionMode && !item.isParentEntry();
        boolean selected = selectable && selectedPaths.contains(path);
        holder.bind(item, clickListener, longClickListener, moreClickListener, selectionMode, selectable, selected,
                showSelectionCheckbox, showMoreButton, selectedBackgroundColorRes);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView info;
        private final TextView moreButton;
        private final CheckBox selectedCheckBox;
        private final Drawable defaultBackground;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_view_icon);
            name = itemView.findViewById(R.id.text_view_name);
            info = itemView.findViewById(R.id.text_view_info);
            moreButton = itemView.findViewById(R.id.button_file_more);
            selectedCheckBox = itemView.findViewById(R.id.check_box_selected);
            defaultBackground = itemView.getBackground();
        }

        void bind(FileItem item, OnItemClickListener clickListener, OnItemLongClickListener longClickListener,
                OnItemMoreClickListener moreClickListener, boolean selectionMode, boolean selectable, boolean selected,
                boolean showSelectionCheckbox, boolean showMoreButton, int selectedBackgroundColorRes) {
            name.setText(item.getName());
            if (item.isParentEntry()) {
                icon.setImageResource(R.drawable.ic_folder_material);
                info.setText(R.string.parent_directory);
            } else if (item.isFile()) {
                icon.setImageResource(R.drawable.term);
                info.setText(itemView.getContext().getString(
                        R.string.file_info_format,
                        formatSize(item.getSize()),
                        item.getModifiedAt()
                ));
            } else {
                icon.setImageResource(R.drawable.ic_folder_material);
                info.setText(itemView.getContext().getString(R.string.folder_info_format, item.getModifiedAt()));
            }

            selectedCheckBox.setVisibility(showSelectionCheckbox && selectable ? View.VISIBLE : View.GONE);
            selectedCheckBox.setChecked(selected);
            moreButton.setVisibility(selectionMode || item.isParentEntry() || !showMoreButton ? View.GONE : View.VISIBLE);
            if (selected) {
                int selectedColor = itemView.getResources().getColor(selectedBackgroundColorRes, null);
                itemView.setBackgroundColor(selectedColor);
            } else {
                itemView.setBackground(defaultBackground);
            }

            itemView.setOnClickListener(v -> clickListener.onClick(item));
            itemView.setOnLongClickListener(v -> {
                longClickListener.onLongClick(item, itemView);
                return true;
            });
            moreButton.setOnClickListener(v -> moreClickListener.onClick(item, moreButton));
        }

        private String formatSize(long size) {
            if (size <= 0) {
                return "0 B";
            }
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int group = (int) (Math.log10(size) / Math.log10(1024));
            group = Math.min(group, units.length - 1);
            return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, group)) + " " + units[group];
        }
    }
}
