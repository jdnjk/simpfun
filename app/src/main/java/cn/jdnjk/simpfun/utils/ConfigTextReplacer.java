package cn.jdnjk.simpfun.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置文件文本的正则提取 / 回填工具。
 *
 * <p>字段定义里的 exp 是含 1 个捕获组的正则，例如 JSON 的 {@code "version": "(.+)"}
 * 或 properties 的 {@code max-players=([0-9]+)}。提取时取第一个匹配的捕获组1；
 * 回填时只替换捕获组1 本身，其余文本（引号 / key / 缩进 / 换行符）原样保留——
 * 因此天然兼容 JSON 与 properties 两种格式，且不会破坏 server.properties 的 CRLF。
 */
public final class ConfigTextReplacer {

    private static final Map<String, Pattern> PATTERN_CACHE = new HashMap<>();

    private ConfigTextReplacer() {
    }

    private static Pattern compile(String exp) {
        Pattern p = PATTERN_CACHE.get(exp);
        if (p == null) {
            p = Pattern.compile(exp);
            PATTERN_CACHE.put(exp, p);
        }
        return p;
    }

    /**
     * 从文件文本中提取字段当前值。
     *
     * @return 第一个匹配的捕获组1；无匹配返回 null（捕获组1 可能为空串，用 null 表示"未找到"）
     */
    public static String extractValue(String text, String exp) {
        if (text == null || exp == null || exp.isEmpty()) return null;
        try {
            Matcher m = compile(exp).matcher(text);
            return (m.find() && m.groupCount() >= 1) ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把捕获组1 替换为 {@code newValue}，其余文本原样保留。
     *
     * @return 替换后的完整文本；无匹配返回 null（调用方据此跳过该字段，不强行插入）
     */
    public static String replaceValue(String text, String exp, String newValue) {
        if (text == null || exp == null || exp.isEmpty()) return null;
        try {
            Matcher m = compile(exp).matcher(text);
            if (!m.find() || m.groupCount() < 1) return null;
            int start = m.start(1);
            int end = m.end(1);
            String value = sanitizeSingleLine(newValue);
            if (isQuotedGroup(text, start, end)) {
                value = escapeJson(value);
            }
            return text.substring(0, start) + value + text.substring(end);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断捕获组前后是否都是引号（即 JSON 字符串字段），是则回填前需做 JSON 转义。
     */
    private static boolean isQuotedGroup(String text, int start, int end) {
        if (start <= 0 || end >= text.length()) return false;
        return text.charAt(start - 1) == '"' && text.charAt(end) == '"';
    }

    private static String escapeJson(String value) {
        if (value == null) return null;
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sanitizeSingleLine(String value) {
        if (value == null) return null;
        return value.replace("\r\n", "").replace("\r", "").replace("\n", "");
    }
}
