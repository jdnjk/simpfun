package cn.jdnjk.simpfun.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * MCP JSON-RPC 请求分发器。
 */
public class McpRequestDispatcher {

    private final McpSessionManager sessionManager;
    private final McpToolRegistry toolRegistry;

    public McpRequestDispatcher(McpSessionManager sessionManager, McpToolRegistry toolRegistry) {
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
    }

    public static class DispatchResult {
        public final JSONObject response;
        public final String newSessionId;
        public final boolean isNotification;

        public DispatchResult(JSONObject response, String newSessionId, boolean isNotification) {
            this.response = response;
            this.newSessionId = newSessionId;
            this.isNotification = isNotification;
        }
    }

    public DispatchResult dispatch(String body, McpSession session) {
        JSONObject req;
        try {
            req = new JSONObject(body);
        } catch (JSONException e) {
            return new DispatchResult(errorResponse(null, McpConstants.ERROR_PARSE, "Parse error"), null, false);
        }

        Object idObj;
        try {
            idObj = req.has("id") ? req.get("id") : null;
        } catch (JSONException e) {
            idObj = null;
        }
        boolean isNotification = !req.has("id");

        String method = req.optString("method", null);
        if (method == null || method.isEmpty()) {
            return new DispatchResult(errorResponse(idObj, McpConstants.ERROR_INVALID_REQUEST, "Invalid Request"), null, false);
        }

        JSONObject params = req.optJSONObject("params");
        if (params == null) {
            params = new JSONObject();
        }

        switch (method) {
            case "initialize":
                return new DispatchResult(handleInitialize(idObj, params, session), session.sessionId, isNotification);
            case "notifications/initialized":
                session.initialized = true;
                return new DispatchResult(null, session.sessionId, true);
            case "ping":
                return new DispatchResult(successResponse(idObj, new JSONObject()), session.sessionId, isNotification);
            case "tools/list":
                return new DispatchResult(handleToolsList(idObj), session.sessionId, isNotification);
            case "tools/call":
                return new DispatchResult(handleToolsCall(idObj, params), session.sessionId, isNotification);
            default:
                return new DispatchResult(errorResponse(idObj, McpConstants.ERROR_METHOD_NOT_FOUND, "Method not found: " + method), session.sessionId, isNotification);
        }
    }

    private JSONObject handleInitialize(Object id, JSONObject params, McpSession session) {
        JSONObject clientInfo = params.optJSONObject("clientInfo");
        if (clientInfo != null) {
            session.clientInfo = clientInfo;
        }

        String requested = params.optString("protocolVersion", McpConstants.PROTOCOL_VERSION);
        String version = McpConstants.PROTOCOL_VERSION;
        if (McpConstants.PROTOCOL_VERSION_FALLBACK.equals(requested)) {
            version = McpConstants.PROTOCOL_VERSION_FALLBACK;
        }

        JSONObject result = new JSONObject();
        JSONObject capabilities = new JSONObject();
        JSONObject toolsCap = new JSONObject();
        try {
            toolsCap.put("listChanged", false);
            capabilities.put("tools", toolsCap);

            result.put("protocolVersion", version);
            result.put("capabilities", capabilities);
            result.put("serverInfo", new JSONObject()
                    .put("name", "simpfun-mcp")
                    .put("version", "1.1.4.9"));
            result.put("instructions", "Controls the user's simpfun Minecraft servers. Server IDs are integers. Read files before overwriting.");
        } catch (JSONException ignored) {}

        return successResponse(id, result);
    }

    private JSONObject handleToolsList(Object id) {
        JSONObject result = new JSONObject();
        try {
            result.put("tools", toolRegistry.toSchemaArray());
        } catch (Exception ignored) {}
        return successResponse(id, result);
    }

    private JSONObject handleToolsCall(Object id, JSONObject params) {
        String name = params.optString("name");
        if (name == null || name.isEmpty()) {
            return errorResponse(id, McpConstants.ERROR_INVALID_PARAMS, "Missing tool name");
        }
        McpTool tool = toolRegistry.find(name);
        if (tool == null) {
            return errorResponse(id, McpConstants.ERROR_INVALID_PARAMS, "Unknown tool: " + name);
        }
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) {
            arguments = new JSONObject();
        }

        McpToolResult result;
        try {
            result = tool.invoke(arguments);
        } catch (McpToolException e) {
            result = McpToolResult.error(e.getMessage());
        } catch (Exception e) {
            result = McpToolResult.error("内部错误: " + e.getMessage());
        }

        JSONObject resultObj = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject textBlock = new JSONObject();
        try {
            textBlock.put("type", "text");
            textBlock.put("text", result.text);
            content.put(textBlock);
            resultObj.put("content", content);
            resultObj.put("isError", result.isError);
        } catch (Exception ignored) {}

        return successResponse(id, resultObj);
    }

    private JSONObject successResponse(Object id, JSONObject result) {
        JSONObject resp = new JSONObject();
        try {
            resp.put("jsonrpc", McpConstants.MCP_JSONRPC);
            if (id != null) resp.put("id", id);
            resp.put("result", result);
        } catch (Exception ignored) {}
        return resp;
    }

    private JSONObject errorResponse(Object id, int code, String message) {
        JSONObject resp = new JSONObject();
        try {
            resp.put("jsonrpc", McpConstants.MCP_JSONRPC);
            if (id != null) resp.put("id", id);
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            resp.put("error", error);
        } catch (Exception ignored) {}
        return resp;
    }
}
