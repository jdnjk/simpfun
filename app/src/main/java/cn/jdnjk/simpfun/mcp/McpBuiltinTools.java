package cn.jdnjk.simpfun.mcp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.api.ins.PlanAPI;
import cn.jdnjk.simpfun.api.ins.PowerApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.service.TerminalWebSocketManager;

/**
 * 内置 MCP 工具集合。
 */
public class McpBuiltinTools {

    public static McpToolRegistry createRegistry(Context appContext) {
        Context ctx = appContext.getApplicationContext();
        List<McpTool> tools = Arrays.asList(
                new ServerListTool(ctx),
                new ServerDetailTool(ctx),
                new ServerPowerTool(ctx, "server_start", PowerApi.Action.START, "启动简幻欢服务器"),
                new ServerPowerTool(ctx, "server_stop", PowerApi.Action.STOP, "停止简幻欢服务器"),
                new ServerPowerTool(ctx, "server_restart", PowerApi.Action.RESTART, "重启简幻欢服务器"),
                new ServerPowerTool(ctx, "server_kill", PowerApi.Action.KILL, "强制结束简幻欢服务器"),
                new FileListTool(ctx),
                new FileReadTool(ctx),
                new FileWriteTool(ctx),
                new FileDeleteTool(ctx),
                new FileMoveTool(ctx),
                new FileCopyTool(ctx),
                new FileRenameTool(ctx),
                new FileFixPermissionsTool(ctx),
                new FileCreateTool(ctx),
                new PlanListTool(ctx),
                new PlanCreateTool(ctx),
                new PlanDeleteTool(ctx),
                new TerminalGetLogsTool(ctx),
                new TerminalSendCommandTool(ctx)
        );
        return new McpToolRegistry(tools);
    }

    // ---------- Base helpers ----------
    private abstract static class BaseTool implements McpTool {
        protected final Context appContext;
        protected final BlockingApiBridge bridge;

        BaseTool(Context appContext) {
            this.appContext = appContext.getApplicationContext();
            this.bridge = new BlockingApiBridge(appContext);
        }

        protected String requireToken() throws McpToolException {
            String token = BlockingApiBridge.simpfunToken(appContext);
            if (token == null || token.isEmpty()) {
                throw new McpToolException("请先登录简幻欢账号");
            }
            return token;
        }

        protected int requireServerId(JSONObject args) throws McpToolException {
            if (!args.has("server_id")) {
                throw new McpToolException("缺少必需参数: server_id");
            }
            return args.optInt("server_id");
        }

