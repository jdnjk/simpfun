package cn.jdnjk.simpfun.ui.ins.term;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fox2code.androidansi.AnsiParser;
import com.fox2code.androidansi.AnsiTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
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
    private TextView aiLoadingMessageView;
    private final Runnable aiSlowHintRunnable = () -> {
        if (aiLoadingMessageView != null) {
            aiLoadingMessageView.setText("AI 为非流式返回，正在等待完整结果…\n日志越长，等待越久。");
        }
    };

    private final TerminalWebSocketManager wsManager = TerminalWebSocketManager.getInstance();
    private final List<String> pendingLines = new ArrayList<>();
    private boolean isBufferUpdateScheduled = false;
    private boolean shouldMaintainFocus = false;
    private boolean isAppInForeground = false;
    private boolean isReconnectScheduled = false;
    private boolean isAiRequestRunning = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
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
                mainHandler.postDelayed(() -> editTextCommand.requestFocus(), 50);
                return true;
            }
            return false;
        });

        connectToTerminal();
        return root;
    }

    @Override
    public void onLogReceived(String line) {
        String[] split = line.split("\r?\n", -1);
        Collections.addAll(pendingLines, split);
        scheduleBufferFlush();
    }

    @Override
    public void onStatusChanged(String status) {
        if ("offline".equalsIgnoreCase(status)) {
            appendOutput("服务器已停止。");
        }
    }

    private void applyTerminalColors() {
        if (terminalAdapter != null) {
            terminalAdapter.notifyDataSetChanged();
        }
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
        if (isAppInForeground && isNetworkConnected() && !isReconnectScheduled && !wsManager.isConnected()) {
            isReconnectScheduled = true;
            mainHandler.postDelayed(() -> {
                if (isAppInForeground && isNetworkConnected() && !wsManager.isConnected()) {
                    appendOutput("尝试重新连接到服务器...");
                    connectToTerminal();
                }
                isReconnectScheduled = false;
            }, 2000);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull android.view.Menu menu, @NonNull android.view.MenuInflater inflater) {
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_ai) {
            View aiIconView = requireActivity().findViewById(R.id.action_ai);
            showAiMenu(aiIconView != null ? aiIconView : recyclerViewOutput);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAiMenu(View anchor) {
        if (isAiRequestRunning) {
            Toast.makeText(getContext(), "AI 正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), anchor);
        popup.inflate(R.menu.menu_terminal_ai);
        popup.setOnMenuItemClickListener(item -> {
            SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            int deviceId = sp.getInt("device_id", -1);
            if (deviceId == -1) return false;

            int itemId = item.getItemId();
            if (itemId == R.id.action_ai_history) {
                handleAiHistory(deviceId);
            } else if (itemId == R.id.action_ai_troubleshoot) {
                handleAiTroubleshoot(deviceId);
            } else if (itemId == R.id.action_ai_analyze) {
                handleAiAnalyze(deviceId);
            }
            return true;
        });
        popup.show();
    }

    private void handleAiHistory(int deviceId) {
        runAiRequest("正在获取 AI 历史记录…", callback ->
                aiApi.getAiHistory(requireContext(), deviceId, callback), this::showAiHistoryList);
    }

    private void showAiHistoryList(JSONObject data) {
        JSONArray list = data.optJSONArray("list");
        if (list == null || list.length() == 0) {
            Toast.makeText(getContext(), "暂无 AI 历史记录", Toast.LENGTH_SHORT).show();
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

        new AlertDialog.Builder(requireContext())
                .setTitle("AI 历史记录")
                .setItems(items, (dialog, which) -> {
                    long id = ids.get(which);
                    if (id <= 0) {
                        Toast.makeText(getContext(), "记录无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    fetchAiHistoryDetail(id);
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void fetchAiHistoryDetail(long historyId) {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId == -1) {
            Toast.makeText(getContext(), "设备信息缺失", Toast.LENGTH_SHORT).show();
            return;
        }
        runAiRequest("正在获取历史详情…", callback ->
                aiApi.getAiHistoryDetail(requireContext(), deviceId, historyId, callback));
    }

    private void handleAiTroubleshoot(int deviceId) {
        showInputDialog(input -> {
            if (input.trim().isEmpty()) {
                Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            runAiRequest("正在向 AI 提问…", callback ->
                    aiApi.answerQuestion(requireContext(), deviceId, input.trim(), callback));
        });
    }

    private void handleAiAnalyze(int deviceId) {
        String logs = terminalAdapter.getCleanLogs();
        if (logs.trim().isEmpty()) {
            Toast.makeText(getContext(), "终端暂无服务器输出信息，请等待日志产生后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        String payload = buildAiAnalyzePayload(logs);
        showAnalyzeDialog(deviceId, payload);
    }

    private void showAnalyzeDialog(int deviceId, String payload) {
        mainHandler.post(() -> {
            if (getContext() == null) return;

            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * requireContext().getResources().getDisplayMetrics().density);
            container.setPadding(padding, padding, padding, padding / 2);

            TextView typeLabel = new TextView(requireContext());
            typeLabel.setText("故障类型");
            container.addView(typeLabel);

            Spinner spinner = new Spinner(requireContext());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, AI_LOG_FAULT_TYPES);
            spinner.setAdapter(adapter);
            spinner.setSelection(3);
            container.addView(spinner);

            TextView supplementLabel = new TextView(requireContext());
            supplementLabel.setText("补充说明（可选）");
            supplementLabel.setPadding(0, padding / 2, 0, 0);
            container.addView(supplementLabel);

            EditText input = new EditText(requireContext());
            input.setMinLines(3);
            input.setHint("例如：什么时候开始报错、做过什么操作、希望解决什么问题");
            container.addView(input);

            new AlertDialog.Builder(requireContext())
                    .setTitle("AI日志回答")
                    .setView(container)
                    .setPositiveButton("开始分析", (dialog, which) -> {
                        String selectedType = String.valueOf(spinner.getSelectedItem());
                        String supplement = input.getText() == null ? "" : input.getText().toString().trim();
                        runAiRequest("正在分析终端日志…", callback ->
                                aiApi.analyzeLogs(requireContext(), deviceId, selectedType, supplement, payload, callback));
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private String buildAiAnalyzePayload(String logs) {
        String normalized = logs == null ? "" : logs.trim();
        if (normalized.length() <= MAX_AI_ANALYZE_CHARS) {
            return normalized;
        }
        Toast.makeText(getContext(), "日志较长，已自动截取最近部分进行分析", Toast.LENGTH_SHORT).show();
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
        if (isAiRequestRunning) {
            Toast.makeText(getContext(), "AI 正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        isAiRequestRunning = true;
        showAiLoadingDialog(loadingText);
        invoker.invoke(new AiApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                isAiRequestRunning = false;
                dismissAiLoadingDialog();
                successHandler.onSuccess(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                isAiRequestRunning = false;
                dismissAiLoadingDialog();
                Toast.makeText(getContext(), "请求失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAiLoadingDialog(String loadingText) {
        mainHandler.removeCallbacks(aiSlowHintRunnable);
        if (getContext() == null) return;

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * requireContext().getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(requireContext());
        container.addView(progressBar);

        aiLoadingMessageView = new TextView(requireContext());
        aiLoadingMessageView.setPadding(0, padding / 2, 0, 0);
        aiLoadingMessageView.setText(loadingText + "\n请稍候…");
        container.addView(aiLoadingMessageView);

        aiLoadingDialog = new AlertDialog.Builder(requireContext())
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
        mainHandler.post(() -> {
            EditText input = new EditText(requireContext());
            input.setMinLines(3);
            input.setPadding(50, 40, 50, 40);
            new AlertDialog.Builder(requireContext())
                    .setTitle("请输入您的问题")
                    .setView(input)
                    .setPositiveButton("确定", (dialog, which) -> callback.onInput(input.getText().toString()))
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void showAiResponse(JSONObject data) {
        mainHandler.post(() -> {
            try {
                String content = AiResponseFormatter.format(data);
                TextView contentView = new TextView(requireContext());
                int padding = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
                contentView.setPadding(padding, padding, padding, padding);
                contentView.setText(content);
                contentView.setTextIsSelectable(true);
                contentView.setMovementMethod(new ScrollingMovementMethod());

                android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
                scrollView.addView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                new AlertDialog.Builder(requireContext())
                        .setTitle("AI 助手")
                        .setView(scrollView)
                        .setPositiveButton("复制回复", (dialog, which) -> {
                            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("AI Reply", content);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(getContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("关闭", null)
                        .show();
            } catch (Exception e) {
                Log.e("AiResponse", "Error parsing AI response", e);
                Toast.makeText(getContext(), "解析回复失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connectToTerminal() {
        SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
        int deviceId = sp.getInt("device_id", -1);
        if (deviceId != -1) {
            wsManager.connect(requireContext(), deviceId, true);
        }
    }

    private void sendCommand() {
        String command = editTextCommand.getText().toString().trim();
        if (command.isEmpty()) {
            return;
        }

        shouldMaintainFocus = true;
        wsManager.sendCommand(command);

        editTextCommand.setText("");
        mainHandler.postDelayed(() -> {
            if (shouldMaintainFocus && editTextCommand != null) {
                editTextCommand.requestFocus();
            }
        }, 100);
    }

    private void appendOutput(String text) {
        if (text == null || text.isEmpty()) return;
        String[] split = text.split("\r?\n", -1);
        Collections.addAll(pendingLines, split);
        scheduleBufferFlush();
    }

    private void scheduleBufferFlush() {
        if (!isBufferUpdateScheduled) {
            isBufferUpdateScheduled = true;
            long renderDelay = 100;
            mainHandler.postDelayed(() -> {
                updateOutputWithFocusPreservation();
                isBufferUpdateScheduled = false;
            }, renderDelay);
        }
    }

    private void updateOutputWithFocusPreservation() {
        boolean hadFocus = editTextCommand != null && editTextCommand.hasFocus();

        if (!pendingLines.isEmpty()) {
            List<String> batch = new ArrayList<>(pendingLines);
            pendingLines.clear();
            terminalAdapter.addLines(batch);
        }

        recyclerViewOutput.post(() -> {
            int itemCount = terminalAdapter.getItemCount();
            if (itemCount > 0) {
                recyclerViewOutput.scrollToPosition(itemCount - 1);
            }

            if ((hadFocus || shouldMaintainFocus) && editTextCommand != null) {
                editTextCommand.post(() -> {
                    editTextCommand.requestFocus();
                    shouldMaintainFocus = false;
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissAiLoadingDialog();
        wsManager.removeListener(this);
        pendingLines.clear();
    }

    @Override
    public void onStart() {
        super.onStart();
        wsManager.addListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        applyTerminalColors();
        if (!wsManager.isConnected()) {
            checkAndReconnect();
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                    || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN));
        } else {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    private static class LinesAdapter extends RecyclerView.Adapter<LinesAdapter.LineVH> {
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
            AnsiParser.setAnsiText(holder.textView, line, 0);
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        String getCleanLogs() {
            StringBuilder sb = new StringBuilder();
            String skipMark = "感谢您使用 简幻欢 以及该APP";
            String ansiPattern = "\\u001B\\[[;\\d]*[A-Za-z]";
            for (String line : lines) {
                if (line.contains(skipMark)) continue;
                String cleanLine = line.replaceAll(ansiPattern, "");
                cleanLine = cleanLine.replace("\u001B[m", "")
                                     .replace("\u001B[0m", "");
                sb.append(cleanLine).append("\n");
            }
            return sb.toString();
        }

        void addLines(List<String> newLines) {
            if (newLines == null || newLines.isEmpty()) return;
            int oldSize = lines.size();
            lines.addAll(newLines);
            int maxLines = 5000;
            if (lines.size() > maxLines) {
                int overflow = lines.size() - maxLines;
                lines.subList(0, overflow).clear();
                notifyItemRangeRemoved(0, overflow);
                notifyItemRangeInserted(Math.max(0, oldSize - overflow), newLines.size());
                return;
            }
            notifyItemRangeInserted(oldSize, newLines.size());
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
