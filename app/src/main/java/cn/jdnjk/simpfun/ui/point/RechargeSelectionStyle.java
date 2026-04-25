package cn.jdnjk.simpfun.ui.point;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import cn.jdnjk.simpfun.R;

public final class RechargeSelectionStyle {
    private RechargeSelectionStyle() {}

    public static void apply(Context context, MaterialCardView card, TextView title, TextView subtitle, boolean selected) {
        card.setCardBackgroundColor(ContextCompat.getColor(context,
                selected ? R.color.md_theme_primaryContainer : R.color.md_theme_surfaceContainerLowest));
        card.setStrokeColor(selected ? ContextCompat.getColor(context, R.color.md_theme_primary) : Color.TRANSPARENT);
        card.setStrokeWidth(selected ? 2 : 0);
        title.setTextColor(ContextCompat.getColor(context,
                selected ? R.color.md_theme_onPrimaryContainer : R.color.md_theme_onSurface));
        subtitle.setTextColor(ContextCompat.getColor(context,
                selected ? R.color.md_theme_onPrimaryContainer : R.color.md_theme_onSurfaceVariant));
    }
}
