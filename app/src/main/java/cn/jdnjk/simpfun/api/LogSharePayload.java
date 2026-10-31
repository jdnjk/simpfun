package cn.jdnjk.simpfun.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 分享时只发送明确允许的游戏信息，不发送实例地址、账号或凭据。 */
public final class LogSharePayload {
    public static final int MAX_CONTENT_BYTES = 10 * 1024 * 1024;

    private LogSharePayload() {}

    public static final class PreparedLog {
        public final String content;
        public final boolean truncated;
        public final int lines;

        private PreparedLog(String content, boolean truncated, int lines) {
            this.content = content;
            this.truncated = truncated;
            this.lines = lines;
        }
    }

    /** 保留最新日志，同时按 UTF-8 字节和站点行数限制裁剪，不切断代理对。 */
    public static PreparedLog prepare(String original, int maxLines) {
        if (maxLines <= 0) throw new IllegalArgumentException("无效的日志行数上限");
        String text = original == null ? "" : original.replace("\r\n", "\n").replace('\r', '\n');
        int start = 0;
        int breaks = 0;
        int scanEnd = text.endsWith("\n") ? text.length() - 1 : text.length();
        for (int i = scanEnd - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n' && ++breaks == maxLines) {
                start = i + 1;
                break;
            }
        }
        int bytes = 0;
        int byteStart = text.length();
        while (byteStart > start) {
            int cp = text.codePointBefore(byteStart);
            int width = cp <= 0x7F ? 1 : cp <= 0x7FF ? 2 : cp <= 0xFFFF ? 3 : 4;
            if (bytes + width > MAX_CONTENT_BYTES) break;
            bytes += width;
            byteStart -= Character.charCount(cp);
        }
        if (byteStart > start) {
            start = byteStart;
            int nextLine = text.indexOf('\n', start);
            if (nextLine >= 0 && nextLine + 1 < text.length()) start = nextLine + 1;
        }
        String content = text.substring(start);
        int lines = content.isEmpty() ? 0 : 1;
        for (int i = 0; i < content.length() - 1; i++) {
            if (content.charAt(i) == '\n') lines++;
        }
        return new PreparedLog(content, start > 0, lines);
    }

    public static JSONArray metadata(JSONObject detail) throws JSONException {
        JSONArray entries = new JSONArray();
        if (detail == null) return entries;
        JSONObject data = detail.optJSONObject("data");
        if (data != null) detail = data;
        JSONObject info = detail.optJSONObject("game_info");
        if (info == null) info = detail;
        add(entries, "game_name", text(info, "game_name"), "游戏");
        String kind = text(info, "kind_name");
        if (kind.isEmpty()) kind = text(info, "image_name");
        add(entries, "kind_name", kind, "镜像类型");
        add(entries, "version_name", text(info, "version_name"), "版本");
        long game = id(info, detail, "game_id");
        long version = id(info, detail, "version_id");
        long custom = id(info, detail, "custom_id");
        long customVersion = id(info, detail, "custom_version_id");
        boolean isCustom = info.optBoolean("custom", info.optBoolean("is_custom", false));
        long source = (isCustom || version <= 0) && custom > 0 ? custom : version;
        if (game > 0 && source > 0) {
            add(entries, "sources_code", game + "-" + source + "-" + customVersion, "来源代码");
        }
        return entries;
    }

    private static long id(JSONObject info, JSONObject detail, String key) {
        long value = info.optLong(key, 0);
        return Math.max(0, value > 0 ? value : detail.optLong(key, 0));
    }

    private static String text(JSONObject json, String key) {
        Object value = json.opt(key);
        if (!(value instanceof String)) return "";
        String result = ((String) value).trim();
        return "null".equalsIgnoreCase(result) ? "" : result;
    }

    private static void add(JSONArray entries, String key, String value, String label) throws JSONException {
        if (value.isEmpty()) return;
        int count = value.codePointCount(0, value.length());
        if (count > 1024) value = value.substring(0, value.offsetByCodePoints(0, 1024));
        entries.put(new JSONObject().put("key", key).put("value", value)
                .put("label", label).put("visible", true));
    }
}
