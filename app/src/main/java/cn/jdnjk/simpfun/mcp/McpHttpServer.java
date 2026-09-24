package cn.jdnjk.simpfun.mcp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

/**
 * MCP 传输层（NanoHTTPD），按路径区分模式：
 * <ul>
 *   <li>{@code /mcp} —— Streamable HTTP，自动识别协议：POST 按 Accept 头协商
 *       返回 JSON 或 SSE 流式响应；GET 打开 SSE 保活通道；</li>
 *   <li>{@code /mcp/stream} —— 强制 Streamable HTTP + SSE 流式响应（跳过 Accept
 *       协商，供客户端手动指定）；</li>
 *   <li>{@code /mcp/sse} —— 旧版 HTTP+SSE 传输：GET 打开 SSE 通道并先发
 *       {@code endpoint} 事件告知 POST 地址；POST 收到请求后应答经 SSE 流推送，
 *       HTTP 层只回 202 Accepted。</li>
 * </ul>
 * JSON 响应不强制 Connection: close，允许 keep-alive 连接复用。
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

        if (!isEndpointPath(uri)) {
            return jsonResponse(Response.Status.NOT_FOUND, errorJson("Not found"));
        }

        boolean legacySse = McpConstants.SSE_PATH.equals(uri);

        if (Method.POST.equals(session.getMethod())) {
            if (inFlight.incrementAndGet() > maxConcurrent) {
                inFlight.decrementAndGet();
                return jsonResponse(Response.Status.SERVICE_UNAVAILABLE, errorJson("Server busy"));
            }
            try {
                if (legacySse) {
                    return handleLegacySsePost(session);
                }
                boolean forceSse = McpConstants.STREAM_PATH.equals(uri);
                return handlePost(session, forceSse);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        if (Method.GET.equals(session.getMethod())) {
            return legacySse ? handleLegacySseGet(session) : handleGetSse(session);
        }

        return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, errorJson("Method not allowed"));
    }

    private boolean isEndpointPath(String uri) {
        return McpConstants.ENDPOINT_PATH.equals(uri)
                || McpConstants.STREAM_PATH.equals(uri)
                || McpConstants.SSE_PATH.equals(uri);
    }

    /** Streamable HTTP POST：forceSse 时跳过 Accept 协商，始终以 SSE 流返回。 */
    private Response handlePost(IHTTPSession session, boolean forceSse) {
        String body = readBodyOr400(session);
        if (body == null) {
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

        if (forceSse || acceptsSse(session)) {
            return sseResponse(mcpSession.sessionId, singleMessageStream(payload));
        }

        Response resp = jsonResponse(Response.Status.OK, payload);
        resp.addHeader(McpConstants.HEADER_SESSION, mcpSession.sessionId);
        return resp;
    }

    /** 生成只含一条 message 事件、随后即关闭的 SSE 流。 */
    private McpSseStream singleMessageStream(String payload) {
        McpSseStream stream = new McpSseStream();
        Thread pump = new Thread(() -> {
            stream.send("event: message\ndata: " + payload + "\n\n");
            stream.closeStream();
        }, "mcp-sse-writer");
        pump.setDaemon(true);
        pump.start();
        return stream;
    }

    /**
     * 旧版 SSE 传输的 POST：应答经会话的 SSE 通道推送，HTTP 层只回 202。
     */
    private Response handleLegacySsePost(IHTTPSession session) {
        String sessionId = queryParam(session, McpConstants.SSE_SESSION_QUERY);
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = session.getHeaders().get(McpConstants.HEADER_SESSION.toLowerCase());
        }
        McpSession mcpSession = sessionId != null && !sessionId.isEmpty()
                ? sessionManager.getOrCreate(sessionId) : null;

        if (mcpSession == null || mcpSession.sseStream == null || mcpSession.sseStream.isClosed()) {
            return jsonResponse(Response.Status.BAD_REQUEST,
                    errorJson("No open SSE stream for session, GET " + McpConstants.SSE_PATH + " first"));
        }

        String body = readBodyOr400(session);
        if (body == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("Parse error"));
        }

        McpRequestDispatcher.DispatchResult result = dispatcher.dispatch(body, mcpSession);
        if (result.response != null) {
            mcpSession.sseStream.send("event: message\ndata: " + result.response.toString() + "\n\n");
        }
        return jsonResponse(Response.Status.ACCEPTED, "");
    }

    /**
     * Streamable HTTP 的 GET：打开 SSE 保活通道。
     */
    private Response handleGetSse(IHTTPSession session) {
        String sessionId = session.getHeaders().get(McpConstants.HEADER_SESSION.toLowerCase());
        McpSession mcpSession;
        if (sessionId != null && !sessionId.isEmpty()) {
            mcpSession = sessionManager.getOrCreate(sessionId);
        } else {
            mcpSession = sessionManager.create();
        }

        McpSseStream stream = new McpSseStream();
        startHeartbeat(stream);
        return sseResponse(mcpSession.sessionId, stream, null);
    }

    /**
     * 旧版 SSE 传输的 GET：先发 {@code endpoint} 事件告知 POST 地址，再进入心跳循环。
     */
    private Response handleLegacySseGet(IHTTPSession session) {
        final McpSession mcpSession = sessionManager.create();
        final McpSseStream stream = new McpSseStream();
        mcpSession.sseStream = stream;

        String endpoint = McpConstants.SSE_PATH
                + "?" + McpConstants.SSE_SESSION_QUERY + "=" + mcpSession.sessionId;
        stream.send("event: endpoint\ndata: " + endpoint + "\n\n");

        startHeartbeat(stream);

        return sseResponse(mcpSession.sessionId, stream, () -> {
            // 流结束时解除会话对通道的引用
            if (mcpSession.sseStream == stream) {
                mcpSession.sseStream = null;
            }
        });
    }

    private void startHeartbeat(McpSseStream stream) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(McpConstants.SSE_HEARTBEAT_INTERVAL_MS);
                while (!stream.isClosed()) {
                    stream.send(": keep-alive\n\n");
                    Thread.sleep(McpConstants.SSE_HEARTBEAT_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mcp-sse-heartbeat");
        t.setDaemon(true);
        t.start();
    }

    private boolean acceptsSse(IHTTPSession session) {
        String accept = session.getHeaders().get("accept");
        return accept != null && accept.toLowerCase().contains(MIME_SSE);
    }

    private Response sseResponse(String sessionId, McpSseStream stream) {
        return sseResponse(sessionId, stream, null);
    }

    /**
     * 构建 SSE chunked 响应。onClose 在流进入关闭状态后回调一次（可为 null）。
     */
    private Response sseResponse(String sessionId, McpSseStream stream, Runnable onClose) {
        if (onClose != null) {
            Thread watcher = new Thread(() -> {
                // 等流进入关闭状态（写入端结束或客户端断开），再做收尾
                while (!stream.isClosed()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                onClose.run();
            }, "mcp-sse-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }

        Response resp = newChunkedResponse(Response.Status.OK, MIME_SSE, stream);
        resp.addHeader("Cache-Control", "no-cache");
        resp.addHeader(McpConstants.HEADER_SESSION, sessionId);
        return resp;
    }

    private String readBodyOr400(IHTTPSession session) {
        try {
            String body = readBody(session);
            return (body == null || body.isEmpty()) ? null : body;
        } catch (IOException e) {
            Log.w(TAG, "Read body failed", e);
            return null;
        }
    }

    private String queryParam(IHTTPSession session, String name) {
        String query = session.getQueryParameterString();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                try {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {
                    return kv[1];
                }
            }
        }
        return null;
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
}
