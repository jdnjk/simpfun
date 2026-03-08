package cn.jdnjk.simpfun.api.ins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class AiApi {
    public static final String TYPE_ANSWER = "answer";
    public static final String TYPE_ANALYZE = "analyze";
    public static final String TYPE_LOG = "log";
    private static final long AI_CONNECT_TIMEOUT_SECONDS = 30L;
    private static final long AI_READ_TIMEOUT_SECONDS = 180L;
    private static final long AI_WRITE_TIMEOUT_SECONDS = 180L;
    private static final long AI_CALL_TIMEOUT_SECONDS = 200L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient aiClient = ApiClient.getInstance()
            .getClient()
            .newBuilder()
            .connectTimeout(AI_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AI_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AI_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(AI_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    public void getAiHistory(Context context, int serverId, Callback callback) {
        String token = getToken(context);
        if (token == null) {
            deliverFailure(callback, "未登录");
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/ai_analyze")
                .header("Authorization", token)
                .get()
                .build();

        executeRequest(request, callback);
    }

    public void getAiHistoryDetail(Context context, int serverId, long historyId, Callback callback) {
        String token = getToken(context);
        if (token == null) {
            deliverFailure(callback, "未登录");
            return;
        }

        String url = Uri.parse(BASE_INS_URL + serverId + "/ai_analyze")
                .buildUpon()
                .appendQueryParameter("id", String.valueOf(historyId))
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get()
                .build();

        executeRequest(request, callback);
    }

    public void answerQuestion(Context context, int serverId, String question, Callback callback) {
        postAiAction(context, serverId, TYPE_ANSWER, question, callback);
    }

    public void analyzeLogs(Context context, int serverId, String logFaultType, String supplement, String logs, Callback callback) {
        String token = getToken(context);
        if (token == null) {
            deliverFailure(callback, "未登录");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("type", TYPE_LOG)
                .add("log_fault_type", logFaultType == null ? "Others" : logFaultType)
                .add("supplement", supplement == null ? "" : supplement)
                .add("log", logs == null ? "" : logs)
                .build();

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/ai_analyze")
                .header("Authorization", token)
                .post(formBody)
                .build();

        executeRequest(request, callback);
    }

    public void postAiAction(Context context, int serverId, String type, String supplement, Callback callback) {
        String token = getToken(context);
        if (token == null) {
            deliverFailure(callback, "未登录");
            return;
        }

        FormBody.Builder formBody = new FormBody.Builder().add("type", type == null ? "" : type);
        if (supplement != null) {
            formBody.add("supplement", supplement);
        }

        Request request = new Request.Builder()
                .url(BASE_INS_URL + serverId + "/ai_analyze")
                .header("Authorization", token)
                .post(formBody.build())
                .build();

        executeRequest(request, callback);
    }

    private String getToken(Context context) {
        SharedPreferences sp = context.getSharedPreferences("token", Context.MODE_PRIVATE);
        return sp.getString("token", null);
    }

    private void executeRequest(Request request, Callback callback) {
        aiClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (e instanceof InterruptedIOException) {
                    deliverFailure(callback, "AI 请求超时，请稍后重试或减少日志内容");
                    return;
                }
                deliverFailure(callback, e.getMessage() == null ? "网络请求失败" : e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                    int code = json.optInt("code", response.code());
                    if (response.isSuccessful() && code == 200) {
                        deliverSuccess(callback, json);
                    } else {
                        String msg = json.optString("msg", "错误代码: " + code);
                        deliverFailure(callback, msg);
                    }
                } catch (Exception e) {
                    deliverFailure(callback, "解析失败: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private void deliverSuccess(Callback callback, JSONObject data) {
        mainHandler.post(() -> callback.onSuccess(data));
    }

    private void deliverFailure(Callback callback, String errorMsg) {
        mainHandler.post(() -> callback.onFailure(errorMsg));
    }
}
