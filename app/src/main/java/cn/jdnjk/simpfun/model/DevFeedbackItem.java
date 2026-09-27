package cn.jdnjk.simpfun.model;

import org.json.JSONObject;

public class DevFeedbackItem {
    private final int id;
    private final String versionName;
    private final String createTime;
    private final String userQq;
    private final boolean like;
    private final boolean dislike;
    private final String comment;

    public DevFeedbackItem(int id, String versionName, String createTime, String userQq,
                           boolean like, boolean dislike, String comment) {
        this.id = id;
        this.versionName = versionName == null ? "" : versionName;
        this.createTime = createTime == null ? "" : createTime;
        this.userQq = userQq == null ? "" : userQq;
        this.like = like;
        this.dislike = dislike;
        this.comment = comment == null ? "" : comment;
    }

    public static DevFeedbackItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new DevFeedbackItem(
                obj.optInt("id", -1),
                obj.optString("version_name", ""),
                obj.optString("create_time", ""),
                obj.optString("user_qq", ""),
                obj.optBoolean("like", false),
                obj.optBoolean("dislike", false),
                obj.optString("comment", ""));
    }

    public int getId() { return id; }
    public String getVersionName() { return versionName; }
    public String getCreateTime() { return createTime; }
    public String getUserQq() { return userQq; }
    public boolean isLike() { return like; }
    public boolean isDislike() { return dislike; }
    public String getComment() { return comment; }

    public String getFormattedTime() {
        String t = createTime;
        if (t.length() > 16 && t.charAt(10) == 'T') {
            return t.substring(0, 10) + " " + t.substring(11, 16);
        }
        if (t.length() > 16) return t.substring(0, 16);
        return t;
    }
}
