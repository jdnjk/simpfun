package cn.jdnjk.simpfun.download;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.notification.TaskQueueNotificationHelper;import cn.jdnjk.simpfun.utils.Feedback;
import cn.jdnjk.simpfun.utils.NotificationPermissionHelper;
import cn.jdnjk.simpfun.utils.NotificationPermissionHelper.DenialListener;
import cn.jdnjk.simpfun.utils.ServerStatsFormatter;

/**
 * 下载任务的界面外壳：进度对话框、取消、转后台通知。
 *
 * <p>UX 与上传流程（{@code FileTransferController} 的 showUploadDialog / moveCurrentUploadToBackground）
 * 保持一致，备份页和文件管理器共用这一份实现。
 *
 * <p>不做成前台 Service：转后台只是把对话框换成通知，HTTP 请求本来就活在 OkHttp 线程上，
 * 只要引擎持有 applicationContext 就不受界面销毁影响。
 *
 * <p><b>必须在 Fragment.onCreate() 中构造</b>——内部要注册 ActivityResultLauncher。
 */
public final class DownloadTaskController {

    public interface Host {
        @Nullable
        Context getContextOrNull();

        /** 视图是否仍然存活。注意是正向语义。 */
        boolean isActive();

        /** 用于 Snackbar 的根视图，通常是 Fragment.getView()。 */
        @Nullable
        View getFeedbackRoot();

        int getDeviceId();

        /** 点通知后要跳转的导航目标，如 R.id.nav_backup。 */
        int getNotificationNavId();
    }

    /**
     * 换取下载地址。服务端下发的地址只能用一次，所以每次下载（含重试）都要重新走一遍，
     * 不能缓存上一次的结果。
     */
    public interface UrlResolver {
        void resolve(@NonNull UrlCallback callback);
    }

    public interface UrlCallback {
        void onUrl(@NonNull String url);

        void onFailure(@NonNull String errorMsg);
    }

    private final Host host;
    private final NotificationPermissionHelper notificationPermissionHelper;

    private AlertDialog dialog;
    private LinearProgressIndicator progressIndicator;
    private TextView statusText;
    private TextView fileNameText;

    private FileDownloader.Handle currentHandle;
    private String currentFileName;
    private String currentSpeedText;
    private int currentNotificationId;
    private int currentDeviceId;
    private int currentNavId;
    private int currentLastProgress = -1;
    private long currentStartMillis;
    private long downloadGeneration;
    private boolean backgrounded;
    private boolean cancelled;
    private boolean preparing;

    public DownloadTaskController(@NonNull Fragment fragment, @NonNull Host host) {
        this.host = host;
        DenialListener denialListener = () -> {
            // 通知被拒时不转后台：TaskQueueNotificationHelper 会静默丢弃通知，
            // 真转过去就变成一个没有任何界面的下载了。
            if (host.isActive()) {
                feedback(R.string.download_notification_denied, true);
            }
        };
        this.notificationPermissionHelper = new NotificationPermissionHelper(fragment, denialListener);
    }

    public boolean isBusy() {
        return currentHandle != null || preparing;
    }

    /**
     * 开始下载。
     *
     * <p>只接受 {@link UrlResolver} 而不接受现成的 URL：下载地址是一次性的，
     * 重试时必须重新换一条，缓存下来的地址第二次用只会拿到「已失效」。
     */
    public void start(@NonNull String fileName, @NonNull UrlResolver urlResolver) {
        startInternal(fileName, null, urlResolver);
    }

    /**
     * 开始下载，并可选地在拿到响应后按服务器文件名改写落盘名。
     * 普通文件下载不传 resolver，行为不变；备份下载传，以按 Content-Disposition 存盘。
     */
    public void start(@NonNull String fileName, @Nullable FileDownloader.FilenameResolver resolver,
                      @NonNull UrlResolver urlResolver) {
        startInternal(fileName, resolver, urlResolver);
    }

