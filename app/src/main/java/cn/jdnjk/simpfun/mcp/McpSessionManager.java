package cn.jdnjk.simpfun.mcp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话注册表，上限 32 条，超出时驱逐最旧。
 */
public class McpSessionManager {
    private static final int MAX_SESSIONS = 32;

    private final LinkedHashMap<String, McpSession> sessions = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, McpSession> eldest) {
            return size() > MAX_SESSIONS;
        }
    };

    public synchronized McpSession create() {
        McpSession session = new McpSession();
        sessions.put(session.sessionId, session);
        return session;
    }

    public synchronized McpSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public synchronized McpSession getOrCreate(String sessionId) {
        McpSession session = sessions.get(sessionId);
        if (session == null) {
            session = new McpSession();
            sessions.put(session.sessionId, session);
        }
        return session;
    }

    public synchronized void touch(String sessionId) {
        McpSession s = sessions.get(sessionId);
        if (s != null) {
            sessions.put(sessionId, s);
        }
    }

    public synchronized void clear() {
        sessions.clear();
    }
}
