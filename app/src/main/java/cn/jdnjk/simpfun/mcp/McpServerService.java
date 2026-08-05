package cn.jdnjk.simpfun.mcp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ui.setting.SettingsActivity;

/**
 * MCP 服务器前台服务。
 */
public class McpServerService extends Service {
    private static final String TAG = "McpServerService";
    private static final int NOTIFICATION_ID = 0x4D4350; // "MCP"
    private static final String CHANNEL_ID = "mcp_server";
    private static final String ACTION_STOP = "cn.jdnjk.simpfun.mcp.STOP";

    private static volatile boolean running = false;

    private McpHttpServer server;
    private McpSettingsManager settingsManager;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, McpServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent);
        } else {
            app.startService(intent);
        }
    }

    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        app.stopService(new Intent(app, McpServerService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        settingsManager = new McpSettingsManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_STOP.equals(intent != null ? intent.getAction() : null)) {
            settingsManager.setEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        // 开关已关闭（如冷启动复位）时，不启动服务器，直接停止服务。
        if (!settingsManager.isEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());

        if (running) {
            return START_STICKY;
        }

        int port = settingsManager.getPort();
        McpSessionManager sessionManager = new McpSessionManager();
        McpToolRegistry registry = McpBuiltinTools.createRegistry(this);
        McpRequestDispatcher dispatcher = new McpRequestDispatcher(sessionManager, registry);

        try {
            server = new McpHttpServer(port, sessionManager, dispatcher);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
            running = true;
            Log.i(TAG, "MCP server started on port " + port);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start MCP server on port " + port, e);
            Toast.makeText(getApplicationContext(), "MCP 服务启动失败：端口 " + port + " 被占用", Toast.LENGTH_LONG).show();
            settingsManager.setEnabled(false);
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        ensureChannel();

        Intent stopIntent = new Intent(this, McpServerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent contentIntent = new Intent(this, SettingsActivity.class);
        PendingIntent contentPending = PendingIntent.getActivity(
                this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ai_assistant)
                .setContentTitle("MCP 服务运行中")
                .setContentText("局域网 AI 助手服务已开启")
                .setOngoing(true)
                .setContentIntent(contentPending)
                .addAction(0, "停止", stopPending)
                .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "MCP 服务", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("局域网 MCP 服务器状态");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
