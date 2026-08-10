package cn.jdnjk.simpfun.ui.ins.manage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.api.ins.DiamondApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.api.ins.PortApi;
import cn.jdnjk.simpfun.api.ins.SupportAPI;
import cn.jdnjk.simpfun.ui.create.CreateServer;
import cn.jdnjk.simpfun.ui.setting.ManageScreenshotProtection;
import cn.jdnjk.simpfun.utils.ClipboardUtils;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;
import cn.jdnjk.simpfun.utils.PageDataStore;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;

public class ManageFragment extends Fragment {
    private static final String PASSWORD_MASK = "••••••••";

    private JSONObject cachedDetail;
    private JSONObject cachedSftp;
    private JSONObject cachedSupport;
    private boolean supportLoaded;
    private boolean supportActionRunning;
    private boolean detailRefreshRunning;
    private int detailRefreshGeneration;
    private boolean sftpPasswordVisible;

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout contentLayout;
    private View cardSupport;
    private TextView noData;
    private TextView tvInstanceId;
    private TextView tvTroubleshootId;
    private TextView tvInstanceStatus;
    private TextView tvDiskUsage;
    private TextView tvTrafficRemain;
    private TextView tvGameType;
    private TextView tvDiskCheckTime;
    private MaterialButton btnRateImage;
    private TextView tvSftpHost;
    private TextView tvSftpPort;
    private TextView tvSftpUser;
    private TextView tvSftpPassword;
    private ImageButton btnResetSftpPassword;
    private TextView tvSupportDevQq;
    private TextView tvSupportGroup;
    private TextView tvSupportComment;
    private TextView tvSupportCreateTime;
    private MaterialButton btnSupportAction;
    private TextView tvPorts;
    private TextView tvDiamondLeft;
    private TextView tvDiamondPlan;
    private TextView tvDiamondValid;
    private Spinner spinnerPorts;
    private MaterialButton btnSetMainPort;
    private MaterialButton btnBuyPort;
    private MaterialButton btnReinstallInstance;
    private MaterialButton btnChangeConfig;
    private MaterialButton btnDestroyInstance;

