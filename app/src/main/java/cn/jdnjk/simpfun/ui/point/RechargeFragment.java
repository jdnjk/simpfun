package cn.jdnjk.simpfun.ui.point;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
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
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alipay.sdk.app.PayTask;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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
import java.util.Objects;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.MainApi;
import cn.jdnjk.simpfun.api.PayApi;
import cn.jdnjk.simpfun.model.BenefitCardPlan;
import cn.jdnjk.simpfun.model.BenefitCardTypeOption;
import cn.jdnjk.simpfun.model.RechargeMode;
import cn.jdnjk.simpfun.model.RechargeTier;
import cn.jdnjk.simpfun.model.TrafficPackageOption;
import cn.jdnjk.simpfun.utils.DialogUtils;

public class RechargeFragment extends Fragment {

    private TextView tvUsername;
    private TextView tvUid;
    private TextView tvCurrentPoint;
    private TextView tvDoubleSignCard;
    private TextView tvAutoSignCard;
    private TabLayout tabCategory;
    private View layoutRechargePoints;
    private View layoutRechargeTraffic;
    private View layoutRechargeCards;
    private TextView tvUnderConstruction;
    private RecyclerView recyclerTiers;
    private RecyclerView recyclerModes;
    private RecyclerView recyclerTrafficPackages;
    private RecyclerView recyclerCardTypes;
    private RecyclerView recyclerCardTiers;
    private RecyclerView recyclerCardModes;
    private Spinner spinnerTrafficInstance;
    private TextView tvTrafficInstanceSummary;
    private RadioButton radioWechat;
    private RadioButton radioAlipay;
    private RadioButton radioWechatCard;
    private RadioButton radioAlipayCard;
    private MaterialButton btnPay;
    private MaterialButton btnBuyTraffic;
    private MaterialButton btnPayCard;
    private TextView tvWechatName;
    private TextView tvAlipayName;
    private TextView tvWechatNameCard;
    private TextView tvAlipayNameCard;

    private WebView hiddenPayWebView;
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

    private TierAdapter tierAdapter;
    private ModeAdapter modeAdapter;
    private TrafficPackageAdapter trafficPackageAdapter;
    private BenefitCardTypeAdapter benefitCardTypeAdapter;
    private BenefitCardPlanAdapter benefitCardPlanAdapter;
    private CardModeAdapter cardModeAdapter;
    private ArrayAdapter<String> trafficInstanceAdapter;

