package cn.jdnjk.simpfun.ui.ins.term;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ApiClient;
import cn.jdnjk.simpfun.api.ins.TermApi;
import cn.jdnjk.simpfun.ui.setting.TerminalColorUtils;
import com.fox2code.androidansi.AnsiParser;
import com.fox2code.androidansi.AnsiTextView;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


public class TerminalFragment extends Fragment {

    private EditText editTextCommand;
    private RecyclerView recyclerViewOutput;
    private LinesAdapter terminalAdapter;
    private LinearLayoutManager layoutManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;
    private String requestToken;
    private final List<String> pendingLines = new ArrayList<>();
    private boolean isBufferUpdateScheduled = false;
    private boolean shouldMaintainFocus = false;
    private boolean isAppInForeground = false;
    private boolean isReconnectScheduled = false;

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

        layoutManager = new LinearLayoutManager(requireContext());
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
        if (terminalAdapter.getItemCount() <= 0 || terminalAdapter.getCleanLogs().trim().isEmpty()) {
            Toast.makeText(getContext(), "终端暂无服务器输出信息，请等待日志产生后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), anchor);
        popup.getMenu().add(0, 1, 0, "AI历史记录");
        popup.getMenu().add(0, 2, 1, "AI疑难解答");
        popup.getMenu().add(0, 3, 2, "AI故障分析");

        popup.setOnMenuItemClickListener(item -> {
            SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            int deviceId = sp.getInt("device_id", -1);
            if (deviceId == -1) return false;

            switch (item.getItemId()) {
                case 1 -> handleAiHistory(deviceId);
                case 2 -> handleAiTroubleshoot(deviceId);
                case 3 -> handleAiAnalyze(deviceId);
            }
            return true;
        });
        popup.show();
    }

    private void handleAiHistory(int deviceId) {
        new cn.jdnjk.simpfun.api.ins.AiApi().getAiHistory(requireContext(), deviceId, new cn.jdnjk.simpfun.api.ins.AiApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                showAiResponse(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> Toast.makeText(getContext(), "获取历史失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleAiTroubleshoot(int deviceId) {
        showInputDialog("请输入您的问题", input -> {
            if (input.trim().isEmpty()) {
                Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            new cn.jdnjk.simpfun.api.ins.AiApi().postAiAction(requireContext(), deviceId, "answer", input, new cn.jdnjk.simpfun.api.ins.AiApi.Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    showAiResponse(data);
                }

                @Override
                public void onFailure(String errorMsg) {
                    mainHandler.post(() -> Toast.makeText(getContext(), "请求失败: " + errorMsg, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

    private void handleAiAnalyze(int deviceId) {
        String logs = terminalAdapter.getCleanLogs();
        if (logs.trim().isEmpty()) {
            Toast.makeText(getContext(), "无有效日志可分析", Toast.LENGTH_SHORT).show();
            return;
        }
        new cn.jdnjk.simpfun.api.ins.AiApi().postAiAction(requireContext(), deviceId, "analyze", logs, new cn.jdnjk.simpfun.api.ins.AiApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                showAiResponse(data);
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> Toast.makeText(getContext(), "分析失败: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private interface InputCallback {
        void onInput(String input);
    }

    private void showInputDialog(String title, InputCallback callback) {
        mainHandler.post(() -> {
            EditText input = new EditText(requireContext());
            input.setPadding(50, 40, 50, 40);
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton("确定", (dialog, which) -> callback.onInput(input.getText().toString()))
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void showAiResponse(JSONObject data) {
        mainHandler.post(() -> {
            try {
                Object dataObj = data.opt("data");
                if (dataObj == null) dataObj = data;

                String content = "";

                if (dataObj instanceof JSONArray) {
                    JSONArray array = (JSONArray) dataObj;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item != null) {
                            String type = item.optString("type", "");
                            String supplement = item.optString("supplement", "");
                            String answer = item.optString("answer", item.optString("content", item.optString("data", "")));
                            String time = item.optString("created_at", item.optString("time", ""));

                            if (!time.isEmpty()) sb.append("[").append(time).append("]\n");
                            if ("analyze".equals(type)) sb.append("🔍 故障分析\n");
                            else if ("answer".equals(type)) sb.append("💡 疑难解答\n");

                            if (!supplement.isEmpty()) {
                                if (supplement.length() > 500) {
                                    sb.append("问: ").append(supplement.substring(0, 100)).append("... (已省略部分日志)\n");
                                } else {
                                    sb.append("问: ").append(supplement).append("\n");
                                }
                            }
                            if (!answer.isEmpty()) {
                                sb.append("答: ").append(answer).append("\n");
                            }
                            sb.append("\n--------------------\n\n");
                        } else {
                            sb.append(array.optString(i)).append("\n\n");
                        }
                    }
                    content = sb.toString();
                } else if (dataObj instanceof JSONObject) {
                    JSONObject obj = (JSONObject) dataObj;
                    content = obj.optString("answer", obj.optString("content", obj.optString("data", "")));
                    if (content.isEmpty()) {
                        content = obj.toString(2);
                    }
                } else {
                    content = String.valueOf(dataObj);
                }

                if (content.trim().isEmpty() || "null".equals(content)) {
                    content = "未获取到回复内容";
                }

                final String finalContent = content;
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("AI 助手")
                        .setMessage(finalContent)
                        .setPositiveButton("复制回复", (dialog, which) -> {
                            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("AI Reply", finalContent);
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
        new TermApi().getWebSocketInfo(requireContext(), deviceId, new TermApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                try {
                    requestToken = data.getString("token");
                    String socketUrl = data.getString("socket");

                    mainHandler.post(() -> {
                        appendOutput("正在连接到服务器...");
                        connectWebSocket(socketUrl);
                    });

                } catch (Exception e) {
                    Log.e("TerminalFragment", "连接失败" + e.getMessage());
                    mainHandler.post(() -> appendOutput("解析连接信息失败: " + e.getMessage()));
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> appendOutput("连接到终端失败: " + errorMsg));
            }
        });
    }

    private void connectWebSocket(String socketUrl) {
        Request request = new Request.Builder()
                .url(socketUrl)
                .build();

        OkHttpClient client = ApiClient.getInstance().getClient();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                mainHandler.post(() -> appendOutput("已连接到服务器"));
                mainHandler.post(() -> appendOutput("感谢您使用 简幻欢 以及该APP"));
                sendAuthMessage();
                sendLogMessage();
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                mainHandler.post(() -> {
                    try {
                        JSONObject msg = new JSONObject(text);
                        String event = msg.optString("event");

                        switch (event) {
                            case "console output" -> {
                                JSONArray args = msg.getJSONArray("args");

                                for (int i = 0; i < args.length(); i++) {
                                    String line = args.getString(i);
                                    line = line.replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Checking server disk space usage, this could take a few seconds...\u001B[0m",
                                                    "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 正在检查磁盘占用情况，请稍等...\u001B[0m")
                                            .replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Updating process configuration files...\u001B[0m",
                                                    "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 已自动更新服务器端口等信息！\u001B[0m")
                                            .replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Pulling Docker container image, this could take a few minutes to complete...\u001B[0m",
                                                    "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 正在拉取Docker镜像，请稍等...\u001B[0m")
                                            .replace("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Finished pulling Docker container image\u001B[0m",
                                                    "\u001B[33m\u001B[1m[简幻欢]:\u001B[39m 已完成Docker镜像拉取！\u001B[0m")
                                            .replaceAll("\u001b\\[\\?1h\u001b=", "")
                                            .replaceAll("\u001b\\[\\?2004h", "")
                                            .replaceAll("\u001b\\[K", "")
                                            .replaceAll(">\\r\\n", "")
                                            .replaceAll(">....", "");
                                    String[] split = line.split("\r?\n", -1);
                                    Collections.addAll(pendingLines, split);
                                }
                                scheduleBufferFlush();
                            }
                            case "status" -> {
                                JSONArray args = msg.optJSONArray("args");
                                if (args != null && args.length() > 0) {
                                    String status = args.getString(0);
                                    if ("offline".equalsIgnoreCase(status)) {
                                        appendOutput("服务器已停止。");
                                    }
                                }
                            }
                            case "token expiring" -> refreshTokenAndReAuth();
                        }
                    } catch (Exception e) {
                        Log.w("TerminalFragment", "处理消息失败: " + e.getMessage());
                    }
                });
            }
            private void refreshTokenAndReAuth() {
                SharedPreferences sp = requireContext().getSharedPreferences("deviceid", Context.MODE_PRIVATE);
                int deviceId = sp.getInt("device_id", -1);

                new TermApi().getWebSocketInfo(requireContext(), deviceId, new TermApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        try {
                            requestToken = data.getString("token");
                            mainHandler.post(TerminalFragment.this::sendAuthMessage);
                        } catch (Exception e) {
                            mainHandler.post(() -> appendOutput("续期失败: " + e.getMessage()));
                        }
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        mainHandler.post(() -> appendOutput("Token 获取失败: " + errorMsg));
                    }
                });
            }


            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                mainHandler.post(() -> {
                    appendOutput("连接错误: " + t.getMessage());
                    TerminalFragment.this.webSocket = null;
                    // 自动重连逻辑
                    if (isAppInForeground && isNetworkConnected() && !isReconnectScheduled) {
                        isReconnectScheduled = true;
                        mainHandler.postDelayed(() -> {
                            if (isAppInForeground && isNetworkConnected() && TerminalFragment.this.webSocket == null) {
                                appendOutput("尝试重新连接到服务器...");
                                connectToTerminal();
                            }
                            isReconnectScheduled = false;
                        }, 2000);
                    }
                });
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                mainHandler.post(() -> {
                    appendOutput("连接正在关闭: " + reason);
                    TerminalFragment.this.webSocket = null;
                    // 自动重连逻辑
                    if (isAppInForeground && isNetworkConnected() && !isReconnectScheduled) {
                        isReconnectScheduled = true;
                        mainHandler.postDelayed(() -> {
                            if (isAppInForeground && isNetworkConnected() && TerminalFragment.this.webSocket == null) {
                                appendOutput("尝试重新连接到服务器...");
                                connectToTerminal();
                            }
                            isReconnectScheduled = false;
                        }, 2000);
                    }
                });
            }
        });
    }
    private void sendAuthMessage() {
        try {
            JSONObject authMsg = new JSONObject();
            authMsg.put("event", "auth");
            JSONArray args = new JSONArray();
            args.put(requestToken);
            authMsg.put("args", args);
            webSocket.send(authMsg.toString());
        } catch (Exception e) {
            Log.w("TermLogin", String.valueOf(e.getMessage()));
        }
    }
    private void sendLogMessage() {
        try {
            JSONObject logMsg = new JSONObject();
            logMsg.put("event", "send logs");
            logMsg.put("args", new JSONArray());
            webSocket.send(logMsg.toString());
        } catch (Exception e) {
            Log.w("TermGetLogs", String.valueOf(e.getMessage()));
        }
    }
    private void sendCommand() {
        String command = editTextCommand.getText().toString().trim();
        if (command.isEmpty() || webSocket == null) {
            return;
        }

        shouldMaintainFocus = true;

        try {
            JSONObject cmdMsg = new JSONObject();
            cmdMsg.put("event", "send command");
            JSONArray args = new JSONArray();
            args.put(command);
            cmdMsg.put("args", args);
            boolean success = webSocket.send(cmdMsg.toString());
            if (!success) {
                appendOutput("发送命令失败");
            } else {
                editTextCommand.setText("");
                mainHandler.postDelayed(() -> {
                    if (shouldMaintainFocus && editTextCommand != null) {
                        editTextCommand.requestFocus();
                    }
                }, 100);
            }
        } catch (Exception e) {
            Log.w("TermSendCmd", String.valueOf(e.getMessage()));
        }
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
            long RENDER_DELAY = 100;
            mainHandler.postDelayed(() -> {
                updateOutputWithFocusPreservation();
                isBufferUpdateScheduled = false;
            }, RENDER_DELAY);
        }
    }

    private void updateOutputWithFocusPreservation() {
        boolean hadFocus = editTextCommand != null && editTextCommand.hasFocus();

        // 取出缓冲数据并更新适配器
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
        // 保留 WebSocket 连接，避免在切换到其他页面（非主页面）时断开
        // 清理与视图相关的轻量资源即可
        pendingLines.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Fragment 被真正销毁时再关闭连接
        if (webSocket != null) {
            webSocket.close(1000, "Closed");
            webSocket = null;
        }
    }

    private void applyTerminalColors() {
        if (getContext() == null) return;

        TerminalColorUtils.applyTerminalBackgroundColor(getContext(), recyclerViewOutput);
    }

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        applyTerminalColors();
        // 如果需要重连，且网络可用，且没有连接，则重连
        if (webSocket == null && isNetworkConnected() && !isReconnectScheduled) {
            isReconnectScheduled = true;
            mainHandler.postDelayed(() -> {
                if (isAppInForeground && isNetworkConnected() && webSocket == null) {
                    appendOutput("尝试重新连接到服务器...");
                    connectToTerminal();
                }
                isReconnectScheduled = false;
            }, 1000);
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
            tv.setPadding(tv.getPaddingLeft()+8, tv.getPaddingTop()+2, tv.getPaddingRight()+8, tv.getPaddingBottom()+2);
            tv.setTextIsSelectable(true);
            TerminalColorUtils.applyTerminalColors(context, tv);
            return new LineVH(tv);
        }
        @Override
        public void onBindViewHolder(@NonNull LineVH holder, int position) {
            String line = lines.get(position);
            AnsiParser.setAnsiText(holder.textView, line, 0);
            holder.textView.setOnLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("terminal_line", line));
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
        @Override
        public int getItemCount() { return lines.size(); }

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
                int overflow = lines.size() - maxLines; // strictly > 0 here
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

