package cn.jdnjk.simpfun.api.ins;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft 自动安装第三方数据源：
 * MC 版本清单 / Forge / Fabric / NeoForge 安装器版本列表。
 *
 * <p>官方源请求失败（超时、HTTP 错误、解析失败）时自动回退 BMCLAPI 镜像：
 * <ul>
 *     <li>版本清单: launchermeta.mojang.com → bmclapi2.bangbang93.com</li>
 *     <li>Forge: maven.minecraftforge.net(maven-metadata.xml) → bmclapi /forge/minecraft/{mc}</li>
 *     <li>Fabric: meta.fabricmc.net → bmclapi /fabric-meta/v2/versions/loader/{mc}</li>
 *     <li>NeoForge: maven.neoforged.net → bmclapi /neoforge/list/{mc}</li>
 * </ul>
 * 所有回调均在主线程执行。
 */
public final class McInstallApi {

    /** 版本清单回调。 */
    public interface ManifestCallback {
        void onSuccess(JSONObject manifest);
        void onFailure(String message);
    }

    /**
     * 安装器版本列表回调。
     * onSuccess 的数组元素形如 {@code {"name":"51.0.33","tag":"稳定版"(可选)}}。
     */
    public interface VersionListCallback {
        void onSuccess(JSONArray items);
        void onFailure(String message);
    }

    private interface HttpCallback {
        void onSuccess(String body);
        void onFailure(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String MANIFEST_OFFICIAL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String MANIFEST_MIRROR = "https://bmclapi2.bangbang93.com/mc/game/version_manifest.json";
    private static final String FORGE_OFFICIAL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String FORGE_MIRROR = "https://bmclapi2.bangbang93.com/forge/minecraft/";
    private static final String FABRIC_OFFICIAL = "https://meta.fabricmc.net/v2/versions/loader/";
    private static final String FABRIC_MIRROR = "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/";
    private static final String NEOFORGE_OFFICIAL = "https://maven.neoforged.net/api/maven/versions/releases/net%2Fneoforged%2Fneoforge";
    private static final String NEOFORGE_MIRROR = "https://bmclapi2.bangbang93.com/neoforge/list/";

    private static final Pattern FORGE_VERSION_TAG = Pattern.compile("<version>([^<]+)</version>");

    private McInstallApi() {
    }

    /** 获取 Mojang 版本清单（官方源失败时回退 BMCLAPI）。 */
    public static void fetchVersionManifest(ManifestCallback cb) {
        getWithFallback(MANIFEST_OFFICIAL, MANIFEST_MIRROR, new HttpCallback() {
            @Override
            public void onSuccess(String body) {
                try {
                    cb.onSuccess(new JSONObject(body));
                } catch (Exception e) {
                    cb.onFailure("数据解析错误");
                }
            }

            @Override
            public void onFailure(String message) {
                cb.onFailure(message);
            }
        });
    }

    /**
     * Forge 安装器版本列表。
     * 官方 maven-metadata.xml 包含所有 MC 版本的构建，按 {@code {mcVersion}-} 前缀过滤；
     * BMCLAPI 镜像按 mcversion 过滤。官方源与镜像并发请求，先到先得。
     */
    public static void fetchForgeVersions(String mcVersion, VersionListCallback cb) {
        String prefix = mcVersion + "-";
        getFirstSuccess(FORGE_OFFICIAL, FORGE_MIRROR + mcVersion, new HttpCallback() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray out = new JSONArray();
                    String trimmed = body.trim();
                    if (trimmed.startsWith("<")) {
                        Matcher m = FORGE_VERSION_TAG.matcher(body);
                        while (m.find()) {
                            String v = m.group(1);
                            if (v != null && v.startsWith(prefix)) {
                                out.put(item(v, null));
                            }
                        }
                    } else {
                        JSONArray arr = new JSONArray(trimmed);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            String mv = o.optString("mcversion", mcVersion);
                            if (!mcVersion.equals(mv)) continue;
                            out.put(item(o.optString("version"), null));
                        }
                    }
                    if (out.length() == 0) {
                        cb.onFailure("未找到该 MC 版本对应的 Forge");
                        return;
                    }
                    cb.onSuccess(out);
                } catch (Exception e) {
                    cb.onFailure("数据解析错误");
                }
            }

