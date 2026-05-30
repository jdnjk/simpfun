package cn.jdnjk.simpfun.ui.point;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.alipay.sdk.app.PayTask;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.PayApi;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.databinding.FragmentRechargeLayoutBinding;
import cn.jdnjk.simpfun.model.BenefitCardPlan;
import cn.jdnjk.simpfun.model.BenefitCardTypeOption;
import cn.jdnjk.simpfun.model.RechargeMode;
import cn.jdnjk.simpfun.model.RechargeTier;
import cn.jdnjk.simpfun.model.TrafficPackageOption;
import cn.jdnjk.simpfun.utils.DialogUtils;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;

public class RechargeFragment extends Fragment {
    private static final String PAY_METHOD_ALIPAY = "ali_pay_1";
    private static final String PAY_METHOD_WECHAT = "wx_pay_2";
    private static final String PAY_OK_URL = "https://api.simpcloud.cn/pics/pay_ok.png";
    private static final long PAY_BUTTON_LOCK_MS = 10_000L;

    private FragmentRechargeLayoutBinding binding;
    private WebView hiddenPayWebView;
    private boolean paySuccessHandled;
    private androidx.appcompat.app.AlertDialog publicRechargeNoticeDialog;
    private androidx.appcompat.app.AlertDialog trafficConfirmDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PayApi payApi = new PayApi();

    private final List<RechargeTier> tiers = new ArrayList<>();
    private final List<RechargeMode> modes = new ArrayList<>();
    private final List<TrafficPackageOption> trafficPackages = new ArrayList<>();
    private final List<InstanceOption> instanceOptions = new ArrayList<>();
    private final List<BenefitCardTypeOption> benefitCardTypes = new ArrayList<>();
    private final List<BenefitCardPlan> visibleBenefitCardPlans = new ArrayList<>();
    private final Map<String, List<BenefitCardPlan>> benefitCardPlanMap = new HashMap<>();
    private final Map<Integer, Long> instanceRemainBytes = new HashMap<>();
    private final Map<Integer, Boolean> instanceDetailLoading = new HashMap<>();

    private RechargeOptionAdapter tierAdapter;
    private RechargeOptionAdapter modeAdapter;
    private RechargeOptionAdapter trafficPackageAdapter;
    private RechargeOptionAdapter benefitCardTypeAdapter;
    private RechargeOptionAdapter benefitCardPlanAdapter;
    private RechargeOptionAdapter cardModeAdapter;
    private ArrayAdapter<String> trafficInstanceAdapter;

    private int selectedTierIndex = 0;
    private int selectedModeIndex = 0;
    private int selectedTrafficPackageIndex = 0;
    private int selectedInstanceIndex = -1;
    private int selectedBenefitCardTypeIndex = 0;
    private int selectedBenefitCardPlanIndex = 0;
    private int selectedCardModeIndex = 0;
    private String selectedPaymentMethod = PAY_METHOD_ALIPAY;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRechargeLayoutBinding.inflate(inflater, container, false);

        initTrafficPackages();
        initBenefitCardTypes();
        setupTabs();
        setupRecyclers();
        setupTrafficSelector();
        setupListeners();
        initHiddenPayWebView();
        fetchData();

