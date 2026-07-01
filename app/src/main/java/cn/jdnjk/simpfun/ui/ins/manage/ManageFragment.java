package cn.jdnjk.simpfun.ui.ins.manage;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.DiamondApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.api.ins.PortApi;
import cn.jdnjk.simpfun.ui.setting.ManageScreenshotProtection;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;

public class ManageFragment extends Fragment {
    private JSONObject cachedDetail;
    private JSONObject cachedSftp;

    private LinearLayout contentLayout;
    private TextView noData;
    private TextView tvInstanceId;
    private TextView tvTroubleshootId;
    private TextView tvInstanceStatus;
    private TextView tvDiskUsage;
    private TextView tvTrafficRemain;
    private TextView tvGameType;
    private TextView tvDiskCheckTime;
    private TextView tvSftpHost;
    private TextView tvSftpPort;
    private TextView tvSftpUser;
    private TextView tvSftpPassword;
    private TextView tvPorts;
    private TextView tvDiamondLeft;
    private TextView tvDiamondPlan;
    private TextView tvDiamondValid;
    private Spinner spinnerPorts;
    private MaterialButton btnSetMainPort;
    private MaterialButton btnBuyPort;
    private MaterialButton btnDestroyInstance;

    private AutoCompleteTextView tvPlanFilter;
    private RecyclerView rvDiamondPlans;
    private MaterialButton btnApplyDiamondPlan;
    private DiamondPlanAdapter diamondPlanAdapter;
    private JSONArray allDiamondPlans;
    private JSONObject selectedDiamondPlan;

