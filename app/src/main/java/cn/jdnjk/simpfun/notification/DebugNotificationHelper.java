package cn.jdnjk.simpfun.notification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SplashActivity;

public final class DebugNotificationHelper {

    public static final String CHANNEL_ID_TEST_PUSH = "test_push";
    public static final String CHANNEL_NAME_TEST_PUSH = "测试通知";
    public static final String ACTION_SHOW_TEST_NOTIFICATION = "cn.jdnjk.simpfun.action.SHOW_TEST_NOTIFICATION";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";

    private DebugNotificationHelper() {}

    public static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        Context appContext = context.getApplicationContext();
        NotificationManager manager = appContext.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID_TEST_PUSH) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_TEST_PUSH,
                CHANNEL_NAME_TEST_PUSH,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Debug 页面触发的测试通知");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    public static void showTestNotification(@NonNull Context context, @Nullable String title, @Nullable String content) {
        showTestNotification(context, title, content, (int) (System.currentTimeMillis() & 0x7fffffff));
    }

    @SuppressLint("MissingPermission")
    public static void showTestNotification(@NonNull Context context, @Nullable String title, @Nullable String content, int notificationId) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String safeTitle = isBlank(title) ? CHANNEL_NAME_TEST_PUSH : title.trim();
        String safeContent = isBlank(content) ? "这是一条测试通知" : content.trim();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_TEST_PUSH)
                .setSmallIcon(R.drawable.ic_notification_test)
                .setContentTitle(safeTitle)
                .setContentText(safeContent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeContent))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(buildContentIntent(appContext, notificationId));

        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
    }

    private static PendingIntent buildContentIntent(@NonNull Context context, int notificationId) {
        Intent intent = new Intent(context, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}

