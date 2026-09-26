package cn.jdnjk.simpfun.ui.ins.term;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * 将 ScrollView 的滚动状态方法（默认 protected）提升为 public，
 * 供 {@link TerminalFastScrollView} 读取滚动位置。
 */
public class ScrollStateScrollView extends ScrollView {

    public ScrollStateScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }

    @Override
    public int computeVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }
}