    private void startInternal(@NonNull String fileName, @Nullable FileDownloader.FilenameResolver resolver,
                               @NonNull UrlResolver urlResolver) {
        Context context = host.getContextOrNull();
        if (context == null) {
            return;
        }
        if (isBusy()) {
            feedback(R.string.download_busy, false);
            return;
        }

        // 目标位置在主线程解析：DownloadLocationManager 背后的 SettingsSaveManager
        // 用的是非同步 HashMap，主线程在写，工作线程不能读。
        DownloadTargetResolver.Result result;
        try {
            result = DownloadTargetResolver.resolve(context, fileName);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "无法确定保存位置" : e.getMessage();
            feedbackText(context.getString(R.string.download_failed_format, message), true);
            return;
        }
        if (result.didFallback()) {
            new DownloadLocationManager(context).setMode(DownloadLocationManager.MODE_APP_PRIVATE);
            feedbackText(result.fallbackReason, true);
        }

        preparing = true;
        currentFileName = fileName;
        currentDeviceId = host.getDeviceId();
        currentNavId = host.getNotificationNavId();
        currentNotificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        currentLastProgress = -1;
        currentStartMillis = System.currentTimeMillis();
        currentSpeedText = context.getString(R.string.download_preparing);
        backgrounded = false;
        cancelled = false;
        long downloadId = ++downloadGeneration;

        // 转后台后，通知栏「取消」经 DownloadCancelReceiver → 本注册表 → cancelCurrent。
        ActiveDownloadRegistry.getInstance().register(currentNotificationId, this::cancelCurrent);

        showDialog(fileName);
        urlResolver.resolve(new UrlCallback() {
            @Override
            public void onUrl(@NonNull String url) {
                if (isStaleDownload(downloadId)) {
                    result.target.deletePartial();
                    return;
                }
                preparing = false;
                beginTransfer(context.getApplicationContext(), url, result.target, downloadId, resolver);
            }

            @Override
            public void onFailure(@NonNull String errorMsg) {
                if (isStaleDownload(downloadId)) {
                    return;
                }
                preparing = false;
                result.target.deletePartial();
                reportFailure(context.getApplicationContext(), errorMsg);
            }
        });
    }

    private void beginTransfer(Context appContext, String url, DownloadTarget target, long downloadId,
                               @Nullable FileDownloader.FilenameResolver resolver) {
        currentHandle = FileDownloader.download(url, target, new FileDownloader.Callback() {
            @Override
            public void onProgress(long downloadedBytes, long totalBytes) {
                if (isStaleDownload(downloadId) || cancelled) {
                    return;
                }
                int progress = calculateProgress(downloadedBytes, totalBytes);
                currentLastProgress = progress;
                currentSpeedText = formatSpeed(downloadedBytes);
                if (backgrounded) {
                    TaskQueueNotificationHelper.showDownloadProgress(appContext, currentNotificationId,
                            currentDeviceId, currentNavId, currentFileName, Math.max(progress, 0),
                            currentSpeedText, progress < 0);
                } else if (host.isActive()) {
                    updateDialog(progress, currentSpeedText);
                }
            }

            @Override
            public void onSuccess(DownloadTarget target) {
                if (isStaleDownload(downloadId) || cancelled) {
                    return;
                }
                String location = target.getDisplayPath();
                if (backgrounded) {
                    TaskQueueNotificationHelper.showDownloadComplete(appContext, currentNotificationId,
                            currentDeviceId, currentNavId, currentFileName, location);
                } else if (host.isActive()) {
                    dismissDialog();
                    feedbackText(appContext.getString(R.string.download_complete_format, location), false);
                }
                cleanup();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (isStaleDownload(downloadId) || cancelled) {
                    return;
                }
                reportFailure(appContext, errorMsg);
                cleanup();
            }
        }, resolver);
    }

    private void reportFailure(Context appContext, String errorMsg) {
        if (backgrounded) {
            TaskQueueNotificationHelper.showDownloadFailed(appContext, currentNotificationId,
                    currentDeviceId, currentNavId, currentFileName, errorMsg);
        } else if (host.isActive()) {
            dismissDialog();
            feedbackText(appContext.getString(R.string.download_failed_format, errorMsg), true);
        }
    }

    private void showDialog(String fileName) {
        Context context = host.getContextOrNull();
        if (context == null || !host.isActive()) {
            return;
        }
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null);
        fileNameText = view.findViewById(R.id.text_download_file_name);
        statusText = view.findViewById(R.id.text_download_status);
        progressIndicator = view.findViewById(R.id.progress_download);
        View buttonBackground = view.findViewById(R.id.button_download_background);
        View buttonCancel = view.findViewById(R.id.button_download_cancel);

        fileNameText.setText(fileName);
        statusText.setText(R.string.download_preparing);
        progressIndicator.setIndeterminate(true);

