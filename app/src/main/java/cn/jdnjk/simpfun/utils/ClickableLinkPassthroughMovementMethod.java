package cn.jdnjk.simpfun.utils;

import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * 可点击链接的 MovementMethod，但只在触摸到 URLSpan 时消耗事件。
 * <p>
 * 标准 {@link LinkMovementMethod} 总是消耗触摸事件，导致卡片父级收不到点击。
 * 本类仅在触摸位置确实存在 {@link ClickableSpan} 时消耗事件，
 * 否则让触摸事件冒泡到父级（如卡片的点击监听器）。
 */
public class ClickableLinkPassthroughMovementMethod extends LinkMovementMethod {

    private static ClickableLinkPassthroughMovementMethod instance;

    public static ClickableLinkPassthroughMovementMethod getInstance() {
        if (instance == null) {
            instance = new ClickableLinkPassthroughMovementMethod();
        }
        return instance;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            if (layout == null) return false;

            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);

            if (links.length > 0) {
                if (action == MotionEvent.ACTION_UP) {
                    links[0].onClick(widget);
                } else if (action == MotionEvent.ACTION_DOWN) {
                    Selection.setSelection(buffer, buffer.getSpanStart(links[0]), buffer.getSpanEnd(links[0]));
                }
                return true;
            }
        }

        // 没有点击到链接，不消耗事件，让父级处理
        return false;
    }
}