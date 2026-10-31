package cn.jdnjk.simpfun.ui.ins.term;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.api.LogShareApi;
import cn.jdnjk.simpfun.api.LogSharePayload;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;
import okhttp3.Call;

/** 日志和删除凭据仅保留在内存中，跨旋转保留，不写入 Bundle/磁盘或应用日志。 */
public final class LogShareViewModel extends ViewModel {
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    String content;
    int deviceId;
    LogShareApi.Site site = LogShareApi.Site.MCLO_GS;
    boolean includeInfo;
    boolean loadingInfo;
    boolean busy;
    boolean deleted;
    String error = "";
    String progress = "";
    LogShareApi.SharedLog sharedLog;
    LogSharePayload.PreparedLog preparedLog;
    private JSONObject detail;
    private final LogShareApi api = new LogShareApi();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Call activeCall;
    private volatile boolean closed;

    void setIncludeInfo(boolean include, Context context) {
        includeInfo = include;
        error = "";
        if (include && detail == null && !loadingInfo) {
            detail = InstanceDetailStore.getInstance().getDetailData(deviceId);
            if (detail == null) {
                loadingInfo = true;
                String token = context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
                new MainApi(context.getApplicationContext()).getInstanceDetail(token, String.valueOf(deviceId), new MainApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (closed) return;
                        detail = data;
                        loadingInfo = false;
                        changed();
                    }

                    @Override
                    public void onFailure(String message) {
                        if (closed) return;
                        loadingInfo = false;
                        error = "获取实例信息失败：" + message + "。可重新勾选重试，或取消勾选后分享。";
                        changed();
                    }
                });
            }
        }
        changed();
    }

    boolean canUpload() {
        return !busy && sharedLog == null && content != null
                && (!includeInfo || (!loadingInfo && detail != null));
    }

    void upload() {
        if (!canUpload()) return;
        busy = true;
        error = "";
        progress = "正在整理并上传日志…";
        LogShareApi.Site selectedSite = site;
        JSONObject selectedDetail = includeInfo ? detail : null;
        changed();
        worker.execute(() -> {
            try {
                LogSharePayload.PreparedLog log = LogSharePayload.prepare(content, selectedSite.maxLines);
                JSONArray metadata = selectedDetail == null ? null : LogSharePayload.metadata(selectedDetail);
                if (closed) return;
                activeCall = api.create(selectedSite, log, metadata, new LogShareApi.ResultCallback<LogShareApi.SharedLog>() {
                    @Override
                    public void onSuccess(LogShareApi.SharedLog result) {
                        main.post(() -> {
                            if (closed) return;
                            sharedLog = result;
                            preparedLog = log;
                            busy = false;
                            changed();
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        fail(message);
                    }
                });
                if (closed) activeCall.cancel();
            } catch (Exception e) {
                fail(e.getMessage() == null ? "分享失败，请重试" : e.getMessage());
            }
        });
    }

    void delete() {
        if (busy || sharedLog == null) return;
        busy = true;
        error = "";
        progress = "正在删除站点上的日志…";
        changed();
        activeCall = api.delete(sharedLog, new LogShareApi.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                main.post(() -> {
                    if (closed) return;
                    busy = false;
                    deleted = true;
                    sharedLog = null;
                    changed();
                });
            }

            @Override
            public void onFailure(String message) {
                // 保留删除凭据和链接，让用户可以重试，不能伪装成已撤销。
                fail(message);
            }
        });
    }

    private void fail(String message) {
        main.post(() -> {
            if (closed) return;
            busy = false;
            error = message;
            changed();
        });
    }

    private void changed() {
        changes.setValue(changes.getValue() == null ? 1 : changes.getValue() + 1);
    }

    @Override
    protected void onCleared() {
        closed = true;
        if (activeCall != null) activeCall.cancel();
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        content = null;
        detail = null;
        sharedLog = null;
    }
}
