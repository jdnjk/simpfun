package cn.jdnjk.simpfun.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import cn.jdnjk.simpfun.BuildConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String SHIPLY_URL = "https://shiply.tds.qq.com/cgi/v1/alpha/get-download-info";
    private static final String SHIPLY_BODY = "{\"short_cut_url\": \"a6cd053496c642b29b06f01e7810e481\"}";
    private static final String GITHUB_API = "https://api.github.com/repos/jdnjk/simpfun/releases";

    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";

    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private UpdateChecker() {
    }

    public static class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String downloadUrl;
        public final String updateDesc;
        public final String updatedAt;

        public UpdateInfo(int versionCode, String versionName, String downloadUrl,
                          String updateDesc, String updatedAt) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.downloadUrl = downloadUrl;
            this.updateDesc = updateDesc;
            this.updatedAt = updatedAt;
        }

        public boolean isNewerThanCurrent() {
            return versionCode > BuildConfig.VERSION_CODE;
        }
    }

    /**
     * 检查更新（自动模式，24小时内只检查一次）
     */
    public static void checkUpdateIfNeeded(Activity activity) {
        Context context = activity.getApplicationContext();
        long lastCheck = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK_TIME, 0);
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) {
            Log.d(TAG, "距上次检查不足24小时，跳过自动检查");
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK_TIME, now).apply();

        checkUpdate(activity, false);
    }

    /**
     * 检查更新（手动模式）
     */
    public static void checkUpdate(Activity activity) {
        checkUpdate(activity, true);
    }

    private static void checkUpdate(Activity activity, boolean showNoUpdateToast) {
        new Thread(() -> {
            try {
                // 优先尝试腾讯云 API
                UpdateInfo info = checkShiply();
                if (info == null) {
                    // 备用：GitHub Releases
                    info = checkGithub();
                }

                final UpdateInfo finalInfo = info;
                final boolean showToast = showNoUpdateToast;
                activity.runOnUiThread(() -> {
                    if (finalInfo == null) {
                        if (showToast) {
                            Toast.makeText(activity, "检查更新失败", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    if (!finalInfo.isNewerThanCurrent()) {
                        if (showToast) {
                            Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    showUpdateDialog(activity, finalInfo);
                });
            } catch (Exception e) {
                Log.e(TAG, "检查更新异常", e);
                final String msg = e.getMessage();
                activity.runOnUiThread(() -> {
                    if (showNoUpdateToast) {
                        Toast.makeText(activity, "检查更新失败: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /**
     * 通过腾讯云 Shiply API 检查更新
     */
    private static UpdateInfo checkShiply() {
        try {
            Request request = new Request.Builder()
                    .url(SHIPLY_URL)
                    .post(RequestBody.create(JSON, SHIPLY_BODY))
                    .addHeader("User-Agent", "SimpfunAPP/" + BuildConfig.VERSION_NAME)
                    .build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.w(TAG, "Shiply API 返回非成功状态码: " + response.code());
                response.close();
                return null;
            }
            String body = response.body() != null ? response.body().string() : null;
            response.close();
            if (body == null) {
                return null;
            }

            JSONObject json = new JSONObject(body);
            int retCode = json.optInt("ret_code", -1);
            if (retCode != 0) {
                Log.w(TAG, "Shiply API ret_code != 0: " + retCode);
                return null;
            }

            JSONArray infos = json.optJSONArray("infos");
            if (infos == null || infos.length() == 0) {
                return null;
            }

            JSONObject first = infos.getJSONObject(0);
            int versionCode = first.optInt("version_code", 0);
            String versionName = first.optString("version_name", "");
            String downloadUrl = first.optString("download_url", "");
            String updateDesc = first.optString("update_desc", "");
            String updatedAt = first.optString("updated_at", "");

            if (versionCode == 0 || TextUtils.isEmpty(downloadUrl)) {
                return null;
            }

            // 转换 unix 时间戳为可读格式
            String formattedTime = updatedAt;
            try {
                long ts = Long.parseLong(updatedAt) * 1000L;
                formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(ts));
            } catch (Exception ignored) {
            }

            return new UpdateInfo(versionCode, versionName, downloadUrl, updateDesc, formattedTime);
        } catch (Exception e) {
            Log.w(TAG, "Shiply 检查失败", e);
            return null;
        }
    }

    /**
     * 通过 GitHub Releases API 检查更新（备用）
     */
    private static UpdateInfo checkGithub() {
        try {
            Request request = new Request.Builder()
                    .url(GITHUB_API)
                    .addHeader("User-Agent", "SimpfunAPP/" + BuildConfig.VERSION_NAME)
                    .build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.w(TAG, "GitHub API 返回非成功状态码: " + response.code());
                response.close();
                return null;
            }
            String body = response.body() != null ? response.body().string() : null;
            response.close();
            if (body == null) {
                return null;
            }

            JSONArray releases = new JSONArray(body);
            if (releases.length() == 0) {
                return null;
            }

            // 遍历所有 release，找最新的版本号（tag 格式如 v1.1.5.2-11520）
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                String tagName = release.optString("tag_name", "");
                String bodyText = release.optString("body", "");
                String publishedAt = release.optString("published_at", "");

                // 解析版本号：从 tag 中提取最后一段数字，如 v1.1.5.2-11520 -> 11520
                int versionCode = parseVersionCodeFromTag(tagName);
                if (versionCode <= 0) {
                    continue;
                }

                // 获取 APK 下载链接
                JSONArray assets = release.optJSONArray("assets");
                String downloadUrl = null;
                if (assets != null) {
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String name = asset.optString("name", "");
                        // 找 APK 文件
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "");
                            if (!TextUtils.isEmpty(downloadUrl)) {
                                break;
                            }
                        }
                    }
                }

                if (downloadUrl == null) {
                    continue;
                }

                // 格式化时间
                String formattedTime = publishedAt;
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    Date date = parser.parse(publishedAt.replace("Z", ""));
                    if (date != null) {
                        formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(date);
                    }
                } catch (Exception ignored) {
                }

                // 从 tag 中提取版本名
                // v1.1.5.2-11520 -> 1.1.5.2
                String versionName = tagName.replaceAll("^v", "");
                int dashIdx = versionName.lastIndexOf('-');
                if (dashIdx > 0) {
                    versionName = versionName.substring(0, dashIdx);
                }

                return new UpdateInfo(versionCode, versionName, downloadUrl, bodyText, formattedTime);
            }
            return null;
        } catch (Exception e) {
            Log.w(TAG, "GitHub 检查失败", e);
            return null;
        }
    }

    /**
     * 从 tag 中解析版本号，如 v1.1.5.2-11520 -> 11520
     */
    private static int parseVersionCodeFromTag(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return -1;
        }
        int dashIdx = tag.lastIndexOf('-');
        if (dashIdx < 0) {
            return -1;
        }
        String codeStr = tag.substring(dashIdx + 1);
        try {
            return Integer.parseInt(codeStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 显示更新对话框
     */
    private static void showUpdateDialog(Activity activity, UpdateInfo info) {
        StringBuilder msg = new StringBuilder();
        msg.append("发现新版本：").append(info.versionName).append("\n\n");

        msg.append("• 版本号：").append(info.versionCode).append("\n");
        if (!TextUtils.isEmpty(info.updatedAt) && !info.updatedAt.equals("0")) {
            msg.append("• 更新时间：").append(info.updatedAt).append("\n");
        }
        msg.append("• 更新内容：\n").append(info.updateDesc).append("\n\n");
        msg.append("是否下载更新？");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("发现新版本")
                .setMessage(msg.toString())
                .setPositiveButton("下载", (dialog, which) -> downloadAndInstall(activity, info))
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 下载 APK 并安装
     */
    private static void downloadAndInstall(Activity activity, UpdateInfo info) {
        Toast.makeText(activity, "开始下载更新...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 下载到应用缓存目录
                File cacheDir = activity.getCacheDir();
                File apkFile = new File(cacheDir, "simpfun_update_" + info.versionCode + ".apk");

                // 如果已存在相同版本的文件，直接安装
                if (apkFile.exists()) {
                    Log.d(TAG, "APK 已存在，直接安装");
                    installApk(activity, apkFile);
                    return;
                }

                // 清理旧版本更新文件
                File[] oldFiles = cacheDir.listFiles((dir, name) ->
                        name.startsWith("simpfun_update_") && name.endsWith(".apk"));
                if (oldFiles != null) {
                    for (File f : oldFiles) {
                        if (!f.delete()) {
                            Log.w(TAG, "删除旧更新文件失败: " + f.getName());
                        }
                    }
                }

                // 下载
                Request request = new Request.Builder()
                        .url(info.downloadUrl)
                        .addHeader("User-Agent", "SimpfunAPP/" + BuildConfig.VERSION_NAME)
                        .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "下载失败: " + response.code(), Toast.LENGTH_SHORT).show());
                    response.close();
                    return;
                }

                InputStream inputStream = response.body() != null ? response.body().byteStream() : null;
                if (inputStream == null) {
                    response.close();
                    return;
                }

                FileOutputStream outputStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                outputStream.close();
                inputStream.close();
                response.close();

                Log.d(TAG, "下载完成: " + totalBytes + " bytes");

                installApk(activity, apkFile);
            } catch (Exception e) {
                Log.e(TAG, "下载更新失败", e);
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "下载更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * 安装 APK
     */
    private static void installApk(Activity activity, File apkFile) {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Uri apkUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+ 使用 FileProvider
                    apkUri = FileProvider.getUriForFile(activity,
                            BuildConfig.APPLICATION_ID + ".fileprovider", apkFile);
                } else {
                    apkUri = Uri.fromFile(apkFile);
                }

                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "安装 APK 失败", e);
                Toast.makeText(activity, "安装失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}