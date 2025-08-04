package cn.jdnjk.simpfun.ui.term;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ApiClient;
import cn.jdnjk.simpfun.api.ins.TermApi;
import com.fox2code.androidansi.AnsiParser;
import com.fox2code.androidansi.AnsiTextView;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;


public class TerminalFragment extends Fragment {

    private EditText editTextCommand;
    private Button buttonSend;
    private AnsiTextView textViewOutput;
    private ScrollView scrollViewOutput;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;
    private String serverId;
    private String requestToken;
    private StringBuilder ansiBuffer = new StringBuilder();
    private boolean isBufferUpdateScheduled = false;
    private final long RENDER_DELAY = 100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_terminal, container, false);

        editTextCommand = root.findViewById(R.id.edit_text_command);
        buttonSend = root.findViewById(R.id.button_send);
        textViewOutput = root.findViewById(R.id.text_view_output);
        scrollViewOutput = root.findViewById(R.id.scroll_view_output);

        buttonSend.setOnClickListener(v -> sendCommand());

        connectToTerminal();
        textViewOutput.setTextIsSelectable(true);
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
                        appendOutput("正在连接到服务器...\n");
                        connectWebSocket(socketUrl);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(() -> appendOutput("解析连接信息失败: " + e.getMessage() + "\n"));
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                mainHandler.post(() -> appendOutput("获取连接信息失败: " + errorMsg + "\n"));
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
                mainHandler.post(() -> appendOutput("已连接到服务器\n"));
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
                                line = line.replaceAll("\u001b\\[\\?1h\u001b=", "")
                                        .replaceAll("\u001b\\[\\?2004h", "")
                                        .replaceAll("\u001b\\[K", "")
                                        .replaceAll(">....", "");;
                                ansiBuffer.append(line).append("\n");
                            }
                            if (!isBufferUpdateScheduled) {
                                isBufferUpdateScheduled = true;
                                mainHandler.postDelayed(() -> {
                                    AnsiParser.setAnsiText(textViewOutput, ansiBuffer.toString(), 0);
                                    scrollViewOutput.post(() -> scrollViewOutput.fullScroll(View.FOCUS_DOWN));
                                    isBufferUpdateScheduled = false;
                                }, RENDER_DELAY);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }


            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                mainHandler.post(() -> appendOutput("连接错误: " + t.getMessage() + "\n"));
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                mainHandler.post(() -> appendOutput("连接正在关闭: " + reason + "\n"));
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
            e.printStackTrace();
        }
    }
    private void sendLogMessage() {
        try {
            JSONObject logMsg = new JSONObject();
            logMsg.put("event", "send logs");
            logMsg.put("args", new JSONArray());
            webSocket.send(logMsg.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void sendCommand() {
        String command = editTextCommand.getText().toString().trim();
        if (command.isEmpty() || webSocket == null) {
            return;
        }

        try {
            JSONObject cmdMsg = new JSONObject();
            cmdMsg.put("event", "send command");
            JSONArray args = new JSONArray();
            args.put(command);
            cmdMsg.put("args", args);
            boolean success = webSocket.send(cmdMsg.toString());
            if (!success) {
                appendOutput("发送命令失败\n");
            } else {
                editTextCommand.setText("");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void appendOutput(String text) {
        ansiBuffer.append(text);
        AnsiParser.setAnsiText(textViewOutput, ansiBuffer.toString(), 0);
        scrollViewOutput.post(() -> scrollViewOutput.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webSocket != null) {
            webSocket.close(1000, "Closed");
            webSocket = null;
        }
        ansiBuffer.setLength(0);
    }
}