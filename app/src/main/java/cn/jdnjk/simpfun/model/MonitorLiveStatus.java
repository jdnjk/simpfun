package cn.jdnjk.simpfun.model;

import java.util.List;

public class MonitorLiveStatus {
    private final List<HeartbeatPoint> heartbeats;
    private final double uptime24h;
    public MonitorLiveStatus(List<HeartbeatPoint> heartbeats, double uptime24h) {
        this.heartbeats = heartbeats;
        this.uptime24h = uptime24h;
    }

    public List<HeartbeatPoint> getHeartbeats() {
        return heartbeats;
    }

    public double getUptime24h() {
        return uptime24h;
    }

    public boolean hasUptime() {
        return uptime24h >= 0;
    }

    public boolean isOnline() {
        return !heartbeats.isEmpty() && heartbeats.get(heartbeats.size() - 1).isUp();
    }

    public boolean isPending() {
        return !heartbeats.isEmpty() && heartbeats.get(heartbeats.size() - 1).isPending();
    }

}
