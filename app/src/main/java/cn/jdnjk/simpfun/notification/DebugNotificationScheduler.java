package cn.jdnjk.simpfun.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DebugNotificationScheduler {

    private DebugNotificationScheduler() {}

    @NonNull
    public static ScheduleResult scheduleTestNotification(@NonNull Context context, long triggerAtMillis, @Nullable String title, @Nullable String content) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return new ScheduleResult(false);
        }

        int requestCode = (int) (System.currentTimeMillis() & 0x7fffffff);
        Intent intent = new Intent(appContext, TestNotificationReceiver.class);
        intent.setAction(DebugNotificationHelper.ACTION_SHOW_TEST_NOTIFICATION);
        intent.putExtra(DebugNotificationHelper.EXTRA_TITLE, title);
        intent.putExtra(DebugNotificationHelper.EXTRA_CONTENT, content);
        intent.putExtra(DebugNotificationHelper.EXTRA_NOTIFICATION_ID, requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        boolean exact = scheduleAlarm(alarmManager, triggerAtMillis, pendingIntent);
        return new ScheduleResult(exact);
    }

    private static boolean scheduleAlarm(@NonNull AlarmManager alarmManager, long triggerAtMillis, @NonNull PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    return true;
                }
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                return false;
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            return true;
        } catch (SecurityException ignored) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            return false;
        }
    }

    public static final class ScheduleResult {
        private final boolean exact;

        public ScheduleResult(boolean exact) {
            this.exact = exact;
        }

        public boolean isExact() {
            return exact;
        }
    }
}

