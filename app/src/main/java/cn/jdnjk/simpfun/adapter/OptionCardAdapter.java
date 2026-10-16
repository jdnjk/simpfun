package cn.jdnjk.simpfun.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.databinding.ItemOptionCardBinding;
import cn.jdnjk.simpfun.model.OptionCard;

/**
 * 通用单选卡片（档位 / 充值方式 / 卡类型 / 时长 / 流量包）。
 * 选中项以主色描边高亮，未选中为 1dp outlineVariant 描边。
 */
public class OptionCardAdapter extends RecyclerView.Adapter<OptionCardAdapter.ViewHolder> {

    public interface OnCardClick {
        void onClick(int position);
    }

    private final List<OptionCard> cards = new ArrayList<>();
    private final OnCardClick clickListener;
    private int selectedIndex = -1;
    private int cardHeightDp = 96;

    public OptionCardAdapter(OnCardClick clickListener) {
        this.clickListener = clickListener;
    }

    public void setCardHeightDp(int dp) {
        this.cardHeightDp = dp;
    }

    public void submit(List<OptionCard> list, int selectedIndex) {
        cards.clear();
        if (list != null) cards.addAll(list);
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOptionCardBinding binding = ItemOptionCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OptionCard card = cards.get(position);
        boolean selected = position == selectedIndex;

        holder.binding.cardOption.getLayoutParams().height =
                (int) (cardHeightDp * holder.binding.cardOption.getResources().getDisplayMetrics().density);
        holder.binding.cardOption.setStrokeWidth((int) holder.binding.cardOption.getResources()
                .getDisplayMetrics().density * (selected ? 2 : 1));
        holder.binding.cardOption.setStrokeColor(ContextCompat.getColor(holder.binding.cardOption.getContext(),
                selected ? R.color.md_theme_primary : R.color.md_theme_outlineVariant));
        holder.binding.tvTitle.setText(card.title);
        holder.binding.tvBody.setText(card.body);
        holder.binding.cardOption.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(position);
        });
        holder.binding.cardOption.setOnTouchListener(new cn.jdnjk.simpfun.utils.PressScaleListener());
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemOptionCardBinding binding;

        ViewHolder(ItemOptionCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
