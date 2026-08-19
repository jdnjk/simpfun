package cn.jdnjk.simpfun.ui.ins.term;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.QuickCommandApi;
import cn.jdnjk.simpfun.model.QuickCommandNode;
import cn.jdnjk.simpfun.ui.setting.QuickCommandStorage;

/**
 * 终端页"快捷指令"的 Options Menu 子树管理器。
 *
 * 以原生 SubMenu 递归构建：每个分类节点生成一个 addSubMenu，
 * 点击后由系统在溢出菜单上再叠一层弹出，实现"点击子项就在子菜单里多展开"。
 * 指令（item）节点作为叶子 MenuItem，点击后交给 OnCommandSelected（填参数/直接发送）。
 *
 * 层级：⋮ → 快捷指令 › 玩家管理 › OP管理 › 设置管理员(OP)
 */
public class QuickCommandMenuManager {

    public interface OnCommandSelected {
        void onSelected(QuickCommandNode node);
    }

    private static final int ID_RETRY = 0x1F00;
    private static final int ID_BASE = 0x10000000;

    private final Context context;
    private final IntSupplier deviceIdSupplier;
    private final Runnable onMenuInvalidate;
    private final OnCommandSelected onCommandSelected;
    private final BooleanSupplier serverOnlineSupplier;

    private final QuickCommandApi api = new QuickCommandApi();
    private final QuickCommandStorage storage;

    private final Map<Integer, QuickCommandNode> idToNode = new HashMap<>();
    private List<QuickCommandNode> rootNodes = new ArrayList<>();
    private int nextId = ID_BASE;
    private boolean loading;
    private boolean loadedOnce;
    private String loadError;

    public QuickCommandMenuManager(Context context,
                                   IntSupplier deviceIdSupplier,
                                   BooleanSupplier serverOnlineSupplier,
                                   Runnable onMenuInvalidate,
                                   OnCommandSelected onCommandSelected) {
        this.context = context.getApplicationContext();
        this.deviceIdSupplier = deviceIdSupplier;
        this.serverOnlineSupplier = serverOnlineSupplier;
        this.onMenuInvalidate = onMenuInvalidate;
        this.onCommandSelected = onCommandSelected;
        this.storage = new QuickCommandStorage(context);
    }

    /**
     * 设备切换时重置加载状态。
     */
    public void reset() {
        loading = false;
        loadedOnce = false;
        loadError = null;
        rootNodes = new ArrayList<>();
        idToNode.clear();
    }

    /**
     * 首次构建菜单时，若尚未加载则触发拉取。
     */
    public void ensureLoaded() {
        if (!loading && !loadedOnce) {
            load();
        }
    }

    private void load() {
        int deviceId = deviceIdSupplier.getAsInt();
        if (loading || deviceId <= 0) return;
        loading = true;
        loadError = null;
        onMenuInvalidate.run(); // 立即显示"正在加载…"

        api.getOneKeyCommands(context, deviceId, new QuickCommandApi.Callback() {
            @Override
            public void onSuccess(JSONArray commands) {
                loading = false;
                loadedOnce = true;
                loadError = null;
                rootNodes = composeRoot(parse(commands));
                onMenuInvalidate.run();
            }

            @Override
            public void onFailure(String errorMsg) {
                loading = false;
                loadedOnce = true;
                loadError = errorMsg;
                List<QuickCommandNode> custom = storage.loadAll();
                rootNodes = custom.isEmpty() ? new ArrayList<>() : composeRoot(new ArrayList<>());
                onMenuInvalidate.run();
            }
        });
    }

    /**
     * 构建"快捷指令"子树。每次 invalidate 后由 MenuProvider 调用。
     */
    public void onCreateMenu(Menu menu) {
        idToNode.clear();
        nextId = ID_BASE;

        SubMenu sub = menu.addSubMenu(Menu.NONE, R.id.action_quick_command, menu.size(), "快捷指令");

        boolean online = serverOnlineSupplier != null && serverOnlineSupplier.getAsBoolean();

        // 服务器 offline → 置灰不可点击
        if (!online) {
            sub.getItem().setEnabled(false);
            sub.add(Menu.NONE, 0, 0, "服务器已离线，无法使用快捷指令").setEnabled(false);
            return;
        }

        if (loading) {
            sub.add(Menu.NONE, 0, 0, "正在加载指令…").setEnabled(false);
            return;
        }

        if (rootNodes.isEmpty()) {
            if (loadError != null) {
                sub.add(Menu.NONE, ID_RETRY, 0, "获取失败，点击重试");
            } else {
                sub.add(Menu.NONE, 0, 0, "暂无指令").setEnabled(false);
            }
            return;
        }

        // 递归构建嵌套 SubMenu（分类 → 子 SubMenu；指令 → 叶子项）
        buildTree(sub, rootNodes);
    }

    private void buildTree(SubMenu parent, List<QuickCommandNode> nodes) {
        for (QuickCommandNode node : nodes) {
            if (node == null) continue;
            if ("list".equals(node.type)) {
                SubMenu child = parent.addSubMenu(Menu.NONE, nextId++, 0, node.name);
                child.setHeaderTitle(node.name);
                if (node.children != null && !node.children.isEmpty()) {
                    buildTree(child, node.children);
                } else {
                    child.add(Menu.NONE, 0, 0, "空分类").setEnabled(false);
                }
            } else {
                int id = nextId++;
                parent.add(Menu.NONE, id, 0, node.name);
                idToNode.put(id, node);
            }
        }
    }

    /**
     * 由 MenuProvider 的 onMenuItemSelected 委托调用。
     * 返回 true 表示已处理（刷新/重试/执行指令）。
     */
    public boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == ID_RETRY) {
            reset();
            load();
            return true;
        }
        QuickCommandNode node = idToNode.get(id);
        if (node == null) return false;
        if (onCommandSelected != null) {
            onCommandSelected.onSelected(node);
        }
        return true;
    }

    // ---------- helpers ----------

    private List<QuickCommandNode> parse(JSONArray array) {
        List<QuickCommandNode> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) {
                list.add(QuickCommandNode.fromJson(obj));
            }
        }
        return list;
    }

    private List<QuickCommandNode> composeRoot(List<QuickCommandNode> apiNodes) {
        List<QuickCommandNode> root = new ArrayList<>(apiNodes);
        List<QuickCommandNode> custom = storage.loadAll();
        if (!custom.isEmpty()) {
            QuickCommandNode cat = new QuickCommandNode();
            cat.type = "list";
            cat.name = "我的指令";
            cat.isCustom = true;
            cat.children = custom;
            root.add(cat);
        }
        return root;
    }
}