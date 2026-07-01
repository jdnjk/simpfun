package cn.jdnjk.simpfun.ui.ins.term;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.AiApi;
import cn.jdnjk.simpfun.service.TerminalWebSocketListener;
import cn.jdnjk.simpfun.service.TerminalWebSocketManager;
import cn.jdnjk.simpfun.ui.setting.TerminalColorUtils;
import cn.jdnjk.simpfun.utils.AiResponseFormatter;

public class TerminalFragment extends Fragment implements TerminalWebSocketListener {
    private static final int MAX_AI_ANALYZE_CHARS = 12000;
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
    private boolean isBufferUpdateScheduled = false;
    private final Runnable bufferFlushRunnable = () -> {
        if (isViewAvailable()) {
            updateOutputWithFocusPreservation();
        }
        isBufferUpdateScheduled = false;
    };
    private boolean shouldMaintainFocus = false;
    private boolean isAppInForeground = false;
    private boolean isReconnectScheduled = false;
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
        Button buttonSend = root.findViewById(R.id.button_send);
        recyclerViewOutput = root.findViewById(R.id.recycler_view_output);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        layoutManager.setStackFromEnd(true);
        recyclerViewOutput.setLayoutManager(layoutManager);
        terminalAdapter = new LinesAdapter(requireContext());
        recyclerViewOutput.setAdapter(terminalAdapter);

        applyTerminalColors();
        setupToolbarAiMenu();

        buttonSend.setOnClickListener(v -> sendCommand());

        recyclerViewOutput.setOnTouchListener((v, event) -> false);
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
        if ("offline".equalsIgnoreCase(status)) {
            appendOutput("服务器已停止。");
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
        return line
                .replaceAll("\\x1B\\][^\\x07]*(?:\\x07|\\x1B\\\\)", "")
                .replaceAll("\\x1B[P\\^_]([\\s\\S]*?)(?:\\x1B\\\\|\\x07)", "")
                .replaceAll("\\x1B\\[[0-9;:]*[ABCDGHEFSTfJK]", "")
                .replaceAll("\\x1B\\[\\?[0-9;:]*[hl]", "")
                .replaceAll("\\x1B[=>78]", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1A\\x1C-\\x1F\\x7F]", "");
    }

