package cn.jdnjk.simpfun.model;

public class PlanItem {
    private final int id;
    private final String command;
    private final String scheduledTime;
    private final boolean repeated;
    private final int interval;

    public PlanItem(int id, String command, String scheduledTime, boolean repeated, int interval) {
        this.id = id;
        this.command = command;
        this.scheduledTime = scheduledTime;
        this.repeated = repeated;
        this.interval = interval;
    }

    public int getId() { return id; }
    public String getCommand() { return command; }
    public String getScheduledTime() { return scheduledTime; }
    public boolean isRepeated() { return repeated; }
    public int getInterval() { return interval; }
}

