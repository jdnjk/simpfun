package cn.jdnjk.simpfun.model;

public class ServerItem {
    private final int id;
    private final String name;
    private final String cpu;
    private final String ram;
    private final String disk;
    private final boolean supportInstance;
    private final String supportInfo;
    private final double cpuCoreCount;
    private ServerStatsSnapshot stats;

    public ServerItem(int id, String name, String cpu, String ram, String disk) {
        this(id, name, cpu, ram, disk, false, null);
    }

    private ServerItem(int id, String name, String cpu, String ram, String disk, boolean supportInstance, String supportInfo) {
        this.id = id;
        this.name = name;
        this.cpu = cpu;
        this.ram = ram;
        this.disk = disk;
        this.supportInstance = supportInstance;
        this.supportInfo = supportInfo;
        this.cpuCoreCount = parseDouble(cpu);
    }

    public static ServerItem supportInstance(int id, String name, String supportInfo) {
        return new ServerItem(id, name, "0", "0", "0", true, supportInfo);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCpu() { return cpu; }
    public String getRam() { return ram; }
    public String getDisk() { return disk; }
    public boolean isSupportInstance() { return supportInstance; }
    public String getSupportInfo() { return supportInfo; }
    public double getCpuCoreCount() { return cpuCoreCount; }
    public double getCpuLimit() { return cpuCoreCount * 100d; }
    public ServerStatsSnapshot getStats() { return stats; }
    public void setStats(ServerStatsSnapshot stats) { this.stats = stats; }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0d;
        }
    }
}