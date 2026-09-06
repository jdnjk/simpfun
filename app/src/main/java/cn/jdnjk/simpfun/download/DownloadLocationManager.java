package cn.jdnjk.simpfun.download;

import android.content.Context;

import cn.jdnjk.simpfun.ui.setting.SettingsSaveManager;
import cn.jdnjk.simpfun.utils.StoragePermissionHelper;

/**
 * 下载保存位置的设置项。
 *
 * <p>持久值刻意是 1-based：{@link SettingsSaveManager} 没有 contains()，
 * 无法区分「没存过」和「存了 0」，所以用 0 表示未设置，读取时按当前权限状态推导默认值。
 */
public class DownloadLocationManager {
    public static final int MODE_APP_PRIVATE = 1;
    public static final int MODE_PUBLIC_DOWNLOADS = 2;
    public static final int MODE_CUSTOM_TREE = 3;

    private static final String KEY_MODE = "download_location_mode";
    private static final String KEY_CUSTOM_TREE_URI = "download_location_tree_uri";
    private static final int MODE_UNSET = 0;

    private final Context appContext;
    private final SettingsSaveManager saveManager;

    public DownloadLocationManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.saveManager = SettingsSaveManager.getInstance(this.appContext);
    }

    /**
     * 当前生效的模式。用户没改过时按权限推导：能直接写公共下载目录就用它，
     * 否则用应用私有目录——这样默认路径永远不需要弹权限请求。
     */
    public int getMode() {
        int stored = saveManager.getInt(KEY_MODE, MODE_UNSET);
        if (stored == MODE_APP_PRIVATE || stored == MODE_PUBLIC_DOWNLOADS || stored == MODE_CUSTOM_TREE) {
            return stored;
        }
        return StoragePermissionHelper.canWritePublicDownloads(appContext)
                ? MODE_PUBLIC_DOWNLOADS
                : MODE_APP_PRIVATE;
    }

    public void setMode(int mode) {
        if (mode != MODE_APP_PRIVATE && mode != MODE_PUBLIC_DOWNLOADS && mode != MODE_CUSTOM_TREE) {
            return;
        }
        saveManager.putInt(KEY_MODE, mode);
    }

    /** 用户是否显式选过位置。false 表示 {@link #getMode()} 返回的是按权限推导的默认值。 */
    public boolean isModeExplicit() {
        return saveManager.getInt(KEY_MODE, MODE_UNSET) != MODE_UNSET;
    }

    /** 自定义目录的 SAF tree Uri，未设置返回空串（不用 null：putString(null) 在 SaveManager 里会静默失效）。 */
    public String getCustomTreeUri() {
        String value = saveManager.getString(KEY_CUSTOM_TREE_URI, "");
        return value == null ? "" : value;
    }

    public void setCustomTreeUri(String uri) {
        saveManager.putString(KEY_CUSTOM_TREE_URI, uri == null ? "" : uri);
    }
}