    private final List<PortItem> portItems = new ArrayList<>();
    private ArrayAdapter<String> portAdapter;
    private int selectedPortIndex = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_manage, container, false);
        bindViews(root);
        render();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        applyScreenshotProtection();
    }

    @Override
    public void onResume() {
        super.onResume();
        applyScreenshotProtection();
        refreshCachedDetail();
        render();
        fetchSftp();
    }

    @Override
    public void onStop() {
        clearScreenshotProtection();
        super.onStop();
    }

    @Nullable
    public JSONObject getCachedDetail() {
        return cachedDetail;
    }

    private void applyScreenshotProtection() {
        if (getActivity() == null) return;
        Window window = requireActivity().getWindow();
        if (ManageScreenshotProtection.isEnabled(requireContext())) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void clearScreenshotProtection() {
        if (getActivity() == null) return;
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void bindViews(View root) {
        contentLayout = root.findViewById(R.id.layout_manage_content);
        noData = root.findViewById(R.id.tv_manage_no_data);
        tvInstanceId = root.findViewById(R.id.tv_instance_id);
        tvTroubleshootId = root.findViewById(R.id.tv_troubleshoot_id);
        tvInstanceStatus = root.findViewById(R.id.tv_instance_status);
        tvDiskUsage = root.findViewById(R.id.tv_disk_usage);
        tvTrafficRemain = root.findViewById(R.id.tv_traffic_remain);
        tvGameType = root.findViewById(R.id.tv_game_type);
        tvDiskCheckTime = root.findViewById(R.id.tv_disk_check_time);
        tvSftpHost = root.findViewById(R.id.tv_sftp_host);
        tvSftpPort = root.findViewById(R.id.tv_sftp_port);
        tvSftpUser = root.findViewById(R.id.tv_sftp_user);
        tvSftpPassword = root.findViewById(R.id.tv_sftp_password);
        tvPorts = root.findViewById(R.id.tv_ports);
        tvDiamondLeft = root.findViewById(R.id.tv_diamond_left);
        tvDiamondPlan = root.findViewById(R.id.tv_diamond_plan);
        tvDiamondValid = root.findViewById(R.id.tv_diamond_valid);
        spinnerPorts = root.findViewById(R.id.spinner_ports);
        btnSetMainPort = root.findViewById(R.id.btn_set_main_port);
        btnBuyPort = root.findViewById(R.id.btn_buy_port);
        btnDestroyInstance = root.findViewById(R.id.btn_destroy_instance);

        portAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        portAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPorts.setAdapter(portAdapter);
        spinnerPorts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPortIndex = position;
                updatePortActionState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPortIndex = -1;
                updatePortActionState();
            }
        });

        tvPlanFilter = root.findViewById(R.id.tv_plan_filter);
        rvDiamondPlans = root.findViewById(R.id.rv_diamond_plans);
        btnApplyDiamondPlan = root.findViewById(R.id.btn_apply_diamond_plan);

        String[] filterOptions = {"当前实例类型", "所有实例类型"};
        ArrayAdapter<String> filterAdapter = new NoFilterArrayAdapter(requireContext(), filterOptions);
        tvPlanFilter.setAdapter(filterAdapter);
        tvPlanFilter.setText(filterOptions[0], false);
        tvPlanFilter.setOnClickListener(v -> tvPlanFilter.showDropDown());
        tvPlanFilter.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tvPlanFilter.showDropDown();
            }
        });
        tvPlanFilter.setOnItemClickListener((parent, view, position, id) -> filterPlans());

        // Adjust span count dynamically based on the available screen width
        int displayWidthDp = getResources().getConfiguration().screenWidthDp;
        int spanCount = 1;
        if (displayWidthDp >= 600) {
            spanCount = 2;
        }

        rvDiamondPlans.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        diamondPlanAdapter = new DiamondPlanAdapter(plan -> {
            selectedDiamondPlan = plan;
            btnApplyDiamondPlan.setEnabled(true);
            btnApplyDiamondPlan.setText("应用该补贴计划");
        });
        rvDiamondPlans.setAdapter(diamondPlanAdapter);

        btnApplyDiamondPlan.setOnClickListener(v -> applyDiamondPlan());

        btnBuyPort.setOnClickListener(v -> confirmBuyPort());
        btnSetMainPort.setOnClickListener(v -> setMainPort());
        btnDestroyInstance.setOnClickListener(v -> confirmDeleteInstance());
        tvTroubleshootId.setOnClickListener(v -> copyTroubleshootId());

        tvSftpHost.setOnClickListener(v -> copyFieldValue(tvSftpHost, "SFTP IP"));
        tvSftpPort.setOnClickListener(v -> copyFieldValue(tvSftpPort, "SFTP 端口"));
        tvSftpUser.setOnClickListener(v -> copyFieldValue(tvSftpUser, "SFTP 用户名"));
        tvSftpPassword.setOnClickListener(v -> copyFieldValue(tvSftpPassword, "SFTP 密码"));
    }

    private void refreshCachedDetail() {
        if (getActivity() instanceof ServerManages activity) {
            cachedDetail = activity.getCachedInstanceDetailData();
        } else {
            cachedDetail = null;
        }
    }

    private void render() {
        if (!isAdded() || noData == null || contentLayout == null) {
            return;
        }

        if (cachedDetail == null) {
            noData.setVisibility(View.VISIBLE);
            contentLayout.setVisibility(View.GONE);
            bindPorts(null);
            btnDestroyInstance.setEnabled(false);
            tvTroubleshootId.setText("故障排错ID: -");
            tvSftpHost.setText("服务器IP: -");
            tvSftpPort.setText("服务器端口: -");
            tvSftpUser.setText("用户名: -");
            tvSftpPassword.setText("密码: -");
            return;
        }

        noData.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
        btnDestroyInstance.setEnabled(true);

        JSONObject utilization = cachedDetail.optJSONObject("utilization");
        JSONObject traffic = cachedDetail.optJSONObject("traffic");
        JSONObject gameInfo = cachedDetail.optJSONObject("game_info");
        JSONObject diamond = cachedDetail.optJSONObject("diamond");
        JSONObject defaultAlloc = cachedDetail.optJSONObject("default_allocation");

        tvInstanceId.setText("实例ID: " + cachedDetail.optInt("id"));

        String troubleshootId = safe(cachedDetail.optString("uuid"));
        tvTroubleshootId.setText("故障排错ID: " + troubleshootId);
        tvInstanceStatus.setText("状态: " + toStatusText(cachedDetail.optString("status")));

        long diskBytes = utilization != null ? utilization.optLong("disk_bytes", -1) : -1;
        int diskQuotaGb = cachedDetail.optInt("disk", 0);
        tvDiskUsage.setText("磁盘占用: 已使用 " + formatBytes(diskBytes) + " / 免费最大 " + diskQuotaGb + "GB");

        long remainBytes = traffic != null ? traffic.optLong("remain_bytes", -1) : -1;
        int dailyPlanGb = traffic != null ? traffic.optInt("plan", 0) : 0;
        tvTrafficRemain.setText("上行流量: 剩余 " + formatBytes(remainBytes) + " / 每日免费 " + dailyPlanGb + "GB");

        String gameName = gameInfo != null ? gameInfo.optString("game_name") : "-";
        String kind = gameInfo != null ? gameInfo.optString("kind_name") : "-";
        String version = gameInfo != null ? gameInfo.optString("version_name") : "-";
        tvGameType.setText("镜像类型: " + safe(gameName) + " - " + safe(kind) + " - " + safe(version));

        String lastCheck = utilization != null ? utilization.optString("disk_last_check_time") : "";
        tvDiskCheckTime.setText("磁盘最近检查时间: " + formatTime(lastCheck));

        String sftpHost = cachedSftp != null ? safe(cachedSftp.optString("ip")) : "-";
        String sftpPort = cachedSftp != null ? safe(cachedSftp.optString("port")) : "-";
        String sftpUser = cachedSftp != null ? safe(cachedSftp.optString("user_name")) : "-";
        String sftpPassword = cachedSftp != null ? safe(cachedSftp.optString("password")) : "-";

        // Fallback for host/port when SFTP API has not returned yet
        if ("-".equals(sftpHost) && defaultAlloc != null) {
            sftpHost = safe(defaultAlloc.optString("ip"));
        }
        if ("-".equals(sftpPort) && defaultAlloc != null) {
            int fallbackPort = defaultAlloc.optInt("port", 0);
            sftpPort = fallbackPort > 0 ? String.valueOf(fallbackPort) : "-";
        }

        tvSftpHost.setText("服务器IP: " + sftpHost);
        tvSftpPort.setText("服务器端口: " + sftpPort);
        tvSftpUser.setText("用户名: " + sftpUser);
        tvSftpPassword.setText("密码: " + sftpPassword);

        bindPorts(cachedDetail.optJSONArray("allocations"));

        if (diamond != null) {
            tvDiamondLeft.setText("剩余钻石: " + diamond.optInt("left", 0));

            int planId = diamond.optInt("diamond_plan_id", -1);
            int planDiscount = diamond.optInt("diamond_plan_discount", -1);
            if (planId >= 0 && planDiscount >= 0) {
                tvDiamondPlan.setVisibility(View.VISIBLE);
                tvDiamondPlan.setText("计划ID/折扣: " + planId + " / " + planDiscount);
            } else if (planId >= 0) {
                tvDiamondPlan.setVisibility(View.VISIBLE);
                tvDiamondPlan.setText("计划ID: " + planId);
            } else if (planDiscount >= 0) {
                tvDiamondPlan.setVisibility(View.VISIBLE);
                tvDiamondPlan.setText("折扣: " + planDiscount);
            } else {
                tvDiamondPlan.setVisibility(View.GONE);
            }

            String validTime = safe(diamond.optString("diamond_plan_valid_time", "-"));
            if (!validTime.isEmpty() && !"-".equals(validTime) && !"-1".equals(validTime)) {
                tvDiamondValid.setVisibility(View.VISIBLE);
                tvDiamondValid.setText("有效期: " + validTime);
            } else {
                tvDiamondValid.setVisibility(View.GONE);
            }
        } else {
            tvDiamondLeft.setText("剩余钻石: 0");
            tvDiamondPlan.setVisibility(View.GONE);
            tvDiamondValid.setVisibility(View.GONE);
        }

        if (allDiamondPlans == null && getActivity() instanceof ServerManages) {
            fetchDiamondPlans();
        }
    }

    private void fetchDiamondPlans() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        new DiamondApi().getDiamondPlan(requireContext(), activity.getDeviceId(), new DiamondApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                allDiamondPlans = data.optJSONArray("list");
                int userDiamond = data.optInt("diamond", 0);
                if (cachedDetail != null) {
                    JSONObject diamondObj = cachedDetail.optJSONObject("diamond");
                    if (diamondObj == null) {
                        try {
                            diamondObj = new JSONObject();
                            cachedDetail.put("diamond", diamondObj);
                        } catch (Exception ignored) {}
                    }
                    if (diamondObj != null) {
                        try {
                            diamondObj.put("left", userDiamond);
                        } catch (Exception ignored) {}
                    }
                    tvDiamondLeft.setText("剩余钻石: " + userDiamond);
                }
                filterPlans();
            }

            @Override
            public void onFailure(String errorMsg) {
                // ignoring errors silently or show toast
            }
        });
    }

    private void filterPlans() {
        if (allDiamondPlans == null || diamondPlanAdapter == null) return;
        selectedDiamondPlan = null;
        btnApplyDiamondPlan.setEnabled(false);
        btnApplyDiamondPlan.setText("请选择补贴计划");

        String filterText = tvPlanFilter.getText().toString();
        String currentAreaGrade = cachedDetail != null ? cachedDetail.optString("area_grade", "") : "";

        JSONArray filtered = new JSONArray();
        for (int i = 0; i < allDiamondPlans.length(); i++) {
            JSONObject plan = allDiamondPlans.optJSONObject(i);
            if (plan == null) continue;

            String pGrade = plan.optString("grade", "-");
            String pVendor = plan.optString("vendor", "-");
            String pSpec = plan.optString("spec", "-");
            boolean pWindows = plan.optBoolean("is_windows", false);
            String fullPlanGrade = String.format("%s.%s.%s.%s", pGrade, pVendor, pSpec, pWindows ? "W" : "L");

            if ("当前实例类型".equals(filterText)) {
                if (!currentAreaGrade.isEmpty() && currentAreaGrade.equals(fullPlanGrade)) {
                    filtered.put(plan);
                }
            } else {
                filtered.put(plan);
            }
        }
        diamondPlanAdapter.setPlans(filtered, currentAreaGrade);
    }

    private void applyDiamondPlan() {
        if (selectedDiamondPlan == null) return;
        if (!(getActivity() instanceof ServerManages activity)) return;

        int planId = selectedDiamondPlan.optInt("id", -1);
        if (planId <= 0) return;

        btnApplyDiamondPlan.setEnabled(false);
        new DiamondApi().applyDiamondPlan(requireContext(), activity.getDeviceId(), planId, new DiamondApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), data.optString("msg", "应用补贴成功"), Toast.LENGTH_SHORT).show();
                fetchDiamondPlans();
                refreshDetailFromApi(() -> btnApplyDiamondPlan.setEnabled(true));
            }

            @Override
            public void onFailure(String errorMsg) {
                btnApplyDiamondPlan.setEnabled(true);
                Toast.makeText(requireContext(), "应用补贴失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindPorts(@Nullable JSONArray allocations) {
        portItems.clear();
        portAdapter.clear();
        selectedPortIndex = -1;

        if (allocations != null) {
            for (int i = 0; i < allocations.length(); i++) {
                JSONObject item = allocations.optJSONObject(i);
                if (item == null) continue;
                int id = item.optInt("id", -1);
                int port = item.optInt("port", -1);
                boolean isDefault = item.optBoolean("is_default", false);
                if (id <= 0 || port <= 0) continue;
                PortItem portItem = new PortItem(id, port, isDefault);
                portItems.add(portItem);
                portAdapter.add(port + (isDefault ? " (主端口)" : ""));
                if (isDefault) {
                    selectedPortIndex = portItems.size() - 1;
                }
            }
        }

        if (portItems.isEmpty()) {
            tvPorts.setText("端口列表: -");
            spinnerPorts.setEnabled(false);
            btnSetMainPort.setEnabled(false);
        } else {
            StringBuilder sb = new StringBuilder("端口列表: ");
            for (int i = 0; i < portItems.size(); i++) {
                if (i > 0) sb.append(", ");
                PortItem p = portItems.get(i);
                sb.append(p.port);
                if (p.isDefault) sb.append("(主端口)");
            }
            tvPorts.setText(sb.toString());
            spinnerPorts.setEnabled(true);
            if (selectedPortIndex < 0) {
                selectedPortIndex = 0;
            }
            spinnerPorts.setSelection(selectedPortIndex, false);
        }

        portAdapter.notifyDataSetChanged();
        updatePortActionState();
    }

    private void updatePortActionState() {
        btnBuyPort.setEnabled(getActivity() instanceof ServerManages);
        if (selectedPortIndex < 0 || selectedPortIndex >= portItems.size()) {
            btnSetMainPort.setEnabled(false);
            return;
        }
        PortItem selected = portItems.get(selectedPortIndex);
        btnSetMainPort.setEnabled(!selected.isDefault);
    }

    private void confirmBuyPort() {
        if (!(getActivity() instanceof ServerManages)) return;
        int cost = cachedDetail != null && cachedDetail.optBoolean("is_pro", false) ? 50 : 100;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确定创建端口吗")
                .setMessage(String.format(Locale.getDefault(), "将花费%d积分来创建一个端口", cost))
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> buyPort())
                .show();
    }

    private void buyPort() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        btnBuyPort.setEnabled(false);
        new PortApi(requireContext()).buyPort(token, activity.getDeviceId(), new PortApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(requireContext(), response.optString("msg", "请求增加新端口成功"), Toast.LENGTH_SHORT).show();
                refreshDetailFromApi(() -> btnBuyPort.setEnabled(true));
            }

            @Override
            public void onFailure(String errorMsg) {
                btnBuyPort.setEnabled(true);
                Toast.makeText(requireContext(), "购买端口失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setMainPort() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        if (selectedPortIndex < 0 || selectedPortIndex >= portItems.size()) return;

        PortItem selected = portItems.get(selectedPortIndex);
        if (selected.isDefault) {
            Toast.makeText(requireContext(), "该端口已是主端口", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSetMainPort.setEnabled(false);
        new PortApi(requireContext()).setMainPort(token, activity.getDeviceId(), selected.id, new PortApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(requireContext(), response.optString("msg", "设为主端口成功"), Toast.LENGTH_SHORT).show();
                refreshDetailFromApi(() -> btnSetMainPort.setEnabled(true));
            }

            @Override
            public void onFailure(String errorMsg) {
                btnSetMainPort.setEnabled(true);
                Toast.makeText(requireContext(), "设置主端口失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void copyTroubleshootId() {
        if (cachedDetail == null) return;
        String id = safe(cachedDetail.optString("uuid"));
        if ("-".equals(id) || getContext() == null) {
            Toast.makeText(requireContext(), "暂无故障排错ID", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("troubleshoot_id", id));
            Toast.makeText(requireContext(), "故障排错ID已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFieldValue(TextView view, String label) {
        if (view == null || getContext() == null) return;
        String raw = String.valueOf(view.getText());
        String value = extractValuePart(raw);
        if (value.isEmpty() || "-".equals(value)) {
            Toast.makeText(requireContext(), label + "暂无可复制内容", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(requireContext(), label + "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private String extractValuePart(String text) {
        if (text == null) return "";
        int idx = text.indexOf(":");
        if (idx < 0) {
            idx = text.indexOf("：");
        }
        if (idx < 0 || idx + 1 >= text.length()) {
            return text.trim();
        }
        return text.substring(idx + 1).trim();
    }

    private void confirmDeleteInstance() {
        if (!(getActivity() instanceof ServerManages)) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认销毁实例")
                .setMessage("销毁后不可恢复，是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认销毁", (dialog, which) -> deleteInstance())
                .show();
    }

    private void deleteInstance() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        btnDestroyInstance.setEnabled(false);
        new MainApi(requireContext()).deleteInstance(token, String.valueOf(activity.getDeviceId()), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                Toast.makeText(requireContext(), data.optString("msg", "申请删除容器成功"), Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                btnDestroyInstance.setEnabled(true);
                Toast.makeText(requireContext(), "销毁实例失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchSftp() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        String instanceId = String.valueOf(activity.getDeviceId());
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(requireContext()).getValid(instanceId);
        if (cached != null) {
            cachedSftp = cached.toJson();
            render();
            return;
        }
        String token = getToken();
        if (token == null) return;

        new MainApi(requireContext()).getSftp(token, instanceId, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONObject sftpData = data.optJSONObject("data");
                cachedSftp = sftpData != null ? sftpData : data;
                render();
            }

            @Override
            public void onFailure(String errorMsg) {
                // Keep previous/fallback display when SFTP request fails.
            }
        });
    }

    private void refreshDetailFromApi(@Nullable Runnable done) {
        if (!(getActivity() instanceof ServerManages activity)) {
            if (done != null) done.run();
            return;
        }

        String token = getToken();
        if (token == null) {
            if (done != null) done.run();
            return;
        }

        new MainApi(requireContext()).getInstanceDetail(token, String.valueOf(activity.getDeviceId()), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                refreshCachedDetail();
                render();
                fetchSftp();
                if (done != null) done.run();
            }

            @Override
            public void onFailure(String errorMsg) {
                Toast.makeText(requireContext(), "刷新实例信息失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                if (done != null) done.run();
            }
        });
    }

    @Nullable
    private String getToken() {
        Context context = getContext();
        if (context == null) return null;
        return context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", null);
    }

    private String toStatusText(String status) {
        return switch (status) {
            case "running" -> "运行中";
            case "offline" -> "已离线";
            case "installing" -> "安装中";
            case "starting" -> "启动中";
            case "stopping" -> "停止中";
            default -> "未知状态";
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "-";
        double gb = bytes / 1024d / 1024d / 1024d;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    private String formatTime(String utc) {
        if (utc == null || utc.isEmpty()) return "-";
        try {
            OffsetDateTime dt = OffsetDateTime.parse(utc);
            return dt.atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return utc.replace('T', ' ').replace("Z", "");
        }
    }

    private String safe(String text) {
        return text == null || text.trim().isEmpty() ? "-" : text;
    }

    private static final class NoFilterArrayAdapter extends ArrayAdapter<String> {
        private final Filter noFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                return new FilterResults();
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };

        NoFilterArrayAdapter(@NonNull Context context, @NonNull String[] items) {
            super(context, android.R.layout.simple_dropdown_item_1line, items);
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return noFilter;
        }
    }

    private static final class PortItem {
        final int id;
        final int port;
        final boolean isDefault;

        PortItem(int id, int port, boolean isDefault) {
            this.id = id;
            this.port = port;
            this.isDefault = isDefault;
        }
    }
}
