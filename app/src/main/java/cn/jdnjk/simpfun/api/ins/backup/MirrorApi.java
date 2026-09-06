package cn.jdnjk.simpfun.api.ins.backup;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;

public class MirrorApi {

    /**
     * 备份下载节点。这个 host 在任何 API 响应里都推导不出来，只能硬编码。
     * 下载本身不需要 Authorization —— getDownloadKey 换到的 uuid 就是凭证。
     */
    private static final String BACKUP_DOWNLOAD_BASE = "https://sfe4-connect.simpfun.cn:1000/download";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MirrorApi(Context context) {
    }

    public interface Callback {
        void onSuccess(JSONObject response);
        void onFailure(String errorMsg);
    }

    /** 用 getDownloadKey 换来的 uuid 拼出备份下载地址。 */
    public static String buildBackupDownloadUrl(String uuid) {
        return BACKUP_DOWNLOAD_BASE + "?uuid=" + Uri.encode(uuid);
    }

    /**
     * 获取账户拥有的备份
     * @param token 用户Token
     * @param serverId 实例ID
     * @param callback 回调
     */
    public void getBackups(String token, int serverId, Callback callback) {
        if (!validateBaseParams(token, serverId, callback)) {
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/backup");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    /**
     * 执行还原
     * @param token 用户Token
     * @param serverId 实例ID
     * @param backupId 备份ID
     * @param callback 回调
     */
    public void restoreBackup(String token, int serverId, int backupId, Callback callback) {
        if (!validateBackupActionParams(token, serverId, backupId, callback)) {
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/backup");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("backup_id", String.valueOf(backupId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .patch(formBody)
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    /**
     * 重命名备份
     * @param token 用户Token
     * @param serverId 实例ID
     * @param backupId 备份ID
     * @param newTag 新备份名称
     * @param callback 回调
     */
    public void renameBackup(String token, int serverId, int backupId, String newTag, Callback callback) {
        if (!validateBackupActionParams(token, serverId, backupId, callback)) {
            return;
        }
        if (newTag == null || newTag.trim().isEmpty()) {
            invokeCallback(callback, null, false, "备份名称不能为空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/backup");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("backup_id", String.valueOf(backupId))
                .add("new_tag", newTag)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .put(formBody)
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    /**
     * 删除备份
     * @param token 用户Token
     * @param serverId 实例ID
     * @param backupId 备份ID
     * @param callback 回调
     */
    public void deleteBackup(String token, int serverId, int backupId, Callback callback) {
        if (!validateBackupActionParams(token, serverId, backupId, callback)) {
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/backup");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("backup_id", String.valueOf(backupId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .delete(formBody)
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    /**
     * 下载密钥
     * @param token 用户Token
     * @param serverId 实例ID
     * @param downId 备份ID
     * @param callback 回调
     */
    public void getDownloadKey(String token, int serverId, int downId, Callback callback) {
        if (!validateBaseParams(token, serverId, callback)) {
            return;
        }
        if (downId <= 0) {
            invokeCallback(callback, null, false, "无效的备份ID");
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(BASE_INS_URL + serverId + "/backup");
        if (baseUrl == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("down_id", String.valueOf(downId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    /**
     * 创建备份
     * @param token 用户Token
     * @param serverId 实例ID
     * @param tag 名称
     * @param callback 回调
     */
    public void createBackup(String token, int serverId, String tag, Callback callback) {
        if (!validateBaseParams(token, serverId, callback)) {
            return;
        }
        if (tag == null || tag.trim().isEmpty()) {
            invokeCallback(callback, null, false, "备份名称不能为空");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_INS_URL + serverId + "/backup");
        if (url == null) {
            invokeCallback(callback, null, false, "无效的URL");
            return;
        }

        FormBody formBody = new FormBody.Builder()
                .add("tag", tag.trim())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .header("Authorization", token)
                .build();

        executeJsonRequest(request, callback);
    }

    private boolean validateBaseParams(String token, int serverId, Callback callback) {
        if (token == null || token.trim().isEmpty()) {
            invokeCallback(callback, null, false, "Token 不能为空");
            return false;
        }
        if (serverId <= 0) {
            invokeCallback(callback, null, false, "无效的服务器ID");
            return false;
        }
        return true;
    }

    private boolean validateBackupActionParams(String token, int serverId, int backupId, Callback callback) {
        if (!validateBaseParams(token, serverId, callback)) {
            return false;
        }
        if (backupId <= 0) {
            invokeCallback(callback, null, false, "无效的备份ID");
            return false;
        }
        return true;
    }

    private void executeJsonRequest(Request request, Callback callback) {
        OkHttpClient client = ApiClient.getInstance().getClient();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> invokeCallback(callback, null, false, "网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                mainHandler.post(() -> {
                    try {
                        String responseBody = Objects.requireNonNull(response.body()).string();
                        JSONObject json = new JSONObject(responseBody);
                        int code = json.optInt("code", response.code());

                        if (code == 200) {
                            invokeCallback(callback, json, true, null);
                        } else {
                            String msg = json.optString("msg", "操作失败");
                            invokeCallback(callback, null, false, msg);
                        }
                    } catch (JSONException e) {
                        invokeCallback(callback, null, false, "数据解析错误");
                    } catch (Exception e) {
                        invokeCallback(callback, null, false, "未知错误");
                    }
                });
            }
        });
    }

    private void invokeCallback(Callback callback, @Nullable JSONObject response, boolean success, @Nullable String errorMsg) {
        if (callback != null) {
            if (success) {
                callback.onSuccess(response);
            } else {
                callback.onFailure(errorMsg);
            }
        }
    }
}
