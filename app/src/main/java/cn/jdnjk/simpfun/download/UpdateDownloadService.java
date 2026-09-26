package cn.jdnjk.simpfun.download;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.notification.TaskQueueNotificationHelper;
import cn.jdnjk.simpfun.ui.setting.SettingsActivity;
import cn.jdnjk.simpfun.utils.UpdateChecker;

/**
 * 更新 APK 下载的前台服务。
 *
 * <p>下载期间用前台服务保活：退出设置页 / App 退后台仍稳定下载，进度（百分比）展示在
 * 通知栏，并带一个「取消」按钮可中止下载并清理未完成的 .apk。
 *
 * <p>两个入口（进 App 自动检查、设置页手动检查）最终都汇到
 * {@link UpdateChecker} 的私有下载逻辑 downloadAndInstall，由它启动本服务。
 */
public class UpdateDownloadService extends Service {

    private static final String TAG = "UpdateDownloadService";
    private static final int NOTIFICATION_ID = 0x55A5; // "UP"
    private static final String ACTION_CANCEL = "cn.jdnjk.simpfun.download.CANCEL_UPDATE";

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long NOTIFY_INTERVAL_MILLIS = 200L;

    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    /** 本次下载写入的 APK，取消/失败时据此清理。 */
    @Nullable
    private volatile File currentApkFile;

