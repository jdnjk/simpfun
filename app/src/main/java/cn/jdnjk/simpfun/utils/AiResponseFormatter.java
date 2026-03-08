package cn.jdnjk.simpfun.utils;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AiResponseFormatter {
    private AiResponseFormatter() {}

    public static String format(JSONObject data) {
        if (data == null) return "未获取到回复内容";
        try {
            Object payload = unwrapPayload(data);
            String content = formatPayload(payload);
            if (content == null || content.trim().isEmpty() || "null".equals(content.trim())) {
                return "未获取到回复内容";
            }
            return content.trim();
        } catch (Exception e) {
            return "解析回复失败: " + e.getMessage();
        }
    }

    private static Object unwrapPayload(Object source) {
        Object current = parseJsonStringIfNeeded(source);
        boolean changed = true;
        while (changed) {
            changed = false;
            if (current instanceof JSONObject obj) {
                Object nested = firstExisting(obj,
                        "data", "history", "list", "records", "items", "result", "message");
                Object parsed = parseJsonStringIfNeeded(nested);
                if (parsed != null && parsed != current) {
                    current = parsed;
                    changed = true;
                }
            }
        }
        return current;
    }

    private static String formatPayload(Object payload) {
        Object parsed = parseJsonStringIfNeeded(payload);
        if (parsed instanceof JSONArray array) {
            return formatArray(array);
        }
        if (parsed instanceof JSONObject obj) {
            return formatObject(obj);
        }
        return parsed == null ? "" : String.valueOf(parsed);
    }

    private static String formatArray(JSONArray array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            Object item = parseJsonStringIfNeeded(array.opt(i));
            String block;
            if (item instanceof JSONObject obj) {
                block = formatHistoryItem(obj);
            } else if (item instanceof JSONArray innerArray) {
                block = formatArray(innerArray);
            } else {
                block = item == null ? "" : String.valueOf(item).trim();
            }
            if (!block.isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n--------------------\n\n");
                sb.append(block);
            }
        }
        return sb.toString();
    }

    private static String formatObject(JSONObject obj) {
        String historyBlock = formatHistoryItem(obj);
        if (!historyBlock.isEmpty()) {
            return historyBlock;
        }
        Object nested = firstExisting(obj, "history", "list", "records", "items", "data", "result", "message");
        if (nested != null && nested != obj) {
            String nestedText = formatPayload(nested);
            if (!nestedText.isEmpty()) {
                return nestedText;
            }
        }
        try {
            return obj.toString(2);
        } catch (Exception ignored) {
            return obj.toString();
        }
    }

    private static String formatHistoryItem(JSONObject item) {
        StringBuilder sb = new StringBuilder();
        String time = firstNonEmpty(item, "answer_time", "created_at", "time", "createdAt", "date");
        String type = firstNonEmpty(item, "type", "action", "role", "kind");
        String supplement = firstNonEmpty(item, "user_txt", "supplement", "question", "prompt", "input", "query");
        String answer = firstNonEmpty(item, "answer_txt");
        if (answer.isEmpty()) {
            answer = findAnswerText(item);
        }

        if (!time.isEmpty()) {
            sb.append("[").append(time).append("]\n");
        }
        if (!type.isEmpty()) {
            if ("log".equalsIgnoreCase(type) || "analyze".equalsIgnoreCase(type)) sb.append("🔍 故障分析\n");
            else if ("answer".equalsIgnoreCase(type)) sb.append("💡 疑难解答\n");
            else if ("history".equalsIgnoreCase(type)) sb.append("🕘 历史记录\n");
            else sb.append(type).append("\n");
        }
        if (!supplement.isEmpty()) {
            sb.append("问: ").append(supplement).append("\n");
        }
        if (!answer.isEmpty()) {
            sb.append("答: ").append(answer);
        }
        return sb.toString().trim();
    }

    private static String findAnswerText(JSONObject item) {
        Object raw = firstExisting(item,
                "answer", "content", "message", "result", "response", "text", "data");
        Object parsed = parseJsonStringIfNeeded(raw);
        if (parsed instanceof JSONObject obj) {
            String nested = firstNonEmpty(obj,
                    "answer_txt", "answer", "content", "message", "result", "response", "text", "data");
            if (!nested.isEmpty()) return nested;
            return formatObject(obj);
        }
        if (parsed instanceof JSONArray array) {
            return formatArray(array);
        }
        return parsed == null ? "" : String.valueOf(parsed).trim();
    }

    private static Object parseJsonStringIfNeeded(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                if (trimmed.startsWith("{")) {
                    return new JSONObject(trimmed);
                }
                return new JSONArray(trimmed);
            } catch (Exception ignored) {
            }
        }
        return text;
    }

    private static Object firstExisting(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.opt(key);
            }
        }
        return null;
    }

    private static String firstNonEmpty(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                Object value = parseJsonStringIfNeeded(obj.opt(key));
                if (value instanceof String text && !text.trim().isEmpty() && !"null".equalsIgnoreCase(text.trim())) {
                    return text.trim();
                }
            }
        }
        return "";
    }
}
