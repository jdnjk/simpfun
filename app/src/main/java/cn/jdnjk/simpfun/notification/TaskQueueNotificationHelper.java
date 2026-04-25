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
import cn.jdnjk.simpfun.ServerManages;

public final class TaskQueueNotificationHelper {
    public static final String CHANNEL_ID_TASK_QUEUE = "task_queue";
    public static final String CHANNEL_NAME_TASK_QUEUE = "任务队列";

    private TaskQueueNotificationHelper() {}

    public static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        Context appContext = context.getApplicationContext();
        NotificationManager manager = appContext.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID_TASK_QUEUE) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_TASK_QUEUE,
                CHANNEL_NAME_TASK_QUEUE,
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("文件上传等任务队列进度");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    @SuppressLint("MissingPermission")
    public static void showUploadProgress(@NonNull Context context, int notificationId, int deviceId,
            @Nullable String fileName, int progress, @Nullable String speedText, boolean indeterminate) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        if (!hasNotificationPermission(appContext)) {
            return;
        }

        String safeFileName = isBlank(fileName) ? "文件" : fileName.trim();
        String safeSpeedText = isBlank(speedText) ? "正在上传" : speedText.trim();
        String content = indeterminate ? safeSpeedText : progress + "% · " + safeSpeedText;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_upload_24)
                .setContentTitle("正在上传 " + safeFileName)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(buildContentIntent(appContext, notificationId, deviceId));
        builder.setProgress(100, Math.max(0, Math.min(progress, 100)), indeterminate);
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
    }

    @SuppressLint("MissingPermission")
    public static void showUploadFailed(@NonNull Context context, int notificationId, int deviceId,
            @Nullable String fileName, @Nullable String errorMsg) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        if (!hasNotificationPermission(appContext)) {
            return;
        }

        String safeFileName = isBlank(fileName) ? "文件" : fileName.trim();
        String safeError = isBlank(errorMsg) ? "上传失败" : errorMsg.trim();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_upload_24)
                .setContentTitle(safeFileName + " 上传失败")
                .setContentText(safeError)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeError))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(buildContentIntent(appContext, notificationId, deviceId));
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
    }

    public static void cancel(@NonNull Context context, int notificationId) {
        NotificationManagerCompat.from(context.getApplicationContext()).cancel(notificationId);
    }

    private static PendingIntent buildContentIntent(@NonNull Context context, int notificationId, int deviceId) {
        Intent intent = new Intent(context, ServerManages.class);
        intent.putExtra(ServerManages.EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(ServerManages.EXTRA_OPEN_NAV_ID, R.id.nav_gallery);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static boolean hasNotificationPermission(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