        return binding.getRoot();
    }

    private boolean isViewAlive() {
        return binding != null && isAdded();
    }

    private void initTrafficPackages() {
        trafficPackages.clear();
        trafficPackages.add(new TrafficPackageOption(1, 10));
        trafficPackages.add(new TrafficPackageOption(3, 30));
        trafficPackages.add(new TrafficPackageOption(10, 100));
        trafficPackages.add(new TrafficPackageOption(30, 300));
        selectedTrafficPackageIndex = 0;
    }

    private void initBenefitCardTypes() {
        benefitCardTypes.clear();
        benefitCardTypes.add(new BenefitCardTypeOption("double_sign", "双倍积分卡", "每日签到积分翻倍"));
        benefitCardTypes.add(new BenefitCardTypeOption("auto_sign", "自动签到卡", "自动执行每日签到"));
        selectedBenefitCardTypeIndex = 0;
        selectedBenefitCardPlanIndex = 0;
    }

    private void setupTabs() {
        binding.tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!isViewAlive()) return;
                int position = tab.getPosition();
                binding.layoutRechargePoints.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                binding.layoutRechargeTraffic.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                binding.layoutRechargeCards.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                binding.tvUnderConstruction.setVisibility(View.GONE);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclers() {
        binding.recyclerTiers.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        tierAdapter = new RechargeOptionAdapter(R.layout.item_recharge_tier, R.id.tv_tier_points, R.id.tv_tier_money, position -> {
            selectedTierIndex = position;
            updatePointPayButtonText();
        });
        binding.recyclerTiers.setAdapter(tierAdapter);

        binding.recyclerModes.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        modeAdapter = new RechargeOptionAdapter(R.layout.item_recharge_mode, R.id.tv_mode_name, R.id.tv_mode_rule, position -> {
            selectedModeIndex = position;
            refreshPointTierRows();
            updatePointPayButtonText();
        });
        binding.recyclerModes.setAdapter(modeAdapter);

        binding.recyclerTrafficPackages.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        trafficPackageAdapter = new RechargeOptionAdapter(R.layout.item_recharge_tier, R.id.tv_tier_points, R.id.tv_tier_money, position -> {
            selectedTrafficPackageIndex = position;
            updateTrafficBuyButtonText();
        });
        binding.recyclerTrafficPackages.setAdapter(trafficPackageAdapter);

        binding.recyclerCardTypes.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        benefitCardTypeAdapter = new RechargeOptionAdapter(R.layout.item_recharge_mode, R.id.tv_mode_name, R.id.tv_mode_rule, position -> {
            selectedBenefitCardTypeIndex = position;
            selectedBenefitCardPlanIndex = 0;
            refreshVisibleBenefitCardPlans();
            updateCardPayButtonText();
        });
        binding.recyclerCardTypes.setAdapter(benefitCardTypeAdapter);

        binding.recyclerCardTiers.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        benefitCardPlanAdapter = new RechargeOptionAdapter(R.layout.item_recharge_tier, R.id.tv_tier_points, R.id.tv_tier_money, position -> {
            selectedBenefitCardPlanIndex = position;
            updateCardPayButtonText();
        });
        binding.recyclerCardTiers.setAdapter(benefitCardPlanAdapter);

        binding.recyclerCardModes.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        cardModeAdapter = new RechargeOptionAdapter(R.layout.item_recharge_mode, R.id.tv_mode_name, R.id.tv_mode_rule, position -> {
            selectedCardModeIndex = position;
            refreshBenefitCardPlanRows();
            updateCardPayButtonText();
        });
        binding.recyclerCardModes.setAdapter(cardModeAdapter);

        refreshPointTierRows();
        refreshPointModeRows();
        refreshTrafficPackageRows();
        refreshBenefitCardTypeRows();
        refreshVisibleBenefitCardPlans();
        refreshCardModeRows();
    }

    private void setupTrafficSelector() {
        if (!isViewAlive()) return;
        trafficInstanceAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        trafficInstanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTrafficInstance.setAdapter(trafficInstanceAdapter);
        binding.spinnerTrafficInstance.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedInstanceIndex = position;
                updateTrafficInstanceSummary();
                updateTrafficBuyButtonText();
                requestSelectedInstanceDetail();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedInstanceIndex = -1;
                updateTrafficInstanceSummary();
                updateTrafficBuyButtonText();
            }
        });
    }

    private void setupListeners() {
        binding.btnWechatPay.setOnClickListener(v -> selectPaymentMethod(PAY_METHOD_WECHAT));
        binding.btnAlipay.setOnClickListener(v -> selectPaymentMethod(PAY_METHOD_ALIPAY));
        binding.btnWechatPayCard.setOnClickListener(v -> selectPaymentMethod(PAY_METHOD_WECHAT));
        binding.btnAlipayCard.setOnClickListener(v -> selectPaymentMethod(PAY_METHOD_ALIPAY));
        binding.btnPay.setOnClickListener(v -> createPointOrder());
        binding.btnBuyTraffic.setOnClickListener(v -> buyTrafficPackage());
        binding.btnPayCard.setOnClickListener(v -> createBenefitCardOrder());
    }

    private void selectPaymentMethod(String method) {
        selectedPaymentMethod = method;
        updatePaymentSelection();
    }

    private void updatePaymentSelection() {
        if (!isViewAlive()) return;
        boolean alipay = PAY_METHOD_ALIPAY.equals(selectedPaymentMethod);
        boolean wechat = PAY_METHOD_WECHAT.equals(selectedPaymentMethod);
        binding.radioAlipay.setChecked(alipay);
        binding.radioWechat.setChecked(wechat);
        binding.radioAlipayCard.setChecked(alipay);
        binding.radioWechatCard.setChecked(wechat);
    }

    private void fetchData() {
        if (getActivity() == null) return;
        SharedPreferences sp = getActivity().getSharedPreferences("token", Context.MODE_PRIVATE);
        String token = sp.getString("token", "");
        if (token.isEmpty()) return;

        payApi.getRechargeMeta(token, new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewAlive()) return;
                if (data.optInt("code") == 200) {
                    JSONObject resultData = data.optJSONObject("data");
                    if (resultData != null) {
                        updateRechargeMetaUI(resultData);
                    }
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("RechargeFragment", "fetchData failed: " + errorMsg);
            }
        });

        fetchInstances(token);
    }

    private void fetchInstances(String token) {
        if (!isViewAlive()) return;
        new UserApi(requireContext().getApplicationContext()).getInstanceList(token, new UserApi.InstanceCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewAlive()) return;
                updateInstances(data.optJSONArray("list"));
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("RechargeFragment", "fetchInstances failed: " + errorMsg);
                if (!isViewAlive()) return;
                updateInstances(null);
                binding.tvTrafficInstanceSummary.setText("实例列表加载失败");
            }
        });
    }

    private void updateRechargeMetaUI(JSONObject data) {
        if (!isViewAlive()) return;
        try {
            JSONObject user = data.optJSONObject("user");
            if (user != null) {
                binding.tvUid.setText("UID: " + user.optInt("uid"));
                binding.tvCurrentPoint.setText("当前积分: " + user.optInt("point"));

                SharedPreferences userInfoSp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                binding.tvUsername.setText(userInfoSp.getString("username", "用户"));

                bindCardStatus(binding.tvDoubleSignCard,
                        user.optLong("double_sign_card_valid_time", 0),
                        user.optBoolean("double_sign_card_is_valid", false),
                        "双倍积分卡");
                bindCardStatus(binding.tvAutoSignCard,
                        user.optLong("auto_sign_card_valid_time", 0),
                        user.optBoolean("auto_sign_card_is_valid", false),
                        "自动签到卡");
            }

            bindChannelNames(data.optJSONArray("channels"));
            parsePointTiers(data);
            parseRechargeModes(data);
            parseBenefitCardPlans(data);

            updatePointPayButtonText();
            updateTrafficBuyButtonText();
            updateCardPayButtonText();
            updatePaymentSelection();
        } catch (Exception e) {
            Log.e("RechargeFragment", "updateRechargeMetaUI failed", e);
        }
    }

    private void bindChannelNames(@Nullable JSONArray channels) {
        if (!isViewAlive() || channels == null) return;
        for (int i = 0; i < channels.length(); i++) {
            JSONObject c = channels.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id");
            String name = c.optString("name");
            if (PAY_METHOD_WECHAT.equals(id)) {
                binding.tvWechatName.setText(name);
                binding.tvWechatNameCard.setText(name);
            }
            if (PAY_METHOD_ALIPAY.equals(id)) {
                binding.tvAlipayName.setText(name);
                binding.tvAlipayNameCard.setText(name);
            }
        }
    }

    private void parsePointTiers(JSONObject data) {
        JSONObject itemPriceTiers = data.optJSONObject("item_price_tiers");
        JSONArray pointTiers = itemPriceTiers != null ? itemPriceTiers.optJSONArray("point") : data.optJSONArray("point_price_tiers");
        tiers.clear();
        if (pointTiers != null) {
            for (int i = 0; i < pointTiers.length(); i++) {
                JSONObject t = pointTiers.optJSONObject(i);
                if (t != null) {
                    tiers.add(new RechargeTier(
                            t.optInt("point"),
                            t.optString("public_recharge_money"),
                            t.optString("pro_recharge_money")));
                }
            }
        }
        if (selectedTierIndex >= tiers.size()) selectedTierIndex = 0;
        refreshPointTierRows();
    }

    private void parseRechargeModes(JSONObject data) {
        JSONObject rechargeModes = data.optJSONObject("recharge_modes");
        modes.clear();
        if (rechargeModes != null) {
            JSONObject pub = rechargeModes.optJSONObject("public_recharge");
            if (pub != null) {
                modes.add(new RechargeMode(RechargeMode.MODE_PUBLIC, pub.optString("name"), pub.optString("rule")));
            }
            JSONObject pro = rechargeModes.optJSONObject("pro_recharge");
            if (pro != null) {
                modes.add(new RechargeMode(RechargeMode.MODE_NORMAL, pro.optString("name"), pro.optString("rule")));
            }
        }
        if (!modes.isEmpty()) {
            int defaultIndex = modes.size() > 1 ? 1 : 0;
            selectedModeIndex = defaultIndex;
            selectedCardModeIndex = defaultIndex;
        }
        refreshPointModeRows();
        refreshCardModeRows();
        refreshPointTierRows();
        refreshBenefitCardPlanRows();
    }

    private void parseBenefitCardPlans(JSONObject data) {
        benefitCardPlanMap.clear();
        JSONObject itemPriceTiers = data.optJSONObject("item_price_tiers");
        JSONObject signCardPriceTiers = data.optJSONObject("sign_card_price_tiers");

        for (BenefitCardTypeOption typeOption : benefitCardTypes) {
            JSONArray array = null;
            if (itemPriceTiers != null) {
                array = itemPriceTiers.optJSONArray(typeOption.getId());
            }
            if (array == null && signCardPriceTiers != null) {
                array = signCardPriceTiers.optJSONArray(typeOption.getId());
            }

            List<BenefitCardPlan> plans = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    plans.add(new BenefitCardPlan(
                            typeOption.getId(),
                            item.optInt("days"),
                            item.optString("public_recharge_money"),
                            item.optString("normal_recharge_money")));
                }
            }
            benefitCardPlanMap.put(typeOption.getId(), plans);
        }
        refreshVisibleBenefitCardPlans();
    }

    private void refreshVisibleBenefitCardPlans() {
        visibleBenefitCardPlans.clear();
        if (!benefitCardTypes.isEmpty() && selectedBenefitCardTypeIndex >= 0 && selectedBenefitCardTypeIndex < benefitCardTypes.size()) {
            BenefitCardTypeOption type = benefitCardTypes.get(selectedBenefitCardTypeIndex);
            List<BenefitCardPlan> plans = benefitCardPlanMap.get(type.getId());
            if (plans != null) {
                visibleBenefitCardPlans.addAll(plans);
            }
        }
        if (selectedBenefitCardPlanIndex >= visibleBenefitCardPlans.size()) {
            selectedBenefitCardPlanIndex = 0;
        }
        refreshBenefitCardPlanRows();
    }

    private void bindCardStatus(TextView view, long validTimeSeconds, boolean valid, String title) {
        if (validTimeSeconds > 0) {
            String dateStr = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
                    .format(new Date(validTimeSeconds * 1000));
            view.setText(title + " " + dateStr + "过期 " + (valid ? "已生效" : "未生效"));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void updateInstances(@Nullable JSONArray list) {
        instanceOptions.clear();
        instanceRemainBytes.clear();
        instanceDetailLoading.clear();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.optJSONObject(i);
                if (obj == null) continue;
                int id = obj.optInt("id", -1);
                if (id <= 0) continue;
                String name = obj.optString("name", "").trim();
                if (name.isEmpty()) name = "未命名实例";
                instanceOptions.add(new InstanceOption(id, name));
            }
        }

        selectedInstanceIndex = instanceOptions.isEmpty() ? -1 : 0;
        bindInstanceAdapter();
        updateTrafficInstanceSummary();
        updateTrafficBuyButtonText();
        requestSelectedInstanceDetail();
    }

    private void bindInstanceAdapter() {
        if (!isViewAlive() || trafficInstanceAdapter == null) return;
        trafficInstanceAdapter.clear();
        for (InstanceOption option : instanceOptions) {
            trafficInstanceAdapter.add(option.getDisplayLabel());
        }
        trafficInstanceAdapter.notifyDataSetChanged();
        binding.spinnerTrafficInstance.setEnabled(!instanceOptions.isEmpty());
        if (!instanceOptions.isEmpty() && selectedInstanceIndex >= 0 && selectedInstanceIndex < instanceOptions.size()) {
            binding.spinnerTrafficInstance.setSelection(selectedInstanceIndex, false);
        }
    }

    private void updateTrafficInstanceSummary() {
        if (!isViewAlive()) return;
        if (instanceOptions.isEmpty() || selectedInstanceIndex < 0 || selectedInstanceIndex >= instanceOptions.size()) {
            binding.tvTrafficInstanceSummary.setText("暂无可选实例，请先创建服务器实例");
            return;
        }
        InstanceOption option = instanceOptions.get(selectedInstanceIndex);
        Long remainBytes = instanceRemainBytes.get(option.getId());
        Boolean loading = instanceDetailLoading.get(option.getId());
        if (Boolean.TRUE.equals(loading)) {
            binding.tvTrafficInstanceSummary.setText("已选择实例：" + option.getName() + "（ID: " + option.getId() + "） · 正在获取剩余流量...");
            return;
        }
        if (remainBytes != null && remainBytes >= 0) {
            binding.tvTrafficInstanceSummary.setText("已选择实例：" + option.getName() + "（ID: " + option.getId() + "） · 剩余流量 " + formatTrafficBytes(remainBytes));
            return;
        }
        binding.tvTrafficInstanceSummary.setText("已选择实例：" + option.getName() + "（ID: " + option.getId() + "）");
    }

    private void requestSelectedInstanceDetail() {
        if (!isViewAlive() || selectedInstanceIndex < 0 || selectedInstanceIndex >= instanceOptions.size()) {
            return;
        }
        String token = requireActivity().getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) {
            return;
        }
        InstanceOption option = instanceOptions.get(selectedInstanceIndex);
        if (instanceRemainBytes.containsKey(option.getId()) || Boolean.TRUE.equals(instanceDetailLoading.get(option.getId()))) {
            updateTrafficInstanceSummary();
            return;
        }

        JSONObject cachedResponse = InstanceDetailStore.getInstance().getResponse(option.getId());
        if (cachedResponse != null) {
            applyInstanceDetail(option.getId(), cachedResponse);
            return;
        }

        instanceDetailLoading.put(option.getId(), true);
        updateTrafficInstanceSummary();
        new MainApi(requireContext()).getInstanceDetail(token, String.valueOf(option.getId()), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                instanceDetailLoading.remove(option.getId());
                if (!isViewAlive()) return;
                applyInstanceDetail(option.getId(), data);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("RechargeFragment", "getInstanceDetail failed: " + errorMsg);
                instanceDetailLoading.remove(option.getId());
                if (!isViewAlive()) return;
                updateTrafficInstanceSummary();
            }
        });
    }

    private void applyInstanceDetail(int deviceId, JSONObject data) {
        if (!isViewAlive()) return;
        JSONObject detail = data.optJSONObject("data");
        JSONObject traffic = detail != null ? detail.optJSONObject("traffic") : null;
        if (traffic != null) {
            instanceRemainBytes.put(deviceId, traffic.optLong("remain_bytes", -1));
        }
        updateTrafficInstanceSummary();
    }

    private String formatTrafficBytes(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        double gb = bytes / 1024d / 1024d / 1024d;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    private void updatePointPayButtonText() {
        if (!isViewAlive()) return;
        if (tiers.isEmpty() || selectedTierIndex >= tiers.size()) {
            binding.btnPay.setText("立即支付 0元");
            return;
        }
        RechargeTier tier = tiers.get(selectedTierIndex);
        RechargeMode mode = modes.size() > selectedModeIndex ? modes.get(selectedModeIndex) : null;
        binding.btnPay.setText("立即支付 " + tier.getPrice(mode != null ? mode.getId() : "") + "元");
    }

    private void updateTrafficBuyButtonText() {
        if (!isViewAlive()) return;
        if (trafficPackages.isEmpty() || selectedTrafficPackageIndex >= trafficPackages.size()) {
            binding.btnBuyTraffic.setText("立即购买 0积分");
            binding.btnBuyTraffic.setEnabled(false);
            return;
        }
        TrafficPackageOption option = trafficPackages.get(selectedTrafficPackageIndex);
        binding.btnBuyTraffic.setText("立即购买 " + option.getPointCost() + "积分");
        binding.btnBuyTraffic.setEnabled(!instanceOptions.isEmpty() && selectedInstanceIndex >= 0);
    }

    private void updateCardPayButtonText() {
        if (!isViewAlive()) return;
        if (visibleBenefitCardPlans.isEmpty() || selectedBenefitCardPlanIndex >= visibleBenefitCardPlans.size()) {
            binding.btnPayCard.setText("立即支付 0元");
            return;
        }
        BenefitCardPlan plan = visibleBenefitCardPlans.get(selectedBenefitCardPlanIndex);
        RechargeMode mode = modes.size() > selectedCardModeIndex ? modes.get(selectedCardModeIndex) : null;
        binding.btnPayCard.setText("立即支付 " + plan.getPrice(mode != null ? mode.getId() : "") + "元");
    }

    private void createPointOrder() {
        if (!isViewAlive() || tiers.isEmpty() || modes.isEmpty()) return;

        RechargeMode mode = modes.get(selectedModeIndex);
        Runnable action = this::executePointOrder;
        if (RechargeMode.MODE_PUBLIC.equals(mode.getId())) {
            showPublicRechargeNotice(action);
        } else {
            action.run();
        }
    }

    private void createBenefitCardOrder() {
        if (!isViewAlive() || visibleBenefitCardPlans.isEmpty() || modes.isEmpty()) return;

        RechargeMode mode = modes.get(selectedCardModeIndex);
        Runnable action = this::executeBenefitCardOrder;
        if (RechargeMode.MODE_PUBLIC.equals(mode.getId())) {
            showPublicRechargeNotice(action);
        } else {
            action.run();
        }
    }

    private void showPublicRechargeNotice(Runnable onConfirm) {
        if (!isViewAlive()) return;
        String content = "（下滑查看更多）<br><br>"
                + "简幻欢目前仍为公益项目，<b>并非以盈利为目的</b>，初衷是给广大用户提供一个<b>「好用」</b>的服务器平台，达成<b>「人人都可免费开属于自己的服务器」</b>的目标。<br><br>"
                + "然而在项目实际运行时，发现：<br>"
                + "1. 部分用户有更高的性能需求，并且需要连续使用，这需要更高的成本。<br>"
                + "2. 用户不会关注实际性能需求、使用需求，在无约束使用时只会选择最高配置并持续占用，给项目造成了资源和金钱的浪费。<br><br>"
                + "为了能够满足不同用户的实际需求，同时节省部分项目开销，连续使用更高性能服务器将需要用户自行承担部分服务器成本，余下成本由我方承担。<br><br>"
                + "即：<b>服务器成本 = 您承担一部分 + 我方承担一部分</b><br><br>"
                + "若需支持我们，可考虑关闭公益模式（变更至 Pro），或直接选择普通充值，收益将会用于简幻欢的项目维护、设备升级等。<br><br>"
                + "我们尽力提供更多的服务器资源，但您应当理解：出于项目预算有限，我们无法承担过多成本。项目设置了全平台范围的每日充值上限，我们不保证您能够进行充值，也不保证您所需配置有足够剩余资源。<br><br>"
                + "您悉知：<b>充值为您的个人意愿，是您有更高的需求，并非本项目所提倡的。您的充值并不能覆盖服务器运行成本，也不能帮助到我们业务。出于项目预算有限，无法保证您能够进行充值或使用到预期配置的服务器。如果您想支持我们，可考虑变更至 Pro 或直接进行普通充值。</b>";

        Spanned message;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            message = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY);
        } else {
            message = Html.fromHtml(content);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("充值前须知")
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("已阅并继续 (15s)", null)
                .create();
        publicRechargeNoticeDialog = dialog;
        dialog.show();

        android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        final int[] countdown = {15};
        final Runnable[] countdownRunnable = new Runnable[1];
        countdownRunnable[0] = new Runnable() {
            @Override
            public void run() {
                if (!isViewAlive()) return;
                countdown[0]--;
                if (countdown[0] > 0) {
                    positiveButton.setText("已阅并继续 (" + countdown[0] + "s)");
                    mainHandler.postDelayed(this, 1000);
                } else {
                    positiveButton.setText("已阅并继续");
                    positiveButton.setEnabled(true);
                    positiveButton.setOnClickListener(v -> {
                        onConfirm.run();
                        dialog.dismiss();
                    });
                }
            }
        };
        dialog.setOnDismissListener(d -> {
            mainHandler.removeCallbacks(countdownRunnable[0]);
            if (publicRechargeNoticeDialog == dialog) {
                publicRechargeNoticeDialog = null;
            }
        });
        mainHandler.postDelayed(countdownRunnable[0], 1000);
    }

    private void lockPayButton(MaterialButton button, Runnable resetAction) {
        button.setEnabled(false);
        mainHandler.postDelayed(() -> {
            if (!isViewAlive()) return;
            button.setEnabled(true);
            resetAction.run();
        }, PAY_BUTTON_LOCK_MS);
    }

    private void executePointOrder() {
        if (!isViewAlive()) return;
        binding.btnPay.setText("请求中...");
        lockPayButton(binding.btnPay, RechargeFragment.this::updatePointPayButtonText);

        RechargeTier tier = tiers.get(selectedTierIndex);
        RechargeMode mode = modes.get(selectedModeIndex);
        String paymentMethod = selectedPaymentMethod;
        String token = requireActivity()
                .getSharedPreferences("token", Context.MODE_PRIVATE)
                .getString("token", "");

        payApi.createOrder(token, "point", tier.getPoint(), paymentMethod, mode.getId(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!isViewAlive()) return;
                handlePayOrderResponse(json, RechargeFragment.this::updatePointPayButtonText, paymentMethod);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isViewAlive()) return;
                showToast("请求失败: " + errorMsg);
                updatePointPayButtonText();
            }
        });
    }

    private void executeBenefitCardOrder() {
        if (!isViewAlive()) return;
        binding.btnPayCard.setText("请求中...");
        lockPayButton(binding.btnPayCard, RechargeFragment.this::updateCardPayButtonText);

        BenefitCardPlan plan = visibleBenefitCardPlans.get(selectedBenefitCardPlanIndex);
        RechargeMode mode = modes.get(selectedCardModeIndex);
        String paymentMethod = selectedPaymentMethod;
        String token = requireActivity()
                .getSharedPreferences("token", Context.MODE_PRIVATE)
                .getString("token", "");

        payApi.createOrder(token, plan.getItemId(), plan.getDays(), paymentMethod, mode.getId(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!isViewAlive()) return;
                handlePayOrderResponse(json, RechargeFragment.this::updateCardPayButtonText, paymentMethod);
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isViewAlive()) return;
                showToast("请求失败: " + errorMsg);
                updateCardPayButtonText();
            }
        });
    }

    private void handlePayOrderResponse(JSONObject json, Runnable resetAction, String paymentMethod) {
        if (!isViewAlive()) return;
        if (json.optInt("code") == 200) {
            String url = json.optString("url");
            if (PAY_METHOD_ALIPAY.equals(paymentMethod)) {
                paySuccessHandled = false;
                if (hiddenPayWebView != null) hiddenPayWebView.loadUrl(url);
            } else {
                copyToClipboard(url);
                showToast("已复制扫码链接，请在微信中打开并粘在任意聊天框点击支付");
            }
        } else {
            showToast("下单失败: " + json.optString("msg"));
        }
        resetAction.run();
    }

    private void buyTrafficPackage() {
        if (!isViewAlive() || instanceOptions.isEmpty() || selectedInstanceIndex < 0 || selectedInstanceIndex >= instanceOptions.size()) {
            showToast("请先选择需要购买流量的实例");
            return;
        }
        if (trafficPackages.isEmpty() || selectedTrafficPackageIndex >= trafficPackages.size()) {
            showToast("请选择流量包");
            return;
        }

        InstanceOption instance = instanceOptions.get(selectedInstanceIndex);
        TrafficPackageOption option = trafficPackages.get(selectedTrafficPackageIndex);
        showTrafficConfirmDialog(instance, option);
    }

    private void showTrafficConfirmDialog(InstanceOption instance, TrafficPackageOption option) {
        if (!isViewAlive()) return;
        StringBuilder message = new StringBuilder()
                .append("将为以下实例购买流量包：\n\n")
                .append(instance.getName())
                .append("（ID: ")
                .append(instance.getId())
                .append("）\n");

        Long remainBytes = instanceRemainBytes.get(instance.getId());
        if (remainBytes != null && remainBytes >= 0) {
            message.append("当前剩余：")
                    .append(formatTrafficBytes(remainBytes))
                    .append("\n");
        }

        message.append("流量包：")
                .append(option.getTrafficLabel())
                .append("\n")
                .append("消耗积分：")
                .append(option.getPointCost())
                .append("\n\n")
                .append("确认继续吗？");

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认购买流量")
                .setMessage(message.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("确认购买", (dialogInterface, which) -> executeBuyTrafficPackage(instance, option))
                .create();
        trafficConfirmDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (trafficConfirmDialog == dialog) {
                trafficConfirmDialog = null;
            }
        });
        dialog.show();
    }

    private void executeBuyTrafficPackage(InstanceOption instance, TrafficPackageOption option) {
        if (!isViewAlive()) return;
        binding.btnBuyTraffic.setEnabled(false);
        binding.btnBuyTraffic.setText("请求中...");

        String token = requireActivity()
                .getSharedPreferences("token", Context.MODE_PRIVATE)
                .getString("token", "");

        payApi.buyTrafficPackage(token, instance.getId(), option.getTraffic(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewAlive()) return;
                showToast(data.optString("msg", "购买流量成功"));
                InstanceDetailStore.getInstance().clear(instance.getId());
                fetchData();
                binding.btnBuyTraffic.setEnabled(true);
                updateTrafficBuyButtonText();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (!isViewAlive()) return;
                showToast(errorMsg);
                binding.btnBuyTraffic.setEnabled(true);
                updateTrafficBuyButtonText();
            }
        });
    }

    private void copyToClipboard(String text) {
        if (getContext() == null) return;
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Pay URL", text));
        }
    }

    private void showToast(String msg) {
        if (!isAdded() || getContext() == null) return;
        DialogUtils.showMessageDialog(getContext(), "提示", msg);
    }

    private void initHiddenPayWebView() {
        if (getContext() == null) return;
        hiddenPayWebView = new WebView(getContext());
        hiddenPayWebView.getSettings().setJavaScriptEnabled(true);
        hiddenPayWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handlePayUrl(view, request != null ? request.getUrl().toString() : null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handlePayUrl(view, url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                if (isPayOkUrl(url)) {
                    handlePaySuccess();
                }
                super.onLoadResource(view, url);
            }
        });
    }

    private boolean handlePayUrl(WebView view, @Nullable String url) {
        if (url == null) return false;
        if (isPayOkUrl(url)) {
            handlePaySuccess();
            return true;
        }
        if (url.startsWith("http") || url.startsWith("https")) {
            android.app.Activity activity = getActivity();
            if (activity == null) {
                view.loadUrl(url);
                return true;
            }
            new Thread(() -> {
                final PayTask task = new PayTask(activity);
                boolean isIntercepted = task.payInterceptorWithUrl(url, true, result -> {
                    String returnUrl = result != null ? result.getReturnUrl() : null;
                    if (!TextUtils.isEmpty(returnUrl)) {
                        mainHandler.post(() -> {
                            if (!isViewAlive() || hiddenPayWebView != view) return;
                            if (isPayOkUrl(returnUrl)) {
                                handlePaySuccess();
                            } else {
                                view.loadUrl(returnUrl);
                            }
                        });
                    }
                });
                if (!isIntercepted) {
                    mainHandler.post(() -> {
                        if (isViewAlive() && hiddenPayWebView == view) {
                            view.loadUrl(url);
                        }
                    });
                }
            }, "AliPayInterceptor").start();
            return true;
        }
        try {
            if (isAdded()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean isPayOkUrl(@Nullable String url) {
        return url != null && (url.equals(PAY_OK_URL) || url.startsWith(PAY_OK_URL + "?"));
    }

    private void handlePaySuccess() {
        if (paySuccessHandled) return;
        paySuccessHandled = true;
        mainHandler.post(() -> {
            if (!isViewAlive()) return;
            fetchData();
            showToast("支付成功");
        });
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        if (publicRechargeNoticeDialog != null) {
            publicRechargeNoticeDialog.dismiss();
            publicRechargeNoticeDialog = null;
        }
        if (trafficConfirmDialog != null) {
            trafficConfirmDialog.dismiss();
            trafficConfirmDialog = null;
        }
        if (hiddenPayWebView != null) {
            hiddenPayWebView.setWebViewClient(null);
            hiddenPayWebView.destroy();
            hiddenPayWebView = null;
        }
        if (binding != null) {
            binding.recyclerTiers.setAdapter(null);
            binding.recyclerModes.setAdapter(null);
            binding.recyclerTrafficPackages.setAdapter(null);
            binding.recyclerCardTypes.setAdapter(null);
            binding.recyclerCardTiers.setAdapter(null);
            binding.recyclerCardModes.setAdapter(null);
            binding.spinnerTrafficInstance.setOnItemSelectedListener(null);
            binding.spinnerTrafficInstance.setAdapter(null);
            binding = null;
        }
        tierAdapter = null;
        modeAdapter = null;
        trafficPackageAdapter = null;
        benefitCardTypeAdapter = null;
        benefitCardPlanAdapter = null;
        cardModeAdapter = null;
        trafficInstanceAdapter = null;
        super.onDestroyView();
    }

    private void refreshPointTierRows() {
        if (tierAdapter != null) {
            tierAdapter.setData(buildPointTierRows(), selectedTierIndex);
        }
    }

    private void refreshPointModeRows() {
        if (modeAdapter != null) {
            modeAdapter.setData(buildModeRows(), selectedModeIndex);
        }
    }

    private void refreshTrafficPackageRows() {
        if (trafficPackageAdapter != null) {
            trafficPackageAdapter.setData(buildTrafficPackageRows(), selectedTrafficPackageIndex);
        }
    }

    private void refreshBenefitCardTypeRows() {
        if (benefitCardTypeAdapter != null) {
            benefitCardTypeAdapter.setData(buildBenefitCardTypeRows(), selectedBenefitCardTypeIndex);
        }
    }

    private void refreshBenefitCardPlanRows() {
        if (benefitCardPlanAdapter != null) {
            benefitCardPlanAdapter.setData(buildBenefitCardPlanRows(), selectedBenefitCardPlanIndex);
        }
    }

    private void refreshCardModeRows() {
        if (cardModeAdapter != null) {
            cardModeAdapter.setData(buildModeRows(), selectedCardModeIndex);
        }
    }

    private List<RechargeOptionRow> buildPointTierRows() {
        List<RechargeOptionRow> rows = new ArrayList<>();
        RechargeMode mode = modes.size() > selectedModeIndex ? modes.get(selectedModeIndex) : null;
        String modeId = mode != null ? mode.getId() : "";
        for (RechargeTier tier : tiers) {
            rows.add(new RechargeOptionRow(tier.getPoint() + "积分", tier.getPrice(modeId) + "元"));
        }
        return rows;
    }

    private List<RechargeOptionRow> buildModeRows() {
        List<RechargeOptionRow> rows = new ArrayList<>();
        for (RechargeMode mode : modes) {
            rows.add(new RechargeOptionRow(mode.getName(), mode.getRule()));
        }
        return rows;
    }

    private List<RechargeOptionRow> buildTrafficPackageRows() {
        List<RechargeOptionRow> rows = new ArrayList<>();
        for (TrafficPackageOption option : trafficPackages) {
            rows.add(new RechargeOptionRow(option.getTrafficLabel(), option.getPointCostLabel()));
        }
        return rows;
    }

    private List<RechargeOptionRow> buildBenefitCardTypeRows() {
        List<RechargeOptionRow> rows = new ArrayList<>();
        for (BenefitCardTypeOption option : benefitCardTypes) {
            rows.add(new RechargeOptionRow(option.getName(), option.getDescription()));
        }
        return rows;
    }

    private List<RechargeOptionRow> buildBenefitCardPlanRows() {
        List<RechargeOptionRow> rows = new ArrayList<>();
        RechargeMode mode = modes.size() > selectedCardModeIndex ? modes.get(selectedCardModeIndex) : null;
        String modeId = mode != null ? mode.getId() : "";
        for (BenefitCardPlan plan : visibleBenefitCardPlans) {
            rows.add(new RechargeOptionRow(plan.getDaysLabel(), plan.getPrice(modeId) + "元"));
        }
        return rows;
    }


    private static class InstanceOption {
        private final int id;
        private final String name;

        InstanceOption(int id, String name) {
            this.id = id;
            this.name = name;
        }

        int getId() {
            return id;
        }

        String getName() {
            return name;
        }

        String getDisplayLabel() {
            return name + "（ID: " + id + "）";
        }
    }
}
