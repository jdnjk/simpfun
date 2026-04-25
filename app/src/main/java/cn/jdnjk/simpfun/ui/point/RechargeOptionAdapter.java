package cn.jdnjk.simpfun.ui.point;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class RechargeOptionAdapter extends RecyclerView.Adapter<RechargeOptionAdapter.ViewHolder> {
    private final List<RechargeOptionRow> rows = new ArrayList<>();
    private final int layoutRes;
    private final int titleViewId;
    private final int subtitleViewId;
    private final OnOptionSelectedListener listener;
    private int selectedPosition;

    public interface OnOptionSelectedListener {
        void onOptionSelected(int position);
    }

    public RechargeOptionAdapter(
            @LayoutRes int layoutRes,
            @IdRes int titleViewId,
            @IdRes int subtitleViewId,
            OnOptionSelectedListener listener) {
        this.layoutRes = layoutRes;
        this.titleViewId = titleViewId;
        this.subtitleViewId = subtitleViewId;
        this.listener = listener;
    }

    public void setData(List<RechargeOptionRow> data, int selectedPosition) {
        rows.clear();
        if (data != null) {
            rows.addAll(data);
        }
        this.selectedPosition = selectedPosition;
        notifyDataSetChanged();
    }

    private void setSelectedPosition(int selectedPosition) {
        int oldPosition = this.selectedPosition;
        this.selectedPosition = selectedPosition;
        if (oldPosition >= 0 && oldPosition < rows.size()) {
            notifyItemChanged(oldPosition);
        }
        if (selectedPosition >= 0 && selectedPosition < rows.size()) {
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new ViewHolder(view, titleViewId, subtitleViewId);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RechargeOptionRow row = rows.get(position);
        holder.title.setText(row.getTitle());
        holder.subtitle.setText(row.getSubtitle());
        RechargeSelectionStyle.apply(holder.itemView.getContext(), holder.card, holder.title, holder.subtitle, selectedPosition == position);
        holder.card.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition == selectedPosition) return;
            setSelectedPosition(adapterPosition);
            if (listener != null) {
                listener.onOptionSelected(adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView, @IdRes int titleViewId, @IdRes int subtitleViewId) {
            super(itemView);
            card = (MaterialCardView) itemView;
            title = itemView.findViewById(titleViewId);
            subtitle = itemView.findViewById(subtitleViewId);
        }
    }
}
