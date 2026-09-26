package cn.jdnjk.simpfun.ui.ins.term;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;

import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.androidansi.AnsiParser;
import com.fox2code.androidansi.AnsiTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.AiApi;
import cn.jdnjk.simpfun.api.ins.TermApi;
import cn.jdnjk.simpfun.model.QuickCommandNode;
import cn.jdnjk.simpfun.service.TerminalWebSocketListener;
import cn.jdnjk.simpfun.service.TerminalWebSocketManager;
import cn.jdnjk.simpfun.ui.setting.TerminalColorUtils;
import cn.jdnjk.simpfun.ui.setting.TerminalFontSizeManager;
import cn.jdnjk.simpfun.ui.setting.TerminalLineLimitManager;
import cn.jdnjk.simpfun.utils.AiResponseFormatter;
import cn.jdnjk.simpfun.utils.ClipboardUtils;
import cn.jdnjk.simpfun.utils.MarkdownRenderer;

public class TerminalFragment extends Fragment implements TerminalWebSocketListener {
    private static final int MAX_AI_ANALYZE_CHARS = 12000;

    // 预编译的 ANSI 清理正则（原先每行调用 String.replaceAll 都会重新编译 Pattern，刷屏时是主线程热点）
    private static final Pattern P_OSC = Pattern.compile("\\x1B\\][^\\x07]*(?:\\x07|\\x1B\\\\)");
    private static final Pattern P_SOS = Pattern.compile("\\x1B[P\\^_]([\\s\\S]*?)(?:\\x1B\\\\|\\x07)");
    private static final Pattern P_CSI_MOVE = Pattern.compile("\\x1B\\[[0-9;:]*[ABCDGHEFSTfJK]");
    private static final Pattern P_CSI_PRIVATE = Pattern.compile("\\x1B\\[\\?[0-9;:]*[hl]");
    private static final Pattern P_ESC_SHORT = Pattern.compile("\\x1B[=>78]");
    private static final Pattern P_CTRL_DISPLAY = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1A\\x1C-\\x1F\\x7F]");
    private static final Pattern P_CSI_ANY = Pattern.compile("\\x1B\\[[0-9;:?>=]*[ -/]*[@-~]");
    private static final Pattern P_ESC_ANY = Pattern.compile("\\x1B[ -/]*[@-~]");
    private static final Pattern P_CTRL_ALL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final String[] AI_LOG_FAULT_TYPES = new String[]{
            "Unable to start",
            "Server crashed",
            "Low performance or network issue",
            "Error in console output",
            "Others"
    };

    private EditText editTextCommand;
    private RecyclerView recyclerViewOutput;
    private LinesAdapter terminalAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AiApi aiApi = new AiApi();
    private QuickCommandMenuManager quickCommandMenuManager;
    private AlertDialog aiLoadingDialog;
    private AlertDialog activeDialog;
    private TextView aiLoadingMessageView;
    private final Runnable aiSlowHintRunnable = () -> {
        if (aiLoadingMessageView != null) {
            aiLoadingMessageView.setText("AI 为非流式返回，正在等待完整结果…\n日志越长，等待越久。");
        }
    };

    private final TerminalWebSocketManager wsManager = TerminalWebSocketManager.getInstance();
    private final List<String> pendingLines = new ArrayList<>();
    // P5：终端流中最后一行是否尚未以换行结束（进度条 \r 重绘场景）
    private boolean lastLinePartial = false;
    // P5：本轮 flush 需要先替换 RecyclerView 尾行的数量（0 或 1）
    private int pendingOverwriteCount = 0;
    // 跨行选择复制模式
    private View selectOverlay;
    private ScrollStateScrollView selectScroll;
    private AnsiTextView selectTextView;
    private boolean isSelectionMode = false;
    private TerminalFastScrollView fastScrollView;
    private TerminalFastScrollView selectFastScroll;
    private volatile String wsStatus = null;
    private boolean isBufferUpdateScheduled = false;
    // 用户手指按在终端上（可能正在长按选择文本）时推迟 flush，
    // 避免 notify 导致正在选择的 item 被回收重建、放大镜冻结
    private boolean isTouchingRecyclerView = false;
    private static final long FLUSH_DELAY_MS = 100;
    private final Runnable bufferFlushRunnable = this::flushBufferedLines;
    private boolean shouldMaintainFocus = false;
    private boolean isAppInForeground = false;
    private boolean isReconnectScheduled = false;
    // 账号积分不足（code 402）导致连接失败时置位，阻止后续自动重连；用户手动重连时复位
    private boolean isInsufficientCreditsBlocked = false;
    private boolean isAiRequestRunning = false;
    private boolean wsListenerRegistered = false;
    private int registeredDeviceId = -1;
    private int activeDeviceId = -1;
    private int aiRequestGeneration = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_terminal, container, false);

        editTextCommand = root.findViewById(R.id.edit_text_command);
        ImageButton buttonSend = root.findViewById(R.id.button_send);
        recyclerViewOutput = root.findViewById(R.id.recycler_view_output);
        fastScrollView = root.findViewById(R.id.fast_scroll_view);
        selectFastScroll = root.findViewById(R.id.fast_scroll_select);
        selectOverlay = root.findViewById(R.id.select_overlay);
        selectScroll = root.findViewById(R.id.select_scroll);
        selectTextView = root.findViewById(R.id.select_text_view);
        View selectDoneButton = root.findViewById(R.id.select_done_button);
        if (selectDoneButton != null) {
            selectDoneButton.setOnClickListener(v -> exitSelectionMode());
        }
        View selectCopyButton = root.findViewById(R.id.select_copy_button);
        if (selectCopyButton != null) {
            selectCopyButton.setOnClickListener(v -> copySelectionFromOverlay());
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        layoutManager.setStackFromEnd(true);
        recyclerViewOutput.setLayoutManager(layoutManager);
        int lineLimit = TerminalLineLimitManager.getInstance(requireContext()).getLineLimit();
        terminalAdapter = new LinesAdapter(requireContext(), lineLimit);
        recyclerViewOutput.setAdapter(terminalAdapter);
        if (fastScrollView != null) {
            fastScrollView.attach(recyclerViewOutput);
        }
        if (selectFastScroll != null && selectScroll != null) {
            selectFastScroll.attach(selectScroll);
        }

        applyTerminalColors();
        setupToolbarAiMenu();

        buttonSend.setOnClickListener(v -> sendCommand());

        recyclerViewOutput.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    isTouchingRecyclerView = true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isTouchingRecyclerView) {
                        isTouchingRecyclerView = false;
                        // 抬手后立即把攒下的日志刷上去
                        scheduleBufferFlush();
                    }
                    break;
            }
            return false;
        });
        // 长按终端任意位置 → 进入跨行选择模式（每行是独立 TextView，
        // 行内选择天生无法跨行，统一引导到覆盖层里拖选复制）
        final GestureDetector longPressDetector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        if (recyclerViewOutput == null) return;
                        // 右缘滚动条区域不触发选择模式（避免拖动快速滚动条被误判为长按）
                        int touchMargin = (int) (28 * getResources().getDisplayMetrics().density);
                        if (e.getX() >= recyclerViewOutput.getWidth() - touchMargin) {
                            return;
                        }
                        int anchorPosition = -1;
                        View child = recyclerViewOutput.findChildViewUnder(e.getX(), e.getY());
                        if (child != null) {
                            anchorPosition = recyclerViewOutput.getChildAdapterPosition(child);
                        }
                        enterSelectionMode(anchorPosition);
                    }
                });
        recyclerViewOutput.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                longPressDetector.onTouchEvent(e);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
        editTextCommand.setOnFocusChangeListener((v, hasFocus) -> {
            shouldMaintainFocus = hasFocus;
            if (!hasFocus) {
                editTextCommand.clearFocus();
            }
        });

        editTextCommand.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                shouldMaintainFocus = true;
                sendCommand();
                return true;
            }
            return false;
        });

        editTextCommand.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                shouldMaintainFocus = true;
                sendCommand();
                mainHandler.postDelayed(() -> {
                    if (isViewAvailable() && editTextCommand != null) {
                        editTextCommand.requestFocus();
                    }
                }, 50);
                return true;
            }
            return false;
        });

        connectToTerminal();
        return root;
    }

    @Override
    public void onLogReceived(String line) {
        appendOutput(line);
    }

    @Override
    public void onConsoleCleared() {
        clearTerminalOutput();
    }

    @Override
    public void onStatusChanged(String status) {
        wsStatus = status;
        if ("offline".equalsIgnoreCase(status)) {
            appendOutput("服务器已停止。");
        }
        // 状态变化后刷新菜单，更新"快捷指令"的可用性
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (isViewAvailable()) {
                    requireActivity().invalidateOptionsMenu();
                }
            });
        }
    }

    private void applyTerminalColors() {
        Context context = getContext();
        if (context == null) return;
        if (recyclerViewOutput != null) {
            TerminalColorUtils.applyTerminalBackgroundColor(context, recyclerViewOutput);
        }
        if (terminalAdapter != null) {
            terminalAdapter.notifyDataSetChanged();
        }
    }

    private boolean isViewAvailable() {
        return isAdded() && getView() != null && getContext() != null;
    }

    private void showToast(String message) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private static String normalizeAnsiForDisplay(String line) {
        if (line == null || line.isEmpty()) return "";
        String s = line;
        s = P_OSC.matcher(s).replaceAll("");
        s = P_SOS.matcher(s).replaceAll("");
        s = P_CSI_MOVE.matcher(s).replaceAll("");
        s = P_CSI_PRIVATE.matcher(s).replaceAll("");
        s = P_ESC_SHORT.matcher(s).replaceAll("");
        s = P_CTRL_DISPLAY.matcher(s).replaceAll("");
        return s;
    }

    private static String stripAnsiForLogs(String line) {
        if (line == null || line.isEmpty()) return "";
        String s = normalizeAnsiForDisplay(line);
        s = P_CSI_ANY.matcher(s).replaceAll("");
        s = P_ESC_ANY.matcher(s).replaceAll("");
        s = P_CTRL_ALL.matcher(s).replaceAll("");
        return s;
    }

    private void registerWebSocketListener() {
        int deviceId = getCurrentDeviceId();
        if (deviceId <= 0) {
            return;
        }
        if (!wsListenerRegistered || registeredDeviceId != deviceId) {
            wsManager.addListener(this, deviceId);
            wsListenerRegistered = true;
            registeredDeviceId = deviceId;
        }
    }

    private void unregisterWebSocketListener() {
        if (wsListenerRegistered) {
            wsManager.removeListener(this);
            wsListenerRegistered = false;
            registeredDeviceId = -1;
        }
    }

    private void showManagedDialog(AlertDialog dialog) {
        if (!isViewAvailable()) {
            dialog.dismiss();
            return;
        }
        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) {
                activeDialog = null;
            }
        });
        dialog.show();
    }

    private void dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }
        activeDialog = null;
    }

    @Override
    public void onConnected() {
        appendOutput("已连接到服务器");
        appendOutput("感谢您使用 简幻欢 以及该APP");
    }

    @Override
    public void onDisconnected(String reason) {
        appendOutput("连接已断开: " + reason);
        checkAndReconnect();
    }

    @Override
    public void onError(String message) {
        appendOutput("连接错误: " + message);
        // 积分不足（code 402）时连接不可能成功，停止自动重连
        if (TermApi.isInsufficientCreditsError(message)) {
            isInsufficientCreditsBlocked = true;
            appendOutput("账号积分不足，无法开启当前服务器，已停止自动重连。");
            showToast("账号积分不足，无法开启当前服务器");
            return;
        }
        checkAndReconnect();
    }

    private void checkAndReconnect() {
        if (isInsufficientCreditsBlocked) {
            return;
        }
        int deviceId = getCurrentDeviceId();
        if (isAppInForeground && isNetworkConnected() && !isReconnectScheduled && !wsManager.isConnectedTo(deviceId)) {
            isReconnectScheduled = true;
            mainHandler.postDelayed(() -> {
                int currentDeviceId = getCurrentDeviceId();
                if (isViewAvailable() && isAppInForeground && isNetworkConnected() && !wsManager.isConnectedTo(currentDeviceId)) {
                    appendOutput("尝试重新连接到服务器...");
                    connectToTerminal();
                }
                isReconnectScheduled = false;
            }, 2000);
        }
    }

    private void setupToolbarAiMenu() {
        quickCommandMenuManager = new QuickCommandMenuManager(
                requireContext(),
                () -> getCurrentDeviceId(),
                () -> {
                    String s = wsStatus;
                    if (s != null) {
                        return !"offline".equalsIgnoreCase(s);
                    }
                    return true;
                },
                () -> {
                    if (isViewAvailable()) {
                        requireActivity().invalidateOptionsMenu();
                    }
                },
                this::handleQuickCommandSelected);

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                SubMenu aiMenu = menu.addSubMenu(Menu.NONE, R.id.action_terminal_ai, 0, "AI助手");
                aiMenu.setIcon(R.drawable.ic_ai_assistant);
                aiMenu.add(Menu.NONE, R.id.action_terminal_copy_output, 0, "复制终端内容");
                aiMenu.add(Menu.NONE, R.id.action_terminal_select_text, 1, isSelectionMode ? "退出选择模式" : "跨行选择复制");
                aiMenu.add(Menu.NONE, R.id.action_ai_history, 2, "AI历史记录");
                aiMenu.add(Menu.NONE, R.id.action_ai_troubleshoot, 3, "AI疑难解答");
                aiMenu.add(Menu.NONE, R.id.action_ai_analyze, 4, "AI故障分析");
                aiMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

                // 快捷指令 — 递归嵌套 SubMenu，仅终端页出现
                if (quickCommandMenuManager != null) {
                    quickCommandMenuManager.ensureLoaded();
                    quickCommandMenuManager.onCreateMenu(menu);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (quickCommandMenuManager != null
                        && quickCommandMenuManager.onMenuItemSelected(item)) {
                    return true;
                }
                return handleTerminalMenuItem(item);
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private int countPlaceholders(QuickCommandNode node) {
        int max = 0;
        List<String> templates = node.actions;
        if (templates == null || templates.isEmpty()) {
            // 兼容单命令字段
            if (node.command != null && !node.command.isEmpty()) {
                templates = java.util.Collections.singletonList(node.command);
            }
        }
        if (templates == null) return 0;

        for (String t : templates) {
            if (t == null) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("!\\{(\\d+)\\}").matcher(t);
            while (m.find()) {
                int n = Integer.parseInt(m.group(1));
                if (n > max) max = n;
            }
        }
        return max;
    }

    /**
     * 快捷指令被选中：无参数直接发送，有参数弹输入框。
     */
    private void handleQuickCommandSelected(QuickCommandNode node) {
        if (!isViewAvailable()) return;
        if (node == null) return;

        // 无显式参数定义 → 若模板含 !{N} 占位符则自动推导输入框，否则直接发送
        List<QuickCommandNode.Param> params = node.params;
        if (params == null || params.isEmpty()) {
            int placeholderCount = countPlaceholders(node);
            if (placeholderCount == 0) {
                executeQuickCommand(node, new String[0]);
                return;
            }
            // 合成临时代理参数列表，复用下方参数输入逻辑
            List<QuickCommandNode.Param> derived = new ArrayList<>();
            for (int i = 0; i < placeholderCount; i++) {
                QuickCommandNode.Param p = new QuickCommandNode.Param();
                p.name = "参数 " + (i + 1);
                p.type = "string";
                p.hint = "请输入参数 " + (i + 1);
                derived.add(p);
            }
            params = derived;
        }

        Context context = requireContext();
        float density = context.getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        final List<QuickCommandNode.Param> paramList = params;
        List<View> inputViews = new ArrayList<>();

        for (int i = 0; i < paramList.size(); i++) {
            QuickCommandNode.Param param = paramList.get(i);

            TextView label = new TextView(context);
            label.setText(param.name);
            label.setPadding(0, i > 0 ? padding : 0, 0, (int) (8 * density));
            label.setTextSize(16);
            container.addView(label);

            if ("select".equals(param.type)) {
                List<QuickCommandNode.Option> options = param.options;
                String[] displayItems;
                if (options != null && !options.isEmpty()) {
                    displayItems = new String[options.size()];
                    for (int k = 0; k < options.size(); k++) {
                        displayItems[k] = options.get(k).label;
                    }
                } else {
                    displayItems = new String[]{""};
                }
                Spinner spinner = new Spinner(context);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_dropdown_item, displayItems);
                spinner.setAdapter(adapter);
                container.addView(spinner);
                inputViews.add(spinner);
            } else {
                EditText editText = new EditText(context);
                editText.setHint(param.hint != null && !param.hint.isEmpty() ? param.hint : "请输入" + param.name);
                editText.setSingleLine(true);
                container.addView(editText);
                inputViews.add(editText);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(node.name)
                .setView(container)
                .setPositiveButton("执行", (d, which) -> {
                    if (!isViewAvailable()) return;
                    String[] values = new String[inputViews.size()];
                    for (int i = 0; i < inputViews.size(); i++) {
                        View v = inputViews.get(i);
                        if (v instanceof EditText) {
                            values[i] = ((EditText) v).getText().toString().trim();
                        } else if (v instanceof Spinner) {
                            int pos = ((Spinner) v).getSelectedItemPosition();
                            List<QuickCommandNode.Option> opts = paramList.get(i).options;
                            if (opts != null && pos >= 0 && pos < opts.size()) {
                                values[i] = opts.get(pos).value;
                            } else {
                                values[i] = ((Spinner) v).getSelectedItem().toString();
                            }
                        }
                    }
                    executeQuickCommand(node, values);
                })
                .setNegativeButton("取消", null)
                .create();
        showManagedDialog(dialog);
    }

    /**
     * 替换占位符并发送命令。
     */
    private void executeQuickCommand(QuickCommandNode item, String[] values) {
        if (!isViewAvailable()) return;

        List<String> actions = item.actions;
        if (actions == null || actions.isEmpty()) {
            showToast("指令模板为空");
            return;
        }

        int deviceId = getCurrentDeviceId();
        int sentCount = 0;

        for (String template : actions) {
            if (template == null || template.trim().isEmpty()) continue;

            String cmd = template;
            for (int i = 0; i < values.length; i++) {
                String placeholder = "!{" + (i + 1) + "}";
                String value = values[i] != null ? values[i] : "";
                boolean needQuote = item.quote;
                if (!needQuote && i < item.params.size()) {
                    needQuote = item.params.get(i).quote;
                }
                if (needQuote && !value.isEmpty()) {
                    value = "\"" + value + "\"";
                }
                cmd = cmd.replace(placeholder, value);
            }

            if (wsManager.sendCommand(deviceId, cmd)) {
                sentCount++;
            }
        }

        if (sentCount > 0) {
            showToast("已发送 " + sentCount + " 条指令");
        } else {
            showToast("发送失败：终端未连接");
        }
    }

    private boolean handleTerminalMenuItem(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_terminal_copy_output) {
            showTerminalOutputDialog();
            return true;
        }
        if (itemId == R.id.action_terminal_select_text) {
            if (isSelectionMode) {
                exitSelectionMode();
            } else {
                enterSelectionMode();
            }
            return true;
        }
        if (itemId != R.id.action_ai_history
                && itemId != R.id.action_ai_troubleshoot
                && itemId != R.id.action_ai_analyze) {
            return false;
        }
        if (isAiRequestRunning) {
            showToast("AI 正在处理中，请稍候");
            return true;
        }

        int deviceId = getCurrentDeviceId();
        if (deviceId == -1) {
            showToast("设备信息缺失");
            return true;
        }

        if (itemId == R.id.action_ai_history) {
            handleAiHistory(deviceId);
        } else if (itemId == R.id.action_ai_troubleshoot) {
            handleAiTroubleshoot(deviceId);
        } else if (itemId == R.id.action_ai_analyze) {
            handleAiAnalyze(deviceId);
        }
        return true;
    }

    private void showTerminalOutputDialog() {
        if (!isViewAvailable() || terminalAdapter == null) return;
        String snapshot = terminalAdapter.getTerminalOutputSnapshot(pendingLines);
        if (snapshot.trim().isEmpty()) {
            showToast("终端暂无内容");
            return;
        }

        Context context = requireContext();
        TextView outputView = new TextView(context);
        int padding = (int) (12 * context.getResources().getDisplayMetrics().density);
        outputView.setPadding(padding, padding, padding, padding);
        outputView.setText(snapshot);
        outputView.setTextIsSelectable(true);
        outputView.setGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextSize(12);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        scrollView.addView(outputView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("终端内容")
                .setView(scrollView)
                .setPositiveButton("复制全部", (d, which) -> copyToClipboard("Terminal Output", snapshot, "已复制终端内容"))
                .setNegativeButton("关闭", null)
                .create();
        showManagedDialog(dialog);
    }

    /**
     * 进入跨行选择模式：用带 ANSI 颜色的整块文本覆盖终端区域，
     * 长按拖选即可跨行复制（与终端同字号、等宽、同配色），完成后退出。
     * @param anchorPosition 长按时所在行，覆盖层将定位到附近；-1 表示滚到底部
     */
    private void enterSelectionMode(int anchorPosition) {
        if (!isViewAvailable() || terminalAdapter == null || selectOverlay == null) return;
        if (terminalAdapter.getItemCount() == 0 && pendingLines.isEmpty()) {
            showToast("终端暂无内容");
            return;
        }
        Context context = requireContext();
        String snapshot = terminalAdapter.getRawOutputSnapshot(pendingLines);

        TerminalColorUtils.applyTerminalBackgroundColor(context, selectOverlay);
        TerminalColorUtils.applyTerminalColors(context, selectTextView);
        TerminalColorUtils.applyTerminalFontSize(context, selectTextView);
        selectTextView.setTypeface(Typeface.MONOSPACE);
        try {
            AnsiParser.setAnsiText(selectTextView, snapshot, 0);
        } catch (Exception e) {
            selectTextView.setText(stripAnsiForLogs(snapshot));
        }
        selectTextView.setTextIsSelectable(true);
        if (selectFastScroll != null) {
            selectFastScroll.postInvalidate();
        }
        // 在系统选择工具条里注入"复制所选"：部分国产 ROM 会拦截系统复制项，
        // 该项直接走应用自己的剪贴板写入，绕开 ROM 魔改路径
        selectTextView.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            private static final int MENU_COPY_SELECTION = 1;

            @Override
            public boolean onCreateActionMode(@NonNull android.view.ActionMode mode, @NonNull android.view.Menu menu) {
                menu.add(Menu.NONE, MENU_COPY_SELECTION, 0, "复制所选");
                return true;
            }

            @Override
            public boolean onPrepareActionMode(@NonNull android.view.ActionMode mode, @NonNull android.view.Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(@NonNull android.view.ActionMode mode, @NonNull android.view.MenuItem item) {
                if (item.getItemId() == MENU_COPY_SELECTION) {
                    copySelectionFromOverlay();
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
            }
        });

        selectOverlay.setVisibility(View.VISIBLE);
        isSelectionMode = true;
        final int anchor = anchorPosition;
        selectScroll.post(() -> {
            if (selectScroll == null || !isAdded()) return;
            if (anchor >= 0) {
                // 按行号近似定位到长按位置附近（长行折行会有少量偏差）
                selectScroll.scrollTo(0, Math.max(0, anchor * selectTextView.getLineHeight()));
            } else {
                selectScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
        requireActivity().invalidateOptionsMenu();
    }

    private void enterSelectionMode() {
        enterSelectionMode(-1);
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        if (selectOverlay != null) {
            selectOverlay.setVisibility(View.GONE);
        }
        if (selectTextView != null) {
            selectTextView.setText("");
        }
        if (isAdded() && getActivity() != null) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void handleAiHistory(int deviceId) {
        runAiRequest("正在获取 AI 历史记录…", callback ->
                aiApi.getAiHistory(requireContext(), deviceId, callback), this::showAiHistoryList);
    }

    private void showAiHistoryList(JSONObject data) {
        JSONArray list = data.optJSONArray("list");
        if (list == null || list.length() == 0) {
            showToast("暂无 AI 历史记录");
            return;
        }

        List<Long> ids = new ArrayList<>();
        String[] items = new String[list.length()];
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                ids.add(-1L);
                items[i] = "未知记录";
                continue;
            }
            long id = item.optLong("id", -1L);
            ids.add(id);
            String type = item.optString("type", "unknown");
            String time = item.optString("answer_time", "未知时间");
            String label = switch (type) {
                case "log", AiApi.TYPE_ANALYZE -> "故障分析";
                case AiApi.TYPE_ANSWER -> "疑难解答";
                default -> type;
            };
            items[i] = String.format(Locale.getDefault(), "%s\n%s", label, time);
        }

        if (!isViewAvailable()) return;
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("AI 历史记录")
                .setItems(items, (d, which) -> {
                    long id = ids.get(which);
                    if (id <= 0) {
                        showToast("记录无效");
                        return;
                    }
                    fetchAiHistoryDetail(id);
                })
                .setNegativeButton("关闭", null)
                .create();
        showManagedDialog(dialog);
    }

    private void fetchAiHistoryDetail(long historyId) {
        int deviceId = getCurrentDeviceId();
        if (deviceId == -1) {
            showToast("设备信息缺失");
            return;
        }
        runAiRequest("正在获取历史详情…", callback ->
                aiApi.getAiHistoryDetail(requireContext(), deviceId, historyId, callback));
    }

    private void handleAiTroubleshoot(int deviceId) {
        showInputDialog(input -> {
            if (input.trim().isEmpty()) {
                showToast("内容不能为空");
                return;
            }
            runAiRequest("正在向 AI 提问…", callback ->
                    aiApi.answerQuestion(requireContext(), deviceId, input.trim(), callback));
        });
    }

    private void handleAiAnalyze(int deviceId) {
        String logs = terminalAdapter.getCleanLogs();
        if (logs.trim().isEmpty()) {
            showToast("终端暂无服务器输出信息，请等待日志产生后再试");
            return;
        }
        String payload = buildAiAnalyzePayload(logs);
        showAnalyzeDialog(deviceId, payload);
    }

    private void showAnalyzeDialog(int deviceId, String payload) {
        if (!isViewAvailable()) return;
        Context context = requireContext();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding / 2);

        TextView typeLabel = new TextView(context);
        typeLabel.setText("故障类型");
        container.addView(typeLabel);

        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, AI_LOG_FAULT_TYPES);
        spinner.setAdapter(adapter);
        spinner.setSelection(3);
        container.addView(spinner);

        TextView supplementLabel = new TextView(context);
        supplementLabel.setText("补充说明（可选）");
        supplementLabel.setPadding(0, padding / 2, 0, 0);
        container.addView(supplementLabel);

        EditText input = new EditText(context);
        input.setMinLines(3);
        input.setHint("例如：什么时候开始报错、做过什么操作、希望解决什么问题");
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("AI日志回答")
                .setView(container)
                .setPositiveButton("开始分析", (d, which) -> {
                    if (!isViewAvailable()) return;
                    String selectedType = String.valueOf(spinner.getSelectedItem());
                    String supplement = input.getText() == null ? "" : input.getText().toString().trim();
                    runAiRequest("正在分析终端日志…", callback ->
                            aiApi.analyzeLogs(requireContext(), deviceId, selectedType, supplement, payload, callback));
                })
                .setNegativeButton("取消", null)
                .create();
        showManagedDialog(dialog);
    }

    private String buildAiAnalyzePayload(String logs) {
        String normalized = logs == null ? "" : logs.trim();
        if (normalized.length() <= MAX_AI_ANALYZE_CHARS) {
            return normalized;
        }
        showToast("日志较长，已自动截取最近部分进行分析");
        return normalized.substring(normalized.length() - MAX_AI_ANALYZE_CHARS);
    }

    private interface InputCallback {
        void onInput(String input);
    }

    private interface AiRequestInvoker {
        void invoke(AiApi.Callback callback);
    }

    private interface AiSuccessHandler {
        void onSuccess(JSONObject data);
    }

    private void runAiRequest(String loadingText, AiRequestInvoker invoker) {
        runAiRequest(loadingText, invoker, this::showAiResponse);
    }

    private void runAiRequest(String loadingText, AiRequestInvoker invoker, AiSuccessHandler successHandler) {
        if (!isViewAvailable()) return;
        if (isAiRequestRunning) {
            showToast("AI 正在处理中，请稍候");
            return;
        }
        isAiRequestRunning = true;
        int requestGeneration = ++aiRequestGeneration;
        showAiLoadingDialog(loadingText);
        invoker.invoke(new AiApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (requestGeneration != aiRequestGeneration) return;
                isAiRequestRunning = false;
                dismissAiLoadingDialog();
                if (isViewAvailable()) {
                    successHandler.onSuccess(data);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (requestGeneration != aiRequestGeneration) return;
                isAiRequestRunning = false;
                dismissAiLoadingDialog();
                if (isViewAvailable()) {
                    showToast("请求失败: " + errorMsg);
                }
            }
        });
    }

    private void showAiLoadingDialog(String loadingText) {
        mainHandler.removeCallbacks(aiSlowHintRunnable);
        if (!isViewAvailable()) return;
        Context context = requireContext();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(context);
        container.addView(progressBar);

        aiLoadingMessageView = new TextView(context);
        aiLoadingMessageView.setPadding(0, padding / 2, 0, 0);
        aiLoadingMessageView.setText(loadingText + "\n请稍候…");
        container.addView(aiLoadingMessageView);

        aiLoadingDialog = new AlertDialog.Builder(context)
                .setTitle("AI 助手")
                .setView(container)
                .setCancelable(false)
                .create();
        aiLoadingDialog.show();
        mainHandler.postDelayed(aiSlowHintRunnable, 2500L);
    }

    private void dismissAiLoadingDialog() {
        mainHandler.removeCallbacks(aiSlowHintRunnable);
        if (aiLoadingDialog != null && aiLoadingDialog.isShowing()) {
            aiLoadingDialog.dismiss();
        }
        aiLoadingDialog = null;
        aiLoadingMessageView = null;
    }

    private void showInputDialog(InputCallback callback) {
        if (!isViewAvailable()) return;
        Context context = requireContext();
        EditText input = new EditText(context);
        input.setMinLines(3);
        input.setPadding(50, 40, 50, 40);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("请输入您的问题")
                .setView(input)
                .setPositiveButton("确定", (d, which) -> {
                    if (isViewAvailable()) {
                        callback.onInput(input.getText().toString());
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        showManagedDialog(dialog);
    }

    private void showAiResponse(JSONObject data) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> showAiResponse(data));
            return;
        }
        if (!isViewAvailable()) return;
        try {
            Context context = requireContext();
            String content = AiResponseFormatter.format(data);
            TextView contentView = new TextView(context);
            int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
            contentView.setPadding(padding, padding, padding, padding);
            MarkdownRenderer.getInstance(context).render(contentView, content);

            android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
            scrollView.addView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("AI 助手")
                    .setView(scrollView)
                    .setPositiveButton("复制回复", (d, which) -> copyToClipboard("AI Reply", content, "已复制到剪贴板"))
                    .setNegativeButton("关闭", null)
                    .create();
            showManagedDialog(dialog);
        } catch (Exception e) {
            Log.e("AiResponse", "Error parsing AI response", e);
            showToast("解析回复失败");
        }
    }

    private void copyToClipboard(String label, String text, String toastText) {
        Context context = getContext();
        if (context == null) return;
        ClipboardUtils.copyPlainText(context, label, text, toastText);
    }

    /** 兼容国产 ROM 的复制：不走系统 ActionMode 复制项，自己读选区、写剪贴板、读回校验并给出明确反馈 */
    private void copySelectionFromOverlay() {
        if (selectTextView == null || !isAdded()) return;
        int start = selectTextView.getSelectionStart();
        int end = selectTextView.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) {
            showToast("请先长按拖选文本");
            return;
        }
        CharSequence selected = selectTextView.getText()
                .subSequence(Math.min(start, end), Math.max(start, end));
        if (selected.toString().trim().isEmpty()) {
            showToast("所选内容为空");
            return;
        }
        Context context = requireContext();
        boolean ok = ClipboardUtils.copyPlainText(context, "Terminal Selection",
                selected.toString(), null);
        // 读回校验：Android 10+ 前台应用可读取自己刚写入的剪贴板；
        // 部分国产 ROM 会静默拦截写入，读不回来时提示用户检查权限
        if (ok) {
            try {
                android.content.ClipboardManager cm =
                        context.getSystemService(android.content.ClipboardManager.class);
                CharSequence clip = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                        ? cm.getPrimaryClip().getItemAt(0).getText() : null;
                ok = clip != null && clip.length() > 0;
            } catch (Exception e) {
                // 读回失败不必然代表写入失败，保守视为成功
                ok = true;
            }
        }
        showToast(ok
                ? "已复制所选内容（" + selected.length() + " 字符）"
                : "复制失败：请在系统设置中允许本应用使用剪贴板");
    }

    public void onHostDeviceChanged() {
        unregisterWebSocketListener();
        activeDeviceId = -1;
        wsStatus = null;
        connectToTerminal();

        if (quickCommandMenuManager != null) {
            quickCommandMenuManager.reset();
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void connectToTerminal() {
        if (!isAdded() || getContext() == null) return;
        int deviceId = getCurrentDeviceId();
        if (deviceId <= 0) return;
        // 主动发起连接视为用户重试，解除积分不足导致的重连封锁
        isInsufficientCreditsBlocked = false;
        if (activeDeviceId != deviceId) {
            activeDeviceId = deviceId;
            clearTerminalOutput();
        }
        registerWebSocketListener();
        wsManager.connect(requireContext(), deviceId, true);
    }

    private void refreshTerminalLogs() {
        if (!isViewAvailable()) return;
        int deviceId = getCurrentDeviceId();
        if (deviceId <= 0) return;
        registerWebSocketListener();
        if (wsManager.isConnectedTo(deviceId)) {
            if (terminalAdapter != null && terminalAdapter.getItemCount() == 0 && pendingLines.isEmpty()) {
                wsManager.requestLogs(false);
            }
        } else {
            connectToTerminal();
        }
    }

    private int getCurrentDeviceId() {
        if (getActivity() instanceof ServerManages activity && activity.getDeviceId() > 0) {
            return activity.getDeviceId();
        }
        Context context = getContext();
        if (context == null) return -1;
        SharedPreferences sp = context.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        return sp.getInt("device_id", -1);
    }

    private void sendCommand() {
        if (!isViewAvailable() || editTextCommand == null) return;
        String command = editTextCommand.getText().toString().trim();
        if (command.isEmpty()) {
            return;
        }

        shouldMaintainFocus = true;
        int deviceId = getCurrentDeviceId();
        boolean sent = wsManager.sendCommand(deviceId, command);
        if (!sent) {
            showToast("发送失败：终端未连接或连接不可用");
            mainHandler.postDelayed(() -> {
                if (shouldMaintainFocus && isViewAvailable() && editTextCommand != null) {
                    editTextCommand.requestFocus();
                }
            }, 100);
            return;
        }

        editTextCommand.setText("");
        mainHandler.postDelayed(() -> {
            if (shouldMaintainFocus && isViewAvailable() && editTextCommand != null) {
                editTextCommand.requestFocus();
            }
        }, 100);
    }

    private void appendOutput(String text) {
        if (!isViewAvailable() || text == null || text.isEmpty()) return;
        String normalized = text.replace("\r\n", "\n");
        boolean endsWithNewline = normalized.endsWith("\n");

        if (!normalized.contains("\r")) {
            // 无 \r 的常规多行文本：保持历史行为，每条消息的每段都是独立行。
            // Pterodactyl 的 console output 通常一条消息就是一行（不带 \n），
            // 不能把它们当成"未写完的部分行"互相拼接。
            String[] split = normalized.split("\n", -1);
            int lineCount = endsWithNewline ? split.length - 1 : split.length;
            for (int i = 0; i < lineCount; i++) {
                String line = split[i];
                if (!line.isEmpty() && stripAnsiForLogs(line).trim().isEmpty()) continue;
                pendingLines.add(line);
            }
            lastLinePartial = false;
            scheduleBufferFlush();
            return;
        }

        // 含 \r：进度条等单行重绘场景，才启用覆盖状态机
        String[] parts = normalized.split("\n", -1);
        int completeCount = parts.length - 1;
        for (int i = 0; i < completeCount; i++) {
            String line = applyCarriageReturn(parts[i]);
            if (!line.isEmpty() && stripAnsiForLogs(line).trim().isEmpty()) continue;
            pendingLines.add(line);
        }
        String tail = parts[parts.length - 1];
        if (!tail.isEmpty()) {
            if (tail.indexOf('\r') >= 0) {
                String content = applyCarriageReturn(tail);
                if (tail.charAt(tail.length() - 1) == '\r') {
                    // 以 \r 结尾：进度条帧，行等待下一帧覆盖
                    overwriteOrAppendLastLine(content);
                    lastLinePartial = true;
                } else {
                    // \r 在中间：\r 前是行首残留（如 MC 控制台的 ">...."），
                    // \r 后才是本行真实内容 → 独立成行（如命令补全列表，每条一行）
                    if (!content.isEmpty() && !stripAnsiForLogs(content).trim().isEmpty()) {
                        pendingLines.add(content);
                    }
                    lastLinePartial = false;
                }
            } else if (lastLinePartial) {
                // 上一帧以 \r 结尾、本段无 \r：视为同一行的续写
                appendToLastLine(tail);
            } else {
                pendingLines.add(tail);
            }
        }
        lastLinePartial = !endsWithNewline && lastLinePartial;
        scheduleBufferFlush();
    }

    /** P5：覆盖（或新建）终端流的最后一行。行已在 RecyclerView 时标记 flush 时先替换尾行 */
    private void overwriteOrAppendLastLine(String content) {
        if (lastLinePartial) {
            if (pendingLines.isEmpty()) {
                if (terminalAdapter != null && terminalAdapter.getItemCount() > 0) {
                    pendingLines.add(content);
                    pendingOverwriteCount = 1;
                    return;
                }
            } else {
                pendingLines.set(pendingLines.size() - 1, content);
                return;
            }
        }
        pendingLines.add(content);
    }

    /** P5：向最后一行追加内容（\r 之间的增量） */
    private void appendToLastLine(String tail) {
        if (pendingLines.isEmpty()) {
            if (terminalAdapter != null && terminalAdapter.getItemCount() > 0) {
                pendingLines.add(terminalAdapter.getLastLine() + tail);
                pendingOverwriteCount = 1;
                return;
            }
            pendingLines.add(tail);
            return;
        }
        int lastIdx = pendingLines.size() - 1;
        pendingLines.set(lastIdx, pendingLines.get(lastIdx) + tail);
    }

    /**
     * 终端 \r 语义近似：光标回到行首后继续输出。
     * 进度条等整行重绘场景下，取最后一个 \r 之后的非空段作为最终内容；
     * \r 后无内容则保留 \r 前的内容（仅回车未重绘）。
     */
    private static String applyCarriageReturn(String s) {
        if (s.indexOf('\r') < 0) return s;
        String[] segments = s.split("\r", -1);
        String result = segments[0];
        for (int i = 1; i < segments.length; i++) {
            if (!segments[i].isEmpty()) {
                result = segments[i];
            }
        }
        return result;
    }

    private void scheduleBufferFlush() {
        if (!isViewAvailable() || terminalAdapter == null) return;
        if (!isBufferUpdateScheduled) {
            isBufferUpdateScheduled = true;
            long renderDelay = FLUSH_DELAY_MS;
            mainHandler.postDelayed(bufferFlushRunnable, renderDelay);
        }
    }

    private void flushBufferedLines() {
        // 触摸中（可能正在长按选择文本）：推迟到抬手后再 flush，
        // 避免 notify 导致正在选择的 item 被回收重建、放大镜冻结
        if (isTouchingRecyclerView && isViewAvailable()) {
            mainHandler.postDelayed(bufferFlushRunnable, FLUSH_DELAY_MS);
            return;
        }
        if (isViewAvailable()) {
            updateOutputWithFocusPreservation();
        }
        isBufferUpdateScheduled = false;
    }

    private void clearTerminalOutput() {
        mainHandler.removeCallbacks(bufferFlushRunnable);
        isBufferUpdateScheduled = false;
        pendingLines.clear();
        lastLinePartial = false;
        pendingOverwriteCount = 0;
        if (terminalAdapter != null) {
            terminalAdapter.clear();
        }
    }

    private void updateOutputWithFocusPreservation() {
        if (!isViewAvailable() || terminalAdapter == null || recyclerViewOutput == null) return;
        boolean hadFocus = editTextCommand != null && editTextCommand.hasFocus();

        // P3：仅当用户本来就停在底部（且未处于选择模式）时才跟随滚动，
        // 回看历史时不被打断
        boolean followTail = isTerminalAtBottom() && !isSelectionMode;

        if (!pendingLines.isEmpty()) {
            List<String> batch = new ArrayList<>(pendingLines);
            pendingLines.clear();
            int replaceCount = Math.min(pendingOverwriteCount, 1);
            pendingOverwriteCount = 0;
            terminalAdapter.addLines(batch, replaceCount);
        }

        if (followTail) {
            recyclerViewOutput.post(() -> {
                if (!isViewAvailable() || terminalAdapter == null || recyclerViewOutput == null) return;
                scrollToBottom();
                restoreCommandFocusIfNeeded(hadFocus);
            });
        } else {
            restoreCommandFocusIfNeeded(hadFocus);
        }
    }

    private void restoreCommandFocusIfNeeded(boolean hadFocus) {
        if ((hadFocus || shouldMaintainFocus) && isViewAvailable() && editTextCommand != null) {
            editTextCommand.post(() -> {
                if (!isViewAvailable() || editTextCommand == null) return;
                editTextCommand.requestFocus();
                shouldMaintainFocus = false;
            });
        }
    }

    /** 终端是否停在最后一屏（允许 2 行容差） */
    private boolean isTerminalAtBottom() {
        if (terminalAdapter == null || terminalAdapter.getItemCount() == 0) return true;
        RecyclerView.LayoutManager lm = recyclerViewOutput.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return true;
        int lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
        return lastVisible >= terminalAdapter.getItemCount() - 2;
    }

    private void scrollToBottom() {
        if (terminalAdapter == null || recyclerViewOutput == null) return;
        int itemCount = terminalAdapter.getItemCount();
        if (itemCount > 0) {
            recyclerViewOutput.scrollToPosition(itemCount - 1);
        }
    }

    @Override
    public void onDestroyView() {
        aiRequestGeneration++;
        isAiRequestRunning = false;
        isBufferUpdateScheduled = false;
        isReconnectScheduled = false;
        shouldMaintainFocus = false;
        isSelectionMode = false;
        mainHandler.removeCallbacksAndMessages(null);
        dismissAiLoadingDialog();
        dismissActiveDialog();
        unregisterWebSocketListener();
        pendingLines.clear();
        lastLinePartial = false;
        pendingOverwriteCount = 0;
        selectOverlay = null;
        selectScroll = null;
        selectTextView = null;
        if (fastScrollView != null) {
            fastScrollView.detach();
            fastScrollView = null;
        }
        if (selectFastScroll != null) {
            selectFastScroll.detach();
            selectFastScroll = null;
        }
        if (recyclerViewOutput != null) {
            recyclerViewOutput.setAdapter(null);
        }
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        registerWebSocketListener();
    }

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        applyTerminalColors();
        // P8：设置页修改行数上限后，返回终端页立即生效
        if (terminalAdapter != null && getContext() != null) {
            terminalAdapter.setMaxLines(TerminalLineLimitManager.getInstance(requireContext()).getLineLimit());
        }
        refreshTerminalLogs();
        // 服务器切换/重新进入后刷新菜单可用性
        if (isViewAvailable()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
    }

    private boolean isNetworkConnected() {
        Context context = getContext();
        if (context == null) return false;
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null &&
                (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN));
    }

        private static class LinesAdapter extends RecyclerView.Adapter<LinesAdapter.LineVH> {
        private static final int MIN_LINES = 100;
        private final List<String> lines = new ArrayList<>();
        private final Context context;
        private int maxLines;

        LinesAdapter(Context context, int maxLines) {
            this.context = context;
            this.maxLines = Math.max(MIN_LINES, maxLines);
        }

        /** P8：设置变更后调整行数上限，超出立即裁剪 */
        void setMaxLines(int maxLines) {
            maxLines = Math.max(MIN_LINES, maxLines);
            if (this.maxLines == maxLines) return;
            this.maxLines = maxLines;
            int overflow = Math.max(0, lines.size() - maxLines);
            if (overflow > 0) {
                lines.subList(0, overflow).clear();
                notifyItemRangeRemoved(0, overflow);
            }
        }

        @NonNull
        @Override
        public LineVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            AnsiTextView tv = new AnsiTextView(parent.getContext());
            tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(tv.getPaddingLeft() + 8, tv.getPaddingTop() + 2, tv.getPaddingRight() + 8, tv.getPaddingBottom() + 2);
            // 行内不做文本选择：每行是独立 TextView 无法跨行，复制统一走
            // 长按进入的选择模式覆盖层（也免去 selectable 的渲染开销）
            TerminalColorUtils.applyTerminalColors(context, tv);
            TerminalColorUtils.applyTerminalFontSize(context, tv);
            return new LineVH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull LineVH holder, int position) {
            String line = lines.get(position);
            try {
                AnsiParser.setAnsiText(holder.textView, normalizeAnsiForDisplay(line), 0);
            } catch (Exception ignored) {
                holder.textView.setText(stripAnsiForLogs(line));
            }
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        /** P5：跨消息行覆盖需要拿到 RecyclerView 中最后一行的内容 */
        String getLastLine() {
            return lines.isEmpty() ? "" : lines.get(lines.size() - 1);
        }

        String getCleanLogs() {
            StringBuilder sb = new StringBuilder();
            String skipMark = "感谢您使用 简幻欢 以及该APP";
            for (String line : lines) {
                if (line.contains(skipMark)) continue;
                String cleanLine = stripAnsiForLogs(line).trim();
                if (cleanLine.isEmpty()) continue;
                sb.append(cleanLine).append("\n");
            }
            return sb.toString();
        }

        String getTerminalOutputSnapshot(List<String> pendingLines) {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                appendSnapshotLine(sb, line);
            }
            if (pendingLines != null) {
                for (String line : pendingLines) {
                    appendSnapshotLine(sb, line);
                }
            }
            return sb.toString();
        }

        /** 选择模式用：保留 ANSI 序列的原始行快照（渲染时保颜色） */
        String getRawOutputSnapshot(List<String> pendingLines) {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            if (pendingLines != null) {
                for (String line : pendingLines) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }

        private void appendSnapshotLine(StringBuilder sb, String line) {
            String cleanLine = stripAnsiForLogs(line);
            if (cleanLine.isEmpty()) return;
            sb.append(cleanLine).append("\n");
        }

        void addLines(List<String> newLines) {
            addLines(newLines, 0);
        }

        /** P5：replaceCount 为 1 时先替换当前最后一行（\r 覆盖），再插入新行 */
        void addLines(List<String> newLines, int replaceCount) {
            if ((newLines == null || newLines.isEmpty()) && replaceCount == 0) return;
            if (newLines == null || newLines.isEmpty()) return;

            int oldSize = lines.size();
            if (replaceCount > 0 && oldSize > 0) {
                int removed = Math.min(replaceCount, oldSize);
                lines.subList(oldSize - removed, oldSize).clear();
                notifyItemRangeRemoved(oldSize - removed, removed);
                oldSize -= removed;
            } else if (replaceCount > 0 && oldSize == 0) {
                // 没有可替换的行，直接插入
            }

            lines.addAll(newLines);
            int overflow = Math.max(0, lines.size() - maxLines);
            if (overflow > 0) {
                lines.subList(0, overflow).clear();
                notifyItemRangeRemoved(0, overflow);
            }
            int insertStart = lines.size() - newLines.size();
            notifyItemRangeInserted(Math.max(0, insertStart), newLines.size());
        }

        void clear() {
            if (lines.isEmpty()) return;
            int oldSize = lines.size();
            lines.clear();
            notifyItemRangeRemoved(0, oldSize);
        }

        static class LineVH extends RecyclerView.ViewHolder {
            final AnsiTextView textView;

            LineVH(@NonNull View itemView) {
                super(itemView);
                this.textView = (AnsiTextView) itemView;
            }
        }
    }
}
