package cn.jdnjk.simpfun.utils;

import android.app.Activity;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

/**
 * 统一的用户反馈入口，替代散落在项目里的 140+ 处 {@code Toast.makeText(...).show()}。
 *
 * <p>相比 Toast 的好处：遵循应用主题、不会在 Android 12+ 被系统降级为小胶囊、
 * 失败提示可以直接挂一个「重试」动作，而 Toast 做不到。
 */
public final class Feedback {

    private Feedback() {
    }

    /** 普通提示。root 传 Fragment 的 getView() 或 Activity 的内容根布局。 */
    public static void info(@Nullable View root, CharSequence message) {
        show(root, message, Snackbar.LENGTH_SHORT, null, null);
    }

    public static void info(@Nullable Activity activity, CharSequence message) {
        info(contentView(activity), message);
    }

    /** 错误提示，无重试动作。 */
    public static void error(@Nullable View root, CharSequence message) {
        show(root, message, Snackbar.LENGTH_LONG, null, null);
    }

    public static void error(@Nullable Activity activity, CharSequence message) {
        error(contentView(activity), message);
    }

    /** 错误提示 + 重试动作。网络失败场景应尽量用这个，让用户不必退出重进。 */
    public static void error(@Nullable View root, CharSequence message,
                             CharSequence actionText, @Nullable Runnable action) {
        show(root, message, Snackbar.LENGTH_LONG, actionText, action);
    }

    private static void show(@Nullable View root, CharSequence message, int duration,
                             @Nullable CharSequence actionText, @Nullable Runnable action) {
        if (root == null || message == null) {
            return;
        }
        Snackbar bar = Snackbar.make(root, message, duration);
        if (actionText != null && action != null) {
            bar.setAction(actionText, v -> action.run());
        }
        bar.show();
    }

    @Nullable
    private static View contentView(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        return activity.findViewById(android.R.id.content);
    }
}
