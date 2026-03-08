package cn.jdnjk.simpfun.service;

import cn.jdnjk.simpfun.model.ServerStatsSnapshot;

public interface ServerStatsListener {
    void onStatsUpdated(int deviceId, ServerStatsSnapshot stats);
    void onStatsDisconnected(int deviceId, String reason);
}
