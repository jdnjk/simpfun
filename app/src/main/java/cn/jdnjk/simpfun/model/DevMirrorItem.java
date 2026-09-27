package cn.jdnjk.simpfun.model;

import org.json.JSONObject;

public class DevMirrorItem {
    private final int id;
    private final String name;
    private final String description;

    public DevMirrorItem(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
    }

    public static DevMirrorItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new DevMirrorItem(
                obj.optInt("id", -1),
                obj.optString("name", ""),
                obj.optString("description", ""));
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
