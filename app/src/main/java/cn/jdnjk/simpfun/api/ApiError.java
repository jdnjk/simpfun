package cn.jdnjk.simpfun.api;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Response;
import okhttp3.ResponseBody;

/** 失败响应优先显示服务端的说明；不能因为 HTTP 非 2xx 就丢弃 JSON 中的 msg。 */
public final class ApiError {
    private ApiError() {}

    /** 仅用于确定失败且不再读取响应体的分支；消费并关闭响应体，不修改 HTTP 状态。 */
    public static String getMessage(Response response) {
        return getMessage(response, "HTTP 错误: " + response.code());
    }

    public static String getMessage(Response response, String fallback) {
        try (ResponseBody body = response.body()) {
            return body == null ? fallback : getMessage(body.string(), fallback);
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }

    /** 已读取的响应体应走此重载，不能再次调用 ResponseBody.string()。 */
    public static String getMessage(String body, String fallback) {
        if (body == null || body.trim().isEmpty()) return fallback;
        String text = body.trim();
        if (text.startsWith("﻿")) text = text.substring(1);
        try {
            return getMessage(new JSONObject(text), fallback);
        } catch (JSONException e) {
            // HTML、纯文本或损坏的 JSON 只显示兜底说明，不直接把整段响应展示给用户。
            return fallback;
        }
    }

    public static String getMessage(JSONObject json, String fallback) {
        if (json == null) return fallback;
        // msg 是简幻欢 API 的标准字段；message/error 兼容上传节点等其它接口。
        for (String key : new String[]{"msg", "message", "error"}) {
            Object value = json.opt(key);
            if (value instanceof String) {
                String message = ((String) value).trim();
                if (!message.isEmpty() && !"null".equalsIgnoreCase(message)) return message;
            }
        }
        return fallback;
    }
}
