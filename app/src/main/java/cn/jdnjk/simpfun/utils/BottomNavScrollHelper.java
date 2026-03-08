package cn.jdnjk.simpfun.utils;

import androidx.core.widget.NestedScrollView;

public final class BottomNavScrollHelper {
    private BottomNavScrollHelper() {}

    public interface Callback {
        void onPrimaryScroll(int dy, boolean atTop);
    }

    public static final class Binding {
        private NestedScrollView.OnScrollChangeListener listener;

        public void attach(NestedScrollView scrollView, Callback callback) {
            detach(scrollView);
            if (scrollView == null || callback == null) return;
            listener = (v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                    callback.onPrimaryScroll(scrollY - oldScrollY, scrollY <= 0);
            scrollView.setOnScrollChangeListener(listener);
        }

        public void detach(NestedScrollView scrollView) {
            if (scrollView != null) {
                scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) null);
            }
            listener = null;
        }
    }
}

