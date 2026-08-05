package cn.jdnjk.simpfun.mcp;

/**
 * MCP 工具调用结果。
 */
public class McpToolResult {
    public final String text;
    public final boolean isError;

    public McpToolResult(String text, boolean isError) {
        this.text = text;
        this.isError = isError;
    }

    public static McpToolResult ok(String text) {
        return new McpToolResult(text, false);
    }

    public static McpToolResult error(String text) {
        return new McpToolResult(text, true);
    }
}
