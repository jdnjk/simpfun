package cn.jdnjk.simpfun.model;

public class BackupItem {
    private final int id;
    private final int status;
    private final long size;
    private final String validTime;
    private final String tag;
    private final boolean isWindows;

    public BackupItem(int id, int status, long size, String validTime, String tag, boolean isWindows) {
        this.id = id;
        this.status = status;
        this.size = size;
        this.validTime = validTime;
        this.tag = tag;
        this.isWindows = isWindows;
    }

    public int getId() {
        return id;
    }

    public int getStatus() {
        return status;
    }

    public long getSize() {
        return size;
    }

    public String getValidTime() {
        return validTime;
    }

    public String getTag() {
        return tag;
    }

    public boolean isWindows() {
        return isWindows;
    }
}

