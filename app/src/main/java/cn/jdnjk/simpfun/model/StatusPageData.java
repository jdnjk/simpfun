package cn.jdnjk.simpfun.model;

import java.util.List;

public class StatusPageData {
    private final String title;
    private final String description;
    private final List<StatusMonitorGroup> groups;
    private final int monitorCount;
    private final boolean hasIncident;

    public StatusPageData(String title, String description, List<StatusMonitorGroup> groups,
                          int monitorCount, boolean hasIncident) {
        this.title = title;
        this.description = description;
        this.groups = groups;
        this.monitorCount = monitorCount;
        this.hasIncident = hasIncident;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<StatusMonitorGroup> getGroups() {
        return groups;
    }

    public int getMonitorCount() {
        return monitorCount;
    }

    public boolean hasIncident() {
        return hasIncident;
    }
}