    private int selectedTierIndex = 0;
    private int selectedModeIndex = 0;
    private int selectedTrafficPackageIndex = 0;
    private int selectedInstanceIndex = -1;
    private int selectedBenefitCardTypeIndex = 0;
    private int selectedBenefitCardPlanIndex = 0;
    private int selectedCardModeIndex = 0;
    private String selectedPaymentMethod = "ali_pay_1";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recharge_layout, container, false);

        bindViews(view);
        initTrafficPackages();
        initBenefitCardTypes();
        setupTabs();
        setupRecyclers();
        setupTrafficSelector();
        setupListeners(view);
        initHiddenPayWebView();
        fetchData();

        return view;
    }

    private void bindViews(View view) {
        tvUsername = view.findViewById(R.id.tv_username);
        tvUid = view.findViewById(R.id.tv_uid);
        tvCurrentPoint = view.findViewById(R.id.tv_current_point);
        tvDoubleSignCard = view.findViewById(R.id.tv_double_sign_card);
        tvAutoSignCard = view.findViewById(R.id.tv_auto_sign_card);
        tabCategory = view.findViewById(R.id.tab_category);
        layoutRechargePoints = view.findViewById(R.id.layout_recharge_points);
        layoutRechargeTraffic = view.findViewById(R.id.layout_recharge_traffic);
        layoutRechargeCards = view.findViewById(R.id.layout_recharge_cards);
        tvUnderConstruction = view.findViewById(R.id.tv_under_construction);
        recyclerTiers = view.findViewById(R.id.recycler_tiers);
        recyclerModes = view.findViewById(R.id.recycler_modes);
        recyclerTrafficPackages = view.findViewById(R.id.recycler_traffic_packages);
        recyclerCardTypes = view.findViewById(R.id.recycler_card_types);
        recyclerCardTiers = view.findViewById(R.id.recycler_card_tiers);
        recyclerCardModes = view.findViewById(R.id.recycler_card_modes);
        spinnerTrafficInstance = view.findViewById(R.id.spinner_traffic_instance);
        tvTrafficInstanceSummary = view.findViewById(R.id.tv_traffic_instance_summary);
        radioWechat = view.findViewById(R.id.radio_wechat);
        radioAlipay = view.findViewById(R.id.radio_alipay);
        radioWechatCard = view.findViewById(R.id.radio_wechat_card);
        radioAlipayCard = view.findViewById(R.id.radio_alipay_card);
        btnPay = view.findViewById(R.id.btn_pay);
        btnBuyTraffic = view.findViewById(R.id.btn_buy_traffic);
        btnPayCard = view.findViewById(R.id.btn_pay_card);
        tvWechatName = view.findViewById(R.id.tv_wechat_name);
        tvAlipayName = view.findViewById(R.id.tv_alipay_name);
        tvWechatNameCard = view.findViewById(R.id.tv_wechat_name_card);
        tvAlipayNameCard = view.findViewById(R.id.tv_alipay_name_card);
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
        tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                layoutRechargePoints.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                layoutRechargeTraffic.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutRechargeCards.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                tvUnderConstruction.setVisibility(View.GONE);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclers() {
        recyclerTiers.setLayoutManager(new GridLayoutManager(getContext(), 2));
        tierAdapter = new TierAdapter();
        recyclerTiers.setAdapter(tierAdapter);

        recyclerModes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        modeAdapter = new ModeAdapter();
        recyclerModes.setAdapter(modeAdapter);

        recyclerTrafficPackages.setLayoutManager(new GridLayoutManager(getContext(), 2));
        trafficPackageAdapter = new TrafficPackageAdapter();
        recyclerTrafficPackages.setAdapter(trafficPackageAdapter);

        recyclerCardTypes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        benefitCardTypeAdapter = new BenefitCardTypeAdapter();
        recyclerCardTypes.setAdapter(benefitCardTypeAdapter);

        recyclerCardTiers.setLayoutManager(new GridLayoutManager(getContext(), 2));
        benefitCardPlanAdapter = new BenefitCardPlanAdapter();
        recyclerCardTiers.setAdapter(benefitCardPlanAdapter);

        recyclerCardModes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        cardModeAdapter = new CardModeAdapter();
        recyclerCardModes.setAdapter(cardModeAdapter);
    }

    private void setupTrafficSelector() {
        if (getContext() == null) return;
        trafficInstanceAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        trafficInstanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTrafficInstance.setAdapter(trafficInstanceAdapter);
        spinnerTrafficInstance.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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

    private void setupListeners(View view) {
        view.findViewById(R.id.btn_wechat_pay).setOnClickListener(v -> selectPaymentMethod("wx_pay_2"));
        view.findViewById(R.id.btn_alipay).setOnClickListener(v -> selectPaymentMethod("ali_pay_1"));
        view.findViewById(R.id.btn_wechat_pay_card).setOnClickListener(v -> selectPaymentMethod("wx_pay_2"));
        view.findViewById(R.id.btn_alipay_card).setOnClickListener(v -> selectPaymentMethod("ali_pay_1"));
        btnPay.setOnClickListener(v -> createPointOrder());
        btnBuyTraffic.setOnClickListener(v -> buyTrafficPackage());
        btnPayCard.setOnClickListener(v -> createBenefitCardOrder());
    }

    private void selectPaymentMethod(String method) {
        selectedPaymentMethod = method;
        updatePaymentSelection();
    }

    private void updatePaymentSelection() {
        boolean alipay = "ali_pay_1".equals(selectedPaymentMethod);
        boolean wechat = "wx_pay_2".equals(selectedPaymentMethod);
        radioAlipay.setChecked(alipay);
        radioWechat.setChecked(wechat);
        radioAlipayCard.setChecked(alipay);
        radioWechatCard.setChecked(wechat);
    }

    private void fetchData() {
        if (getActivity() == null) return;
        SharedPreferences sp = getActivity().getSharedPreferences("token", Context.MODE_PRIVATE);
        String token = sp.getString("token", "");
        if (token.isEmpty()) return;

        payApi.getRechargeMeta(token, new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
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
        if (getContext() == null) return;
        new MainApi(requireContext()).getInstanceList(token, new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                updateInstances(data.optJSONArray("list"));
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("RechargeFragment", "fetchInstances failed: " + errorMsg);
                updateInstances(null);
                tvTrafficInstanceSummary.setText("实例列表加载失败");
            }
        });
    }

    private void updateRechargeMetaUI(JSONObject data) {
        try {
            JSONObject user = data.optJSONObject("user");
            if (user != null) {
                tvUid.setText("UID: " + user.optInt("uid"));
                tvCurrentPoint.setText("当前积分: " + user.optInt("point"));

                if (getContext() != null) {
                    SharedPreferences userInfoSp = getContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                    tvUsername.setText(userInfoSp.getString("username", "用户"));
                }

                bindCardStatus(tvDoubleSignCard,
                        user.optLong("double_sign_card_valid_time", 0),
                        user.optBoolean("double_sign_card_is_valid", false),
                        "双倍积分卡");
                bindCardStatus(tvAutoSignCard,
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
        if (channels == null) return;
        for (int i = 0; i < channels.length(); i++) {
            JSONObject c = channels.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id");
            String name = c.optString("name");
            if ("wx_pay_2".equals(id)) {
                tvWechatName.setText(name);
                tvWechatNameCard.setText(name);
            }
            if ("ali_pay_1".equals(id)) {
                tvAlipayName.setText(name);
                tvAlipayNameCard.setText(name);
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
        tierAdapter.notifyDataSetChanged();
    }

    private void parseRechargeModes(JSONObject data) {
        JSONObject rechargeModes = data.optJSONObject("recharge_modes");
        modes.clear();
        if (rechargeModes != null) {
            JSONObject pub = rechargeModes.optJSONObject("public_recharge");
            if (pub != null) {
                modes.add(new RechargeMode("public", pub.optString("name"), pub.optString("rule")));
            }
            JSONObject pro = rechargeModes.optJSONObject("pro_recharge");
            if (pro != null) {
                modes.add(new RechargeMode("normal", pro.optString("name"), pro.optString("rule")));
            }
        }
        if (!modes.isEmpty()) {
            int defaultIndex = modes.size() > 1 ? 1 : 0;
            selectedModeIndex = defaultIndex;
            selectedCardModeIndex = defaultIndex;
        }
        modeAdapter.notifyDataSetChanged();
        cardModeAdapter.notifyDataSetChanged();
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
        benefitCardPlanAdapter.notifyDataSetChanged();
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
        if (trafficInstanceAdapter == null) return;
        trafficInstanceAdapter.clear();
        for (InstanceOption option : instanceOptions) {
            trafficInstanceAdapter.add(option.getDisplayLabel());
        }
        trafficInstanceAdapter.notifyDataSetChanged();
        spinnerTrafficInstance.setEnabled(!instanceOptions.isEmpty());
        if (!instanceOptions.isEmpty() && selectedInstanceIndex >= 0 && selectedInstanceIndex < instanceOptions.size()) {
            spinnerTrafficInstance.setSelection(selectedInstanceIndex, false);
        }
    }

    private void updateTrafficInstanceSummary() {
        if (instanceOptions.isEmpty() || selectedInstanceIndex < 0 || selectedInstanceIndex >= instanceOptions.size()) {
            tvTrafficInstanceSummary.setText("暂无可选实例，请先创建服务器实例");
            return;
        }
        InstanceOption option = instanceOptions.get(selectedInstanceIndex);
        Long remainBytes = instanceRemainBytes.get(option.getId());
        Boolean loading = instanceDetailLoading.get(option.getId());
        if (Boolean.TRUE.equals(loading)) {
            tvTrafficInstanceSummary.setText("已选择实例：" + option.getName() + "（ID: " + option.getId() + "） · 正在获取剩余流量...");
            return;
        }
        if (remainBytes != null && remainBytes >= 0) {
            tvTrafficInstanceSummary.setText("已选择实例：" + option.getName() + "（ID: " + option.getId() + "） · 剩余流量 " + formatTrafficBytes(remainBytes));
            return;
        }
        tvTrafficInstanceSummary.setText("已选择实例：" + option.getName() + "（ID: " + option.getId() + "）");
    }

    private void requestSelectedInstanceDetail() {
        if (getActivity() == null || selectedInstanceIndex < 0 || selectedInstanceIndex >= instanceOptions.size()) {
            return;
        }
        String token = getActivity().getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) {
            return;
        }
        InstanceOption option = instanceOptions.get(selectedInstanceIndex);
        if (instanceRemainBytes.containsKey(option.getId()) || Boolean.TRUE.equals(instanceDetailLoading.get(option.getId()))) {
            updateTrafficInstanceSummary();
            return;
        }
        instanceDetailLoading.put(option.getId(), true);
        updateTrafficInstanceSummary();
        new MainApi(requireContext()).getInstanceDetail(token, String.valueOf(option.getId()), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                instanceDetailLoading.remove(option.getId());
                JSONObject detail = data.optJSONObject("data");
                JSONObject traffic = detail != null ? detail.optJSONObject("traffic") : null;
                if (traffic != null) {
                    instanceRemainBytes.put(option.getId(), traffic.optLong("remain_bytes", -1));
                }
                updateTrafficInstanceSummary();
            }

            @Override
            public void onFailure(String errorMsg) {
                instanceDetailLoading.remove(option.getId());
                Log.e("RechargeFragment", "getInstanceDetail failed: " + errorMsg);
                updateTrafficInstanceSummary();
            }
        });
    }

    private String formatTrafficBytes(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        double gb = bytes / 1024d / 1024d / 1024d;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    private void updatePointPayButtonText() {
        if (tiers.isEmpty() || selectedTierIndex >= tiers.size()) {
            btnPay.setText("立即支付 0元");
            return;
        }
        RechargeTier tier = tiers.get(selectedTierIndex);
        RechargeMode mode = modes.size() > selectedModeIndex ? modes.get(selectedModeIndex) : null;
        btnPay.setText("立即支付 " + tier.getPrice(mode != null ? mode.getId() : "") + "元");
    }

    private void updateTrafficBuyButtonText() {
        if (trafficPackages.isEmpty() || selectedTrafficPackageIndex >= trafficPackages.size()) {
            btnBuyTraffic.setText("立即购买 0积分");
            btnBuyTraffic.setEnabled(false);
            return;
        }
        TrafficPackageOption option = trafficPackages.get(selectedTrafficPackageIndex);
        btnBuyTraffic.setText("立即购买 " + option.getPointCost() + "积分");
        btnBuyTraffic.setEnabled(!instanceOptions.isEmpty() && selectedInstanceIndex >= 0);
    }

    private void updateCardPayButtonText() {
        if (visibleBenefitCardPlans.isEmpty() || selectedBenefitCardPlanIndex >= visibleBenefitCardPlans.size()) {
            btnPayCard.setText("立即支付 0元");
            return;
        }
        BenefitCardPlan plan = visibleBenefitCardPlans.get(selectedBenefitCardPlanIndex);
        RechargeMode mode = modes.size() > selectedCardModeIndex ? modes.get(selectedCardModeIndex) : null;
        btnPayCard.setText("立即支付 " + plan.getPrice(mode != null ? mode.getId() : "") + "元");
    }

    private void createPointOrder() {
        if (getActivity() == null || tiers.isEmpty() || modes.isEmpty()) return;

        RechargeMode mode = modes.get(selectedModeIndex);
        Runnable action = this::executePointOrder;
        if ("public".equals(mode.getId())) {
            showPublicRechargeNotice(action);
        } else {
            action.run();
        }
    }

    private void createBenefitCardOrder() {
        if (getActivity() == null || visibleBenefitCardPlans.isEmpty() || modes.isEmpty()) return;

        RechargeMode mode = modes.get(selectedCardModeIndex);
        Runnable action = this::executeBenefitCardOrder;
        if ("public".equals(mode.getId())) {
            showPublicRechargeNotice(action);
        } else {
            action.run();
        }
    }

    private void showPublicRechargeNotice(Runnable onConfirm) {
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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("充值前须知")
                .setMessage(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY))
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("已阅并继续 (15s)", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        final int[] countdown = {15};
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    countdown[0]--;
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
        mainHandler.postDelayed(runnable, 1000);
    }

    private void executePointOrder() {
        btnPay.setEnabled(false);
        btnPay.setText("请求中...");

        RechargeTier tier = tiers.get(selectedTierIndex);
        RechargeMode mode = modes.get(selectedModeIndex);
        String token = Objects.requireNonNull(getActivity())
                .getSharedPreferences("token", Context.MODE_PRIVATE)
                .getString("token", "");

        payApi.createOrder(token, "point", tier.getPoint(), selectedPaymentMethod, mode.getId(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                handlePayOrderResponse(json, btnPay, RechargeFragment.this::updatePointPayButtonText);
            }

            @Override
            public void onFailure(String errorMsg) {
                showToast("请求失败: " + errorMsg);
                btnPay.setEnabled(true);
                updatePointPayButtonText();
            }
        });
    }

    private void executeBenefitCardOrder() {
        btnPayCard.setEnabled(false);
        btnPayCard.setText("请求中...");

        BenefitCardPlan plan = visibleBenefitCardPlans.get(selectedBenefitCardPlanIndex);
        RechargeMode mode = modes.get(selectedCardModeIndex);
        String token = Objects.requireNonNull(getActivity())
                .getSharedPreferences("token", Context.MODE_PRIVATE)
                .getString("token", "");

        payApi.createOrder(token, plan.getItemId(), plan.getDays(), selectedPaymentMethod, mode.getId(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                handlePayOrderResponse(json, btnPayCard, RechargeFragment.this::updateCardPayButtonText);
            }

            @Override
            public void onFailure(String errorMsg) {
                showToast("请求失败: " + errorMsg);
                btnPayCard.setEnabled(true);
                updateCardPayButtonText();
            }
        });
    }

    private void handlePayOrderResponse(JSONObject json, MaterialButton button, Runnable resetAction) {
        if (json.optInt("code") == 200) {
            String url = json.optString("url");
            if ("ali_pay_1".equals(selectedPaymentMethod)) {
                if (hiddenPayWebView != null) hiddenPayWebView.loadUrl(url);
            } else {
                copyToClipboard(url);
                showToast("已复制扫码链接，请在微信中打开并粘在任意聊天框点击支付");
            }
        } else {
            showToast("下单失败: " + json.optString("msg"));
        }
        button.setEnabled(true);
        resetAction.run();
    }

    private void buyTrafficPackage() {
        if (getActivity() == null || instanceOptions.isEmpty() || selectedInstanceIndex < 0 || selectedInstanceIndex >= instanceOptions.size()) {
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

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认购买流量")
                .setMessage(message.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("确认购买", (dialog, which) -> executeBuyTrafficPackage(instance, option))
                .show();
    }

    private void executeBuyTrafficPackage(InstanceOption instance, TrafficPackageOption option) {
        btnBuyTraffic.setEnabled(false);
        btnBuyTraffic.setText("请求中...");

        String token = Objects.requireNonNull(getActivity())
                .getSharedPreferences("token", Context.MODE_PRIVATE)
                .getString("token", "");

        payApi.buyTrafficPackage(token, instance.getId(), option.getTraffic(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                showToast(data.optString("msg", "购买流量成功"));
                fetchData();
                btnBuyTraffic.setEnabled(true);
                updateTrafficBuyButtonText();
            }

            @Override
            public void onFailure(String errorMsg) {
                showToast(errorMsg);
                btnBuyTraffic.setEnabled(true);
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
        if (getContext() == null) return;
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
        });
    }

        private boolean handlePayUrl(WebView view, @Nullable String url) {
            if (url == null) return false;
            if (url.startsWith("http") || url.startsWith("https")) {
                if (getActivity() == null) {
                    view.loadUrl(url);
                    return true;
                }
                final PayTask task = new PayTask(getActivity());
                boolean isIntercepted = task.payInterceptorWithUrl(url, true, result -> {
                    String returnUrl = result.getReturnUrl();
                    if (!TextUtils.isEmpty(returnUrl)) {
                        mainHandler.post(() -> view.loadUrl(returnUrl));
                    }
                });
                if (!isIntercepted) view.loadUrl(url);
                return true;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public void onDestroy() {
            if (hiddenPayWebView != null) {
                hiddenPayWebView.destroy();
                hiddenPayWebView = null;
            }
            super.onDestroy();
        }

        private int getPrimaryColor() {
            return ContextCompat.getColor(requireContext(), R.color.md_theme_primary);
        }

        private class TierAdapter extends RecyclerView.Adapter<TierAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_tier, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                RechargeTier tier = tiers.get(position);
                holder.tvPoints.setText(tier.getPoint() + "积分");
                RechargeMode mode = modes.size() > selectedModeIndex ? modes.get(selectedModeIndex) : null;
                holder.tvMoney.setText(tier.getPrice(mode != null ? mode.getId() : "") + "元");
                boolean selected = selectedTierIndex == position;
                holder.card.setStrokeColor(selected ? getPrimaryColor() : Color.TRANSPARENT);
                holder.card.setStrokeWidth(selected ? 4 : 0);
                holder.card.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    selectedTierIndex = adapterPosition;
                    notifyDataSetChanged();
                    updatePointPayButtonText();
                });
            }

            @Override
            public int getItemCount() {
                return tiers.size();
            }

            class VH extends RecyclerView.ViewHolder {
                final MaterialCardView card;
                final TextView tvPoints;
                final TextView tvMoney;

                VH(View v) {
                    super(v);
                    card = (MaterialCardView) v;
                    tvPoints = v.findViewById(R.id.tv_tier_points);
                    tvMoney = v.findViewById(R.id.tv_tier_money);
                }
            }
        }

        private class ModeAdapter extends RecyclerView.Adapter<ModeAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_mode, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                RechargeMode mode = modes.get(position);
                holder.tvName.setText(mode.getName());
                holder.tvRule.setText(mode.getRule());
                boolean selected = selectedModeIndex == position;
                holder.card.setStrokeColor(selected ? getPrimaryColor() : Color.TRANSPARENT);
                holder.card.setStrokeWidth(selected ? 4 : 0);
                holder.card.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    selectedModeIndex = adapterPosition;
                    notifyDataSetChanged();
                    tierAdapter.notifyDataSetChanged();
                    updatePointPayButtonText();
                });
            }

            @Override
            public int getItemCount() {
                return modes.size();
            }

            class VH extends RecyclerView.ViewHolder {
                final MaterialCardView card;
                final TextView tvName;
                final TextView tvRule;

                VH(View v) {
                    super(v);
                    card = (MaterialCardView) v;
                    tvName = v.findViewById(R.id.tv_mode_name);
                    tvRule = v.findViewById(R.id.tv_mode_rule);
                }
            }
        }

        private class TrafficPackageAdapter extends RecyclerView.Adapter<TrafficPackageAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_tier, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                TrafficPackageOption option = trafficPackages.get(position);
                holder.tvTitle.setText(option.getTrafficLabel());
                holder.tvSubtitle.setText(option.getPointCostLabel());
                boolean selected = selectedTrafficPackageIndex == position;
                holder.card.setStrokeColor(selected ? getPrimaryColor() : Color.TRANSPARENT);
                holder.card.setStrokeWidth(selected ? 4 : 0);
                holder.card.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    selectedTrafficPackageIndex = adapterPosition;
                    notifyDataSetChanged();
                    updateTrafficBuyButtonText();
                });
            }

            @Override
            public int getItemCount() {
                return trafficPackages.size();
            }

            class VH extends RecyclerView.ViewHolder {
                final MaterialCardView card;
                final TextView tvTitle;
                final TextView tvSubtitle;

                VH(View v) {
                    super(v);
                    card = (MaterialCardView) v;
                    tvTitle = v.findViewById(R.id.tv_tier_points);
                    tvSubtitle = v.findViewById(R.id.tv_tier_money);
                }
            }
        }

        private class BenefitCardTypeAdapter extends RecyclerView.Adapter<BenefitCardTypeAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_mode, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                BenefitCardTypeOption option = benefitCardTypes.get(position);
                holder.tvName.setText(option.getName());
                holder.tvRule.setText(option.getDescription());
                boolean selected = selectedBenefitCardTypeIndex == position;
                holder.card.setStrokeColor(selected ? getPrimaryColor() : Color.TRANSPARENT);
                holder.card.setStrokeWidth(selected ? 4 : 0);
                holder.card.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    selectedBenefitCardTypeIndex = adapterPosition;
                    selectedBenefitCardPlanIndex = 0;
                    notifyDataSetChanged();
                    refreshVisibleBenefitCardPlans();
                    updateCardPayButtonText();
                });
            }

            @Override
            public int getItemCount() {
                return benefitCardTypes.size();
            }

            class VH extends RecyclerView.ViewHolder {
                final MaterialCardView card;
                final TextView tvName;
                final TextView tvRule;

                VH(View v) {
                    super(v);
                    card = (MaterialCardView) v;
                    tvName = v.findViewById(R.id.tv_mode_name);
                    tvRule = v.findViewById(R.id.tv_mode_rule);
                }
            }
        }

        private class BenefitCardPlanAdapter extends RecyclerView.Adapter<BenefitCardPlanAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_tier, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                BenefitCardPlan plan = visibleBenefitCardPlans.get(position);
                RechargeMode mode = modes.size() > selectedCardModeIndex ? modes.get(selectedCardModeIndex) : null;
                holder.tvTitle.setText(plan.getDaysLabel());
                holder.tvSubtitle.setText(plan.getPrice(mode != null ? mode.getId() : "") + "元");
                boolean selected = selectedBenefitCardPlanIndex == position;
                holder.card.setStrokeColor(selected ? getPrimaryColor() : Color.TRANSPARENT);
                holder.card.setStrokeWidth(selected ? 4 : 0);
                holder.card.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    selectedBenefitCardPlanIndex = adapterPosition;
                    notifyDataSetChanged();
                    updateCardPayButtonText();
                });
            }

            @Override
            public int getItemCount() {
                return visibleBenefitCardPlans.size();
            }

            class VH extends RecyclerView.ViewHolder {
                final MaterialCardView card;
                final TextView tvTitle;
                final TextView tvSubtitle;

                VH(View v) {
                    super(v);
                    card = (MaterialCardView) v;
                    tvTitle = v.findViewById(R.id.tv_tier_points);
                    tvSubtitle = v.findViewById(R.id.tv_tier_money);
                }
            }
        }

        private class CardModeAdapter extends RecyclerView.Adapter<CardModeAdapter.VH> {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_mode, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                RechargeMode mode = modes.get(position);
                holder.tvName.setText(mode.getName());
                holder.tvRule.setText(mode.getRule());
                boolean selected = selectedCardModeIndex == position;
                holder.card.setStrokeColor(selected ? getPrimaryColor() : Color.TRANSPARENT);
                holder.card.setStrokeWidth(selected ? 4 : 0);
                holder.card.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    selectedCardModeIndex = adapterPosition;
                    notifyDataSetChanged();
                    benefitCardPlanAdapter.notifyDataSetChanged();
                    updateCardPayButtonText();
                });
            }

            @Override
            public int getItemCount() {
                return modes.size();
            }

            class VH extends RecyclerView.ViewHolder {
                final MaterialCardView card;
                final TextView tvName;
                final TextView tvRule;

                VH(View v) {
                    super(v);
                    card = (MaterialCardView) v;
                    tvName = v.findViewById(R.id.tv_mode_name);
                    tvRule = v.findViewById(R.id.tv_mode_rule);
                }
            }
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

