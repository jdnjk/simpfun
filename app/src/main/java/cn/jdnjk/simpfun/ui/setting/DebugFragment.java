package cn.jdnjk.simpfun.ui.setting;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import cn.jdnjk.simpfun.utils.Feedback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.tencent.bugly.crashreport.CrashReport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SWebView;
import cn.jdnjk.simpfun.notification.DebugNotificationHelper;
import cn.jdnjk.simpfun.notification.DebugNotificationScheduler;
import cn.jdnjk.simpfun.utils.BottomNavScrollHelper;
import cn.jdnjk.simpfun.utils.LogCapture;

public class DebugFragment extends Fragment {

    private static final String SP_DEBUG = "setting_sp";
    private static final String KEY_BUGLY_ENABLED = "bugly_enabled";

    private static final String SP_TOKEN = "token";
    private static final String KEY_TOKEN = "token";

    private NestedScrollView scrollView;
    private final BottomNavScrollHelper.Binding bottomNavBinding = new BottomNavScrollHelper.Binding();
    private final SimpleDateFormat scheduleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "debug-io"));

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> safExportLauncher;
    private Runnable pendingNotificationAction;
    private String pendingExportLog;
    private TextView tvNotificationTime;
    private EditText etNotificationTitle;
    private EditText etNotificationContent;
    private long scheduledTriggerAtMillis = -1L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_debug, container, false);
        scrollView = root.findViewById(R.id.scroll_debug);
        if (getActivity() instanceof cn.jdnjk.simpfun.MainActivity mainActivity) {
            bottomNavBinding.attach(scrollView, mainActivity::onPrimaryScroll);
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        bottomNavBinding.detach(scrollView);
        scrollView = null;
        tvNotificationTime = null;
        etNotificationTitle = null;
        etNotificationContent = null;
        super.onDestroyView();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            Runnable action = pendingNotificationAction;
            pendingNotificationAction = null;
            if (isGranted) {
                if (action != null) {
                    action.run();
                }
            } else if (isAdded()) {
                Feedback.error(getView(), "通知权限未授予，无法发送测试通知");
            }
        });
        safExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
            if (uri == null) {
                return;
            }
            exportToUri(uri);
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        SharedPreferences spDebug = ctx.getSharedPreferences(SP_DEBUG, Context.MODE_PRIVATE);
        SharedPreferences spToken = ctx.getSharedPreferences(SP_TOKEN, Context.MODE_PRIVATE);

        View btnOpenSWebView = view.findViewById(R.id.btn_open_swebview);
        MaterialSwitch swBugly = view.findViewById(R.id.switch_bugly);
        MaterialSwitch swManageScreenshotProtection = view.findViewById(R.id.switch_manage_screenshot_protection);
        EditText etToken = view.findViewById(R.id.et_token);
        EditText etWebUrl = view.findViewById(R.id.et_web_url);
        View btnSave = view.findViewById(R.id.btn_save_token);
        View btnClear = view.findViewById(R.id.btn_clear_token);
        etNotificationTitle = view.findViewById(R.id.et_notification_title);
        etNotificationContent = view.findViewById(R.id.et_notification_content);
        tvNotificationTime = view.findViewById(R.id.tv_notification_time_value);
        View btnPickNotificationTime = view.findViewById(R.id.btn_pick_notification_time);
        View btnSendTestNotification = view.findViewById(R.id.btn_send_test_notification);
        View btnScheduleTestNotification = view.findViewById(R.id.btn_schedule_test_notification);

        DebugNotificationHelper.ensureChannel(ctx);
        etNotificationTitle.setText("测试通知");
        etNotificationContent.setText("这是一条来自 Debug 页的测试通知");
        updateScheduledTimeText();

        boolean buglyEnabled = spDebug.getBoolean(KEY_BUGLY_ENABLED, true);
        swBugly.setChecked(buglyEnabled);
        swManageScreenshotProtection.setChecked(ManageScreenshotProtection.isEnabled(ctx));

        btnOpenSWebView.setOnClickListener(v -> {
            String input = etWebUrl.getText() == null ? null : etWebUrl.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                Feedback.error(getView(), "请输入 URL");
                return;
            }
            if ("simpfun://debug/testJavaCrash".equals(input)) {
                CrashReport.testJavaCrash();
                return;
            }
            if ("simpfun://debug/testNativeCrash".equals(input)) {
                CrashReport.testNativeCrash();
                return;
            }
            String url = input.contains("://") ? input : ("https://" + input);

            Intent intent = new Intent(ctx, SWebView.class);
            intent.putExtra("url", url);
            startActivity(intent);
        });

        View btnExportLog = view.findViewById(R.id.btn_export_log);
        btnExportLog.setOnClickListener(v -> onExportLogClicked());

        swBugly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spDebug.edit().putBoolean(KEY_BUGLY_ENABLED, isChecked).apply();
            if (!isChecked) {
                try { CrashReport.closeBugly(); } catch (Throwable ignored) {}
            }
        });

        swManageScreenshotProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ManageScreenshotProtection.setEnabled(ctx, isChecked);
        });

        String token = spToken.getString(KEY_TOKEN, "");
        etToken.setText(token);

        btnSave.setOnClickListener(v -> {
            String newToken = etToken.getText() == null ? null : etToken.getText().toString();
            if (TextUtils.isEmpty(newToken)) {
                Feedback.error(getView(), "Token 为空");
                return;
            }
            spToken.edit().putString(KEY_TOKEN, newToken).apply();
            Feedback.info(getView(), "Token 已保存");
        });

        btnClear.setOnClickListener(v -> {
            spToken.edit().remove(KEY_TOKEN).apply();
            etToken.setText("");
            Feedback.info(getView(), "Token 已清空");
        });

        btnPickNotificationTime.setOnClickListener(v -> showDateTimePicker());

        btnSendTestNotification.setOnClickListener(v -> withNotificationPermission(() -> {
            DebugNotificationHelper.showTestNotification(ctx, getNotificationTitle(), getNotificationContent());
            Feedback.info(getView(), "测试通知已发送");
        }));

        btnScheduleTestNotification.setOnClickListener(v -> withNotificationPermission(() -> {
            if (scheduledTriggerAtMillis <= System.currentTimeMillis()) {
                Feedback.error(getView(), "请先选择未来时间");
                return;
            }
            DebugNotificationScheduler.ScheduleResult result = DebugNotificationScheduler.scheduleTestNotification(
                    ctx,
                    scheduledTriggerAtMillis,
                    getNotificationTitle(),
                    getNotificationContent()
            );
            String timeText = scheduleFormat.format(new Date(scheduledTriggerAtMillis));
            String resultText = result.isExact()
                    ? "已设置测试通知：" + timeText
                    : "系统未授予精确定时，已设置近似提醒：" + timeText;
            Feedback.info(getView(), resultText);
        }));
    }

    private void withNotificationPermission(@NonNull Runnable action) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingNotificationAction = action;
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void onExportLogClicked() {
        View root = getView();
        if (root == null) return;
        Feedback.info(root, "正在准备日志…");
        ioExecutor.execute(() -> {
            String content;
            try {
                content = LogCapture.read();
            } catch (Exception e) {
                content = "日志读取失败: " + e.getMessage();
            }
            final String logText = content;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                String fileName = "debug.log";
                safExportLauncher.launch(fileName);
                this.pendingExportLog = logText;
            });
        });
    }

    private void exportToUri(Uri uri) {
        View root = getView();
        if (root == null) return;
        String logText = pendingExportLog;
        pendingExportLog = null;
        if (logText == null) return;
        final Context ctx = getContext();
        if (ctx == null) return;

        ioExecutor.execute(() -> {
            final boolean ok = writeToUri(ctx, uri, logText);
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (ok) {
                    Feedback.info(getView(), "日志已导出到所选位置");
                } else {
                    showFallbackExportDialog(logText);
                }
            });
        });
    }

    private static boolean writeToUri(Context ctx, Uri uri, String content) {
        try {
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) return false;
                os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            Log.d("DebugExport", "SAF write failed", e);
            return false;
        }
    }

    private void showFallbackExportDialog(String logText) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("保存日志失败")
                .setMessage("无法保存到所选位置，是否导出到应用目录？\n(Android/data/应用包名/files/log)")
                .setPositiveButton("导出", (dialog, which) -> exportToAppLogDir(logText))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportToAppLogDir(String logText) {
        Context ctx = getContext();
        if (ctx == null) return;
        final Context appCtx = ctx.getApplicationContext();
        ioExecutor.execute(() -> {
            final String path = writeToAppDir(appCtx, logText);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (path == null) {
                    Feedback.error(getView(), "导出失败，请重试");
                } else {
                    Feedback.info(getView(), "已导出到 " + path);
                }
            });
        });
    }

    private static String writeToAppDir(Context ctx, String content) {
        try {
            File dir = new File(ctx.getFilesDir(), "log");
            if (dir.exists() || dir.mkdirs()) {
                File out = new File(dir, "debug.log");
                try (OutputStream os = new FileOutputStream(out)) {
                    os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return out.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.d("DebugExport", "app dir write failed", e);
        }
        return null;
    }

    private void showDateTimePicker() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Calendar base = Calendar.getInstance();
        if (scheduledTriggerAtMillis > System.currentTimeMillis()) {
            base.setTimeInMillis(scheduledTriggerAtMillis);
        } else {
            base.add(Calendar.MINUTE, 1);
        }
        new DatePickerDialog(context, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            picked.set(Calendar.SECOND, 0);
            picked.set(Calendar.MILLISECOND, 0);
            new TimePickerDialog(context, (timeView, hourOfDay, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                picked.set(Calendar.MINUTE, minute);
                scheduledTriggerAtMillis = picked.getTimeInMillis();
                updateScheduledTimeText();
            }, base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), true).show();
        }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateScheduledTimeText() {
        if (tvNotificationTime == null) {
            return;
        }
        if (scheduledTriggerAtMillis <= System.currentTimeMillis()) {
            tvNotificationTime.setText("未设置");
            return;
        }
        tvNotificationTime.setText(scheduleFormat.format(new Date(scheduledTriggerAtMillis)));
    }

    @NonNull
    private String getNotificationTitle() {
        String value = etNotificationTitle != null && etNotificationTitle.getText() != null
                ? etNotificationTitle.getText().toString().trim()
                : "";
        return value.isEmpty() ? "测试通知" : value;
    }

    @NonNull
    private String getNotificationContent() {
        String value = etNotificationContent != null && etNotificationContent.getText() != null
                ? etNotificationContent.getText().toString().trim()
                : "";
        return value.isEmpty() ? "这是一条来自 Debug 页的测试通知" : value;
    }
}
