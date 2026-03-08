package cn.jdnjk.simpfun.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import cn.jdnjk.simpfun.api.ApiClient;
import cn.jdnjk.simpfun.api.ins.TermApi;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class TerminalWebSocketManager {
    private static final String TAG = "TermWSManager";
    private static TerminalWebSocketManager instance;

    private WebSocket webSocket;
    private final List<TerminalWebSocketListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> logBuffer = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_BUFFER_SIZE = 500;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context appContext;
    private int currentDeviceId = -1;
    private String requestToken;
    private boolean isConnecting = false;
    private boolean isManualClose = false;
    private boolean isLogsRequested = false;

    private TerminalWebSocketManager() {}

    public static synchronized TerminalWebSocketManager getInstance() {
        if (instance == null) {
            instance = new TerminalWebSocketManager();
        }
        return instance;
    }

    public void addListener(TerminalWebSocketListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            // Send existing buffer to new listener
            synchronized (logBuffer) {
                for (String line : logBuffer) {
                    listener.onLogReceived(line);
                }
            }
        }
    }

    public void removeListener(TerminalWebSocketListener listener) {
        listeners.remove(listener);
    }

    public void connect(Context context, int deviceId) {
        connect(context, deviceId, false);
    }

    public void connect(Context context, int deviceId, boolean requestLogs) {
        this.appContext = context.getApplicationContext();
        this.isLogsRequested = requestLogs;
        if (currentDeviceId == deviceId && webSocket != null) {
            // Already connected to this device
            // If logs were not requested before but are now, request them
            if (requestLogs) {
                sendLogMessage();
            }
            notifyConnected();
            return;
        }

        if (webSocket != null) {
            disconnect();
        }

        this.currentDeviceId = deviceId;
        this.isManualClose = false;
        fetchWebSocketInfo(context.getApplicationContext(), deviceId);
    }

    private void fetchWebSocketInfo(Context context, int deviceId) {
        if (isConnecting) return;
        isConnecting = true;

        new TermApi().getWebSocketInfo(context, deviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                isConnecting = false;
                try {
                    requestToken = data.getString("token");
                    String socketUrl = data.getString("socket");
                    openWebSocket(socketUrl);
                } catch (Exception e) {
                    notifyError("解析连接信息失败: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                isConnecting = false;
                notifyError("获取终端信息失败: " + errorMsg);
            }
        });
    }

    private void openWebSocket(String socketUrl) {
        Request request = new Request.Builder().url(socketUrl).build();
        OkHttpClient client = ApiClient.getInstance().getClient();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.i(TAG, "WS Open");
                sendAuthMessage();
                if (isLogsRequested) {
                    sendLogMessage();
                }
                mainHandler.post(() -> notifyConnected());
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                processMessage(text);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "WS Failure: " + t.getMessage());
                TerminalWebSocketManager.this.webSocket = null;
                if (!isManualClose) {
                    mainHandler.post(() -> notifyDisconnected("连接错误: " + t.getMessage()));
                }
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.i(TAG, "WS Closing: " + reason);
                TerminalWebSocketManager.this.webSocket = null;
                if (!isManualClose) {
                    mainHandler.post(() -> notifyDisconnected(reason));
                }
            }
        });
    }

    private void processMessage(String text) {
        try {
            JSONObject msg = new JSONObject(text);
            String event = msg.optString("event");

            switch (event) {
                case "console output" -> {
                    JSONArray args = msg.getJSONArray("args");
                    for (int i = 0; i < args.length(); i++) {
                        String line = sanitizeLog(args.getString(i));
                        addToBuffer(line);
                        mainHandler.post(() -> notifyLogReceived(line));
                    }
                }
                case "status" -> {
                    JSONArray args = msg.optJSONArray("args");
                    if (args != null && args.length() > 0) {
                        String status = args.getString(0);
                        mainHandler.post(() -> notifyStatusChanged(status));
                    }
                }
                case "token expiring" -> refreshToken();
                case "jwt error" -> {
                    JSONArray args = msg.optJSONArray("args");
                    if (args != null && args.length() > 0) {
                        String error = args.optString(0, "");
                        if (error.contains("exp claim is invalid")) {
                            refreshToken();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Process message failed: " + e.getMessage());
        }
    }

    private String sanitizeLog(String line) {
        return line.replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Checking server disk space usage, this could take a few seconds...\u001B[0m",
                        "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 正在检查磁盘占用情况，请稍等...\u001B[0m")
                .replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Updating process configuration files...\u001B[0m",
                        "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 已自动更新服务器端口等信息！\u001B[0m")
                .replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Pulling Docker container image, this could take a few minutes to complete...\u001B[0m",
                        "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 正在拉取Docker镜像，请稍等...\u001B[0m")
                .replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Finished pulling Docker container image\u001B[0m",
                        "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 已完成Docker镜像拉取！\u001B[0m")
                .replaceAll("\u001b\\[\\?1h\u001b=", "")
                .replaceAll("\u001b\\[\\?2004h", "")
                .replaceAll("\u001b\\[K", "")
                .replaceAll(">\\r\\n", "")
                .replaceAll(">....", "");
    }

    private void refreshToken() {
        if (currentDeviceId == -1 || appContext == null) return;
        new TermApi().getWebSocketInfo(appContext, currentDeviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    requestToken = data.getString("token");
                    sendAuthMessage();
                } catch (Exception ignored) {}
            }
            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "Token refresh failed: " + errorMsg);
            }
        });
    }

    public void sendCommand(String command) {
        if (webSocket == null) return;
        try {
            JSONObject cmdMsg = new JSONObject();
            cmdMsg.put("event", "send command");
            JSONArray args = new JSONArray();
            args.put(command);
            cmdMsg.put("args", args);
            webSocket.send(cmdMsg.toString());
        } catch (Exception e) {
            Log.w(TAG, "Send command failed: " + e.getMessage());
        }
    }

    private void sendAuthMessage() {
        if (webSocket == null) return;
        try {
            JSONObject authMsg = new JSONObject();
            authMsg.put("event", "auth");
            JSONArray args = new JSONArray();
            args.put(requestToken);
            authMsg.put("args", args);
            webSocket.send(authMsg.toString());
        } catch (Exception ignored) {}
    }

    private void sendLogMessage() {
        if (webSocket == null) return;
        try {
            JSONObject logMsg = new JSONObject();
            logMsg.put("event", "send logs");
            logMsg.put("args", new JSONArray());
            webSocket.send(logMsg.toString());
        } catch (Exception ignored) {}
    }

    public void requestLogs() {
        this.isLogsRequested = true;
        sendLogMessage();
    }

    private void addToBuffer(String line) {
        synchronized (logBuffer) {
            logBuffer.add(line);
            if (logBuffer.size() > MAX_BUFFER_SIZE) {
                logBuffer.remove(0);
            }
        }
    }

    public void disconnect() {
        isManualClose = true;
        isLogsRequested = false;
        if (webSocket != null) {
            webSocket.close(1000, "Normal Closure");
            webSocket = null;
        }
        currentDeviceId = -1;
        logBuffer.clear();
    }

    private void notifyLogReceived(String line) {
        for (TerminalWebSocketListener l : listeners) l.onLogReceived(line);
    }

    private void notifyStatusChanged(String status) {
        for (TerminalWebSocketListener l : listeners) l.onStatusChanged(status);
    }

    private void notifyConnected() {
        for (TerminalWebSocketListener l : listeners) l.onConnected();
    }

    private void notifyDisconnected(String reason) {
        for (TerminalWebSocketListener l : listeners) l.onDisconnected(reason);
    }

    private void notifyError(String message) {
        for (TerminalWebSocketListener l : listeners) l.onError(message);
    }

    public boolean isConnected() {
        return webSocket != null;
    }
}
