package cn.jdnjk.simpfun.mcp;

/**
 * MCP（Model Context Protocol）常量定义。
 * <p>
 * 参考 Streamable HTTP transport，spec 版本 2025-06-18。
 */
public final class McpConstants {
    private McpConstants() {}

    public static final String PROTOCOL_VERSION = "2025-06-18";
    public static final String PROTOCOL_VERSION_FALLBACK = "2025-03-26";

    public static final String ENDPOINT_PATH = "/mcp";
    public static final int DEFAULT_PORT = 8090;

    public static final String HEADER_SESSION = "Mcp-Session-Id";

    public static final int MAX_BODY_BYTES = 1_000_000;

    public static final long REGULAR_TOOL_TIMEOUT_MS = 130_000;
    public static final long AI_TOOL_TIMEOUT_MS = 210_000;

    public static final String MCP_JSONRPC = "2.0";

    // JSON-RPC 错误码
    public static final int ERROR_PARSE = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;
    public static final int ERROR_INTERNAL = -32603;
}