            @Override
            public void onFailure(String message) {
                cb.onFailure(message);
            }
        });
    }

    /** Fabric 安装器版本列表（loader 版本，标记稳定/测试）。官方源与镜像并发请求，先到先得。 */
    public static void fetchFabricVersions(String mcVersion, VersionListCallback cb) {
        getFirstSuccess(FABRIC_OFFICIAL + mcVersion, FABRIC_MIRROR + mcVersion, new HttpCallback() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray arr = new JSONArray(body);
                    JSONArray out = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        JSONObject loader = o == null ? null : o.optJSONObject("loader");
                        if (loader == null) continue;
                        String version = loader.optString("version");
                        if (version.isEmpty()) continue;
                        out.put(item(version, loader.optBoolean("stable") ? "稳定版" : "测试版"));
                    }
                    if (out.length() == 0) {
                        cb.onFailure("未找到该 MC 版本对应的 Fabric");
                        return;
                    }
                    cb.onSuccess(out);
                } catch (Exception e) {
                    cb.onFailure("数据解析错误");
                }
            }

            @Override
            public void onFailure(String message) {
                cb.onFailure(message);
            }
        });
    }

    /**
     * NeoForge 安装器版本列表。
     * 版本号前缀映射：1.21.1 → 21.1，26.2 → 26.2。官方源与镜像并发请求，先到先得。
     */
    public static void fetchNeoForgeVersions(String mcVersion, VersionListCallback cb) {
        String prefix = mcVersion.startsWith("1.") ? mcVersion.substring(2) : mcVersion;
        getFirstSuccess(NEOFORGE_OFFICIAL, NEOFORGE_MIRROR + mcVersion, new HttpCallback() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray out = new JSONArray();
                    String trimmed = body.trim();
                    if (trimmed.startsWith("{")) {
                        JSONArray versions = new JSONObject(trimmed).optJSONArray("versions");
                        if (versions != null) {
                            for (int i = 0; i < versions.length(); i++) {
                                String v = versions.optString(i, "");
                                if (matchesPrefix(v, prefix)) {
                                    out.put(item(v, v.endsWith("-beta") ? "测试版" : null));
                                }
                            }
                        }
                    } else {
                        JSONArray arr = new JSONArray(trimmed);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            if (!mcVersion.equals(o.optString("mcversion", mcVersion))) continue;
                            String v = o.optString("version");
                            if (!matchesPrefix(v, prefix)) continue;
                            out.put(item(v, v.endsWith("-beta") ? "测试版" : null));
                        }
                    }
                    if (out.length() == 0) {
                        cb.onFailure("未找到该 MC 版本对应的 NeoForge");
                        return;
                    }
                    cb.onSuccess(out);
                } catch (Exception e) {
                    cb.onFailure("数据解析错误");
                }
            }

            @Override
            public void onFailure(String message) {
                cb.onFailure(message);
            }
        });
    }

    private static boolean matchesPrefix(String version, String prefix) {
        if (version == null || version.isEmpty()) return false;
        return version.equals(prefix) || version.startsWith(prefix + ".");
    }

    private static JSONObject item(String name, @Nullable String tag) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            if (tag != null && !tag.isEmpty()) o.put("tag", tag);
        } catch (Exception ignored) {
        }
        return o;
    }

    /** 先请求官方源，失败后请求镜像源；两者都失败时以官方源的错误回调。 */
    private static void getWithFallback(String primary, String mirror, HttpCallback cb) {
        get(primary, new HttpCallback() {
            @Override
            public void onSuccess(String body) {
                cb.onSuccess(body);
            }

            @Override
            public void onFailure(String message) {
                get(mirror, new HttpCallback() {
                    @Override
                    public void onSuccess(String body) {
                        cb.onSuccess(body);
                    }

                    @Override
                    public void onFailure(String mirrorMessage) {
                        cb.onFailure(message);
                    }
                });
            }
        });
    }

    /**
     * 官方源与镜像源并发请求，任一先成功即采用其结果（先到先得）；
     * 两者都失败才回调失败。
     */
    private static void getFirstSuccess(String primary, String mirror, HttpCallback cb) {
        AtomicBoolean settled = new AtomicBoolean(false);
        AtomicInteger failures = new AtomicInteger(0);
        HttpCallback racing = new HttpCallback() {
            @Override
            public void onSuccess(String body) {
                if (settled.compareAndSet(false, true)) {
                    cb.onSuccess(body);
                }
            }

            @Override
            public void onFailure(String message) {
                if (settled.get()) return;
                if (failures.incrementAndGet() >= 2) {
                    cb.onFailure(message);
                }
            }
        };
        get(primary, racing);
        get(mirror, racing);
    }

    private static void get(String url, HttpCallback cb) {
        Request request = new Request.Builder().url(url).build();
        ApiClient.getInstance().getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                MAIN.post(() -> cb.onFailure("网络失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "";
                MAIN.post(() -> {
                    if (code >= 200 && code < 300 && !body.isEmpty()) {
                        cb.onSuccess(body);
                    } else {
                        cb.onFailure("HTTP " + code);
                    }
                });
            }
        });
    }
}
