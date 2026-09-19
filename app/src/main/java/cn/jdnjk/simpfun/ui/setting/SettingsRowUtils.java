package cn.jdnjk.simpfun.ui.setting;

import android.view.View;
import android.view.ViewGroup;

import cn.jdnjk.simpfun.R;

/**
 * 设置列表行共用的小工具。
 */
final class SettingsRowUtils {
    private SettingsRowUtils() {
    }

    static void hideLeadingIcon(View row) {
        if (row == null) return;
        View iconBox = row.findViewById(R.id.entry_icon_container);
        if (iconBox != null) iconBox.setVisibility(View.GONE);
        View texts = row.findViewById(R.id.entry_texts);
        if (texts != null) {
            ViewGroup.LayoutParams lp = texts.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMarginStart(0);
                texts.setLayoutParams(lp);
            }
        }
    }
}
