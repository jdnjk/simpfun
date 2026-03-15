package cn.jdnjk.simpfun.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TestNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!DebugNotificationHelper.ACTION_SHOW_TEST_NOTIFICATION.equals(intent.getAction())) {
            return;
        }

        String title = intent.getStringExtra(DebugNotificationHelper.EXTRA_TITLE);
        String content = intent.getStringExtra(DebugNotificationHelper.EXTRA_CONTENT);
        int notificationId = intent.getIntExtra(
                DebugNotificationHelper.EXTRA_NOTIFICATION_ID,
                (int) (System.currentTimeMillis() & 0x7fffffff)
        );
        DebugNotificationHelper.showTestNotification(context, title, content, notificationId);
    }
}

