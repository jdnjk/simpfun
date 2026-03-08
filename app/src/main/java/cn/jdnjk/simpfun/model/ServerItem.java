package cn.jdnjk.simpfun.model;

public class ServerItem {
    private final int id;
    private final String name;
    private final String cpu;
    private final String ram;
    private final String disk;
    private final double cpuCoreCount;
    private ServerStatsSnapshot stats;

    public ServerItem(int id, String name, String cpu, String ram, String disk) {
        this.id = id;
        this.name = name;
        this.cpu = cpu;
        this.ram = ram;
        this.disk = disk;
        this.cpuCoreCount = parseDouble(cpu);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCpu() { return cpu; }
    public String getRam() { return ram; }
    public String getDisk() { return disk; }
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