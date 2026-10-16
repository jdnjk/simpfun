package cn.jdnjk.simpfun.adapter;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.databinding.ItemRecordBinding;

/**
 * 明细流水列表：每行只展示事由、变动值、变动后余额与时间。
 */
public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    public static class Row {
        public final String reason;
        public final int delta;
        public final int left;
        public final String time;

        public Row(String reason, int delta, int left, String time) {
            this.reason = reason;
            this.delta = delta;
            this.left = left;
            this.time = time;
        }

        public boolean isIncome() {
            return delta >= 0;
        }

        public CharSequence metaText(Context context) {
            String deltaText = (delta >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%,d", delta);
            SpannableString span = new SpannableString(deltaText
                    + " · 变动后 " + String.format(Locale.getDefault(), "%,d", left)
                    + " · " + time);
            int color = ContextCompat.getColor(context, isIncome() ? R.color.success : R.color.task_status_error);
            span.setSpan(new android.text.style.ForegroundColorSpan(color), 0, deltaText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, deltaText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    public void submit(List<Row> list) {
        rows.clear();
        if (list != null) rows.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecordBinding binding = ItemRecordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.binding.tvReason.setText(row.reason);
        holder.binding.tvMeta.setText(row.metaText(holder.binding.tvMeta.getContext()));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemRecordBinding binding;

        ViewHolder(ItemRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /** 把服务端 comment 里的内部编号（如 (ID=16)）从展示文案中清洗掉。 */
    public static String cleanComment(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("[（(]\\s*[Ii][Dd]\\s*=\\s*\\d+\\s*[)）]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /** 秒级/毫秒级时间戳或 ISO 时间 → "MM-dd HH:mm"。 */
    public static String formatShortTime(long seconds) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return fmt.format(new java.util.Date(seconds * 1000L));
    }
}
