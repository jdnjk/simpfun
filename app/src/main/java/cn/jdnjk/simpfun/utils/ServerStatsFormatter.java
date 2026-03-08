package cn.jdnjk.simpfun.utils;

import java.util.Locale;

public final class ServerStatsFormatter {
    private ServerStatsFormatter() {}

    public static int toCpuPercent(double cpuAbsolute, double cpuLimit) {
        if (cpuLimit <= 0) return 0;
        return clampToPercent((cpuAbsolute / cpuLimit) * 100d);
    }

    public static int toMemoryPercent(long usedBytes, long limitBytes) {
        if (limitBytes <= 0) return 0;
        return clampToPercent((usedBytes * 100d) / limitBytes);
    }

    public static String formatPercentText(int percent) {
        return percent + "%";
    }

    public static String formatSpeed(long bytesPerSecond) {
        double value = bytesPerSecond;
        String unit = "B/s";
        if (value >= 1024) {
            value /= 1024d;
            unit = "KB/s";
        }
        if (value >= 1024) {
            value /= 1024d;
            unit = "MB/s";
        }
        if ("B/s".equals(unit)) {
            return String.format(Locale.US, "%d %s", bytesPerSecond, unit);
        }
        return String.format(Locale.US, "%.1f %s", value, unit);
    }

    public static String formatUptime(long uptimeMillis) {
        long totalSeconds = Math.max(0L, uptimeMillis / 1000L);
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (days > 0) {
            return String.format(Locale.US, "%d days, %02d:%02d", days, hours, minutes);
        }
        return String.format(Locale.US, "%02d:%02d", hours, minutes);
    }

    private static int clampToPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return Math.max(0, Math.min(100, (int) Math.round(value)));
    }
}
