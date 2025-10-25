package cn.jdnjk.simpfun.ui.term;

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

        editTextCommand.setOnFocusChangeListener((v, hasFocus) -> shouldMaintainFocus = hasFocus);

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

                        if ("console output".equals(event)) {
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
                        } else if ("status".equals(event)) {
                            JSONArray args = msg.optJSONArray("args");
                            if (args != null && args.length() > 0) {
                                String status = args.getString(0);
                                if ("offline".equalsIgnoreCase(status)) {
                                    appendOutput("服务器已停止。");
                                }
                            }
                        } else if ("token expiring".equals(event)) {
                            refreshTokenAndReAuth();
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
                mainHandler.post(() -> appendOutput("连接错误: " + t.getMessage()));
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                mainHandler.post(() -> appendOutput("连接正在关闭: " + reason));
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
            Log.w("TermLogin", e.getMessage());
        }
    }
    private void sendLogMessage() {
        try {
            JSONObject logMsg = new JSONObject();
            logMsg.put("event", "send logs");
            logMsg.put("args", new JSONArray());
            webSocket.send(logMsg.toString());
        } catch (Exception e) {
            Log.w("TermGetLogs", e.getMessage());
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
            Log.w("TermSendCmd", e.getMessage());
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
            // 滚动到底部（仅当用户接近底部时）
            int itemCount = terminalAdapter.getItemCount();
            if (itemCount > 0) {
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= itemCount - 2) {
                    recyclerViewOutput.scrollToPosition(itemCount - 1);
                }
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
        if (webSocket != null) {
            webSocket.close(1000, "Closed");
            webSocket = null;
        }
        pendingLines.clear();
    }

    private void applyTerminalColors() {
        if (getContext() == null) return;

        TerminalColorUtils.applyTerminalBackgroundColor(getContext(), recyclerViewOutput);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyTerminalColors();
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
        }
        @Override
        public int getItemCount() { return lines.size(); }
        void addLines(List<String> newLines) {
            if (newLines == null || newLines.isEmpty()) return;
            int oldSize = lines.size();
            lines.addAll(newLines);
            int maxLines = 5000;
            if (lines.size() > maxLines) {
                int overflow = lines.size() - maxLines;
                if (overflow > 0) {
                    lines.subList(0, overflow).clear();
                    notifyItemRangeRemoved(0, overflow);
                    notifyItemRangeInserted(Math.max(0, oldSize - overflow), newLines.size());
                    return;
                }
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
