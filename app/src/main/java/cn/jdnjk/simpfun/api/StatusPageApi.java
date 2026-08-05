package cn.jdnjk.simpfun.api;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import cn.jdnjk.simpfun.model.HeartbeatPoint;
import cn.jdnjk.simpfun.model.MonitorLiveStatus;
import cn.jdnjk.simpfun.model.StatusMonitor;
import cn.jdnjk.simpfun.model.StatusMonitorGroup;
import cn.jdnjk.simpfun.model.StatusPageData;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StatusPageApi {
    private static final String BASE_URL = "https://simp.host/api/status-page/simpfun";
    private static final String HEARTBEAT_URL = "https://simp.host/api/status-page/heartbeat/simpfun";
    private static final String UPTIME_KEY_SUFFIX = "_24";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(StatusPageData data);

        void onFailure(String errorMsg);
    }

    public interface LiveCallback {
        void onSuccess(Map<Long, MonitorLiveStatus> live);

        void onFailure(String errorMsg);
    }

    @Nullable
    public Call fetchLiveStatus(LiveCallback callback) {
        HttpUrl url = HttpUrl.parse(HEARTBEAT_URL);
        if (url == null) {
            postLiveFailure(callback, "URL 解析错误");
            return null;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (call.isCanceled()) return;
                postLiveFailure(callback, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleLiveResponse(call, response, callback);
            }
        });
        return call;
    }

    private void handleLiveResponse(@NotNull Call call, @NotNull Response response, LiveCallback callback) {
        try {
            String bodyString = Objects.requireNonNull(response.body()).string();
            if (call.isCanceled()) return;
            if (!response.isSuccessful()) {
                postLiveFailure(callback, "HTTP " + response.code());
                return;
            }

            JSONObject root = new JSONObject(bodyString);
            JSONObject hb = root.optJSONObject("heartbeatList");
            JSONObject up = root.optJSONObject("uptimeList");
            Map<Long, MonitorLiveStatus> map = new HashMap<>();
            if (hb != null) {
                Iterator<String> it = hb.keys();
                while (it.hasNext()) {
                    String idKey = it.next();
                    long id;
                    try {
                        id = Long.parseLong(idKey);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    JSONArray arr = hb.optJSONArray(idKey);
                    List<HeartbeatPoint> points = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            points.add(new HeartbeatPoint(
                                    parseTime(o.optString("time", "")),
                                    o.optInt("status", 0),
                                    o.optLong("ping", -1L),
                                    o.optString("msg", "")
                            ));
                        }
                    }
                    double uptime = -1.0;
                    if (up != null) {
                        uptime = up.optDouble(idKey + UPTIME_KEY_SUFFIX, -1.0);
                        if (!Double.isFinite(uptime)) uptime = -1.0;
                    }
                    map.put(id, new MonitorLiveStatus(points, uptime));
                }
            }

            if (!call.isCanceled()) {
                postLiveSuccess(callback, map);
            }
        } catch (JSONException ex) {
            if (!call.isCanceled()) postLiveFailure(callback, "数据解析错误");
        } catch (Exception ex) {
            if (!call.isCanceled()) postLiveFailure(callback, "未知错误");
        }
    }

    private static long parseTime(String time) {
        if (time == null || time.isEmpty()) return 0L;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            Date date = format.parse(time);
            return date == null ? 0L : date.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Nullable
    public Call fetchStatusPage(Callback callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL);
        if (url == null) {
            postFailure(callback, "URL 解析错误");
            return null;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (call.isCanceled()) return;
                postFailure(callback, "网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                handleResponse(call, response, callback);
            }
        });
        return call;
    }

    private void handleResponse(@NotNull Call call, @NotNull Response response, Callback callback) {
        try {
            String bodyString = Objects.requireNonNull(response.body()).string();
            if (call.isCanceled()) return;
            if (!response.isSuccessful()) {
                postFailure(callback, "HTTP " + response.code());
                return;
            }

            JSONObject root = new JSONObject(bodyString);
            JSONObject config = root.optJSONObject("config");
            String title = config == null ? "" : config.optString("title", "").trim();
            String description = cleanMarkdown(config == null ? "" : config.optString("description", ""));

            JSONArray groupArr = root.optJSONArray("publicGroupList");
            List<StatusMonitorGroup> groups = new ArrayList<>();
            int monitorCount = 0;
            if (groupArr != null) {
                for (int i = 0; i < groupArr.length(); i++) {
                    JSONObject groupObj = groupArr.optJSONObject(i);
                    if (groupObj == null) continue;
                    String groupName = groupObj.optString("name", "").trim();
                    JSONArray monitorArr = groupObj.optJSONArray("monitorList");
                    List<StatusMonitor> monitors = new ArrayList<>();
                    if (monitorArr != null) {
                        for (int j = 0; j < monitorArr.length(); j++) {
                            JSONObject mon = monitorArr.optJSONObject(j);
                            if (mon == null) continue;
                            monitors.add(new StatusMonitor(
                                    mon.optLong("id", 0L),
                                    mon.optString("name", ""),
                                    mon.optString("type", ""),
                                    mon.optString("url", ""),
                                    mon.optInt("sendUrl", 0) == 1,
                                    mon.optLong("certExpiryDaysRemaining", -1L),
                                    mon.optBoolean("validCert", false)
                            ));
                        }
                    }
                    monitorCount += monitors.size();
                    if (!monitors.isEmpty()) {
                        groups.add(new StatusMonitorGroup(groupName, monitors));
                    }
                }
            }

            JSONObject incident = root.optJSONObject("incident");
            boolean hasIncident = incident != null && !incident.optString("title", "").isEmpty();

            if (!call.isCanceled()) {
                postSuccess(callback, new StatusPageData(title, description, groups, monitorCount, hasIncident));
            }
        } catch (JSONException ex) {
            if (!call.isCanceled()) postFailure(callback, "数据解析错误");
        } catch (Exception ex) {
            if (!call.isCanceled()) postFailure(callback, "未知错误");
        }
    }

    private static String cleanMarkdown(String text) {
        if (text == null) return "";
        String s = text.trim();
        while (s.startsWith("#")) {
            s = s.substring(1);
        }
        return s.trim();
    }

    private void postSuccess(Callback callback, StatusPageData data) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(data));
    }

    private void postFailure(Callback callback, String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }

    private void postLiveSuccess(LiveCallback callback, Map<Long, MonitorLiveStatus> live) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(live));
    }

    private void postLiveFailure(LiveCallback callback, String errorMsg) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }
}
