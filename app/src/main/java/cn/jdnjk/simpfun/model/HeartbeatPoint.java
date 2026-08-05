package cn.jdnjk.simpfun.model;

public class HeartbeatPoint {
    private final long timeMillis;
    private final int status;
    private final long ping;
    private final String msg;

    public HeartbeatPoint(long timeMillis, int status, long ping, String msg) {
        this.timeMillis = timeMillis;
        this.status = status;
        this.ping = ping;
        this.msg = msg;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public int getStatus() {
        return status;
    }

    public boolean isUp() {
        return status == 1;
    }

    public boolean isPending() {
        return status == 2;
    }

    public long getPing() {
        return ping;
    }

    public String getMsg() {
        return msg;
    }
}
