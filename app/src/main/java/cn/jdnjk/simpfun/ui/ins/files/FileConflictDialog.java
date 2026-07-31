package cn.jdnjk.simpfun.ui.ins.files;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import cn.jdnjk.simpfun.R;

class FileConflictDialog {

    enum ConflictAction {
        REPLACE, SKIP, KEEP_BOTH
    }

    interface Callback {
        void onConflictResolved(ConflictAction action, boolean applyToAll);
    }

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    FileConflictDialog(Activity activity) {
        this.activity = activity;
    }

    /**
     * 在主线程显示冲突对话框（异步回调方式）
     */
    void show(String fileName, long incomingSize, String incomingTime,
              long existingSize, String existingTime,
              boolean showApplyAll, Callback callback) {
        mainHandler.post(() -> showDialogOnMainThread(
                fileName, incomingSize, incomingTime,
                existingSize, existingTime,
                showApplyAll, callback));
    }

    /**
     * 在主线程显示冲突对话框（同步阻塞方式，供后台线程使用）
     * 通过 CountDownLatch 阻塞调用线程，直到用户做出选择。
     */
    ConflictResult showBlocking(String fileName, long incomingSize, String incomingTime,
                                long existingSize, String existingTime,
                                boolean showApplyAll) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConflictResult> resultRef = new AtomicReference<>();

        mainHandler.post(() -> showDialogOnMainThread(
                fileName, incomingSize, incomingTime,
                existingSize, existingTime,
                showApplyAll, (action, applyToAll) -> {
                    resultRef.set(new ConflictResult(action, applyToAll));
                    latch.countDown();
                }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ConflictResult(ConflictAction.SKIP, false);
        }
        return resultRef.get();
    }

    /**
     * 从本地文件获取信息并显示对话框（异步回调方式）
     */
    void showForLocalFile(String fileName, File existingFile, File incomingFile,
                          boolean showApplyAll, Callback callback) {
        String existingTime = formatFileTime(existingFile.lastModified());
        long existingSize = existingFile.length();
        String incomingTime = incomingFile != null ? formatFileTime(incomingFile.lastModified()) : "-";
        long incomingSize = incomingFile != null ? incomingFile.length() : 0;
        show(fileName, incomingSize, incomingTime, existingSize, existingTime, showApplyAll, callback);
    }

    /**
     * 从本地文件获取信息并显示对话框（同步阻塞方式）
     */
    ConflictResult showForLocalFileBlocking(String fileName, File existingFile, File incomingFile,
                                            boolean showApplyAll) {
        String existingTime = formatFileTime(existingFile.lastModified());
        long existingSize = existingFile.length();
        String incomingTime = incomingFile != null ? formatFileTime(incomingFile.lastModified()) : "-";
        long incomingSize = incomingFile != null ? incomingFile.length() : 0;
        return showBlocking(fileName, incomingSize, incomingTime, existingSize, existingTime, showApplyAll);
    }

    private void showDialogOnMainThread(String fileName, long incomingSize, String incomingTime,
                                        long existingSize, String existingTime,
                                        boolean showApplyAll, Callback callback) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            callback.onConflictResolved(ConflictAction.SKIP, false);
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_file_conflict, null);

        TextView filenameText = view.findViewById(R.id.text_conflict_filename);
        TextView incomingText = view.findViewById(R.id.text_incoming_info);
        TextView existingText = view.findViewById(R.id.text_existing_info);
        RadioGroup radioGroup = view.findViewById(R.id.radio_group_conflict);
        RadioButton radioReplace = view.findViewById(R.id.radio_replace);
        RadioButton radioSkip = view.findViewById(R.id.radio_skip);
        MaterialCheckBox applyAllCheckbox = view.findViewById(R.id.checkbox_apply_all);

        filenameText.setText(fileName);
        incomingText.setText(activity.getString(R.string.file_conflict_incoming, incomingTime, formatSize(incomingSize)));
        existingText.setText(activity.getString(R.string.file_conflict_existing, existingTime, formatSize(existingSize)));

        if (showApplyAll) {
            applyAllCheckbox.setVisibility(View.VISIBLE);
        } else {
            applyAllCheckbox.setVisibility(View.GONE);
        }

        // 默认选中"替换"
        radioReplace.setChecked(true);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.file_conflict_title)
                .setView(view)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    ConflictAction action;
                    int checkedId = radioGroup.getCheckedRadioButtonId();
                    if (checkedId == R.id.radio_skip) {
                        action = ConflictAction.SKIP;
                    } else if (checkedId == R.id.radio_keep_both) {
                        action = ConflictAction.KEEP_BOTH;
                    } else {
                        action = ConflictAction.REPLACE;
                    }
                    boolean applyToAll = applyAllCheckbox.isChecked();
                    callback.onConflictResolved(action, applyToAll);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    callback.onConflictResolved(ConflictAction.SKIP, false);
                })
                .setCancelable(false)
                .show();
    }

    static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.US, "%.2f %s",
                size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    static String formatFileTime(long millis) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    static class ConflictResult {
        final ConflictAction action;
        final boolean applyToAll;

        ConflictResult(ConflictAction action, boolean applyToAll) {
            this.action = action;
            this.applyToAll = applyToAll;
        }
    }

    /**
     * 用于 SFTP 批量传输中的冲突解析结果。
     * 包含当前动作和（可能更新的）缓存动作。
     */
    static class ResolvedAction {
        final ConflictAction action;
        final ConflictAction cachedAction; // 非 null 表示后续冲突应使用此动作

        ResolvedAction(ConflictAction action, ConflictAction cachedAction) {
            this.action = action;
            this.cachedAction = cachedAction;
        }
    }
}
