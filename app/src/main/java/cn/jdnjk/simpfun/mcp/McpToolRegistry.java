package cn.jdnjk.simpfun.mcp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MCP 工具注册表。
 */
public class McpToolRegistry {
    private final List<McpTool> tools;

    public McpToolRegistry(List<McpTool> tools) {
        this.tools = new ArrayList<>(tools);
    }

    public McpTool find(String name) {
        for (McpTool tool : tools) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    public JSONArray toSchemaArray() {
        JSONArray arr = new JSONArray();
        for (McpTool tool : tools) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", tool.getName());
                obj.put("description", tool.getDescription());
                obj.put("inputSchema", tool.getInputSchema());
            } catch (Exception ignored) {}
            arr.put(obj);
        }
        return arr;
    }

    public List<McpTool> getTools() {
        return Collections.unmodifiableList(tools);
    }
}
