package cn.jdnjk.simpfun.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 快捷指令节点 — 表示树结构中的分类（list）或指令（item）。
 */
public class QuickCommandNode {

    public String type;          // "list" 或 "item"
    public String name;          // 显示名称
    public String notice;        // 说明 HTML（仅 item）
    public String command;       // 命令模板，如 "op !{1}"（仅 item，单动作）
    public List<String> actions; // 多动作序列（仅自定义指令，每项一条命令模板）
    public boolean delay;        // 是否需要延迟
    public boolean quote;        // 参数值是否加引号（item 级）
    public List<QuickCommandNode> children;   // 子节点（仅 type=list）
    public List<Param> params;   // 参数定义（仅 item）

    // 自定义指令标识
    public boolean isCustom;

    public QuickCommandNode() {
        children = new ArrayList<>();
        params = new ArrayList<>();
        actions = new ArrayList<>();
    }

    /**
     * 从 JSONObject 递归解析节点。
     */
    public static QuickCommandNode fromJson(JSONObject obj) {
        QuickCommandNode node = new QuickCommandNode();
        node.type = obj.optString("type", "item");
        node.name = obj.optString("name", "未命名");

        if ("list".equals(node.type)) {
            node.children = new ArrayList<>();
            JSONArray list = obj.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject child = list.optJSONObject(i);
                    if (child != null) {
                        node.children.add(QuickCommandNode.fromJson(child));
                    }
                }
            }
        } else {
            node.notice = obj.optString("notice", "");
            node.command = obj.optString("command", "");
            node.delay = obj.optBoolean("delay", false);
            node.quote = obj.optBoolean("quote", false);
            node.params = new ArrayList<>();

            // 兼容多动作（自定义指令）
            JSONArray actionsArray = obj.optJSONArray("actions");
            node.actions = new ArrayList<>();
            if (actionsArray != null) {
                for (int i = 0; i < actionsArray.length(); i++) {
                    node.actions.add(actionsArray.optString(i, ""));
                }
            }
            // 如果 actions 为空但 command 有值，作为单动作
            if (node.actions.isEmpty() && !node.command.isEmpty()) {
                node.actions.add(node.command);
            }

            JSONArray paramsArray = obj.optJSONArray("params");
            if (paramsArray != null) {
                for (int i = 0; i < paramsArray.length(); i++) {
                    JSONObject p = paramsArray.optJSONObject(i);
                    if (p != null) {
                        node.params.add(Param.fromJson(p));
                    }
                }
            }
        }

        return node;
    }

    /**
     * 将自定义指令序列化为 JSONObject。
     */
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", "item");
            obj.put("name", name != null ? name : "");
            obj.put("command", command != null ? command : "");
            obj.put("notice", "");
            obj.put("delay", delay);
            obj.put("quote", quote);

            JSONArray actionsArray = new JSONArray();
            if (actions != null) {
                for (String a : actions) {
                    actionsArray.put(a != null ? a : "");
                }
            }
            obj.put("actions", actionsArray);

            JSONArray paramsArray = new JSONArray();
            for (Param p : params) {
                paramsArray.put(p.toJson());
            }
            obj.put("params", paramsArray);
        } catch (Exception ignored) {
        }
        return obj;
    }

    /**
     * 参数定义。
     */
    public static class Param {
        public String name;       // 参数名
        public String type;       // "string" 或 "select"
        public String hint;       // 输入提示
        public boolean quote;     // 该参数值是否加引号
        public List<Option> options; // select 选项

        public Param() {
            options = new ArrayList<>();
        }

        public static Param fromJson(JSONObject obj) {
            Param param = new Param();
            param.name = obj.optString("name", "参数");
            param.type = obj.optString("type", "string");
            param.hint = obj.optString("hint", "");
            param.quote = obj.optBoolean("quote", false);

            JSONArray optionsArray = obj.optJSONArray("options");
            if (optionsArray != null) {
                param.options = new ArrayList<>();
                for (int i = 0; i < optionsArray.length(); i++) {
                    JSONObject opt = optionsArray.optJSONObject(i);
                    if (opt != null) {
                        param.options.add(Option.fromJson(opt));
                    }
                }
            }

            return param;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", name != null ? name : "");
                obj.put("type", type != null ? type : "string");
                obj.put("hint", hint != null ? hint : "");
                obj.put("quote", quote);
                JSONArray optionsArray = new JSONArray();
                for (Option o : options) {
                    optionsArray.put(o.toJson());
                }
                obj.put("options", optionsArray);
            } catch (Exception ignored) {
            }
            return obj;
        }
    }

    /**
     * Select 选项。
     */
    public static class Option {
        public String label;
        public String value;

        public static Option fromJson(JSONObject obj) {
            Option opt = new Option();
            opt.label = obj.optString("label", "");
            opt.value = obj.optString("value", "");
            return opt;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("label", label != null ? label : "");
                obj.put("value", value != null ? value : "");
            } catch (Exception ignored) {
            }
            return obj;
        }
    }
}