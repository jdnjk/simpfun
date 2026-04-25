package cn.jdnjk.simpfun.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DecimalFormat;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.FileItem;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
    public interface OnItemClickListener {
        void onClick(FileItem item);
    }

    public interface OnItemLongClickListener {
        void onLongClick(FileItem item);
    }

    private final List<FileItem> data;
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public FileAdapter(List<FileItem> data, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
        this.data = data;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(data.get(position), clickListener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView info;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_view_icon);
            name = itemView.findViewById(R.id.text_view_name);
            info = itemView.findViewById(R.id.text_view_info);
        }

        void bind(FileItem item, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
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

            itemView.setOnClickListener(v -> clickListener.onClick(item));
            itemView.setOnLongClickListener(v -> {
                longClickListener.onLongClick(item);
                return true;
            });
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

