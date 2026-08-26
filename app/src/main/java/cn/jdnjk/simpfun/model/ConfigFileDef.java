package cn.jdnjk.simpfun.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个配置文件的定义（对应 properties 接口返回数组中的一个元素）。
 */
public class ConfigFileDef {
    /** 文件路径，如 "/config.json" */
    public String file;
    /** 展示名，如 "启动配置" */
    public String name;
    /** 说明文案（可空） */
    public String notice;
    /** 字段列表（由 exps 解析） */
    public List<ConfigFieldDef> fields;

    public static ConfigFileDef fromJson(JSONObject o) {
        ConfigFileDef d = new ConfigFileDef();
        if (o == null) return d;
        d.file = o.optString("file");
        d.name = o.optString("name");
        d.notice = o.optString("notice");
        d.fields = new ArrayList<>();
        JSONArray exps = o.optJSONArray("exps");
        if (exps != null) {
            for (int i = 0; i < exps.length(); i++) {
                JSONObject e = exps.optJSONObject(i);
                if (e != null) {
                    d.fields.add(ConfigFieldDef.fromJson(e));
                }
            }
        }
        return d;
    }

    /** 解析 properties 接口返回的配置定义数组。 */
    public static List<ConfigFileDef> parse(JSONArray array) {
        List<ConfigFileDef> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) {
                list.add(fromJson(o));
            }
        }
        return list;
    }
}
