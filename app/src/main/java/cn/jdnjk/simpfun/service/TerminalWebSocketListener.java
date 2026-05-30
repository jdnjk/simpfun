package cn.jdnjk.simpfun.service;

public interface TerminalWebSocketListener {
    void onLogReceived(String line);
    void onConsoleCleared();
    void onStatusChanged(String status);
    void onConnected();
    void onDisconnected(String reason);
    void onError(String message);
}
