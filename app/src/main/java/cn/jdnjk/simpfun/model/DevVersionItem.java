package cn.jdnjk.simpfun.model;

import org.json.JSONObject;

public class DevVersionItem {
    private final int id;
    private final String name;
    private final String description;
    private final String recommendSetting;
    private final boolean state;
    private final String createTime;
    private final String size;
    private final String restoreTimes;
    private final String lastRestoreTime;
    private final int like;
    private final int dislike;
    private final String oneKey;
    private final String properties;

    public DevVersionItem(int id, String name, String description, String recommendSetting,
                          boolean state, String createTime, String size, String restoreTimes,
                          String lastRestoreTime, int like, int dislike, String oneKey, String properties) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.recommendSetting = recommendSetting == null ? "" : recommendSetting;
        this.state = state;
        this.createTime = createTime == null ? "" : createTime;
        this.size = size == null ? "" : size;
        this.restoreTimes = restoreTimes == null ? "0" : restoreTimes;
        this.lastRestoreTime = lastRestoreTime == null ? "" : lastRestoreTime;
        this.like = like;
        this.dislike = dislike;
        this.oneKey = oneKey == null ? "" : oneKey;
        this.properties = properties == null ? "" : properties;
    }

    public static DevVersionItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new DevVersionItem(
                obj.optInt("id", -1),
                obj.optString("name", ""),
                obj.optString("description", ""),
                obj.optString("recommend_setting", ""),
                obj.optBoolean("state", false),
                obj.optString("create_time", ""),
                obj.optString("size", ""),
                obj.optString("restore_times", "0"),
                obj.optString("last_restore_time", ""),
                obj.optInt("like", 0),
                obj.optInt("dislike", 0),
                obj.optString("one_key", ""),
                obj.optString("properties", ""));
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRecommendSetting() { return recommendSetting; }
    public boolean isState() { return state; }
    public String getCreateTime() { return createTime; }
    public String getSize() { return size; }
    public String getRestoreTimes() { return restoreTimes; }
    public String getLastRestoreTime() { return lastRestoreTime; }
    public int getLike() { return like; }
    public int getDislike() { return dislike; }
    public String getOneKey() { return oneKey; }
    public String getProperties() { return properties; }
}
