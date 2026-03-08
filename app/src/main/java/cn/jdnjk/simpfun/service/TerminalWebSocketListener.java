package cn.jdnjk.simpfun.service;

import org.json.JSONObject;

public interface TerminalWebSocketListener {
    void onLogReceived(String line);
    void onStatusChanged(String status);
    void onConnected();
    void onDisconnected(String reason);
    void onError(String message);
}
