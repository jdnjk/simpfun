package cn.jdnjk.simpfun.model;

public class InstanceStatPoint {
    private final long uptimeSeconds;
    private final long inBytes;
    private final long outBytes;
    private final long outRemainBytes;
    private final int cpuPercent;
    private final long memUsedBytes;
    private final long createTimeTimestampSeconds;

    public InstanceStatPoint(long uptimeSeconds,
                             long inBytes,
                             long outBytes,
                             long outRemainBytes,
                             int cpuPercent,
                             long memUsedBytes,
                             long createTimeTimestampSeconds) {
        this.uptimeSeconds = uptimeSeconds;
        this.inBytes = inBytes;
        this.outBytes = outBytes;
        this.outRemainBytes = outRemainBytes;
        this.cpuPercent = cpuPercent;
        this.memUsedBytes = memUsedBytes;
        this.createTimeTimestampSeconds = createTimeTimestampSeconds;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public long getInBytes() {
        return inBytes;
    }

    public long getOutBytes() {
        return outBytes;
    }

    public long getOutRemainBytes() {
        return outRemainBytes;
    }

    public int getCpuPercent() {
        return cpuPercent;
    }

    public long getMemUsedBytes() {
        return memUsedBytes;
    }

    public long getCreateTimeTimestampSeconds() {
        return createTimeTimestampSeconds;
    }
}