    private final ActivityResultLauncher<Intent> reinstallLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) return;
                Intent data = result.getData();
                boolean changeConfig = data != null && data.getBooleanExtra(CreateServer.EXTRA_RESULT_CHANGE_CONFIG, false);
                if (changeConfig) {
                    returnToServerListAndRefresh();
                    return;
                }
                refreshDetailFromApi(null);
            });

    private AutoCompleteTextView tvPlanFilter;
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
    public void onDestroyView() {
        detailRefreshGeneration++;
        detailRefreshRunning = false;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(null);
            swipeRefreshLayout.setRefreshing(false);
        }
        swipeRefreshLayout = null;
        super.onDestroyView();
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
        sftpPasswordVisible = false;
        refreshCachedDetail();
        render();
        fetchSftp();
        fetchSupport();
    }

    @Override
    public void onStop() {
        clearScreenshotProtection();
        super.onStop();
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
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> refreshDetailFromApi(true, true, null));
        }

        contentLayout = root.findViewById(R.id.layout_manage_content);
        cardSupport = root.findViewById(R.id.card_support);
        noData = root.findViewById(R.id.tv_manage_no_data);
        tvInstanceId = root.findViewById(R.id.tv_instance_id);
        tvTroubleshootId = root.findViewById(R.id.tv_troubleshoot_id);
        tvInstanceStatus = root.findViewById(R.id.tv_instance_status);
        tvDiskUsage = root.findViewById(R.id.tv_disk_usage);
        tvTrafficRemain = root.findViewById(R.id.tv_traffic_remain);
        tvGameType = root.findViewById(R.id.tv_game_type);
        tvDiskCheckTime = root.findViewById(R.id.tv_disk_check_time);
        btnRateImage = root.findViewById(R.id.btn_rate_image);
        tvSftpHost = root.findViewById(R.id.tv_sftp_host);
        tvSftpPort = root.findViewById(R.id.tv_sftp_port);
        tvSftpUser = root.findViewById(R.id.tv_sftp_user);
        tvSftpPassword = root.findViewById(R.id.tv_sftp_password);
        btnResetSftpPassword = root.findViewById(R.id.btn_reset_sftp_password);
        tvSupportDevQq = root.findViewById(R.id.tv_support_dev_qq);
        tvSupportGroup = root.findViewById(R.id.tv_support_group);
        tvSupportComment = root.findViewById(R.id.tv_support_comment);
        tvSupportCreateTime = root.findViewById(R.id.tv_support_create_time);
        btnSupportAction = root.findViewById(R.id.btn_support_action);
        tvPorts = root.findViewById(R.id.tv_ports);
        tvDiamondLeft = root.findViewById(R.id.tv_diamond_left);
        tvDiamondPlan = root.findViewById(R.id.tv_diamond_plan);
        tvDiamondValid = root.findViewById(R.id.tv_diamond_valid);
        spinnerPorts = root.findViewById(R.id.spinner_ports);
        btnSetMainPort = root.findViewById(R.id.btn_set_main_port);
        btnBuyPort = root.findViewById(R.id.btn_buy_port);
        btnReinstallInstance = root.findViewById(R.id.btn_reinstall_instance);
        btnChangeConfig = root.findViewById(R.id.btn_change_config);
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
        RecyclerView rvDiamondPlans = root.findViewById(R.id.rv_diamond_plans);
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
        btnRateImage.setOnClickListener(v -> showRatingDialog());
        btnSupportAction.setOnClickListener(v -> showSupportCommentDialog(isSupportValid()));

        btnBuyPort.setOnClickListener(v -> confirmBuyPort());
        btnSetMainPort.setOnClickListener(v -> setMainPort());
        btnReinstallInstance.setOnClickListener(v -> openReinstallWizard());
        btnChangeConfig.setOnClickListener(v -> openChangeConfigWizard());
        btnDestroyInstance.setOnClickListener(v -> confirmDeleteInstance());
        tvTroubleshootId.setOnClickListener(v -> copyTroubleshootId());

        tvSftpHost.setOnClickListener(v -> copyFieldValue(tvSftpHost, "SFTP IP"));
        tvSftpPort.setOnClickListener(v -> copyFieldValue(tvSftpPort, "SFTP 端口"));
        tvSftpUser.setOnClickListener(v -> copyFieldValue(tvSftpUser, "SFTP 用户名"));
        tvSftpPassword.setOnClickListener(v -> copyFieldValue(tvSftpPassword, "SFTP 密码", true));
        tvSftpPassword.setOnLongClickListener(v -> {
            Object tagValue = tvSftpPassword.getTag();
            String password = tagValue instanceof String ? ((String) tagValue).trim() : "";
            if (password.isEmpty() || "-".equals(password)) {
                return true;
            }
            sftpPasswordVisible = !sftpPasswordVisible;
            updateSftpPasswordText();
            Toast.makeText(requireContext(), sftpPasswordVisible ? "SFTP 密码已显示" : "SFTP 密码已隐藏", Toast.LENGTH_SHORT).show();
            return true;
        });
        btnResetSftpPassword.setOnClickListener(v -> confirmResetSftpPassword());
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
            btnReinstallInstance.setEnabled(false);
            btnChangeConfig.setEnabled(false);
            btnDestroyInstance.setEnabled(false);
            renderRatingButton();
            tvTroubleshootId.setText("故障排错ID: -");
            tvSftpHost.setText("服务器IP: -");
            tvSftpPort.setText("服务器端口: -");
            tvSftpUser.setText("用户名: -");
            tvSftpHost.setTag("-");
            tvSftpPort.setTag("-");
            tvSftpUser.setTag("-");
            tvSftpPassword.setTag("-");
            updateSftpPasswordText();
            renderSupportCard();
            return;
        }

        noData.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
        btnReinstallInstance.setEnabled(true);
        btnChangeConfig.setEnabled(true);
        btnDestroyInstance.setEnabled(true);
        renderRatingButton();

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
        tvSftpHost.setTag(sftpHost);
        tvSftpPort.setTag(sftpPort);
        tvSftpUser.setTag(sftpUser);
        tvSftpPassword.setTag(sftpPassword);
        updateSftpPasswordText();

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

        renderSupportCard();

        if (allDiamondPlans == null && getActivity() instanceof ServerManages) {
            fetchDiamondPlans();
        }
    }

    private void fetchSupport() {
        fetchSupport(null);
    }

    private void fetchSupport(@Nullable Runnable done) {
        if (!(getActivity() instanceof ServerManages activity) || !shouldShowSupportCard()) {
            cachedSupport = null;
            supportLoaded = false;
            renderSupportCard();
            if (done != null) done.run();
            return;
        }
        String token = getToken();
        if (token == null) {
            supportLoaded = false;
            renderSupportCard();
            if (done != null) done.run();
            return;
        }

        new SupportAPI().GetSupport(token, activity.getDeviceId(), new SupportAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) return;
                JSONObject data = response.optJSONObject("data");
                cachedSupport = data != null ? data : response;
                supportLoaded = true;
                if (done != null) done.run();
                renderSupportCard();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                supportLoaded = false;
                if (done != null) done.run();
                renderSupportCard();
            }
        });
    }

    private void renderRatingButton() {
        if (btnRateImage == null) return;
        boolean showButton = shouldShowSupportCard();
        btnRateImage.setVisibility(showButton ? View.VISIBLE : View.GONE);
        btnRateImage.setEnabled(showButton && cachedDetail != null);
    }

    private void renderSupportCard() {
        if (tvSupportDevQq == null || tvSupportGroup == null || btnSupportAction == null) return;

        boolean showCard = shouldShowSupportCard();
        if (cardSupport != null) {
            cardSupport.setVisibility(showCard ? View.VISIBLE : View.GONE);
        }
        if (!showCard) {
            btnSupportAction.setEnabled(false);
            return;
        }

        boolean valid = isSupportValid();
        tvSupportDevQq.setText("开发者QQ：" + supportValue("dev_qq"));
        tvSupportGroup.setText("技术交流群：" + supportValue("support_group"));
        tvSupportComment.setVisibility(valid ? View.VISIBLE : View.GONE);
        tvSupportCreateTime.setVisibility(valid ? View.VISIBLE : View.GONE);
        if (valid) {
            tvSupportComment.setText("请求原因：" + supportValue("comment"));
            tvSupportCreateTime.setText("创建时间：" + formatTime(cachedSupport.optString("create_time")));
        }
        btnSupportAction.setText(valid ? "结束" : "创建");
        btnSupportAction.setEnabled(cachedDetail != null && supportLoaded && !supportActionRunning);
    }

    private boolean shouldShowSupportCard() {
        if (cachedDetail == null) return false;
        JSONObject gameInfo = cachedDetail.optJSONObject("game_info");
        return gameInfo != null && gameInfo.optBoolean("custom", false);
    }

    private boolean isSupportValid() {
        return cachedSupport != null && cachedSupport.optBoolean("valid", false);
    }

    private String supportValue(String key) {
        return cachedSupport == null ? "-" : safe(cachedSupport.optString(key));
    }

    private void showRatingDialog() {
        if (!(getActivity() instanceof ServerManages) || !shouldShowSupportCard()) return;

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        content.setPadding(padding, 0, padding, 0);

        RadioGroup ratingGroup = new RadioGroup(requireContext());
        ratingGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton likeButton = new RadioButton(requireContext());
        likeButton.setId(View.generateViewId());
        likeButton.setText("点赞");
        RadioButton dislikeButton = new RadioButton(requireContext());
        dislikeButton.setId(View.generateViewId());
        dislikeButton.setText("差评");
        ratingGroup.addView(likeButton);
        ratingGroup.addView(dislikeButton);
        ratingGroup.check(likeButton.getId());
        content.addView(ratingGroup);

        EditText feedbackInput = new EditText(requireContext());
        feedbackInput.setSingleLine(false);
        feedbackInput.setMinLines(3);
        feedbackInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        feedbackInput.setHint("体验反馈");
        content.addView(feedbackInput);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("评价第三方镜像")
                .setMessage("您可以对该第三方镜像进行评价\n这有助于帮助开发者改进，也有助于其它用户进行镜像选择\n每个镜像版本只能够评价一次\n您的QQ将会共享给开发者以帮助开发者判断解决问题。")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String feedback = feedbackInput.getText() == null ? "" : feedbackInput.getText().toString().trim();
            if (feedback.isEmpty()) {
                Toast.makeText(requireContext(), "请填写体验反馈", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean like = ratingGroup.getCheckedRadioButtonId() == likeButton.getId();
            dialog.dismiss();
            submitImageRating(like, feedback);
        }));
        dialog.show();
    }

    private void submitImageRating(boolean like, String feedback) {
        if (!(getActivity() instanceof ServerManages activity) || !shouldShowSupportCard()) return;
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRateImage.setEnabled(false);
        new SupportAPI().Rating(token, activity.getDeviceId(), like, feedback, new SupportAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), response.optString("msg", "评价成功"), Toast.LENGTH_SHORT).show();
                renderRatingButton();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                renderRatingButton();
                Toast.makeText(requireContext(), "评价失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSupportCommentDialog(boolean ending) {
        if (!(getActivity() instanceof ServerManages) || !shouldShowSupportCard()) return;
        EditText input = new EditText(requireContext());
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("请填写原因");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(ending ? "结束技术支持" : "创建技术支持")
                .setMessage(ending
                        ? "结束技术支持后，开发者将不再有权限访问您的实例\n同时为确保安全，SFTP密码将会重置。"
                        : "点击创建将允许开发者访问您的实例\n为确保您能够及时有效获得技术支持，请在发起前联系开发者\n您随时可以收回开发者的访问权限。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton(ending ? "结束" : "创建", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String comment = input.getText() == null ? "" : input.getText().toString().trim();
            if (comment.isEmpty()) {
                Toast.makeText(requireContext(), "请填写原因", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (ending) {
                stopSupport(comment);
            } else {
                createSupport(comment);
            }
        }));
        dialog.show();
    }

    private void createSupport(String comment) {
        if (!(getActivity() instanceof ServerManages activity) || !shouldShowSupportCard()) return;
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        supportActionRunning = true;
        renderSupportCard();
        new SupportAPI().CreateSupport(token, activity.getDeviceId(), comment, new SupportAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), response.optString("msg", "创建成功"), Toast.LENGTH_SHORT).show();
                fetchSupport(() -> supportActionRunning = false);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                supportActionRunning = false;
                renderSupportCard();
                Toast.makeText(requireContext(), "创建技术支持失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopSupport(String comment) {
        if (!(getActivity() instanceof ServerManages activity) || !shouldShowSupportCard()) return;
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        supportActionRunning = true;
        renderSupportCard();
        new SupportAPI().StopSupport(token, activity.getDeviceId(), comment, new SupportAPI.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), response.optString("msg", "结束支持成功"), Toast.LENGTH_SHORT).show();
                cachedSupport = null;

                boolean isDev = cachedDetail != null && cachedDetail.optBoolean("dev", false);
                if (isDev) {
                    String instanceId = String.valueOf(activity.getDeviceId());
                    SftpCredentialStore.get(requireContext()).delete(instanceId);
                    returnToServerListAndRefresh();
                } else {
                    fetchSupport(() -> supportActionRunning = false);
                    fetchSftp(true);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                supportActionRunning = false;
                renderSupportCard();
                Toast.makeText(requireContext(), "结束技术支持失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
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

    private void openReinstallWizard() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        int serverId = activity.getDeviceId();
        if (serverId <= 0) {
            Toast.makeText(requireContext(), "实例ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), CreateServer.class);
        intent.putExtra(CreateServer.EXTRA_MODE, CreateServer.MODE_REINSTALL);
        intent.putExtra(CreateServer.EXTRA_SERVER_ID, serverId);

        JSONObject gameInfo = cachedDetail != null ? cachedDetail.optJSONObject("game_info") : null;
        if (gameInfo != null) {
            putCurrentGameInfoExtras(intent, gameInfo);
        }

        reinstallLauncher.launch(intent);
    }

    private void openChangeConfigWizard() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        int serverId = activity.getDeviceId();
        if (serverId <= 0) {
            Toast.makeText(requireContext(), "实例ID无效", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject gameInfo = cachedDetail != null ? cachedDetail.optJSONObject("game_info") : null;
        if (gameInfo == null) {
            Toast.makeText(requireContext(), "实例镜像信息未加载，无法变配", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), CreateServer.class);
        intent.putExtra(CreateServer.EXTRA_MODE, CreateServer.MODE_CHANGE_CONFIG);
        intent.putExtra(CreateServer.EXTRA_SERVER_ID, serverId);
        putCurrentGameInfoExtras(intent, gameInfo);

        reinstallLauncher.launch(intent);
    }

    private void putCurrentGameInfoExtras(Intent intent, JSONObject gameInfo) {
        intent.putExtra(CreateServer.EXTRA_CURRENT_GAME_NAME, gameInfo.optString("game_name", ""));
        intent.putExtra(CreateServer.EXTRA_CURRENT_KIND_NAME, gameInfo.optString("kind_name", ""));
        intent.putExtra(CreateServer.EXTRA_CURRENT_IMAGE_NAME, gameInfo.optString("image_name", ""));
        intent.putExtra(CreateServer.EXTRA_CURRENT_VERSION_NAME, gameInfo.optString("version_name", ""));
        putFirstPositiveExtra(intent, CreateServer.EXTRA_CURRENT_GAME_ID, gameInfo, "game_id", "game");
        putFirstPositiveExtra(intent, CreateServer.EXTRA_CURRENT_KIND_ID, gameInfo, "kind_id", "image_id", "custom_id", "kind");
        putFirstPositiveExtra(intent, CreateServer.EXTRA_CURRENT_VERSION_ID, gameInfo, "version_id", "version");
        putFirstPositiveExtra(intent, CreateServer.EXTRA_CURRENT_CUSTOM_ID, gameInfo, "custom_id");
        if (gameInfo.has("custom")) {
            intent.putExtra(CreateServer.EXTRA_CURRENT_CUSTOM, gameInfo.optBoolean("custom"));
        } else if (gameInfo.has("is_custom")) {
            intent.putExtra(CreateServer.EXTRA_CURRENT_CUSTOM, gameInfo.optBoolean("is_custom"));
        } else {
            intent.putExtra(CreateServer.EXTRA_CURRENT_CUSTOM, false);
        }
    }

    private void putFirstPositiveExtra(Intent intent, String extra, JSONObject data, String... keys) {
        for (String key : keys) {
            int value = data.optInt(key, -1);
            if (value > 0) {
                intent.putExtra(extra, value);
                return;
            }
        }
    }

    private void returnToServerListAndRefresh() {
        String token = getToken();
        if (token != null && !token.trim().isEmpty()) {
            PageDataStore.getInstance().clearServerData(token);
        }
        if (getActivity() instanceof ServerManages activity) {
            int oldId = activity.getDeviceId();
            if (oldId > 0) {
                InstanceDetailStore.getInstance().clear(oldId);
                SftpCredentialStore.get(requireContext()).delete(String.valueOf(oldId));
            }
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_REFRESH_SERVER_LIST, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            activity.finish();
        }
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
        if (cachedDetail == null || getContext() == null) return;
        String id = safe(cachedDetail.optString("uuid"));
        if ("-".equals(id)) {
            Toast.makeText(requireContext(), "暂无故障排错ID", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardUtils.copyPlainText(requireContext(), "troubleshoot_id", id, "故障排错ID已复制");
    }

    private void copyFieldValue(TextView view, String label) {
        copyFieldValue(view, label, false);
    }

    private void copyFieldValue(TextView view, String label, boolean sensitive) {
        if (view == null || getContext() == null) return;
        Object tagValue = view.getTag();
        String value = tagValue instanceof String ? ((String) tagValue).trim() : extractValuePart(String.valueOf(view.getText()));
        if (value.isEmpty() || "-".equals(value)) {
            Toast.makeText(requireContext(), label + "暂无可复制内容", Toast.LENGTH_SHORT).show();
            return;
        }

        if (sensitive) {
            ClipboardUtils.copySensitiveText(requireContext(), label, value, label + "已复制");
        } else {
            ClipboardUtils.copyPlainText(requireContext(), label, value, label + "已复制");
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

    private void confirmResetSftpPassword() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("重置 SFTP 密码")
                .setMessage("重置后旧密码将立即失效，需要重新用新密码连接 SFTP。是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置密码", (dialog, which) -> resetSftpPassword())
                .show();
    }

    private void resetSftpPassword() {
        if (!(getActivity() instanceof ServerManages activity)) return;
        String token = getToken();
        if (token == null) {
            Toast.makeText(requireContext(), "尚未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        final String instanceId = String.valueOf(activity.getDeviceId());
        if (btnResetSftpPassword != null) {
            btnResetSftpPassword.setEnabled(false);
        }
        new MainApi(requireContext()).resetSftpPassword(token, instanceId, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) return;
                if (btnResetSftpPassword != null) {
                    btnResetSftpPassword.setEnabled(true);
                }

                String newPassword = data.optString("new_passwd", "");
                if (newPassword.isEmpty()) {
                    JSONObject dataObj = data.optJSONObject("data");
                    if (dataObj != null) {
                        newPassword = dataObj.optString("new_passwd", "");
                    }
                }
                if (newPassword.isEmpty()) {
                    Toast.makeText(requireContext(), "重置失败：未获取到新密码", Toast.LENGTH_SHORT).show();
                    return;
                }

                SftpCredentialStore.get(requireContext()).updatePassword(instanceId, newPassword);

                if (cachedSftp != null) {
                    try {
                        cachedSftp.put("password", newPassword);
                    } catch (JSONException ignored) {
                    }
                }
                sftpPasswordVisible = true;
                tvSftpPassword.setTag(newPassword);
                updateSftpPasswordText();

                Toast.makeText(requireContext(), "SFTP 密码已重置", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isAdded()) return;
                if (btnResetSftpPassword != null) {
                    btnResetSftpPassword.setEnabled(true);
                }
                Toast.makeText(requireContext(), "重置 SFTP 密码失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSftpPasswordText() {
        if (tvSftpPassword == null) return;
        Object tagValue = tvSftpPassword.getTag();
        String password = tagValue instanceof String ? ((String) tagValue).trim() : "-";
        tvSftpPassword.setText("密码: " + formatSftpPasswordDisplay(password));
    }

    private String formatSftpPasswordDisplay(String password) {
        if (password == null || password.trim().isEmpty() || "-".equals(password.trim())) {
            return "-";
        }
        return sftpPasswordVisible ? password : PASSWORD_MASK;
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
        fetchSftp(false, null);
    }

    private void fetchSftp(boolean forceRefresh) {
        fetchSftp(forceRefresh, null);
    }

    private void fetchSftp(boolean forceRefresh, @Nullable Runnable done) {
        if (!(getActivity() instanceof ServerManages activity) || getContext() == null) {
            if (done != null) done.run();
            return;
        }
        String instanceId = String.valueOf(activity.getDeviceId());
        if (forceRefresh) {
            SftpCredentialStore.get(requireContext()).delete(instanceId);
            cachedSftp = null;
            sftpPasswordVisible = false;
            render();
        } else {
            SftpCredentialStore.Credential cached = SftpCredentialStore.get(requireContext()).getValid(instanceId);
            if (cached != null) {
                cachedSftp = cached.toJson();
                sftpPasswordVisible = false;
                render();
                if (done != null) done.run();
                return;
            }
        }
        String token = getToken();
        if (token == null) {
            if (done != null) done.run();
            return;
        }

        new MainApi(requireContext()).getSftp(token, instanceId, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isAdded()) {
                    if (done != null) done.run();
                    return;
                }
                JSONObject sftpData = data.optJSONObject("data");
                cachedSftp = sftpData != null ? sftpData : data;
                sftpPasswordVisible = false;
                render();
                if (done != null) done.run();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (done != null) done.run();
                // Keep previous/fallback display when SFTP request fails.
            }
        });
    }

    private void refreshDetailFromApi(@Nullable Runnable done) {
        refreshDetailFromApi(false, false, done);
    }

    private void refreshDetailFromApi(boolean fromSwipe, boolean forceSftpRefresh, @Nullable Runnable done) {
        if (detailRefreshRunning) {
            if (fromSwipe && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(true);
            }
            if (done != null) done.run();
            return;
        }
        if (!(getActivity() instanceof ServerManages activity)) {
            finishDetailRefresh(done);
            return;
        }

        detailRefreshRunning = true;
        int requestGeneration = ++detailRefreshGeneration;
        if (fromSwipe && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        activity.refreshInstanceDetail(new ServerManages.InstanceDetailCallback() {
            @Override
            public void onSuccess(@Nullable JSONObject detail) {
                if (requestGeneration != detailRefreshGeneration || !isAdded()) {
                    finishDetailRefresh(done);
                    return;
                }
                cachedDetail = detail;
                render();
                fetchSftp(forceSftpRefresh, () -> fetchSupport(() -> finishDetailRefresh(done)));
            }

            @Override
            public void onFailure(String errorMsg) {
                if (requestGeneration == detailRefreshGeneration && isAdded()) {
                    Toast.makeText(requireContext(), "刷新实例信息失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                }
                finishDetailRefresh(done);
            }
        });
    }

    private void finishDetailRefresh(@Nullable Runnable done) {
        detailRefreshRunning = false;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        if (done != null) done.run();
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
