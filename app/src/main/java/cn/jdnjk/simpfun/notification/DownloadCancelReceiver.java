package cn.jdnjk.simpfun.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cn.jdnjk.simpfun.download.ActiveDownloadRegistry;

/**
 * 处理「下载进度通知」里的取消按钮。
 *
 * <p>进度通知的取消动作用 {@code PendingIntent.getBroadcast} 指向本 receiver，
 * extra 携带 notificationId。收到后在 {@link ActiveDownloadRegistry} 里找到对应下载
 * 并取消，同时清掉通知本身。
 */
public class DownloadCancelReceiver extends BroadcastReceiver {

    public static final String ACTION_CANCEL_DOWNLOAD = "cn.jdnjk.simpfun.download.CANCEL";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!ACTION_CANCEL_DOWNLOAD.equals(intent.getAction())) {
            return;
        }
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (notificationId < 0) {
            return;
        }
        // 触发在途下载取消（会自己清通知/清理半成品），这里兜底也清一遍。
        ActiveDownloadRegistry.getInstance().cancel(notificationId);
        TaskQueueNotificationHelper.cancel(context, notificationId);
    }
}
