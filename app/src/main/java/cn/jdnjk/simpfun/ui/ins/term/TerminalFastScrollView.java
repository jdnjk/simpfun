package cn.jdnjk.simpfun.ui.ins.term;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class TerminalFastScrollView extends View {

    private interface ScrollHost {
        int getRange();

        int getExtent();

        int getOffset();

        void scrollToOffset(int offset);
    }

    private static final int THUMB_WIDTH_DP = 8;
    private static final int TOUCH_WIDTH_DP = 28;
    private static final int MIN_THUMB_HEIGHT_DP = 48;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ScrollHost host;
    private RecyclerView attachedRecyclerView;
    private RecyclerView.AdapterDataObserver dataObserver;

    private boolean dragging = false;
    private float downY;
    private float downThumbTop;
    private int downScrollOffset;
    private float thumbTop;
    private float thumbHeight;

    public TerminalFastScrollView(Context context) {
        this(context, null);
    }

    public TerminalFastScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int primary = resolveThemeColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF378ADD);
        trackPaint.setColor(0x26888888);
        thumbPaint.setColor(primary);
        thumbActivePaint.setColor(darken(primary));
    }

    /** 绑定终端日志列表（RecyclerView 宿主） */
    public void attach(@NonNull RecyclerView rv) {
        detach();
        attachedRecyclerView = rv;
        host = new ScrollHost() {
            @Override
            public int getRange() {
                return rv.computeVerticalScrollRange();
            }

            @Override
            public int getExtent() {
                return rv.computeVerticalScrollExtent();
            }

            @Override
            public int getOffset() {
                return rv.computeVerticalScrollOffset();
            }

            @Override
            public void scrollToOffset(int offset) {
                int delta = offset - rv.computeVerticalScrollOffset();
                if (delta != 0) {
                    rv.scrollBy(0, delta);
                }
            }
        };
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                postInvalidate();
            }
        });
        dataObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                postInvalidate();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                postInvalidate();
            }

            @Override
            public void onChanged() {
                postInvalidate();
            }
        };
        if (rv.getAdapter() != null) {
            rv.getAdapter().registerAdapterDataObserver(dataObserver);
        }
    }

    /** 绑定选择模式快照（ScrollView 宿主） */
    public void attach(@NonNull ScrollStateScrollView sv) {
        detach();
        host = new ScrollHost() {
            @Override
            public int getRange() {
                return sv.computeVerticalScrollRange();
            }

            @Override
            public int getExtent() {
                return sv.computeVerticalScrollExtent();
            }

            @Override
            public int getOffset() {
                return sv.computeVerticalScrollOffset();
            }

            @Override
            public void scrollToOffset(int offset) {
                sv.scrollTo(0, offset);
            }
        };
        if (Build.VERSION.SDK_INT >= 23) {
            sv.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) ->
                    postInvalidate());
        }
    }

    /** 供宿主在销毁视图时解除 observer / 监听 */
    public void detach() {
        if (attachedRecyclerView != null) {
            if (dataObserver != null && attachedRecyclerView.getAdapter() != null) {
                attachedRecyclerView.getAdapter().unregisterAdapterDataObserver(dataObserver);
            }
            attachedRecyclerView.setOnScrollChangeListener(null);
        }
        dataObserver = null;
        attachedRecyclerView = null;
        host = null;
        dragging = false;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        ScrollHost h = host;
        if (h == null) return;
        int range = h.getRange();
        int extent = h.getExtent();
        if (range <= extent || range == 0) return; // 内容不满一屏，无需滚动条

        int w = getWidth();
        int trackW = dp(THUMB_WIDTH_DP);
        canvas.drawRect(w - trackW, 0, w, getHeight(), trackPaint);

        thumbHeight = Math.max(dp(MIN_THUMB_HEIGHT_DP), (float) getHeight() * extent / range);
        if (!dragging) {
            thumbTop = thumbTopForOffset(h.getOffset(), range, extent);
        }
        float top = Math.min(thumbTop, getHeight() - thumbHeight);
        canvas.drawRoundRect(w - trackW, top, w, top + thumbHeight,
                trackW / 2f, trackW / 2f, dragging ? thumbActivePaint : thumbPaint);
    }

    private float thumbTopForOffset(int offset, int range, int extent) {
        float scrollable = range - extent;
        if (scrollable <= 0 || getHeight() <= thumbHeight) return 0;
        return (getHeight() - thumbHeight) * Math.max(0, Math.min(offset, (int) scrollable)) / scrollable;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final ScrollHost h = host;
        if (h == null) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int touchW = dp(TOUCH_WIDTH_DP);
                if (event.getX() < getWidth() - touchW) {
                    return false; // 只接管右缘触摸区
                }
                dragging = true;
                downY = event.getY();
                int range = h.getRange();
                int extent = h.getExtent();
                thumbHeight = Math.max(dp(MIN_THUMB_HEIGHT_DP), (float) getHeight() * extent / range);
                downThumbTop = thumbTopForOffset(h.getOffset(), range, extent);
                downScrollOffset = h.getOffset();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                float trackScrollable = getHeight() - thumbHeight;
                float scrollable = h.getRange() - h.getExtent();
                if (trackScrollable <= 0 || scrollable <= 0) return true;
                float thumbDy = event.getY() - downY;
                // thumb 像素位移 → 内容像素位移：比例映射，对不等高行完全精确
                int targetOffset = Math.round(downScrollOffset + thumbDy * (scrollable / trackScrollable));
                h.scrollToOffset(targetOffset);
                thumbTop = Math.max(0, Math.min(downThumbTop + thumbDy, getHeight() - thumbHeight));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private static int resolveThemeColor(Context context, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, tv, true)) {
            return fallback;
        }
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        try {
            ColorStateList csl = context.getResources().getColorStateList(tv.resourceId, context.getTheme());
            return csl.getDefaultColor();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int darken(int color) {
        int r = Math.max(0, (color >> 16) & 0xFF) * 7 / 8;
        int g = Math.max(0, (color >> 8) & 0xFF) * 7 / 8;
        int b = Math.max(0, color & 0xFF) * 7 / 8;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
