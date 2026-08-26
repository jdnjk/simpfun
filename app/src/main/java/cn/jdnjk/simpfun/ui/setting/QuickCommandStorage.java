package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.model.QuickCommandNode;

/**
 * 自定义快捷指令存储。
 * 存储于应用外部私有目录的 quickcommand.json：
 * /storage/emulated/0/Android/data/{包名}/files/quickcommand.json
 * <p>
 * 文件为 JSONArray 文本，每条一个 {@link QuickCommandNode}（type=item 自定义指令）。
 * <p>
 * 首次加载时自动从旧 SharedPreferences 迁移，迁移后删除旧 key。
 */
public class QuickCommandStorage {

    private static final String FILE_NAME = "quickcommand.json";
    private static final String OLD_SP_NAME = "setting_sp";
    private static final String OLD_KEY = "quick_command_custom";
    private final Context appContext;

    public QuickCommandStorage(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * 返回存储目录（应用外部私有目录 files/），目录不存在时创建。
     */
    private File getStorageDir() {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) {
            // 外部存储不可用时回退到内部 files/
            dir = appContext.getFilesDir();
        }
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private File getDataFile() {
        return new File(getStorageDir(), FILE_NAME);
    }

    /**
     * 加载所有自定义指令。
     * 若文件不存在但旧 SP 有数据，自动迁移并删除旧 key。
     */
    public List<QuickCommandNode> loadAll() {
        List<QuickCommandNode> list = new ArrayList<>();
        File file = getDataFile();
        if (file.exists()) {
            // 直接从文件读取
            return readFromFile(file);
        }
        // 文件不存在 → 尝试从旧 SP 迁移
        List<QuickCommandNode> migrated = migrateFromSp();
        if (migrated != null) {
            // 写入文件并删除旧 key
            saveAll(migrated);
            deleteOldSpKey();
            return migrated;
        }
        return list;
    }

    /**
     * 从旧 SharedPreferences 读取数据。
     * 返回 null 表示旧 SP 无数据或已处理过。
     */
    private List<QuickCommandNode> migrateFromSp() {
        SharedPreferences sp = appContext.getSharedPreferences(OLD_SP_NAME, Context.MODE_PRIVATE);
        String raw = sp.getString(OLD_KEY, null);
        if (raw == null || raw.isEmpty() || "[]".equals(raw)) return null;
        List<QuickCommandNode> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    QuickCommandNode node = QuickCommandNode.fromJson(obj);
                    node.isCustom = true;
                    list.add(node);
                }
            }
        } catch (Exception ignored) {
        }
        return list.isEmpty() ? null : list;
    }

    private void deleteOldSpKey() {
        SharedPreferences sp = appContext.getSharedPreferences(OLD_SP_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(OLD_KEY).apply();
    }

    private List<QuickCommandNode> readFromFile(File file) {
        List<QuickCommandNode> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            if (read <= 0) return list;
            String raw = new String(buf, 0, read, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    QuickCommandNode node = QuickCommandNode.fromJson(obj);
                    node.isCustom = true;
                    list.add(node);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * 添加自定义指令。
     */
    public void add(QuickCommandNode node) {
        List<QuickCommandNode> list = loadAll();
        list.add(node);
        saveAll(list);
    }

    /**
     * 更新指定索引的自定义指令。
     */
    public void update(int index, QuickCommandNode node) {
        List<QuickCommandNode> list = loadAll();
        if (index >= 0 && index < list.size()) {
            list.set(index, node);
            saveAll(list);
        }
    }

    /**
     * 删除指定索引的自定义指令。
     */
    public void delete(int index) {
        List<QuickCommandNode> list = loadAll();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveAll(list);
        }
    }

    private void saveAll(List<QuickCommandNode> list) {
        JSONArray array = new JSONArray();
        for (QuickCommandNode node : list) {
            array.put(node.toJson());
        }
        String json = array.toString();
        try (FileOutputStream fos = new FileOutputStream(getDataFile())) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            // 写入失败静默处理
        }
    }
}