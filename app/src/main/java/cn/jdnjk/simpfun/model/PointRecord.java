package cn.jdnjk.simpfun.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PointRecord {
    private String description;
    private String amount;
    private String time;
    private int left;

    public PointRecord(String description, String amount, String time) {
        this(description, amount, time, 0);
    }

    public PointRecord(String description, String amount, String time, int left) {
        this.description = description;
        this.amount = amount;
        this.time = time;
        this.left = left;
    }

    public String getDescription() {
        return description;
    }

    public String getAmount() {
        return amount;
    }

    public String getTime() {
        return time;
    }

    public int getLeft() {
        return left;
    }

    public static String formatTime(String rawTime) {
        if (rawTime == null) return "";
        String t = rawTime.trim();
        if (t.isEmpty()) return "";

        // Most backend times are ISO-8601 like: 2026-01-30T16:09:23.481602Z
        try {
            Instant instant = Instant.parse(t);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            return fmt.format(instant);
        } catch (Throwable ignore) {
            // Try a quick fallback for common variants (no Z, or using space)
            // If we can't parse, just return original.
            return t.replace('T', ' ').replace("Z", "");
        }
    }
}
