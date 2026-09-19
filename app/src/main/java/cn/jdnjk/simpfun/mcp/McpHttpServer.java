package cn.jdnjk.simpfun.mcp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

/**
 * MCP Streamable HTTP 传输层（NanoHTTPD）。
 * <p>
 * 支持：
 * - POST：按客户端 Accept 头协商，返回普通 JSON 或 SSE 流式响应（单条 message 事件）；
 * - GET：SSE 长连接，周期性注释心跳保活（服务端 -> 客户端通道）；
 * - JSON 响应不再强制 Connection: close，允许 keep-alive 连接复用。
 */
public class McpHttpServer extends NanoHTTPD {
    private static final String TAG = "McpHttpServer";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_SSE = "text/event-stream";

    private final McpSessionManager sessionManager;
    private final McpRequestDispatcher dispatcher;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final int maxConcurrent;

    public McpHttpServer(int port, McpSessionManager sessionManager,
                         McpRequestDispatcher dispatcher) {
        super(port);
        this.sessionManager = sessionManager;
        this.dispatcher = dispatcher;
        this.maxConcurrent = 8;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String uri = session.getUri();

        if (Method.OPTIONS.equals(session.getMethod())) {
            return corsResponse(Response.Status.OK, "{}");
        }

        if (!McpConstants.ENDPOINT_PATH.equals(uri)) {
            return jsonResponse(Response.Status.NOT_FOUND, errorJson("Not found"));
        }

        if (Method.POST.equals(session.getMethod())) {
            if (inFlight.incrementAndGet() > maxConcurrent) {
                inFlight.decrementAndGet();
                return jsonResponse(Response.Status.SERVICE_UNAVAILABLE, errorJson("Server busy"));
            }
            try {
                return handlePost(session);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        if (Method.GET.equals(session.getMethod())) {
            return handleGetSse(session);
        }

        return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, errorJson("Method not allowed"));
    }

    private Response handlePost(IHTTPSession session) {
        String body;
        try {
            body = readBody(session);
        } catch (IOException e) {
            Log.w(TAG, "Read body failed", e);
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("Parse error"));
        }

        if (body == null || body.isEmpty()) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("Parse error"));
        }

        String sessionId = session.getHeaders().get(McpConstants.HEADER_SESSION.toLowerCase());
        McpSession mcpSession;
        if (sessionId != null && !sessionId.isEmpty()) {
            mcpSession = sessionManager.getOrCreate(sessionId);
        } else {
            mcpSession = sessionManager.create();
        }

        McpRequestDispatcher.DispatchResult result = dispatcher.dispatch(body, mcpSession);

        if (result.isNotification && result.response == null) {
            Response resp = jsonResponse(Response.Status.ACCEPTED, "");
            resp.addHeader(McpConstants.HEADER_SESSION, mcpSession.sessionId);
            return resp;
        }

        String payload = result.response.toString();

        if (acceptsSse(session)) {
            return sseResponse(mcpSession.sessionId, stream -> {
                stream.send("event: message\ndata: " + payload + "\n\n");
                stream.closeStream();
            });
        }

        Response resp = jsonResponse(Response.Status.OK, payload);
        resp.addHeader(McpConstants.HEADER_SESSION, mcpSession.sessionId);
        return resp;
    }

    /**
     * GET /mcp：打开 SSE 长连接。先发一条注释尽早冲刷响应头（降低 TTFB），
     * 之后周期性发送心跳注释防止中间设备断开空闲连接。
     */
    private Response handleGetSse(IHTTPSession session) {
        String sessionId = session.getHeaders().get(McpConstants.HEADER_SESSION.toLowerCase());
        McpSession mcpSession;
        if (sessionId != null && !sessionId.isEmpty()) {
            mcpSession = sessionManager.getOrCreate(sessionId);
        } else {
            mcpSession = sessionManager.create();
        }

        return sseResponse(mcpSession.sessionId, stream -> {
            stream.send(": connected\n\n");
            while (!stream.isClosed()) {
                try {
                    Thread.sleep(McpConstants.SSE_HEARTBEAT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
                if (stream.isClosed()) break;
                stream.send(": keep-alive\n\n");
            }
        });
    }

    private boolean acceptsSse(IHTTPSession session) {
        String accept = session.getHeaders().get("accept");
        return accept != null && accept.toLowerCase().contains(MIME_SSE);
    }

    private interface SseWriter {
        void write(SseStream stream);
    }

    /**
     * 构建 SSE chunked 响应，写入逻辑放到守护线程执行。
     */
    private Response sseResponse(String sessionId, SseWriter writer) {
        SseStream stream = new SseStream();
        Thread pump = new Thread(() -> {
            try {
                writer.write(stream);
            } catch (Throwable t) {
                Log.w(TAG, "SSE writer ended", t);
            } finally {
                stream.closeStream();
            }
        }, "mcp-sse-writer");
        pump.setDaemon(true);
        pump.start();

        Response resp = newChunkedResponse(Response.Status.OK, MIME_SSE, stream);
        resp.addHeader("Cache-Control", "no-cache");
        resp.addHeader(McpConstants.HEADER_SESSION, sessionId);
        return resp;
    }

    private String readBody(IHTTPSession session) throws IOException {
        long contentLength = 0;
        String lengthHeader = session.getHeaders().get("content-length");
        if (lengthHeader != null) {
            try {
                contentLength = Long.parseLong(lengthHeader);
            } catch (NumberFormatException ignored) {}
        }

        if (contentLength > McpConstants.MAX_BODY_BYTES) {
            throw new IOException("Body too large");
        }

        InputStream is = session.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        long total = 0;
        while ((read = is.read(buffer)) != -1) {
            total += read;
            if (total > McpConstants.MAX_BODY_BYTES) {
                throw new IOException("Body too large");
            }
            baos.write(buffer, 0, read);
            if (contentLength > 0 && total >= contentLength) {
                break;
            }
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private Response jsonResponse(Response.Status status, String body) {
        return newFixedLengthResponse(status, MIME_JSON, body);
    }

    private Response corsResponse(Response.Status status, String body) {
        Response resp = newFixedLengthResponse(status, MIME_JSON, body);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Headers", "authorization, content-type, mcp-session-id");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        return resp;
    }

    private String errorJson(String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("error", message);
        } catch (JSONException ignored) {}
        return obj.toString();
    }

    /**
     * 基于 BlockingQueue 的无限流，供 NanoHTTPD chunked 输出。
     * 队列设了上限：客户端断开导致积压时置为 closed，写入线程随之退出，避免线程泄漏。
     */
    static final class SseStream extends InputStream {
        private static final int QUEUE_CAPACITY = 16;

        private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        private volatile boolean closed = false;
        private byte[] current = null;
        private int pos = 0;

        void send(String text) {
            if (closed) return;
            if (!queue.offer(text.getBytes(StandardCharsets.UTF_8))) {
                closed = true; // 客户端已断开，缓冲积压
            }
        }

        boolean isClosed() {
            return closed;
        }

        void closeStream() {
            closed = true;
            queue.offer(new byte[0]); // 唤醒阻塞的读取端并标记 EOF
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            while (current == null || pos >= current.length) {
                byte[] next;
                try {
                    next = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                if (next == null) {
                    continue;
                }
                if (next.length == 0) {
                    return -1; // EOF
                }
                current = next;
                pos = 0;
            }
            int n = Math.min(len, current.length - pos);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : (one[0] & 0xFF);
        }
    }
}