        buttonBackground.setOnClickListener(v -> moveToBackground());
        buttonCancel.setOnClickListener(v -> cancelCurrent());

        dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.file_action_download)
                .setView(view)
                .setCancelable(false)
                .create();
        dialog.show();
    }

    private void updateDialog(int progress, String speedText) {
        if (progressIndicator == null || statusText == null) {
            return;
        }
        if (progress < 0) {
            progressIndicator.setIndeterminate(true);
            statusText.setText(speedText);
            return;
        }
        progressIndicator.setIndeterminate(false);
        try {
            progressIndicator.setProgressCompat(progress, true);
        } catch (Throwable t) {
            progressIndicator.setProgress(progress);
        }
        statusText.setText(statusText.getContext().getString(R.string.download_progress_format, progress, speedText));
    }

    private void moveToBackground() {
        if (currentHandle == null && !preparing) {
            dismissDialog();
            return;
        }
        notificationPermissionHelper.withPermission(() -> {
            Context context = host.getContextOrNull();
            if (context == null) {
                return;
            }
            if (currentHandle == null && !preparing) {
                dismissDialog();
                return;
            }
            backgrounded = true;
            dismissDialog();
            TaskQueueNotificationHelper.showDownloadProgress(context.getApplicationContext(),
                    currentNotificationId, currentDeviceId, currentNavId, currentFileName,
                    Math.max(currentLastProgress, 0), currentSpeedText, currentLastProgress < 0);
            feedback(R.string.download_backgrounded, false);
        });
    }

    private void cancelCurrent() {
        cancelled = true;
        // 换取下载地址期间也可能被取消，此时 currentHandle 还是 null。
        // 递增 generation 让在途的地址回调作废，否则取消后仍会开始下载。
        downloadGeneration++;
        if (currentHandle != null) {
            currentHandle.cancel();
        }
        Context context = host.getContextOrNull();
        if (context != null) {
            TaskQueueNotificationHelper.cancel(context.getApplicationContext(), currentNotificationId);
        }
        dismissDialog();
        cleanup();
        feedback(R.string.download_canceled, false);
    }

    /** 返回 true 表示这个回调属于已被取代的旧任务。命名为正向语义，不沿用上传侧反向的 isCurrentUpload。 */
    private boolean isStaleDownload(long downloadId) {
        return downloadId != downloadGeneration;
    }

    private void cleanup() {
        if (currentNotificationId != 0) {
            ActiveDownloadRegistry.getInstance().unregister(currentNotificationId);
        }
        currentHandle = null;
        currentFileName = null;
        currentSpeedText = null;
        currentNotificationId = 0;
        currentDeviceId = -1;
        currentNavId = 0;
        currentLastProgress = -1;
        currentStartMillis = 0L;
        backgrounded = false;
        cancelled = false;
        preparing = false;
    }

    private void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        progressIndicator = null;
        statusText = null;
        fileNameText = null;
    }

    /** 视图销毁：未转后台的下载随界面一起取消，与上传流程一致。 */
    public void onDestroyView() {
        notificationPermissionHelper.clearPending();
        if (!backgrounded && (currentHandle != null || preparing)) {
            cancelled = true;
            // 同时作废在途的地址换取回调，否则界面已销毁下载还会开始。
            downloadGeneration++;
            if (currentHandle != null) {
                currentHandle.cancel();
            }
            cleanup();
        }
        dismissDialog();
    }

    private int calculateProgress(long downloadedBytes, long totalBytes) {
        if (totalBytes <= 0) {
            return -1;
        }
        long value = downloadedBytes * 100L / totalBytes;
        return (int) Math.max(0, Math.min(value, 100));
    }

    private String formatSpeed(long downloadedBytes) {
        long elapsedMillis = Math.max(1L, System.currentTimeMillis() - currentStartMillis);
        return ServerStatsFormatter.formatSpeed(downloadedBytes * 1000L / elapsedMillis);
    }

    private void feedback(int stringRes, boolean isError) {
        Context context = host.getContextOrNull();
        if (context != null) {
            feedbackText(context.getString(stringRes), isError);
        }
    }

    private void feedbackText(@Nullable String message, boolean isError) {
        View root = host.getFeedbackRoot();
        if (root == null || message == null) {
            return;
        }
        if (isError) {
            Feedback.error(root, message);
        } else {
            Feedback.info(root, message);
        }
    }
}
