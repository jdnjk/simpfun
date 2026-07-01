package cn.jdnjk.simpfun.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InstanceDetailStore {
    private static final InstanceDetailStore INSTANCE = new InstanceDetailStore();
    private final Map<Integer, JSONObject> cache = new ConcurrentHashMap<>();

    private InstanceDetailStore() {
    }

    public static InstanceDetailStore getInstance() {
        return INSTANCE;
    }

    public synchronized void put(int deviceId, JSONObject response) {
        if (deviceId <= 0 || response == null) return;
        cache.put(deviceId, deepCopy(response));
    }

    public synchronized JSONObject getResponse(int deviceId) {
        return deepCopy(cache.get(deviceId));
    }

    public synchronized JSONObject getDetailData(int deviceId) {
        JSONObject response = cache.get(deviceId);
        if (response == null) return null;
        JSONObject detail = response.optJSONObject("data");
        return deepCopy(detail != null ? detail : response);
    }

    public synchronized void updateName(int deviceId, String newName) {
        if (deviceId <= 0 || newName == null) return;
        JSONObject response = cache.get(deviceId);
        if (response == null) return;
        try {
            JSONObject detail = response.optJSONObject("data");
            if (detail != null) {
                detail.put("name", newName);
            } else {
                response.put("name", newName);
            }
            cache.put(deviceId, deepCopy(response));
        } catch (JSONException ignored) {
        }
    }

    public synchronized void updateStatus(int deviceId, String status) {
        if (deviceId <= 0 || status == null) return;
        JSONObject response = cache.get(deviceId);
        if (response == null) return;
        try {
            JSONObject detail = response.optJSONObject("data");
            if (detail != null) {
                detail.put("status", status);
            } else {
                response.put("status", status);
            }
            cache.put(deviceId, deepCopy(response));
        } catch (JSONException ignored) {
        }
    }

    public synchronized void clear(int deviceId) {
        cache.remove(deviceId);
    }

    public synchronized void clearAll() {
        cache.clear();
    }

    private JSONObject deepCopy(JSONObject source) {
        if (source == null) return null;
        try {
            return new JSONObject(source.toString());
        } catch (JSONException e) {
            return null;
        }
    }
}

