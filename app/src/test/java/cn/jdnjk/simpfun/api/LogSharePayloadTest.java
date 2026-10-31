package cn.jdnjk.simpfun.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class LogSharePayloadTest {
    @Test
    public void keepsSmallLogsAndNormalizesNewlines() {
        LogSharePayload.PreparedLog log = LogSharePayload.prepare("first\r\nlast\r", 25000);
        assertEquals("first\nlast\n", log.content);
        assertEquals(2, log.lines);
        assertFalse(log.truncated);
    }

    @Test
    public void keepsNewestLinesWithAndWithoutTrailingNewline() {
        for (String suffix : new String[]{"", "\n"}) {
            LogSharePayload.PreparedLog log = LogSharePayload.prepare("old\nnew\nlast" + suffix, 2);
            assertEquals("new\nlast" + suffix, log.content);
            assertEquals(2, log.lines);
            assertTrue(log.truncated);
        }
    }

    @Test
    public void siteLineLimitsDiffer() {
        String text = "entry\n".repeat(30000);
        assertEquals(25000, LogSharePayload.prepare(text, LogShareApi.Site.MCLO_GS.maxLines).lines);
        assertEquals(30000, LogSharePayload.prepare(text, LogShareApi.Site.LOGSHARE_CN.maxLines).lines);
    }

    @Test
    public void utf8ByteLimitDoesNotSplitChineseOrSurrogatePairs() {
        for (String unit : new String[]{"世", "😀"}) {
            int width = unit.getBytes(StandardCharsets.UTF_8).length;
            String input = unit.repeat(LogSharePayload.MAX_CONTENT_BYTES / width + 3);
            LogSharePayload.PreparedLog log = LogSharePayload.prepare(input, 25000);
            assertTrue(log.truncated);
            assertTrue(log.content.getBytes(StandardCharsets.UTF_8).length <= LogSharePayload.MAX_CONTENT_BYTES);
            assertFalse(Character.isLowSurrogate(log.content.charAt(0)));
            assertTrue(log.content.endsWith(unit));
            assertEquals(1, log.lines);
        }
    }

    @Test
    public void byteTruncationPrefersCompleteRecentLines() {
        String input = "x".repeat(LogSharePayload.MAX_CONTENT_BYTES) + "\nlast\n";
        assertEquals("last\n", LogSharePayload.prepare(input, 25000).content);
    }

    @Test
    public void emptyLogsRemainEmpty() {
        assertEquals("", LogSharePayload.prepare(null, 25000).content);
        assertEquals(0, LogSharePayload.prepare("", 25000).lines);
    }

    @Test
    public void metadataUsesOnlyAllowlistedGameFields() throws Exception {
        JSONObject info = new JSONObject().put("game_name", "Terraria").put("kind_name", "泰拉瑞亚 | 原版")
                .put("version_name", "1.4.5.8").put("game_id", 7).put("version_id", 8)
                .put("custom_version_id", 9).put("password", "NEVER-SHARE");
        JSONObject detail = new JSONObject().put("game_info", info).put("ip", "192.0.2.1")
                .put("token", "NEVER-SHARE").put("id", 123);
        JSONArray metadata = LogSharePayload.metadata(detail);
        assertEquals(4, metadata.length());
        assertEquals("Terraria", value(metadata, "game_name"));
        assertEquals("7-8-9", value(metadata, "sources_code"));
        assertFalse(metadata.toString().contains("NEVER-SHARE"));
        assertFalse(metadata.toString().contains("192.0.2.1"));
        for (int i = 0; i < metadata.length(); i++) assertTrue(metadata.getJSONObject(i).getBoolean("visible"));
    }

    @Test
    public void customSourcePrefersCustomIdAndFallsBackToImageName() throws Exception {
        JSONObject info = new JSONObject().put("game_id", "7").put("version_id", 8).put("custom_id", 22)
                .put("custom_version_id", 33).put("custom", true).put("image_name", "第三方镜像");
        JSONArray metadata = LogSharePayload.metadata(new JSONObject().put("data", new JSONObject().put("game_info", info)));
        assertEquals("7-22-33", value(metadata, "sources_code"));
        assertEquals("第三方镜像", value(metadata, "kind_name"));
    }

    @Test
    public void missingIdsDoNotPublishInventedSourceCode() throws Exception {
        assertEquals(0, LogSharePayload.metadata(null).length());
        assertEquals(0, LogSharePayload.metadata(new JSONObject()).length());
        JSONObject detail = new JSONObject().put("game_id", 1).put("version_id", 2);
        assertEquals("1-2-0", value(LogSharePayload.metadata(detail), "sources_code"));
    }

    @Test
    public void metadataLengthIsCapped() throws Exception {
        JSONArray metadata = LogSharePayload.metadata(new JSONObject().put("game_name", "a".repeat(1200)));
        assertEquals(1024, value(metadata, "game_name").length());
    }

    private static String value(JSONArray array, String key) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            if (key.equals(entry.getString("key"))) return entry.getString("value");
        }
        fail("Missing metadata " + key);
        return "";
    }
}
