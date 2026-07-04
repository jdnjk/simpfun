package cn.jdnjk.simpfun.utils;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.jdnjk.simpfun.model.InviteData;

public final class PageDataStore {
    private static final PageDataStore INSTANCE = new PageDataStore();

    private final Map<String, InviteData> inviteCache = new ConcurrentHashMap<>();
    private final Map<String, ServerData> serverCache = new ConcurrentHashMap<>();

    private PageDataStore() {
    }

    public static PageDataStore getInstance() {
        return INSTANCE;
    }

    public synchronized InviteData getInviteData(String token) {
        if (isInvalidToken(token)) return null;
        return inviteCache.get(token);
    }

    public synchronized void putInviteData(String token, InviteData data) {
        if (isInvalidToken(token) || data == null) return;
        inviteCache.put(token, data);
    }

    public synchronized ServerData getServerData(String token) {
        if (isInvalidToken(token)) return null;
        ServerData data = serverCache.get(token);
        return data == null ? null : new ServerData(data.getInstanceList(), data.getSupportList());
    }

    public synchronized void putServerData(String token, JSONArray instanceList, JSONArray supportList) {
        if (isInvalidToken(token)) return;
        serverCache.put(token, new ServerData(instanceList, supportList));
    }

    public synchronized void clearServerData(String token) {
        if (isInvalidToken(token)) return;
        serverCache.remove(token);
    }

    public synchronized void clearAll() {
        inviteCache.clear();
        serverCache.clear();
    }

    private boolean isInvalidToken(String token) {
        return token == null || token.trim().isEmpty();
    }

    private static JSONArray deepCopy(JSONArray source) {
        if (source == null) return null;
        try {
            return new JSONArray(source.toString());
        } catch (JSONException e) {
            return null;
        }
    }

    public static final class ServerData {
        private final JSONArray instanceList;
        private final JSONArray supportList;

        private ServerData(JSONArray instanceList, JSONArray supportList) {
            this.instanceList = deepCopy(instanceList);
            this.supportList = deepCopy(supportList);
        }

        public JSONArray getInstanceList() {
            return deepCopy(instanceList);
        }

        public JSONArray getSupportList() {
            return deepCopy(supportList);
        }
    }
}
