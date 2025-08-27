package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.util.Log;
import cn.jdnjk.simpfun.api.ApiClient;
//import com.tencent.upgrade.core.UpgradeManager;
//import com.tencent.upgrade.core.UpgradeReqCallbackForUserManualCheck;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.core.content.FileProvider;

public class UpdateHelper {
    public interface UpdateListener {
        void onChecking();
        void onNoUpdate();
        void onFoundNewVersion(String version, String downloadUrl);
        void onDownloadStart();
        void onDownloadSuccess(File apkFile);
        void onDownloadError(String error);
    }

    private Context context;
    private UpdateListener listener;

    public UpdateHelper(Context context, UpdateListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void checkForUpdate(String currentVersion, String updateChannel) {
        if ("github".equals(updateChannel)) {
            fetchGitHubRelease(currentVersion);
        }
    }

    private void fetchGitHubRelease(String currentVersion) {
        if (listener != null) listener.onChecking();

        OkHttpClient client = ApiClient.getInstance().getClient();

        Request request = new Request.Builder()
                .url("https://api.github.com/repos/jdnjk/simpfun/releases")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postToUi(() -> {
                    if (listener != null) listener.onDownloadError("网络请求失败");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    postToUi(() -> listener.onDownloadError("获取失败: " + response.code()));
                    return;
                }
                try {
                    JSONArray releases = new JSONArray(response.body().string());
                    if (releases.length() == 0) {
                        Log.d("UpdateHelper", "没有可用的更新");
                        //UpgradeManager.getInstance().checkUpgrade(true, null, new UpgradeReqCallbackForUserManualCheck());
                        postToUi(() -> listener.onNoUpdate());
                        return;
                    }
                    JSONObject latestRelease = releases.getJSONObject(0);
                    String tagName = latestRelease.getString("tag_name");
                    boolean isPreRelease = latestRelease.getBoolean("prerelease");
                    String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    if (!isVersionNewer(latestVersion, currentVersion)) {
                        Log.d("UpdateHelper", "当前版本已是最新版本");
                        //UpgradeManager.getInstance().checkUpgrade(true, null, new UpgradeReqCallbackForUserManualCheck());
                        postToUi(() -> listener.onNoUpdate());
                        return;
                    }
                    if (isPreRelease) {
                        postToUi(() -> listener.onDownloadError("最新版本为测试版，暂不支持自动更新"));
                        return;
                    }
                    JSONArray assets = latestRelease.getJSONArray("assets");
                    if (assets.length() == 0) {
                        postToUi(() -> listener.onDownloadError("最新版本未上传安装包，请稍后再试"));
                        return;
                    }

                    String assetUrl = assets.getJSONObject(0).getString("browser_download_url");
                    postToUi(() -> {
                        listener.onFoundNewVersion(latestVersion, assetUrl);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    postToUi(() -> listener.onDownloadError("检查更新失败：" + e.getMessage()));
                }
            }
        });
    }

    private boolean isVersionNewer(String newVersion, String currentVersion) {
        String[] newParts = newVersion.split("\\.");
        String[] curParts = currentVersion.split("\\.");

        int length = Math.max(newParts.length, curParts.length);
        for (int i = 0; i < length; i++) {
            int newPart = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
            int curPart = i < curParts.length ? Integer.parseInt(curParts[i]) : 0;

            if (newPart > curPart) return true;
            if (newPart < curPart) return false;
        }
        return false;
    }

    public void downloadApk(String url) {
        if (listener != null) listener.onDownloadStart();

        Request request = new Request.Builder().url(url).build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postToUi(() -> {
                    if (listener != null) listener.onDownloadError("下载失败");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    postToUi(() -> {
                        if (listener != null) listener.onDownloadError("下载失败: " + response.code());
                    });
                    return;
                }

                File apkFile = new File(context.getCacheDir(), "app-update.apk");
                try (FileOutputStream fos = new FileOutputStream(apkFile);
                     InputStream is = response.body().byteStream()) {

                    byte[] buffer = new byte[4096];
                    long total = response.body().contentLength();
                    long downloaded = 0;
                    int len;

                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;
                    }

                    fos.flush();

                    postToUi(() -> {
                        if (listener != null) listener.onDownloadSuccess(apkFile);
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                    postToUi(() -> {
                        if (listener != null) listener.onDownloadError("保存失败");
                    });
                }
            }
        });
    }

    private void postToUi(Runnable runnable) {
        new android.os.Handler(context.getMainLooper()).post(runnable);
    }

    public static void installApk(Context context, File apkFile) {
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                apkFile
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}