package cn.jdnjk.simpfun.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.tencent.upgrade.bean.ApkBasicInfo;
import com.tencent.upgrade.bean.UpgradeStrategy;
import com.tencent.upgrade.callback.UpgradeStrategyRequestCallback;
import com.tencent.upgrade.core.UpgradeManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.download.UpdateDownloadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    // Shiply SDK 通过 UpgradeManager 管理，无需手动维护请求 URL/Body
    private static final String GITHUB_API = "https://api.github.com/repos/jdnjk/simpfun/releases";

    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";

    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** 供下载前台服务复用（构建下载请求）。 */
    public static OkHttpClient httpClient() {
        return client;
    }

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
     * 检查更新（自动模式，24小时内只检查一次）。
     * 自动场景按文档建议走 checkUpgrade(false)：优先使用 SDK 缓存的灰度策略，
     * 缓存有效期默认 1 天，不会每次启动都强制请求网络。
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

    /** 更新检查回调：info 为 null 表示检查失败。 */
    public interface UpdateCallback {
        void onResult(UpdateInfo info);
    }

    /**
     * 后台静默获取最新版本信息（不弹任何 UI），供设置页红点等场景使用。
     */
    public static void fetchLatestUpdate(UpdateCallback callback) {
        new Thread(() -> {
            UpdateInfo info = null;
            try {
                // 静默拉取最新策略：强制走网络请求（文档方式一），保证红点信息实时
                info = checkShiply(true);
                if (info == null) {
                    info = checkGithub();
                }
            } catch (Exception e) {
                Log.e(TAG, "检查更新异常", e);
            }
            final UpdateInfo finalInfo = info;
            if (callback != null) {
                MAIN_HANDLER.post(() -> callback.onResult(finalInfo));
            }
        }).start();
    }

    /**
     * 检查更新
     *
     * @param userManual true：用户主动点击「检测升级」，强制发起网络请求，忽略限频；
     *                   false：首启自动检查，优先使用 SDK 缓存策略，遵循灰度平台限频配置
     */
    private static void checkUpdate(Activity activity, boolean userManual) {
        new Thread(() -> {
            try {
                // 优先尝试腾讯云 API
                UpdateInfo info = checkShiply(userManual);
                if (info == null) {
                    // 备用：GitHub Releases
                    info = checkGithub();
                }

                final UpdateInfo finalInfo = info;
                final boolean showToast = userManual;
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
                    if (userManual) {
                        Toast.makeText(activity, "检查更新失败: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /**
     * 通过腾讯云 Shiply SDK 检查更新。
     * 用 CountDownLatch 将异步回调桥接为同步返回，超时 10s 视为失败。
     *
     * @param userManual true：手动点击「检测升级」，强制发起网络请求并忽略免打扰期；
     *                   false：首启自动检查场景，SDK 优先返回缓存策略（缓存时长默认1天）
     */
    private static UpdateInfo checkShiply(boolean userManual) {
        CountDownLatch latch = new CountDownLatch(1);
        UpdateInfo[] result = {null};

        UpgradeStrategyRequestCallback callback = new UpgradeStrategyRequestCallback() {
            @Override
            public void onReceiveStrategy(UpgradeStrategy strategy) {
                try {
                    result[0] = strategyToUpdateInfo(strategy);
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onFail(int i, String s) {
                Log.w(TAG, "Shiply SDK 检查失败: errCode=" + i + " msg=" + s);
                latch.countDown();
            }

            @Override
            public void onReceivedNoStrategy() {
                Log.i(TAG, "Shiply SDK 检查结果：无更新策略");
                latch.countDown();
            }
        };

        if (userManual) {
            // 五参重载：forceRequestRemoteStrategy / requestRemoteWhenCacheIsInvalid / ignoreNoDisturbPeriod
            // 手动检查必须 ignoreNoDisturbPeriod = true：收到新策略后 SDK 会进入
            // undisturbedDuration（后台配置，当前 3 天）免打扰期，期间两参重载
            // checkUpgrade(true, null, cb) 会被 NoDisturbHelper 直接短路成
            // onReceivedNoStrategy，导致用户手动检查更新误报失败。
            UpgradeManager.getInstance().checkUpgrade(true, true, true, null, callback);
        } else {
            UpgradeManager.getInstance().checkUpgrade(false, null, callback);
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Shiply 检查被中断");
        }

        // 兜底：手动检查若仍未拿到策略（如被免打扰/限频短路），
        // 直接读 SDK 本地缓存的策略再判断一次
        if (result[0] == null && userManual) {
            UpdateInfo cached = strategyToUpdateInfo(UpgradeManager.getInstance().getCachedStrategy());
            if (cached != null) {
                Log.i(TAG, "Shiply 网络检查未返回策略，使用本地缓存策略兜底");
                return cached;
            }
        }
        return result[0];
    }

    /** 将 Shiply 策略转换为应用内 UpdateInfo，字段无效时返回 null。 */
    private static UpdateInfo strategyToUpdateInfo(UpgradeStrategy strategy) {
        if (strategy == null) return null;
        ApkBasicInfo apk = strategy.getApkBasicInfo();
        if (apk == null) return null;
        int versionCode = apk.getVersionCode();
        String downloadUrl = apk.getDownloadUrl();
        if (versionCode == 0 || TextUtils.isEmpty(downloadUrl)) return null;

        String updateDesc = "";
        if (strategy.getClientInfo() != null) {
            updateDesc = strategy.getClientInfo().getDescription();
            if (updateDesc == null) updateDesc = "";
        }
        return new UpdateInfo(versionCode, apk.getVersionName(), downloadUrl, updateDesc, "");
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
     * 下载 APK 并安装。交给前台服务 {@link UpdateDownloadService} 在后台下载：
     * 通知栏展示进度并可取消，退出当前页/退后台仍继续。
     */
    private static void downloadAndInstall(Activity activity, UpdateInfo info) {
        Toast.makeText(activity, "开始下载更新...", Toast.LENGTH_SHORT).show();
        UpdateDownloadService.start(activity, info);
    }
}