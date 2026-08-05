package cn.jdnjk.simpfun.mcp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

/**
 * MCP Streamable HTTP 传输层（NanoHTTPD）。
 */
public class McpHttpServer extends NanoHTTPD {
    private static final String TAG = "McpHttpServer";
    private static final String MIME_JSON = "application/json";

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

        if (!"POST".equals(method)) {
            return jsonResponse(Response.Status.METHOD_NOT_ALLOWED, errorJson("Method not allowed"));
        }

        if (!McpConstants.ENDPOINT_PATH.equals(uri)) {
            return jsonResponse(Response.Status.NOT_FOUND, errorJson("Not found"));
        }

        if (inFlight.incrementAndGet() > maxConcurrent) {
            inFlight.decrementAndGet();
            return jsonResponse(Response.Status.SERVICE_UNAVAILABLE, errorJson("Server busy"));
        }
        try {
            return serveMcp(session);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private Response serveMcp(IHTTPSession session) {
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

        Response resp = jsonResponse(Response.Status.OK, result.response.toString());
        resp.addHeader(McpConstants.HEADER_SESSION, mcpSession.sessionId);
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
        Response resp = newFixedLengthResponse(status, MIME_JSON, body);
        resp.addHeader("Connection", "close");
        return resp;
    }

    private Response corsResponse(Response.Status status, String body) {
        Response resp = newFixedLengthResponse(status, MIME_JSON, body);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Headers", "authorization, content-type, mcp-session-id");
        resp.addHeader("Access-Control-Allow-Methods", "POST");
        resp.addHeader("Connection", "close");
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
