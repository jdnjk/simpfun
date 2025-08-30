package cn.jdnjk.simpfun.ui.term;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private boolean shouldMaintainFocus = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_terminal, container, false);

        editTextCommand = root.findViewById(R.id.edit_text_command);
        buttonSend = root.findViewById(R.id.button_send);
        textViewOutput = root.findViewById(R.id.text_view_output);
        scrollViewOutput = root.findViewById(R.id.scroll_view_output);

        buttonSend.setOnClickListener(v -> sendCommand());

        editTextCommand.setOnFocusChangeListener((v, hasFocus) -> {
            shouldMaintainFocus = hasFocus;
        });

        editTextCommand.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                    shouldMaintainFocus = true;
                    sendCommand();
                    return true;
                }
                return false;
            }
        });

        editTextCommand.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    shouldMaintainFocus = true;
                    sendCommand();
                    mainHandler.postDelayed(() -> {
                        editTextCommand.requestFocus();
                    }, 50);
                    return true;
                }
                return false;
            }
        });

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
                mainHandler.post(() -> appendOutput("连接到终端失败: " + errorMsg + "\n"));
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
                mainHandler.post(() -> appendOutput("感谢你使用 简幻欢\n"));
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
                                        .replaceAll(">\\r\\n", "")
                                        .replaceAll(">....", "");
                                ansiBuffer.append(line).append("\n");
                            }
                            if (!isBufferUpdateScheduled) {
                                isBufferUpdateScheduled = true;
                                mainHandler.postDelayed(() -> {
                                    updateOutputWithFocusPreservation();
                                    isBufferUpdateScheduled = false;
                                }, RENDER_DELAY);
                            }
                        } else if ("status".equals(event)) {
                            JSONArray args = msg.optJSONArray("args");
                            if (args != null && args.length() > 0) {
                                String status = args.getString(0);
                                if ("offline".equalsIgnoreCase(status)) {
                                    appendOutput("服务器已停止。\n");
                                }
                            }
                        } else if ("token expiring".equals(event)) {
                            refreshTokenAndReAuth();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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

                            mainHandler.post(() -> {
                                sendAuthMessage();
                            });

                        } catch (Exception e) {
                            mainHandler.post(() -> appendOutput("Token 续期失败: " + e.getMessage() + "\n"));
                        }
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        mainHandler.post(() -> appendOutput("Token 获取失败: " + errorMsg + "\n"));
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

        shouldMaintainFocus = true;

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
                mainHandler.postDelayed(() -> {
                    if (shouldMaintainFocus && editTextCommand != null) {
                        editTextCommand.requestFocus();
                    }
                }, 100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void appendOutput(String text) {
        ansiBuffer.append(text);
        updateOutputWithFocusPreservation();
    }

    private void updateOutputWithFocusPreservation() {
        boolean hadFocus = editTextCommand != null && editTextCommand.hasFocus();

        AnsiParser.setAnsiText(textViewOutput, ansiBuffer.toString(), 0);
        scrollViewOutput.post(() -> {
            scrollViewOutput.fullScroll(View.FOCUS_DOWN);

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
        ansiBuffer.setLength(0);
    }
}