package cn.jdnjk.simpfun.mcp;

/**
 * 工具执行异常，message 将返回给客户端。
 */
public class McpToolException extends Exception {
    public McpToolException(String message) {
        super(message);
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
