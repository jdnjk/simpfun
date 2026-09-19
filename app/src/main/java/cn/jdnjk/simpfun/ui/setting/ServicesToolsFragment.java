package cn.jdnjk.simpfun.ui.setting;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.mcp.McpServerService;
import cn.jdnjk.simpfun.mcp.McpSettingsManager;
import cn.jdnjk.simpfun.model.QuickCommandNode;
import cn.jdnjk.simpfun.ui.troubleshoot.TroubleshootActivity;
import cn.jdnjk.simpfun.utils.NetworkUtils;

public class ServicesToolsFragment extends Fragment {
    private static final String SP_TOKEN = "token";
    private static final String KEY_TOKEN = "token";

    private McpSettingsManager mcpSettingsManager;
    private SharedPreferences authInfo;

    private MaterialSwitch switchMcp;
    private TextInputLayout inputMcpPort;
    private TextInputEditText etMcpPort;
    private MaterialCardView cardMcpUrl;
    private TextView tvMcpUrl;
    private NestedScrollView scrollView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mcpSettingsManager = new McpSettingsManager(requireContext());
        authInfo = requireContext().getSharedPreferences(SP_TOKEN, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_services_tools, container, false);
        scrollView = root.findViewById(R.id.scroll_services);

        setupMcpSwitch(root);
        setupMcpPortInput(root);
        setupMcpUrlCard(root);
        setupToolEntries(root);

        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("服务与工具");
            activity.setHelpEnabled(true);
            activity.bindToolbarScroll(scrollView);
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.setAppBarTitle("服务与工具");
            activity.setHelpEnabled(true);
        }
        updateMcpDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        SettingsSaveManager.getInstance(requireContext()).flush();
    }

    @Override
    public void onDestroyView() {
        SettingsSaveManager.getInstance(requireContext()).flush();
        if (getActivity() instanceof SettingsActivity activity) {
            activity.unbindToolbarScroll(null);
        }
        super.onDestroyView();
    }

    private void setupMcpSwitch(View root) {
        View row = root.findViewById(R.id.row_mcp);
        switchMcp = row.findViewById(R.id.switch_entry);
        bindToggleRow(row, R.drawable.ic_lan,
                "允许局域网访问", "服务运行中");
        switchMcp.setChecked(mcpSettingsManager.isEnabled());
        // 打开服务前验证账户 Token；关闭后停止前台服务
        switchMcp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                String token = authInfo.getString(KEY_TOKEN, "");
                if (token.isEmpty()) {
                    Toast.makeText(requireContext(), "请先登录简幻欢账号", Toast.LENGTH_SHORT).show();
                    switchMcp.setChecked(false);
                    return;
                }
                mcpSettingsManager.setEnabled(true);
                McpServerService.start(requireContext());
                // 服务异步启动，稍后刷新以显示地址
                if (scrollView != null) {
                    scrollView.postDelayed(this::updateMcpDisplay, 800);
                }
            } else {
                mcpSettingsManager.setEnabled(false);
                McpServerService.stop(requireContext());
            }
            updateMcpDisplay();
        });
    }

    private void setupMcpPortInput(View root) {
        inputMcpPort = root.findViewById(R.id.input_mcp_port);
        etMcpPort = root.findViewById(R.id.et_mcp_port);
        etMcpPort.setText(String.valueOf(mcpSettingsManager.getPort()));

        Runnable applyPort = () -> {
            String input = etMcpPort.getText() == null ? "" : etMcpPort.getText().toString().trim();
            if (input.isEmpty()) return;
            try {
                int port = Integer.parseInt(input);
                if (port < 1025 || port > 65535) {
                    inputMcpPort.setError("端口号必须在 1025–65535 之间");
                    return;
                }
                inputMcpPort.setError(null);
                if (port == mcpSettingsManager.getPort()) return;
                mcpSettingsManager.setPort(port);
                updateMcpDisplay();
                if (McpServerService.isRunning()) {
                    Toast.makeText(requireContext(), "MCP 服务将自动重启以应用新端口",
                            Toast.LENGTH_SHORT).show();
                    McpServerService.stop(requireContext());
                    McpServerService.start(requireContext());
                    if (scrollView != null) {
                        scrollView.postDelayed(this::updateMcpDisplay, 800);
                    }
                }
            } catch (NumberFormatException e) {
                inputMcpPort.setError("请输入有效的端口号");
            }
        };

        etMcpPort.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyPort.run();
                return true;
            }
            return false;
        });
        etMcpPort.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyPort.run();
        });
    }

    private void setupMcpUrlCard(View root) {
        cardMcpUrl = root.findViewById(R.id.card_mcp_url);
        tvMcpUrl = root.findViewById(R.id.tv_mcp_url);
        // 仅在 MCP 服务成功运行时显示；点按复制完整地址
        cardMcpUrl.setOnClickListener(v -> {
            String url = tvMcpUrl.getText().toString();
            ClipboardManager cm = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("MCP URL", url));
                Toast.makeText(requireContext(), "已复制服务地址", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMcpDisplay() {
        if (cardMcpUrl == null) return;
        boolean running = mcpSettingsManager.isEnabled() && McpServerService.isRunning();
        cardMcpUrl.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            String ip = NetworkUtils.getLocalIpAddress(requireContext());
            String url = ip != null
                    ? "http://" + ip + ":" + mcpSettingsManager.getPort() + "/mcp"
                    : "未获取到局域网地址";
            tvMcpUrl.setText(url);
        }
    }

    private void setupToolEntries(View root) {
        View rowTroubleshoot = root.findViewById(R.id.row_troubleshoot);
        bindEntryRow(rowTroubleshoot, R.drawable.ic_troubleshoot,
                "故障排错", "查看服务状态并诊断连接问题");
        rowTroubleshoot.setOnClickListener(v -> {
            if (getActivity() instanceof SettingsActivity activity) {
                activity.openTroubleshootPage();
            }
        });

        View rowQuickCommands = root.findViewById(R.id.row_quick_commands);
        bindEntryRow(rowQuickCommands, R.drawable.ic_bolt,
                "快捷指令", "新建和编辑常用服务器操作");
        rowQuickCommands.setOnClickListener(v -> showQuickCommandManagementDialog());
    }

    private void showQuickCommandManagementDialog() {
        List<QuickCommandNode> commands = new QuickCommandStorage(requireContext()).loadAll();
        String[] names = new String[commands.size()];
        for (int i = 0; i < commands.size(); i++) {
            names[i] = commands.get(i).name;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(commands.isEmpty() ? "还没有自定义指令" : "我的快捷指令")
                .setItems(names, (dialog, which) ->
                        openQuickCommandEditor(which, commands.get(which)))
                .setPositiveButton("添加新指令", (dialog, which) ->
                        openQuickCommandEditor(-1, null))
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 进入快捷指令编辑器；离开未保存的修改前由编辑器弹窗确认。 */
    private void openQuickCommandEditor(int index, QuickCommandNode existing) {
        if (getActivity() == null) return;
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.fragment_container,
                        QuickCommandEditorFragment.newInstance(index, existing))
                .addToBackStack("quick_command_editor")
                .commit();
    }

    private void bindToggleRow(View row, int iconRes, String title, String subtitle) {
        SettingsRowUtils.hideLeadingIcon(row);
        ImageView icon = row.findViewById(R.id.iv_entry_icon);
        icon.setImageResource(iconRes);
        TextView tvTitle = row.findViewById(R.id.tv_entry_title);
        tvTitle.setText(title);
        TextView tvSubtitle = row.findViewById(R.id.tv_entry_subtitle);
        tvSubtitle.setText(subtitle);
    }

    private void bindEntryRow(View row, int iconRes, String title, String subtitle) {
        bindToggleRow(row, iconRes, title, subtitle);
    }
}