    public static void start(Context context, UpdateChecker.UpdateInfo info) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, UpdateDownloadService.class);
        intent.putExtra("version_code", info.versionCode);
        intent.putExtra("version_name", info.versionName);
        intent.putExtra("download_url", info.downloadUrl);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent);
        } else {
            app.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelAndCleanup();
            return START_NOT_STICKY;
        }

        final int versionCode = intent.getIntExtra("version_code", -1);
        final String versionName = intent.getStringExtra("version_name");
        final String downloadUrl = intent.getStringExtra("download_url");

        if (versionCode <= 0 || downloadUrl == null || downloadUrl.isEmpty()) {
            Log.w(TAG, "无效的更新下载参数");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("正在下载更新", 0, null));

        cancelled.set(false);
        downloadExecutor.execute(() -> doDownload(versionCode, versionName, downloadUrl));
        return START_NOT_STICKY;
    }

    /** 用户点通知「取消」：中断下载、移除通知、清理未完成的 APK 并结束服务。 */
    private void cancelAndCleanup() {
        cancelled.set(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        // 通知栏里点「取消」也要把可能存在的通知清掉（stopForeground 只处理本前台通知）。
        TaskQueueNotificationHelper.cancel(this, NOTIFICATION_ID);
        deleteCurrentApk();
        stopSelf();
    }

    /** 下载完成拉起安装后，缓存里的 APK 保留（供同版本再次直接安装），不在此清理。 */
    private void keepApkForReuse() {
        currentApkFile = null;
    }

    /** 取消 / 失败时删除未完成的 APK。 */
    private void deleteCurrentApk() {
        File apk = currentApkFile;
        if (apk != null && apk.exists() && !apk.delete()) {
            Log.w(TAG, "清理 APK 失败: " + apk.getName());
        }
        currentApkFile = null;
    }

    @Override
    public void onDestroy() {
        cancelled.set(true);
        downloadExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 下载 ----------

    private void doDownload(int versionCode, String versionName, String downloadUrl) {
        File apkFile = new File(getCacheDir(), "simpfun_update_" + versionCode + ".apk");
        currentApkFile = apkFile;

        // 若已存在同版本的 APK（之前下载完整并保留），直接安装，无需重新下载。
        if (apkFile.exists()) {
            Log.d(TAG, "APK 已存在，直接安装");
            keepApkForReuse();
            installApk(apkFile);
            return;
        }

        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .addHeader("User-Agent", "SimpfunAPP/" + BuildConfig.VERSION_NAME)
                    .build();
            okhttp3.Response response = UpdateChecker.httpClient().newCall(request).execute();
            try {
                if (cancelled.get()) {
                    return; // 取消路径会清理 APK（cancelAndCleanup / onDestroy）
                }
                if (!response.isSuccessful()) {
                    deleteCurrentApk();
                    notifyFailed("下载失败: HTTP " + response.code());
                    return;
                }
                okhttp3.ResponseBody body = response.body();
                if (body == null) {
                    deleteCurrentApk();
                    notifyFailed("下载失败: 空响应");
                    return;
                }

                long totalBytes = body.contentLength();
                long downloaded = 0L;
                long lastNotifyAt = 0L;
                byte[] buffer = new byte[BUFFER_SIZE];
                try (InputStream input = body.byteStream();
                     FileOutputStream output = new FileOutputStream(apkFile)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (cancelled.get()) {
                            return; // 取消路径会清理 APK
                        }
                        output.write(buffer, 0, read);
                        downloaded += read;
                        long now = System.currentTimeMillis();
                        // 节流：不要每读一次就更新一次通知。
                        if (now - lastNotifyAt >= NOTIFY_INTERVAL_MILLIS) {
                            lastNotifyAt = now;
                            int pct = totalBytes > 0 ? (int) Math.min(100L, downloaded * 100L / totalBytes) : 0;
                            String content = totalBytes > 0
                                    ? pct + "% · " + formatSize(downloaded) + " / " + formatSize(totalBytes)
                                    : formatSize(downloaded);
                            updateNotification(buildNotification("正在下载更新", pct, content));
                        }
                    }
                }
                if (cancelled.get()) {
                    return;
                }
                Log.d(TAG, "下载完成: " + downloaded + " bytes");
                keepApkForReuse(); // 下载完整，供安装/复用，onDestroy 不应删除
                installApk(apkFile);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "下载更新失败", e);
            if (!cancelled.get()) {
                deleteCurrentApk();
                notifyFailed("下载更新失败: " + describe(e));
            }
        }
    }

    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", apkFile)
                    : Uri.fromFile(apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            // 下载已完整，APK 保留在缓存供复用；移除进度通知并结束服务。
            finishAfterDone();
        } catch (Exception e) {
            Log.e(TAG, "拉起安装失败", e);
            deleteCurrentApk();
            notifyFailed("拉起安装失败: " + e.getMessage());
        }
    }

    /** 下载成功、拉起安装后：移除进度通知并结束服务。 */
    private void finishAfterDone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    // ---------- 通知 ----------

    private Notification buildNotification(String title, int progress, @Nullable String contentText) {
        // startForeground 的通知若指向不存在的渠道，Android 14+ 会直接抛
        // CannotPostForegroundServiceNotificationException 致命崩溃，必须先确保渠道存在。
        TaskQueueNotificationHelper.ensureChannel(this);
        String content = contentText != null ? contentText : (progress > 0 ? progress + "%" : "正在连接…");

        Intent cancelIntent = new Intent(this, UpdateDownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPending = PendingIntent.getService(
                this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                TaskQueueNotificationHelper.CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_download_24)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(contentPending())
                .addAction(0, "取消", cancelPending);
        if (progress > 0) {
            builder.setProgress(100, Math.min(100, progress), false);
        } else {
            builder.setProgress(0, 0, true); // 不确定进度
        }
        return builder.build();
    }

    private PendingIntent contentPending() {
        Intent intent = new Intent(this, SettingsActivity.class);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void updateNotification(Notification notification) {
        try {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "更新通知失败", e);
        }
    }

    private void notifyFailed(String message) {
        TaskQueueNotificationHelper.ensureChannel(this);
        Intent intent = new Intent(this, SettingsActivity.class);
        PendingIntent contentPending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this,
                TaskQueueNotificationHelper.CHANNEL_ID_TASK_QUEUE)
                .setSmallIcon(R.drawable.ic_download_24)
                .setContentTitle("更新下载失败")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(contentPending)
                .build();
        try {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "更新失败通知失败", e);
        }
        finishAfterDone();
    }

    private static String describe(IOException e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static String formatSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int group = (int) (Math.log10(size) / Math.log10(1024));
        group = Math.min(group, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, group), units[group]);
    }
}
