package cn.jdnjk.simpfun.model;

import java.util.List;

public class StatusMonitorGroup {
    private final String name;
    private final List<StatusMonitor> monitors;

    public StatusMonitorGroup(String name, List<StatusMonitor> monitors) {
        this.name = name;
        this.monitors = monitors;
    }

    public String getName() {
        return name;
    }

    public List<StatusMonitor> getMonitors() {
        return monitors;
    }
}
