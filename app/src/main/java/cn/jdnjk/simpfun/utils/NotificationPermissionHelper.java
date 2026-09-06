package cn.jdnjk.simpfun.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * Android 13+ 的 POST_NOTIFICATIONS 运行时授权。
 *
 * <p>低于 TIRAMISU 或已授权时直接执行动作；否则记住动作、发起授权请求，
 * 在回调里补跑或告知用户被拒。
 *
 * <p><b>必须在 Fragment 的 onCreate 里构造</b>，registerForActivityResult
 * 在 STARTED 之后调用会抛异常。
 */
public final class NotificationPermissionHelper {

    public interface DenialListener {
        void onPermissionDenied();
    }

    private final Fragment fragment;
    private final ActivityResultLauncher<String> launcher;
    @Nullable
    private Runnable pendingAction;

    public NotificationPermissionHelper(@NonNull Fragment fragment, @NonNull DenialListener denialListener) {
        this.fragment = fragment;
        this.launcher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    Runnable action = pendingAction;
                    pendingAction = null;
                    if (Boolean.TRUE.equals(granted)) {
                        if (action != null) {
                            action.run();
                        }
                    } else if (fragment.isAdded()) {
                        denialListener.onPermissionDenied();
                    }
                });
    }

    /** 已有权限则立即执行 action，否则先请求权限、授权后再执行。 */
    public void withPermission(@NonNull Runnable action) {
        Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingAction = action;
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** 视图销毁时调用，避免授权回调跑到已失效的界面上。 */
    public void clearPending() {
        pendingAction = null;
    }
}
