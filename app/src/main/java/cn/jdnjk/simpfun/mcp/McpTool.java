package cn.jdnjk.simpfun.mcp;

import org.json.JSONObject;

/**
 * MCP 工具契约。
 */
public interface McpTool {
    String getName();
    String getDescription();
    JSONObject getInputSchema();
    McpToolResult invoke(JSONObject arguments) throws McpToolException;
    default long getTimeoutMs() { return McpConstants.REGULAR_TOOL_TIMEOUT_MS; }
}
