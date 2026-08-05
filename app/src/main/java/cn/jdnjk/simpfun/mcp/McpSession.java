package cn.jdnjk.simpfun.mcp;

import org.json.JSONObject;

import java.util.UUID;

/**
 * MCP 客户端会话。
 */
public class McpSession {
    public final String sessionId;
    public volatile boolean initialized;
    public volatile JSONObject clientInfo;
    public final long createdAt;

    public McpSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.initialized = false;
    }
}
