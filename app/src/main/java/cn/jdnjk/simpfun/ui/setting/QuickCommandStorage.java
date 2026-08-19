package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.model.QuickCommandNode;

/**
 * 自定义快捷指令存储。
 * 存储于 SharedPreferences "setting_sp"，key "quick_command_custom"。
 */
public class QuickCommandStorage {

    private static final String KEY = "quick_command_custom";
    private final SettingsSaveManager saveManager;

    public QuickCommandStorage(Context context) {
        this.saveManager = SettingsSaveManager.getInstance(context.getApplicationContext());
    }

    /**
     * 加载所有自定义指令。
     */
    public List<QuickCommandNode> loadAll() {
        String raw = saveManager.getString(KEY, "[]");
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
        saveManager.putString(KEY, array.toString());
    }
}