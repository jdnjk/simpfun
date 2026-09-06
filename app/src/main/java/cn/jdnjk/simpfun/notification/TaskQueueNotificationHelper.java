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
        channel.setDescription("文件上传下载等任务队列进度");
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

    @SuppressLint("MissingPermission")
    public static void showDownloadProgress(@NonNull Context context, int notificationId, int deviceId, int navId,
            @Nullable String fileName, int progress, @Nullable String speedText, boolean indeterminate) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        if (!hasNotificationPermission(appContext)) {
            return;
        }

        String safeFileName = isBlank(fileName) ? "文件" : fileName.trim();
        String safeSpeedText = isBlank(speedText) ? "正在下载" : speedText.trim();
        String content = indeterminate ? safeSpeedText : progress + "% · " + safeSpeedText;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_download_24)
                .setContentTitle("正在下载 " + safeFileName)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(buildContentIntent(appContext, notificationId, deviceId, navId));
        // 转后台后也能从通知栏取消：动作经 DownloadCancelReceiver 路由到在途下载。
        builder.addAction(0, "取消", buildDownloadCancelPending(appContext, notificationId));
        builder.setProgress(100, Math.max(0, Math.min(progress, 100)), indeterminate);
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
    }

    /** 通知「取消」按钮：广播给 DownloadCancelReceiver，携带本通知的 id。 */
    private static PendingIntent buildDownloadCancelPending(@NonNull Context context, int notificationId) {
        Intent intent = new Intent(context, DownloadCancelReceiver.class);
        intent.setAction(DownloadCancelReceiver.ACTION_CANCEL_DOWNLOAD);
        intent.putExtra(DownloadCancelReceiver.EXTRA_NOTIFICATION_ID, notificationId);
        return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @SuppressLint("MissingPermission")
    public static void showDownloadComplete(@NonNull Context context, int notificationId, int deviceId, int navId,
            @Nullable String fileName, @Nullable String location) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        if (!hasNotificationPermission(appContext)) {
            return;
        }

        String safeFileName = isBlank(fileName) ? "文件" : fileName.trim();
        String content = isBlank(location) ? "下载完成" : "已保存到 " + location.trim();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_download_24)
                .setContentTitle(safeFileName + " 下载完成")
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(buildContentIntent(appContext, notificationId, deviceId, navId));
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
    }

    @SuppressLint("MissingPermission")
    public static void showDownloadFailed(@NonNull Context context, int notificationId, int deviceId, int navId,
            @Nullable String fileName, @Nullable String errorMsg) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        if (!hasNotificationPermission(appContext)) {
            return;
        }

        String safeFileName = isBlank(fileName) ? "文件" : fileName.trim();
        String safeError = isBlank(errorMsg) ? "下载失败" : errorMsg.trim();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_download_24)
                .setContentTitle(safeFileName + " 下载失败")
                .setContentText(safeError)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeError))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(buildContentIntent(appContext, notificationId, deviceId, navId));
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
    }

    private static PendingIntent buildContentIntent(@NonNull Context context, int notificationId, int deviceId) {
        return buildContentIntent(context, notificationId, deviceId, R.id.nav_gallery);
    }

    /** navId 决定点通知后跳到哪个页面：文件传输跳文件页，备份下载跳备份页。 */
    private static PendingIntent buildContentIntent(@NonNull Context context, int notificationId, int deviceId, int navId) {
        Intent intent = new Intent(context, ServerManages.class);
        intent.putExtra(ServerManages.EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(ServerManages.EXTRA_OPEN_NAV_ID, navId);
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
