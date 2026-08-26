package cn.jdnjk.simpfun.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个配置字段的定义（对应 properties 接口 exps 数组中的一个元素）。
 */
public class ConfigFieldDef {
    /** 字段类型："string" | "number" | "boolean" */
    public String type;
    /** 字段显示名 */
    public String name;
    /** 说明文案（可空） */
    public String notice;
    /** 正则表达式，含 1 个捕获组，用于从文件文本中提取 / 回填当前值 */
    public String exp;
    /** 可选：带 rule 数组时用于单选（多选一），如 ["true","false"] */
    public List<String> rules;
    /** 可选：select 类型的选项，每项含 label（显示名）与 value（实际值） */
    public List<ConfigOptionDef> options;

    public static ConfigFieldDef fromJson(JSONObject o) {
        ConfigFieldDef f = new ConfigFieldDef();
        if (o == null) return f;
        f.type = o.optString("type");
        f.name = o.optString("name");
        f.notice = o.optString("notice");
        f.exp = o.optString("exp");
        JSONArray rulesArr = o.optJSONArray("rule");
        if (rulesArr != null) {
            f.rules = new ArrayList<>();
            for (int i = 0; i < rulesArr.length(); i++) {
                f.rules.add(rulesArr.optString(i));
            }
        }
        JSONArray optionsArr = o.optJSONArray("options");
        if (optionsArr != null) {
            f.options = new ArrayList<>();
            for (int i = 0; i < optionsArr.length(); i++) {
                JSONObject opt = optionsArr.optJSONObject(i);
                if (opt != null) {
                    ConfigOptionDef od = new ConfigOptionDef();
                    od.label = opt.optString("label");
                    od.value = opt.optString("value");
                    f.options.add(od);
                }
            }
        }
        return f;
    }

    /** 单个 select 选项。 */
    public static class ConfigOptionDef {
        public String label;
        public String value;
    }
}
