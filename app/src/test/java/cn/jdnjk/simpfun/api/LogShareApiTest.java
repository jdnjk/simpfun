package cn.jdnjk.simpfun.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import cn.jdnjk.simpfun.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import okio.Buffer;

import static org.junit.Assert.*;

public class LogShareApiTest {
    @Test
    public void mclogsUsesJsonV1SourceAndOptionalMetadata() throws Exception {
        JSONArray metadata = new JSONArray().put(new JSONObject().put("key", "game_name").put("value", "Terraria").put("visible", true));
        Request request = LogShareApi.createRequest(LogShareApi.Site.MCLO_GS, LogSharePayload.prepare("server log", 25000), metadata);
        assertEquals("POST", request.method());
        assertEquals("https://api.mclo.gs/1/log", request.url().toString());
        assertNull(request.header("Authorization"));
        assertNull(request.header("Content-Encoding"));
        Buffer body = new Buffer();
        request.body().writeTo(body);
        JSONObject json = new JSONObject(body.readUtf8());
        assertEquals("server log", json.getString("content"));
        assertEquals("SimpfunAPP/" + BuildConfig.VERSION_NAME, json.getString("source"));
        assertFalse(json.has("sources"));
        assertEquals(1, json.getJSONArray("metadata").length());
    }

    @Test
    public void logshareUsesV1PathAndGzipWithoutAdvertisingBrotli() throws Exception {
        Request request = LogShareApi.createRequest(LogShareApi.Site.LOGSHARE_CN, LogSharePayload.prepare("日志内容", 50000), null);
        assertEquals("https://api.logshare.cn/v1/log", request.url().toString());
        assertEquals("gzip", request.header("Content-Encoding"));
        assertNull(request.header("Accept-Encoding"));
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(buffer.readByteArray()))) {
            JSONObject json = new JSONObject(new String(gzip.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("日志内容", json.getString("content"));
            assertFalse(json.has("metadata"));
        }
    }

    @Test
    public void deletionUsesOriginalSiteIdAndOnlyItsDeletionToken() {
        for (LogShareApi.Site site : LogShareApi.Site.values()) {
            LogShareApi.SharedLog log = new LogShareApi.SharedLog(site, "abc123", "https://example.org/log", "https://example.org/raw", "deletion-secret");
            Request request = LogShareApi.deleteRequest(log);
            assertEquals("DELETE", request.method());
            assertEquals(site.endpoint + "/abc123", request.url().toString());
            assertEquals("Bearer deletion-secret", request.header("Authorization"));
        }
    }

    @Test
    public void successRequiresAllFieldsAndHttpsLinks() throws Exception {
        JSONObject json = created();
        LogShareApi.SharedLog log = LogShareApi.parseSharedLog(LogShareApi.Site.MCLO_GS, json);
        assertEquals("abc123", log.id);
        assertEquals("https://mclo.gs/abc123", log.url);
        json.remove("token");
        assertThrows(java.io.IOException.class, () -> LogShareApi.parseSharedLog(LogShareApi.Site.MCLO_GS, json));
        JSONObject unsafe = created().put("url", "javascript:alert(1)");
        assertThrows(java.io.IOException.class, () -> LogShareApi.parseSharedLog(LogShareApi.Site.MCLO_GS, unsafe));
    }

    @Test
    public void errorsPreferProviderErrorAndRejectNon2xxEvenWithSuccessTrue() throws Exception {
        try (Response response = response(new Request.Builder().url("https://example.org/").build(), 429,
                "{\"success\":false,\"error\":\"请求过于频繁\"}")) {
            assertEquals("请求过于频繁", assertThrows(java.io.IOException.class, () -> LogShareApi.readResult(response)).getMessage());
        }
        try (Response response = response(new Request.Builder().url("https://example.org/").build(), 200,
                "{\"success\":false,\"error\":\"Invalid token.\"}")) {
            assertEquals("Invalid token.", assertThrows(java.io.IOException.class, () -> LogShareApi.readResult(response)).getMessage());
        }
        try (Response response = response(new Request.Builder().url("https://example.org/").build(), 500, "{\"success\":true}")) {
            assertThrows(java.io.IOException.class, () -> LogShareApi.readResult(response));
        }
    }

    @Test
    public void asynchronousCreateAndDeleteWorkWithoutAnyExternalRequests() throws Exception {
        AtomicReference<Request> captured = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            captured.set(chain.request());
            return response(chain.request(), 200, "DELETE".equals(chain.request().method())
                    ? "{\"success\":true}" : created().toString());
        }).build();
        LogShareApi api = new LogShareApi(client);
        AwaitResult<LogShareApi.SharedLog> creation = new AwaitResult<>();
        api.create(LogShareApi.Site.MCLO_GS, LogSharePayload.prepare("local fixture", 25000), null, creation);
        LogShareApi.SharedLog log = creation.awaitSuccess();
        assertNull(captured.get().header("Authorization"));
        AwaitResult<Void> deletion = new AwaitResult<>();
        api.delete(log, deletion);
        deletion.awaitSuccess();
        assertEquals("DELETE", captured.get().method());
        assertEquals("Bearer secret", captured.get().header("Authorization"));
    }

    @Test
    public void failedLogshareDeletionIsNotReportedAsSuccess() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> response(chain.request(), 200,
                "{\"success\":true,\"deletedCount\":0,\"failed\":[\"abc123\"]}")).build();
        AwaitResult<Void> deletion = new AwaitResult<>();
        new LogShareApi(client).delete(new LogShareApi.SharedLog(LogShareApi.Site.LOGSHARE_CN,
                "abc123", "https://logshare.cn/abc123", "https://api.logshare.cn/v1/raw/abc123", "secret"), deletion);
        assertTrue(deletion.done.await(5, TimeUnit.SECONDS));
        assertNotNull(deletion.error);
    }

    @Test
    public void emptyContentCannotBePublished() {
        assertThrows(java.io.IOException.class, () -> LogShareApi.createRequest(LogShareApi.Site.MCLO_GS,
                LogSharePayload.prepare("   ", 25000), null));
    }

    private static JSONObject created() {
        try {
            return new JSONObject().put("success", true).put("id", "abc123").put("url", "https://mclo.gs/abc123")
                    .put("raw", "https://api.mclo.gs/1/raw/abc123").put("token", "secret");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Response response(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(ResponseBody.create(MediaType.get("application/json"), body)).build();
    }

    private static final class AwaitResult<T> implements LogShareApi.ResultCallback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T result;
        String error;

        @Override
        public void onSuccess(T value) { result = value; done.countDown(); }
        @Override
        public void onFailure(String message) { error = message; done.countDown(); }

        T awaitSuccess() throws InterruptedException {
            assertTrue("callback timed out", done.await(5, TimeUnit.SECONDS));
            assertNull(error);
            return result;
        }
    }
}
