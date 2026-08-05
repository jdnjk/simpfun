package cn.jdnjk.simpfun.ui.troubleshoot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.List;

import cn.jdnjk.simpfun.model.HeartbeatPoint;

public class HeartbeatStripView extends View {
    private static final int GREEN = 0xFF43A047;
    private static final int RED = 0xFFE53935;
    private static final int AMBER = 0xFFFB8C00;

    private final Paint upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint downPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] statuses;

    public HeartbeatStripView(Context context) {
        this(context, null);
    }

    public HeartbeatStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        upPaint.setColor(GREEN);
        downPaint.setColor(RED);
        pendingPaint.setColor(AMBER);
        setWillNotDraw(false);
    }

    public void setHeartbeats(List<HeartbeatPoint> points) {
        if (points == null || points.isEmpty()) {
            statuses = null;
        } else {
            statuses = new int[points.size()];
            for (int i = 0; i < points.size(); i++) {
                statuses[i] = points.get(i).getStatus();
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (statuses == null || statuses.length == 0) {
            return;
        }
        int n = statuses.length;
        float gap = dp(1.5f);
        float segW = (getWidth() - gap * (n - 1)) / n;
        if (segW <= 0) return;
        float radius = Math.min(segW / 2f, dp(1.5f));
        RectF rect = new RectF();
        for (int i = 0; i < n; i++) {
            float left = i * (segW + gap);
            rect.set(left, 0, left + segW, getHeight());
            canvas.drawRoundRect(rect, radius, radius, paintForStatus(statuses[i]));
        }
    }

    private Paint paintForStatus(int status) {
        if (status == 1) return upPaint;
        if (status == 2) return pendingPaint;
        return downPaint;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
