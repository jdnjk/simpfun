package cn.jdnjk.simpfun.api;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiErrorTest {
    @Test
    public void non2xxResponsesShowServerMsgAndKeepHttpFailure() {
        for (int status : new int[]{400, 401, 402, 403, 404, 409, 422, 429, 500, 502, 503}) {
            try (Response response = response(status, "{\"code\":" + status + ",\"msg\":\"具体失败原因\"}")) {
                assertEquals("HTTP " + status, "具体失败原因", ApiError.getMessage(response));
                assertEquals(status, response.code());
                assertFalse(response.isSuccessful());
            }
        }
    }

    @Test
    public void businessCodeDoesNotOverrideErrorMessageOrHttpStatus() {
        try (Response response = response(500, "{\"code\":200,\"msg\":\"服务器仍然失败\"}")) {
            assertEquals("服务器仍然失败", ApiError.getMessage(response));
            assertFalse(response.isSuccessful());
        }
    }

    @Test
    public void businessFailureWithHttp200AlsoPrefersMsg() {
        assertEquals("余额不足", ApiError.getMessage("{\"code\":402,\"msg\":\"余额不足\"}", "操作失败"));
    }

    @Test
    public void msgHasPriorityOverCompatibilityFields() {
        assertEquals("标准消息", ApiError.getMessage(
                "{\"msg\":\"标准消息\",\"message\":\"上传节点消息\",\"error\":\"第三方消息\"}", "失败"));
    }

    @Test
    public void compatibilityFieldsAreUsedWhenMsgIsUnavailable() {
        assertEquals("上传失败", ApiError.getMessage("{\"message\":\"上传失败\"}", "失败"));
        assertEquals("IP 被封禁", ApiError.getMessage("{\"msg\":null,\"error\":\"IP 被封禁\"}", "失败"));
        assertEquals("有效说明", ApiError.getMessage("{\"msg\":\"   \",\"message\":\"有效说明\"}", "失败"));
    }

    @Test
    public void missingBlankNullAndNonStringMsgUseFallback() {
        for (String body : new String[]{"{}", "{\"msg\":\"\"}", "{\"msg\":\"   \"}",
                "{\"msg\":null}", "{\"msg\":\"null\"}", "{\"msg\":42}",
                "{\"msg\":false}", "{\"msg\":{}}", "{\"msg\":[]}"}) {
            assertEquals(body, "兜底说明", ApiError.getMessage(body, "兜底说明"));
        }
    }

    @Test
    public void invalidHtmlPlainTextAndEmptyBodyUseFallback() {
        for (String body : new String[]{null, "", "  ", "null", "[]", "{broken",
                "<!doctype html><html>502 Bad Gateway</html>", "Bad Gateway"}) {
            assertEquals("HTTP 错误: 502", ApiError.getMessage(body, "HTTP 错误: 502"));
        }
    }

    @Test
    public void unicodeAndMultilineMessagesArePreserved() {
        assertEquals("积分不足\n请先充值", ApiError.getMessage(
                "{\"msg\":\"  积分不足\\n请先充值  \"}", "失败"));
    }

    @Test
    public void unicodeEscapesAndBomAreSupported() {
        assertEquals("余额不足", ApiError.getMessage("﻿{\"msg\":\"余额不足\"}", "失败"));
        assertEquals("错误", ApiError.getMessage("{\"msg\":\"\\u9519\\u8bef\"}", "失败"));
    }

    @Test
    public void alreadyParsedJsonCanBeReused() throws Exception {
        JSONObject json = new JSONObject("{\"code\":403,\"msg\":\"没有操作权限\"}");
        assertEquals("没有操作权限", ApiError.getMessage(json, "失败"));
        assertEquals("失败", ApiError.getMessage((JSONObject) null, "失败"));
    }

    @Test
    public void responseWithoutMessageUsesDefaultOrCustomHttpFallback() {
        try (Response response = response(503, "<html>maintenance</html>")) {
            assertEquals("HTTP 错误: 503", ApiError.getMessage(response));
        }
        try (Response response = response(500, "")) {
            assertEquals("文件可能不是文本文件", ApiError.getMessage(response, "文件可能不是文本文件"));
        }
    }

    @Test
    public void bodyIsClosedAfterErrorMessageIsRead() {
        TrackingBody body = new TrackingBody("{\"msg\":\"请求被拒绝\"}", false);
        Response response = response(403, body);
        assertEquals("请求被拒绝", ApiError.getMessage(response));
        assertTrue(body.closed);
    }

    @Test
    public void bodyReadFailureStillClosesAndUsesFallback() {
        TrackingBody body = new TrackingBody("", true);
        Response response = response(500, body);
        assertEquals("HTTP 错误: 500", ApiError.getMessage(response));
        assertTrue(body.closed);
    }

    private static Response response(int status, String content) {
        return response(status, ResponseBody.create(MediaType.get("application/json; charset=utf-8"), content));
    }

    private static Response response(int status, ResponseBody body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://api.simpfun.cn/api/test").build())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Test response")
                .body(body)
                .build();
    }

    private static final class TrackingBody extends ResponseBody {
        private boolean closed;
        private final BufferedSource source;

        TrackingBody(String content, boolean failRead) {
            source = Okio.buffer(new ForwardingSource(new Buffer().writeUtf8(content)) {
                @Override
                public long read(Buffer sink, long byteCount) throws IOException {
                    if (failRead) throw new IOException("simulated read failure");
                    return super.read(sink, byteCount);
                }

                @Override
                public void close() throws IOException {
                    closed = true;
                    super.close();
                }
            });
        }

        @Override
        public MediaType contentType() {
            return MediaType.get("application/json; charset=utf-8");
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public BufferedSource source() {
            return source;
        }
    }
}
