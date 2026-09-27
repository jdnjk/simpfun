package cn.jdnjk.simpfun.model;

import org.json.JSONObject;

public class DevBackupItem {
    private final int id;
    private final int status;
    private final long size;
    private final String validTime;
    private final String tag;
    private final boolean isWindows;

    public DevBackupItem(int id, int status, long size, String validTime, String tag, boolean isWindows) {
        this.id = id;
        this.status = status;
        this.size = size;
        this.validTime = validTime == null ? "" : validTime;
        this.tag = tag == null ? "" : tag;
        this.isWindows = isWindows;
    }

    public static DevBackupItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new DevBackupItem(
                obj.optInt("id", -1),
                obj.optInt("status", 0),
                obj.optLong("size", 0),
                obj.optString("valid_time", ""),
                obj.optString("tag", ""),
                obj.optBoolean("is_windows", false));
    }

    public int getId() { return id; }
    public int getStatus() { return status; }
    public long getSize() { return size; }
    public String getValidTime() { return validTime; }
    public String getTag() { return tag; }
    public boolean isWindows() { return isWindows; }

    public String getFormattedSize() {
        if (size <= 0) return "0 B";
        double kb = size / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }
}
