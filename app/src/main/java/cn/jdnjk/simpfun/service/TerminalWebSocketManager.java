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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String CLEAR_SCREEN_SEQUENCE = "[H[2J[3J";
    private static TerminalWebSocketManager instance;

    private WebSocket webSocket;
    private final List<TerminalWebSocketListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<TerminalWebSocketListener, Integer> listenerDeviceIds = new ConcurrentHashMap<>();
    private final List<String> logBuffer = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_BUFFER_SIZE = 500;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context appContext;
    private int currentDeviceId = -1;
    private int connectionGeneration = 0;
    private String requestToken;
    private boolean isConnecting = false;
    private boolean isManualClose = false;
    private boolean isLogsRequested = false;
    private volatile boolean isSocketOpen = false;

    private TerminalWebSocketManager() {}

    public static synchronized TerminalWebSocketManager getInstance() {
        if (instance == null) {
            instance = new TerminalWebSocketManager();
        }
        return instance;
    }

    public void addListener(TerminalWebSocketListener listener) {
        addListener(listener, currentDeviceId);
    }

    public void addListener(TerminalWebSocketListener listener, int deviceId) {
        listenerDeviceIds.put(listener, deviceId);
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        if (currentDeviceId != deviceId) {
            return;
        }
        synchronized (logBuffer) {
            for (String line : logBuffer) {
                listener.onLogReceived(line);
            }
        }
    }

    public void removeListener(TerminalWebSocketListener listener) {
        listeners.remove(listener);
        listenerDeviceIds.remove(listener);
    }

    public void connect(Context context, int deviceId) {
        connect(context, deviceId, false);
    }

    public void connect(Context context, int deviceId, boolean requestLogs) {
        appContext = context.getApplicationContext();
        if (currentDeviceId == deviceId && webSocket != null) {
            boolean shouldRequestLogs = requestLogs && !isLogsRequested;
            isLogsRequested = isLogsRequested || requestLogs;
            if (isSocketOpen) {
                if (shouldRequestLogs) {
                    sendLogMessage();
                }
                notifyConnected();
            }
            return;
        }

        if (currentDeviceId == deviceId && isConnecting) {
            isLogsRequested = isLogsRequested || requestLogs;
            return;
        }

        connectionGeneration++;
        closeCurrentSocket();
        clearBuffer();
        currentDeviceId = deviceId;
        isLogsRequested = requestLogs;
        isManualClose = false;
        isSocketOpen = false;
        isConnecting = false;
        fetchWebSocketInfo(appContext, deviceId, connectionGeneration);
    }

    private void fetchWebSocketInfo(Context context, int deviceId, int generation) {
        isConnecting = true;
        new TermApi().getWebSocketInfo(context, deviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                isConnecting = false;
                try {
                    requestToken = data.getString("token");
                    String socketUrl = data.getString("socket");
                    openWebSocket(socketUrl, deviceId, generation);
                } catch (Exception e) {
                    notifyError("解析连接信息失败: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                isConnecting = false;
                notifyError("获取终端信息失败: " + errorMsg);
            }
        });
    }

    private void openWebSocket(String socketUrl, int deviceId, int generation) {
        isSocketOpen = false;
        Request request = new Request.Builder().url(socketUrl).build();
        OkHttpClient client = ApiClient.getInstance().getClient();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket socket, @NonNull Response response) {
                if (!isCurrentConnection(deviceId, generation)) {
                    socket.close(1000, "Stale Connection");
                    return;
                }
                Log.i(TAG, "WS Open for device " + deviceId);
                isSocketOpen = true;
                sendAuthMessage();
            }

            @Override
            public void onMessage(@NonNull WebSocket socket, @NonNull String text) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                Log.d(TAG, "WS Incoming: " + text);
                processMessage(text);
            }

            @Override
            public void onFailure(@NonNull WebSocket socket, @NonNull Throwable t, @Nullable Response response) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                Log.e(TAG, "WS Failure: " + t.getMessage());
                isSocketOpen = false;
                TerminalWebSocketManager.this.webSocket = null;
                if (!isManualClose) {
                    mainHandler.post(() -> notifyDisconnected("连接错误: " + t.getMessage()));
                }
            }

            @Override
            public void onClosing(@NonNull WebSocket socket, int code, @NonNull String reason) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                Log.i(TAG, "WS Closing: " + reason);
                isSocketOpen = false;
                TerminalWebSocketManager.this.webSocket = null;
                if (!isManualClose) {
                    mainHandler.post(() -> notifyDisconnected(reason));
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket socket, int code, @NonNull String reason) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                isSocketOpen = false;
                TerminalWebSocketManager.this.webSocket = null;
            }
        });
    }

    private void processMessage(String text) {
        try {
            JSONObject msg = new JSONObject(text);
            String event = msg.optString("event");
            Log.d(TAG, "WS Event: " + event + " | payload=" + text);

            switch (event) {
                case "auth success" -> {
                    if (isLogsRequested) {
                        sendLogMessage();
                    }
                    mainHandler.post(this::notifyConnected);
                }
                case "console output" -> {
                    JSONArray args = msg.getJSONArray("args");
                    for (int i = 0; i < args.length(); i++) {
                        String rawLine = args.getString(i);
                        if (containsClearScreenSequence(rawLine)) {
                            Log.d(TAG, "WS Console: clear screen sequence detected");
                            clearBuffer();
                            mainHandler.post(this::notifyConsoleCleared);
                        }

                        String line = sanitizeLog(rawLine);
                        if (line.isEmpty()) {
                            continue;
                        }
                        Log.d(TAG, "WS Console: " + line);
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
        return line.replace("[33m[1m[Pterodactyl Daemon]:[39m Checking server disk space usage, this could take a few seconds...[0m",
                        "[33m[1m[简幻欢]:[39m 正在检查磁盘占用情况，请稍等...[0m")
                .replace("[33m[1m[Pterodactyl Daemon]:[39m Updating process configuration files...[0m",
                        "[33m[1m[简幻欢]:[39m 已自动更新服务器端口等信息！[0m")
                .replace("[33m[1m[Pterodactyl Daemon]:[39m Pulling Docker container image, this could take a few minutes to complete...[0m",
                        "[33m[1m[简幻欢]:[39m 正在拉取Docker镜像，请稍等...[0m")
                .replace("[33m[1m[Pterodactyl Daemon]:[39m Finished pulling Docker container image[0m",
                        "[33m[1m[简幻欢]:[39m 已完成Docker镜像拉取！[0m")
                .replaceAll("\\[\\?1[hl][=>]", "")
                .replaceAll("\\[\\?2004[hl]", "")
                .replaceAll("\\[[0-9;?]*[JK]", "");
    }

    private boolean containsClearScreenSequence(String line) {
        return line != null && (line.contains(CLEAR_SCREEN_SEQUENCE)
                || line.contains("[2J")
                || line.contains("[3J"));
    }

    private void clearBuffer() {
        synchronized (logBuffer) {
            logBuffer.clear();
        }
    }

    private void refreshToken() {
        if (currentDeviceId == -1 || appContext == null) return;
        int deviceId = currentDeviceId;
        int generation = connectionGeneration;
        new TermApi().getWebSocketInfo(appContext, deviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isCurrentConnection(deviceId, generation)) {
                    return;
                }
                try {
                    requestToken = data.getString("token");
                    sendAuthMessage();
                } catch (Exception ignored) {}
            }
            @Override
            public void onFailure(String errorMsg) {
                if (isCurrentConnection(deviceId, generation)) {
                    Log.e(TAG, "Token refresh failed: " + errorMsg);
                }
            }
        });
    }

    public boolean sendCommand(String command) {
        return sendCommand(currentDeviceId, command);
    }

    public boolean sendCommand(int deviceId, String command) {
        if (deviceId != currentDeviceId || command == null || command.trim().isEmpty() || webSocket == null || !isSocketOpen) return false;
        try {
            Log.d(TAG, "WS Outgoing command for device " + deviceId + ": " + command);
            JSONObject cmdMsg = new JSONObject();
            cmdMsg.put("event", "send command");
            JSONArray args = new JSONArray();
            args.put(command);
            cmdMsg.put("args", args);
            return webSocket.send(cmdMsg.toString());
        } catch (Exception e) {
            Log.w(TAG, "Send command failed: " + e.getMessage());
            return false;
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
            Log.d(TAG, "WS Outgoing auth: " + authMsg);
            webSocket.send(authMsg.toString());
        } catch (Exception ignored) {}
    }

    private void sendLogMessage() {
        if (webSocket == null) return;
        try {
            JSONObject logMsg = new JSONObject();
            logMsg.put("event", "send logs");
            logMsg.put("args", new JSONArray());
            Log.d(TAG, "WS Outgoing logs request: " + logMsg);
            webSocket.send(logMsg.toString());
        } catch (Exception ignored) {}
    }

    public void requestLogs() {
        isLogsRequested = true;
        if (isSocketOpen) {
            sendLogMessage();
        }
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
        connectionGeneration++;
        isManualClose = true;
        isConnecting = false;
        isLogsRequested = false;
        isSocketOpen = false;
        closeCurrentSocket();
        currentDeviceId = -1;
        clearBuffer();
    }

    private void closeCurrentSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal Closure");
            webSocket = null;
        }
    }

    private boolean isCurrentConnection(int deviceId, int generation) {
        return currentDeviceId == deviceId && connectionGeneration == generation;
    }

    private void notifyLogReceived(String line) {
        for (TerminalWebSocketListener l : listeners) {
            if (isListenerForCurrentDevice(l)) l.onLogReceived(line);
        }
    }

    private void notifyConsoleCleared() {
        for (TerminalWebSocketListener l : listeners) {
            if (isListenerForCurrentDevice(l)) l.onConsoleCleared();
        }
    }

    private void notifyStatusChanged(String status) {
        for (TerminalWebSocketListener l : listeners) {
            if (isListenerForCurrentDevice(l)) l.onStatusChanged(status);
        }
    }

    private void notifyConnected() {
        for (TerminalWebSocketListener l : listeners) {
            if (isListenerForCurrentDevice(l)) l.onConnected();
        }
    }

    private void notifyDisconnected(String reason) {
        for (TerminalWebSocketListener l : listeners) {
            if (isListenerForCurrentDevice(l)) l.onDisconnected(reason);
        }
    }

    private void notifyError(String message) {
        for (TerminalWebSocketListener l : listeners) {
            if (isListenerForCurrentDevice(l)) l.onError(message);
        }
    }

    private boolean isListenerForCurrentDevice(TerminalWebSocketListener listener) {
        Integer deviceId = listenerDeviceIds.get(listener);
        return deviceId != null && deviceId == currentDeviceId;
    }

    public boolean isConnected() {
        return webSocket != null && isSocketOpen;
    }

    public boolean isConnectedTo(int deviceId) {
        return currentDeviceId == deviceId && isConnected();
    }
}
