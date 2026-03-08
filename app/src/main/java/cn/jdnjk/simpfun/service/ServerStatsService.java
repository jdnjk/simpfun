package cn.jdnjk.simpfun.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import cn.jdnjk.simpfun.api.ApiClient;
import cn.jdnjk.simpfun.api.ins.TermApi;
import cn.jdnjk.simpfun.model.ServerStatsSnapshot;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ServerStatsService {
    private static final String TAG = "ServerStatsService";
    private static ServerStatsService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ServerStatsListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
    private Context appContext;

    public static synchronized ServerStatsService getInstance() {
        if (instance == null) {
            instance = new ServerStatsService();
        }
        return instance;
    }

    public void addListener(ServerStatsListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(ServerStatsListener listener) {
        listeners.remove(listener);
    }

    public void subscribe(Context context, int deviceId) {
        if (context == null || deviceId <= 0) return;
        appContext = context.getApplicationContext();
        Session existing = sessions.get(deviceId);
        if (existing != null) {
            existing.subscriberCount++;
            return;
        }

        Session session = new Session(deviceId);
        session.subscriberCount = 1;
        sessions.put(deviceId, session);
        fetchWsInfo(session);
    }

    public void unsubscribe(int deviceId) {
        Session session = sessions.get(deviceId);
        if (session == null) return;
        session.subscriberCount = Math.max(0, session.subscriberCount - 1);
        if (session.subscriberCount == 0) {
            closeSession(deviceId, true, "No subscribers");
        }
    }

    public void clearAll() {
        for (Integer deviceId : sessions.keySet()) {
            closeSession(deviceId, true, "Clear all");
        }
        sessions.clear();
    }

    private void fetchWsInfo(Session session) {
        if (appContext == null || session.connecting) return;
        session.connecting = true;
        new TermApi().getWebSocketInfo(appContext, session.deviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                session.connecting = false;
                session.token = data.optString("token", "");
                session.socketUrl = data.optString("socket", "");
                if (session.token.isEmpty() || session.socketUrl.isEmpty()) {
                    notifyDisconnected(session.deviceId, "连接信息不完整");
                    return;
                }
                openSocket(session);
            }

            @Override
            public void onFailure(String errorMsg) {
                session.connecting = false;
                notifyDisconnected(session.deviceId, errorMsg);
                scheduleReconnect(session);
            }
        });
    }

    private void openSocket(Session session) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        Request request = new Request.Builder().url(session.socketUrl).build();
        session.manualClose = false;
        session.webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                sendAuth(session);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                handleMessage(session, text);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "ws failure " + session.deviceId + ": " + t.getMessage());
                session.webSocket = null;
                if (!session.manualClose) {
                    notifyDisconnected(session.deviceId, t.getMessage() == null ? "连接错误" : t.getMessage());
                    scheduleReconnect(session);
                }
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                session.webSocket = null;
                if (!session.manualClose) {
                    notifyDisconnected(session.deviceId, reason);
                    scheduleReconnect(session);
                }
            }
        });
    }

    private void handleMessage(Session session, String text) {
        try {
            JSONObject message = new JSONObject(text);
            String event = message.optString("event", "");
            switch (event) {
                case "stats" -> handleStats(session, message.optJSONArray("args"));
                case "status" -> handleStatus(session, message.optJSONArray("args"));
                case "token expiring" -> refreshToken(session);
                case "jwt error" -> {
                    JSONArray args = message.optJSONArray("args");
                    String error = args != null && args.length() > 0 ? args.optString(0, "") : "";
                    if (error.contains("exp claim is invalid")) {
                        refreshToken(session);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "handleMessage failed for " + session.deviceId + ": " + e.getMessage());
        }
    }

    private void handleStats(Session session, JSONArray args) {
        if (args == null || args.length() == 0) return;
        try {
            JSONObject statsJson = new JSONObject(args.optString(0, "{}"));
            long now = SystemClock.elapsedRealtime();
            long rxBytes = 0L;
            long txBytes = 0L;
            JSONObject network = statsJson.optJSONObject("network");
            if (network != null) {
                rxBytes = network.optLong("rx_bytes", 0L);
                txBytes = network.optLong("tx_bytes", 0L);
            }

            long downloadSpeed = 0L;
            long uploadSpeed = 0L;
            if (session.lastStatsAt > 0) {
                long deltaMs = Math.max(1L, now - session.lastStatsAt);
                downloadSpeed = Math.max(0L, (rxBytes - session.lastRxBytes) * 1000L / deltaMs);
                uploadSpeed = Math.max(0L, (txBytes - session.lastTxBytes) * 1000L / deltaMs);
            }
            session.lastStatsAt = now;
            session.lastRxBytes = rxBytes;
            session.lastTxBytes = txBytes;

            ServerStatsSnapshot snapshot = new ServerStatsSnapshot(
                    statsJson.optDouble("cpu_absolute", 0d),
                    statsJson.optLong("memory_bytes", 0L),
                    statsJson.optLong("memory_limit_bytes", 0L),
                    rxBytes,
                    txBytes,
                    statsJson.optString("state", "offline"),
                    statsJson.optLong("uptime", 0L),
                    downloadSpeed,
                    uploadSpeed
            );
            notifyStats(session.deviceId, snapshot);
        } catch (Exception e) {
            Log.w(TAG, "handleStats failed for " + session.deviceId + ": " + e.getMessage());
        }
    }

    private void handleStatus(Session session, JSONArray args) {
        String state = args != null && args.length() > 0 ? args.optString(0, "offline") : "offline";
        if (!"offline".equalsIgnoreCase(state)) return;
        ServerStatsSnapshot snapshot = new ServerStatsSnapshot(0d, 0L, 0L, 0L, 0L, state, 0L, 0L, 0L);
        notifyStats(session.deviceId, snapshot);
    }

    private void refreshToken(Session session) {
        if (appContext == null) return;
        new TermApi().getWebSocketInfo(appContext, session.deviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                session.token = data.optString("token", session.token);
                sendAuth(session);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "refreshToken failed for " + session.deviceId + ": " + errorMsg);
            }
        });
    }

    private void sendAuth(Session session) {
        if (session.webSocket == null || session.token == null || session.token.isEmpty()) return;
        try {
            JSONObject auth = new JSONObject();
            auth.put("event", "auth");
            JSONArray args = new JSONArray();
            args.put(session.token);
            auth.put("args", args);
            session.webSocket.send(auth.toString());
        } catch (Exception e) {
            Log.w(TAG, "sendAuth failed for " + session.deviceId + ": " + e.getMessage());
        }
    }

    private void scheduleReconnect(Session session) {
        if (session.subscriberCount <= 0 || session.connecting) return;
        mainHandler.postDelayed(() -> {
            Session current = sessions.get(session.deviceId);
            if (current != null && current.subscriberCount > 0 && current.webSocket == null) {
                fetchWsInfo(current);
            }
        }, 2500L);
    }

    private void closeSession(int deviceId, boolean manualClose, String reason) {
        Session session = sessions.remove(deviceId);
        if (session == null) return;
        session.manualClose = manualClose;
        if (session.webSocket != null) {
            session.webSocket.close(1000, reason);
            session.webSocket = null;
        }
    }

    private void notifyStats(int deviceId, ServerStatsSnapshot snapshot) {
        mainHandler.post(() -> {
            for (ServerStatsListener listener : listeners) {
                listener.onStatsUpdated(deviceId, snapshot);
            }
        });
    }

    private void notifyDisconnected(int deviceId, String reason) {
        mainHandler.post(() -> {
            for (ServerStatsListener listener : listeners) {
                listener.onStatsDisconnected(deviceId, reason);
            }
        });
    }

    private static class Session {
        final int deviceId;
        WebSocket webSocket;
        String token;
        String socketUrl;
        int subscriberCount;
        boolean connecting;
        boolean manualClose;
        long lastStatsAt;
        long lastRxBytes;
        long lastTxBytes;

        Session(int deviceId) {
            this.deviceId = deviceId;
        }
    }
}

