package cn.jdnjk.simpfun.model;

public class ServerStatsSnapshot {
    private final double cpuAbsolute;
    private final long memoryBytes;
    private final long memoryLimitBytes;
    private final long rxBytes;
    private final long txBytes;
    private final String state;
    private final long uptimeMillis;
    private final long downloadBytesPerSecond;
    private final long uploadBytesPerSecond;

    public ServerStatsSnapshot(double cpuAbsolute, long memoryBytes, long memoryLimitBytes, long rxBytes, long txBytes,
                               String state, long uptimeMillis, long downloadBytesPerSecond, long uploadBytesPerSecond) {
        this.cpuAbsolute = cpuAbsolute;
        this.memoryBytes = memoryBytes;
        this.memoryLimitBytes = memoryLimitBytes;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
        this.state = state;
        this.uptimeMillis = uptimeMillis;
        this.downloadBytesPerSecond = downloadBytesPerSecond;
        this.uploadBytesPerSecond = uploadBytesPerSecond;
    }

    public double getCpuAbsolute() { return cpuAbsolute; }
    public long getMemoryBytes() { return memoryBytes; }
    public long getMemoryLimitBytes() { return memoryLimitBytes; }
    public long getRxBytes() { return rxBytes; }
    public long getTxBytes() { return txBytes; }
    public String getState() { return state; }
    public long getUptimeMillis() { return uptimeMillis; }
    public long getDownloadBytesPerSecond() { return downloadBytesPerSecond; }
    public long getUploadBytesPerSecond() { return uploadBytesPerSecond; }
}