    private static String stripAnsiForLogs(String line) {
        if (line == null || line.isEmpty()) return "";
        return normalizeAnsiForDisplay(line)
                .replaceAll("\\x1B\\[[0-9;:?>=]*[ -/]*[@-~]", "")
                .replaceAll("\\x1B[ -/]*[@-~]", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
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
        checkAndReconnect();
    }

    private void checkAndReconnect() {
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
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                SubMenu aiMenu = menu.addSubMenu(Menu.NONE, R.id.action_terminal_ai, 0, "AI助手");
                aiMenu.setIcon(R.drawable.ic_ai_assistant);
                aiMenu.add(Menu.NONE, R.id.action_terminal_copy_output, 0, "复制终端内容");
                aiMenu.add(Menu.NONE, R.id.action_ai_history, 1, "AI历史记录");
                aiMenu.add(Menu.NONE, R.id.action_ai_troubleshoot, 2, "AI疑难解答");
                aiMenu.add(Menu.NONE, R.id.action_ai_analyze, 3, "AI故障分析");
                aiMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                return handleTerminalMenuItem(item);
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private boolean handleTerminalMenuItem(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_terminal_copy_output) {
            showTerminalOutputDialog();
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
            contentView.setText(content);
            contentView.setTextIsSelectable(true);
            contentView.setMovementMethod(new ScrollingMovementMethod());

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
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        showToast(toastText);
    }

    public void onHostDeviceChanged() {
        unregisterWebSocketListener();
        activeDeviceId = -1;
        connectToTerminal();
    }

    private void connectToTerminal() {
        if (!isAdded() || getContext() == null) return;
        int deviceId = getCurrentDeviceId();
        if (deviceId <= 0) return;
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
        if (normalized.indexOf('\r') >= 0 && normalized.indexOf('\n') < 0) {
            normalized = normalized.substring(normalized.lastIndexOf('\r') + 1);
        } else {
            normalized = normalized.replace('\r', '\n');
        }
        String[] split = normalized.split("\n", -1);
        int lineCount = normalized.endsWith("\n") ? split.length - 1 : split.length;
        for (int i = 0; i < lineCount; i++) {
            String line = split[i];
            if (!line.isEmpty() && stripAnsiForLogs(line).trim().isEmpty()) continue;
            pendingLines.add(line);
        }
        scheduleBufferFlush();
    }

    private void scheduleBufferFlush() {
        if (!isViewAvailable() || terminalAdapter == null) return;
        if (!isBufferUpdateScheduled) {
            isBufferUpdateScheduled = true;
            long renderDelay = 100;
            mainHandler.postDelayed(bufferFlushRunnable, renderDelay);
        }
    }

    private void clearTerminalOutput() {
        mainHandler.removeCallbacks(bufferFlushRunnable);
        isBufferUpdateScheduled = false;
        pendingLines.clear();
        if (terminalAdapter != null) {
            terminalAdapter.clear();
        }
    }

    private void updateOutputWithFocusPreservation() {
        if (!isViewAvailable() || terminalAdapter == null || recyclerViewOutput == null) return;
        boolean hadFocus = editTextCommand != null && editTextCommand.hasFocus();

        if (!pendingLines.isEmpty()) {
            List<String> batch = new ArrayList<>(pendingLines);
            pendingLines.clear();
            terminalAdapter.addLines(batch);
        }

        recyclerViewOutput.post(() -> {
            if (!isViewAvailable() || terminalAdapter == null || recyclerViewOutput == null) return;
            scrollToBottom();

            if ((hadFocus || shouldMaintainFocus) && editTextCommand != null) {
                editTextCommand.post(() -> {
                    if (!isViewAvailable() || editTextCommand == null) return;
                    editTextCommand.requestFocus();
                    shouldMaintainFocus = false;
                });
            }
        });
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
        mainHandler.removeCallbacksAndMessages(null);
        dismissAiLoadingDialog();
        dismissActiveDialog();
        unregisterWebSocketListener();
        pendingLines.clear();
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
        refreshTerminalLogs();
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
        private static final int MAX_LINES = 5000;
        private final List<String> lines = new ArrayList<>();
        private final Context context;

        LinesAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public LineVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            AnsiTextView tv = new AnsiTextView(parent.getContext());
            tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(tv.getPaddingLeft() + 8, tv.getPaddingTop() + 2, tv.getPaddingRight() + 8, tv.getPaddingBottom() + 2);
            tv.setTextIsSelectable(true);
            TerminalColorUtils.applyTerminalColors(context, tv);
            return new LineVH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull LineVH holder, int position) {
            String line = lines.get(position);
            TerminalColorUtils.applyTerminalColors(context, holder.textView);
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

        private void appendSnapshotLine(StringBuilder sb, String line) {
            String cleanLine = stripAnsiForLogs(line);
            if (cleanLine.isEmpty()) return;
            sb.append(cleanLine).append("\n");
        }

        void addLines(List<String> newLines) {
            if (newLines == null || newLines.isEmpty()) return;
            int oldSize = lines.size();
            lines.addAll(newLines);
            int overflow = Math.max(0, lines.size() - MAX_LINES);
            if (overflow == 0) {
                notifyItemRangeInserted(oldSize, newLines.size());
                return;
            }

            lines.subList(0, overflow).clear();
            int removedOldCount = Math.min(overflow, oldSize);
            int oldRemainingCount = oldSize - removedOldCount;
            int insertedCount = lines.size() - oldRemainingCount;
            if (removedOldCount > 0) {
                notifyItemRangeRemoved(0, removedOldCount);
            }
            if (insertedCount > 0) {
                notifyItemRangeInserted(oldRemainingCount, insertedCount);
            }
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
