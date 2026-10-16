package cn.jdnjk.simpfun.utils;

import android.view.MotionEvent;
import android.view.View;

/**
 * 按下时轻微缩放（0.97）的按压反馈，配合涟漪一起用。
 */
public class PressScaleListener implements View.OnTouchListener {

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                break;
        }
        return false;
    }
}
