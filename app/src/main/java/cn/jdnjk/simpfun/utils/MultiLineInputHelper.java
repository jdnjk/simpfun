package cn.jdnjk.simpfun.utils;

import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.widget.EditText;

/**
 * 让 maxLines 封顶的多行输入框在外层 ScrollView 里可以自行滚动：
 * 框内还有可滚动内容时，按住后拦截父容器的滚动接管。
 */
public final class MultiLineInputHelper {

    private MultiLineInputHelper() {
    }

    public static void apply(EditText... editors) {
        for (EditText editor : editors) {
            if (editor == null) continue;
            editor.setMovementMethod(ScrollingMovementMethod.getInstance());
            editor.setOnTouchListener((v, event) -> {
                boolean scrollable = editor.canScrollVertically(1)
                        || editor.canScrollVertically(-1);
                if (scrollable && v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                return false;
            });
        }
    }
}