        protected JSONObject emptySchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                schema.put("properties", new JSONObject());
            } catch (Exception ignored) {}
            return schema;
        }


        protected JSONObject singleIntSchema(String paramName, String description) {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject p = new JSONObject();
                p.put("type", "integer");
                p.put("description", description);
                props.put(paramName, p);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put(paramName));
            } catch (Exception ignored) {}
            return schema;
        }

        protected JSONObject stringOrObjectResult(String text) {
            JSONObject r = new JSONObject();
            try {
                r.put("text", text);
            } catch (Exception ignored) {}
            return r;
        }
    }

    // ---------- server_list ----------
    private static class ServerListTool extends BaseTool {
        ServerListTool(Context context) { super(context); }
        @Override public String getName() { return "server_list"; }
        @Override public String getDescription() { return "获取当前账号下的简幻欢服务器列表，得到服务器id与CPU,RAM(GB),DISK(GB)信息"; }
        @Override public JSONObject getInputSchema() { return emptySchema(); }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new UserApi(appContext).getInstanceList(token, new UserApi.InstanceCallback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- server_detail ----------
    private static class ServerDetailTool extends BaseTool {
        ServerDetailTool(Context context) { super(context); }
        @Override public String getName() { return "server_detail"; }
        @Override public String getDescription() { return "获取指定简幻欢服务器的详细信息"; }
        @Override public JSONObject getInputSchema() { return singleIntSchema("server_id", "服务器 ID"); }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new MainApi(appContext).getInstanceDetail(token, String.valueOf(serverId), new MainApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- server_start/stop/restart/kill ----------
    private static class ServerPowerTool extends BaseTool {
        private final String actionName;
        private final String action;
        private final String description;

        ServerPowerTool(Context context, String name, String action, String description) {
            super(context);
            this.actionName = name;
            this.action = action;
            this.description = description;
        }

        @Override public String getName() { return actionName; }
        @Override public String getDescription() { return description; }
        @Override public JSONObject getInputSchema() { return singleIntSchema("server_id", "服务器 ID"); }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new PowerApi(appContext).powerControl(token, serverId, action, new PowerApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_list ----------
    private static class FileListTool extends BaseTool {
        FileListTool(Context context) { super(context); }
        @Override public String getName() { return "file_list"; }
        @Override public String getDescription() { return "列出简幻欢服务器指定目录中的文件"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject path = new JSONObject();
                path.put("type", "string");
                path.put("description", "目录路径，例如 /");
                props.put("path", path);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("path"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            String path = args.optString("path", "/");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().getFileList(appContext, serverId, path, new FileApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_read ----------
    private static class FileReadTool extends BaseTool {
        FileReadTool(Context context) { super(context); }
        @Override public String getName() { return "file_read"; }
        @Override public String getDescription() { return "读取简幻欢服务器上的文件内容，内容过大时会被截断"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject path = new JSONObject();
                path.put("type", "string");
                path.put("description", "服务器上的文件路径");
                props.put("path", path);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("path"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            String path = args.optString("path");
            if (path == null || path.isEmpty()) {
                throw new McpToolException("缺少必需参数: path");
            }
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().fetchFileContent(appContext, serverId, path, new FileCallback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            String content = data.optString("content", data.toString());
            final int MAX_LEN = 64 * 1024;
            if (content.length() > MAX_LEN) {
                content = content.substring(0, MAX_LEN) + "\n\n... (truncated)";
            }
            return McpToolResult.ok(content);
        }
    }

    // ---------- file_write ----------
    private static class FileWriteTool extends BaseTool {
        FileWriteTool(Context context) { super(context); }
        @Override public String getName() { return "file_write"; }
        @Override public String getDescription() { return "覆盖写入简幻欢服务器上的文件，建议先通过 file_read 查看原文件"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject path = new JSONObject();
                path.put("type", "string");
                path.put("description", "服务器上的文件路径");
                props.put("path", path);
                JSONObject content = new JSONObject();
                content.put("type", "string");
                content.put("description", "要写入的文件内容");
                props.put("content", content);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("path").put("content"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            String path = args.optString("path");
            String content = args.optString("content");
            if (path.isEmpty()) throw new McpToolException("缺少 path");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().saveFileContent(appContext, serverId, path, content, new FileCallback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_delete ----------
    private static class FileDeleteTool extends BaseTool {
        FileDeleteTool(Context context) { super(context); }
        @Override public String getName() { return "file_delete"; }
        @Override public String getDescription() { return "删除简幻欢服务器上的文件或文件夹（危险操作，不可恢复，要求经过用户审批才能执行）"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject path = new JSONObject();
                path.put("type", "string");
                path.put("description", "要删除的文件或文件夹路径，如 /plugins/bad.jar");
                props.put("path", path);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("path"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            String path = args.optString("path");
            if (path.isEmpty()) throw new McpToolException("缺少 path");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().deleteFileOrFolderBatch(appContext, serverId,
                            Collections.singletonList(path), new FileApi.Callback() {
                                @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                                @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                            }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_move (剪贴) ----------
    private static class FileMoveTool extends BaseTool {
        FileMoveTool(Context context) { super(context); }
        @Override public String getName() { return "file_move"; }
        @Override public String getDescription() { return "把简幻欢服务器上的文件或文件夹移动到目标目录"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject path = new JSONObject();
                path.put("type", "string");
                path.put("description", "要移动的文件路径，如 /plugins/old.jar");
                props.put("path", path);
                JSONObject target = new JSONObject();
                target.put("type", "string");
                target.put("description", "目标目录，如 /plugins/backup");
                props.put("target", target);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("path").put("target"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            String path = args.optString("path");
            String target = args.optString("target");
            if (path.isEmpty()) throw new McpToolException("缺少 path");
            if (target.isEmpty()) throw new McpToolException("缺少 target");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().moveFileOrFolder(appContext, serverId, path, target, new FileApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_copy ----------
    private static class FileCopyTool extends BaseTool {
        FileCopyTool(Context context) { super(context); }
        @Override public String getName() { return "file_copy"; }
        @Override public String getDescription() { return "在简幻欢服务器上创建文件或文件夹的副本"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject path = new JSONObject();
                path.put("type", "string");
                path.put("description", "要复制的文件路径，如 /plugins/config.yml");
                props.put("path", path);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("path"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            String path = args.optString("path");
            if (path.isEmpty()) throw new McpToolException("缺少 path");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().copyFileOrFolder(appContext, serverId, path, new FileApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_rename ----------
    private static class FileRenameTool extends BaseTool {
        FileRenameTool(Context context) { super(context); }
        @Override public String getName() { return "file_rename"; }
        @Override public String getDescription() { return "重命名简幻欢服务器上的文件或文件夹"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject origin = new JSONObject();
                origin.put("type", "string");
                origin.put("description", "源文件完整路径，如 /plugins/old.jar");
                props.put("origin", origin);
                JSONObject target = new JSONObject();
                target.put("type", "string");
                target.put("description", "新文件完整路径，如 /plugins/new.jar");
                props.put("target", target);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("origin").put("target"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            String origin = args.optString("origin");
            String target = args.optString("target");
            if (origin.isEmpty()) throw new McpToolException("缺少 origin");
            if (target.isEmpty()) throw new McpToolException("缺少 target");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().renameFile(appContext, serverId, origin, target, new FileApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_fix_permissions ----------
    private static class FileFixPermissionsTool extends BaseTool {
        FileFixPermissionsTool(Context context) { super(context); }
        @Override public String getName() { return "file_fix_permissions"; }
        @Override public String getDescription() { return "修复简幻欢服务器上文件的权限和中文名（工具箱操作，执行后会有一段时间无法操作服务器）"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().toolboxOperation(appContext, serverId, "fix_permission_and_charset", new FileApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- file_create ----------
    private static class FileCreateTool extends BaseTool {
        FileCreateTool(Context context) { super(context); }
        @Override public String getName() { return "file_create"; }
        @Override public String getDescription() { return "在简幻欢服务器上新建文件或文件夹"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject type = new JSONObject();
                type.put("type", "string");
                type.put("enum", new JSONArray().put("file").put("folder"));
                type.put("description", "创建类型：file 或 folder");
                props.put("type", type);
                JSONObject root = new JSONObject();
                root.put("type", "string");
                root.put("description", "目标目录，如 /plugins");
                props.put("root", root);
                JSONObject name = new JSONObject();
                name.put("type", "string");
                name.put("description", "要创建的文件或文件夹名称");
                props.put("name", name);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("type").put("root").put("name"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            String type = args.optString("type");
            String root = args.optString("root");
            String name = args.optString("name");
            if (!"file".equals(type) && !"folder".equals(type)) {
                throw new McpToolException("type 必须是 file 或 folder");
            }
            if (root.isEmpty()) throw new McpToolException("缺少 root");
            if (name.isEmpty()) throw new McpToolException("缺少 name");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new FileApi().createFileOrFolder(appContext, serverId, type, root, name, new FileApi.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- plan_list ----------
    private static class PlanListTool extends BaseTool {
        PlanListTool(Context context) { super(context); }
        @Override public String getName() { return "plan_list"; }
        @Override public String getDescription() { return "列出指定简幻欢服务器上的计划任务，返回任务id、命令、执行时间、是否循环及循环间隔"; }
        @Override public JSONObject getInputSchema() { return singleIntSchema("server_id", "服务器 ID"); }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new PlanAPI().listPlans(token, serverId, new PlanAPI.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- plan_create ----------
    private static class PlanCreateTool extends BaseTool {
        PlanCreateTool(Context context) { super(context); }
        @Override public String getName() { return "plan_create"; }
        @Override public String getDescription() { return "在指定简幻欢服务器上创建计划任务，定时执行命令。time 为东八区时间，格式 yyyy-MM-dd HH:mm（如 2026-08-09 22:00）；interval 为可选循环间隔（秒），范围 1800（30分钟）到 8640000（100天）"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject command = new JSONObject();
                command.put("type", "string");
                command.put("description", "要定时执行的命令，可用 <POWER_ON> <POWER_OFF> <RESTART> 代表开关机/重启");
                props.put("command", command);
                JSONObject time = new JSONObject();
                time.put("type", "string");
                time.put("description", "首次执行时间（东八区），格式 yyyy-MM-dd HH:mm，如 2026-08-09 22:00");
                props.put("time", time);
                JSONObject interval = new JSONObject();
                interval.put("type", "integer");
                interval.put("description", "循环执行间隔（秒），范围 1800（30分钟）到 8640000（100天），不传则不循环");
                props.put("interval", interval);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("command").put("time"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            String command = args.optString("command");
            String time = args.optString("time");
            if (command.isEmpty()) throw new McpToolException("缺少 command");
            if (time.isEmpty()) throw new McpToolException("缺少 time");
            Integer interval = args.has("interval") ? args.optInt("interval") : null;
            if (interval != null && (interval < 1800 || interval > 8640000)) {
                throw new McpToolException("interval 必须在 1800（30分钟）到 8640000（100天）之间");
            }
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new PlanAPI().createPlan(token, serverId, command, time, interval, new PlanAPI.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- plan_delete ----------
    private static class PlanDeleteTool extends BaseTool {
        PlanDeleteTool(Context context) { super(context); }
        @Override public String getName() { return "plan_delete"; }
        @Override public String getDescription() { return "删除指定简幻欢服务器上的计划任务（危险操作，不可恢复，要求经过用户审批才能执行）"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject planId = new JSONObject();
                planId.put("type", "integer");
                planId.put("description", "要删除的计划任务 ID（通过 plan_list 获取）");
                props.put("plan_id", planId);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("plan_id"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            String token = requireToken();
            int serverId = requireServerId(args);
            int planId = args.optInt("plan_id");
            if (planId <= 0) throw new McpToolException("缺少有效的 plan_id");
            JSONObject data = bridge.await(McpConstants.REGULAR_TOOL_TIMEOUT_MS, (ok, fail) ->
                    new PlanAPI().deletePlan(token, serverId, planId, new PlanAPI.Callback() {
                        @Override public void onSuccess(JSONObject data) { ok.accept(data); }
                        @Override public void onFailure(String errorMsg) { fail.accept(errorMsg); }
                    }));
            return McpToolResult.ok(data.toString());
        }
    }

    // ---------- terminal_get_logs ----------
    private static class TerminalGetLogsTool extends BaseTool {
        TerminalGetLogsTool(Context context) { super(context); }
        @Override public String getName() { return "terminal_get_logs"; }
        @Override public String getDescription() { return "获取简幻欢服务器终端的最近日志（最后 N 行）"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject lines = new JSONObject();
                lines.put("type", "integer");
                lines.put("description", "获取最后多少行，如 50");
                lines.put("minimum", 1);
                props.put("lines", lines);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("lines"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            int lines = args.optInt("lines", 50);
            if (lines <= 0) throw new McpToolException("lines 必须大于 0");

            TerminalWebSocketManager term = TerminalWebSocketManager.getInstance();
            if (!term.isConnectedTo(serverId)) {
                ensureTerminalConnected(term, appContext, serverId);
            }
            java.util.List<String> logs = term.getRecentLogLines(lines);
            if (logs.isEmpty()) {
                return McpToolResult.ok("（暂无终端日志）");
            }
            return McpToolResult.ok(String.join("\n", logs));
        }
    }

    // ---------- terminal_send_command ----------
    private static class TerminalSendCommandTool extends BaseTool {
        TerminalSendCommandTool(Context context) { super(context); }
        @Override public String getName() { return "terminal_send_command"; }
        @Override public String getDescription() { return "向简幻欢服务器终端发送命令，可选等待几秒后返回最近日志"; }
        @Override public JSONObject getInputSchema() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                JSONObject props = new JSONObject();
                JSONObject sid = new JSONObject();
                sid.put("type", "integer");
                sid.put("description", "服务器 ID");
                props.put("server_id", sid);
                JSONObject command = new JSONObject();
                command.put("type", "string");
                command.put("description", "要发送的命令，如 say hello");
                props.put("command", command);
                JSONObject waitSeconds = new JSONObject();
                waitSeconds.put("type", "integer");
                waitSeconds.put("description", "发送后等待的秒数");
                waitSeconds.put("minimum", 0);
                props.put("wait_seconds", waitSeconds);
                JSONObject lines = new JSONObject();
                lines.put("type", "integer");
                lines.put("description", "返回最后多少行日志；传 0 则不获取日志");
                lines.put("minimum", 0);
                props.put("lines", lines);
                schema.put("properties", props);
                schema.put("required", new JSONArray().put("server_id").put("command"));
            } catch (Exception ignored) {}
            return schema;
        }
        @Override public McpToolResult invoke(JSONObject args) throws McpToolException {
            requireToken();
            int serverId = requireServerId(args);
            String command = args.optString("command");
            int waitSeconds = args.optInt("wait_seconds", 0);
            int lines = args.optInt("lines", 0);
            if (command.isEmpty()) throw new McpToolException("缺少 command");
            if (waitSeconds < 0) waitSeconds = 0;
            if (lines < 0) lines = 0;

            TerminalWebSocketManager term = TerminalWebSocketManager.getInstance();
            if (!term.isConnectedTo(serverId)) {
                ensureTerminalConnected(term, appContext, serverId);
            }
            if (!term.sendCommand(serverId, command)) {
                return McpToolResult.error("命令发送失败（终端未连接或命令为空）");
            }
            if (waitSeconds > 0) {
                try {
                    Thread.sleep(waitSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            StringBuilder sb = new StringBuilder("命令已发送: ").append(command);
            if (lines > 0) {
                java.util.List<String> logs = term.getRecentLogLines(lines);
                if (!logs.isEmpty()) {
                    sb.append("\n\n").append(String.join("\n", logs));
                }
            }
            return McpToolResult.ok(sb.toString());
        }
    }

    /** 等待终端 WebSocket 连接就绪（最多 15s）。 */
    private static void ensureTerminalConnected(TerminalWebSocketManager term, Context ctx, int serverId) throws McpToolException {
        // 单例一次只连一台：若正连着别的服务器，先发 connect 会切过去
        term.connect(ctx, serverId, true);
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (term.isConnectedTo(serverId)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new McpToolException("终端连接超时");
    }
}