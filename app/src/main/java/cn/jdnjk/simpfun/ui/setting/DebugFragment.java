package cn.jdnjk.simpfun.ui.setting;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.tencent.bugly.crashreport.CrashReport;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SWebView;
import cn.jdnjk.simpfun.notification.DebugNotificationHelper;
import cn.jdnjk.simpfun.notification.DebugNotificationScheduler;
import cn.jdnjk.simpfun.utils.BottomNavScrollHelper;

public class DebugFragment extends Fragment {

    private static final String SP_DEBUG = "debug_settings";
    private static final String KEY_BUGLY_ENABLED = "bugly_enabled";

    private static final String SP_TOKEN = "token";
    private static final String KEY_TOKEN = "token";

    private NestedScrollView scrollView;
    private final BottomNavScrollHelper.Binding bottomNavBinding = new BottomNavScrollHelper.Binding();
    private final SimpleDateFormat scheduleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private Runnable pendingNotificationAction;
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
                Toast.makeText(requireContext(), "通知权限未授予，无法发送测试通知", Toast.LENGTH_LONG).show();
            }
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

        btnOpenSWebView.setOnClickListener(v -> {
            String input = etWebUrl.getText() == null ? null : etWebUrl.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(ctx, "请输入 URL", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = input.contains("://") ? input : ("https://" + input);

            Intent intent = new Intent(ctx, SWebView.class);
            intent.putExtra("url", url);
            startActivity(intent);
        });

        swBugly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spDebug.edit().putBoolean(KEY_BUGLY_ENABLED, isChecked).apply();
            if (!isChecked) {
                try { CrashReport.closeBugly(); } catch (Throwable ignored) {}
            }
        });

        String token = spToken.getString(KEY_TOKEN, "");
        etToken.setText(token);

        btnSave.setOnClickListener(v -> {
            String newToken = etToken.getText() == null ? null : etToken.getText().toString();
            if (TextUtils.isEmpty(newToken)) {
                Toast.makeText(ctx, "Token 为空", Toast.LENGTH_SHORT).show();
                return;
            }
            spToken.edit().putString(KEY_TOKEN, newToken).apply();
            Toast.makeText(ctx, "Token 已保存", Toast.LENGTH_SHORT).show();
        });

        btnClear.setOnClickListener(v -> {
            spToken.edit().remove(KEY_TOKEN).apply();
            etToken.setText("");
            Toast.makeText(ctx, "Token 已清空", Toast.LENGTH_SHORT).show();
        });

        btnPickNotificationTime.setOnClickListener(v -> showDateTimePicker());

        btnSendTestNotification.setOnClickListener(v -> withNotificationPermission(() -> {
            DebugNotificationHelper.showTestNotification(ctx, getNotificationTitle(), getNotificationContent());
            Toast.makeText(ctx, "测试通知已发送", Toast.LENGTH_SHORT).show();
        }));

        btnScheduleTestNotification.setOnClickListener(v -> withNotificationPermission(() -> {
            if (scheduledTriggerAtMillis <= System.currentTimeMillis()) {
                Toast.makeText(ctx, "请先选择未来时间", Toast.LENGTH_SHORT).show();
                return;
            }
            DebugNotificationScheduler.ScheduleResult result = DebugNotificationScheduler.scheduleTestNotification(
                    ctx,
                    scheduledTriggerAtMillis,
                    getNotificationTitle(),
                    getNotificationContent()
            );
            String timeText = scheduleFormat.format(new Date(scheduledTriggerAtMillis));
            String toastText = result.isExact()
                    ? "已设置测试通知：" + timeText
                    : "系统未授予精确定时，已设置近似提醒：" + timeText;
            Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show();
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
