package cn.jdnjk.simpfun.model;

public class ServerItem {
    private final int id;
    private final String name;
    private final String cpu;
    private final String ram;
    private final String disk;

    public ServerItem(int id, String name, String cpu, String ram, String disk) {
        this.id = id;
        this.name = name;
        this.cpu = cpu;
        this.ram = ram;
        this.disk = disk;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCpu() { return cpu; }
    public String getRam() { return ram; }
    public String getDisk() { return disk; }
}