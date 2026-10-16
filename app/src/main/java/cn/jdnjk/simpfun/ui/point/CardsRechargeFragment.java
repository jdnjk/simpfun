package cn.jdnjk.simpfun.ui.point;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.OptionCardAdapter;
import cn.jdnjk.simpfun.databinding.FragmentRechargeCardsBinding;
import cn.jdnjk.simpfun.databinding.ItemCardStatusBinding;
import cn.jdnjk.simpfun.model.BenefitCardPlan;
import cn.jdnjk.simpfun.model.OptionCard;
import cn.jdnjk.simpfun.model.RechargeMeta;
import cn.jdnjk.simpfun.model.RechargeMode;

/**
 * 充值 · 签到卡：顶部双卡状态，依次选卡类型、时长、充值方式与支付渠道。
 */
public class CardsRechargeFragment extends BaseRechargeFragment {

    private static final String CARD_DOUBLE = "double_sign";
    private static final String CARD_AUTO = "auto_sign";
    private static final String[] CARD_IDS = {CARD_DOUBLE, CARD_AUTO};
    private static final String[] CARD_NAMES = {"双倍积分卡", "自动签到卡"};
    private static final String[] CARD_DESCS = {"每日签到积分翻倍", "自动执行每日签到"};
    private static final String[] CARD_SUBTITLES = {"每日签到积分 ×2", "自动执行每日签到"};

    private FragmentRechargeCardsBinding binding;
    private ItemCardStatusBinding statusDouble;
    private ItemCardStatusBinding statusAuto;    private OptionCardAdapter typeAdapter;
    private OptionCardAdapter planAdapter;
    private OptionCardAdapter modeAdapter;
    private int selectedTypeIndex = 0;
    private int selectedPlanIndex = 0;
    private int selectedModeIndex = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRechargeCardsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        statusDouble = binding.statusDouble;
        statusAuto = binding.statusAuto;
        typeAdapter = new OptionCardAdapter(this::onTypeClick);
        typeAdapter.setCardHeightDp(80);
        binding.recyclerCardTypes.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerCardTypes.setAdapter(typeAdapter);

        planAdapter = new OptionCardAdapter(this::onPlanClick);
        planAdapter.setCardHeightDp(80);
        binding.recyclerCardPlans.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerCardPlans.setAdapter(planAdapter);

        modeAdapter = new OptionCardAdapter(this::onModeClick);
        modeAdapter.setCardHeightDp(96);
        binding.recyclerCardModes.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerCardModes.setAdapter(modeAdapter);

        binding.btnPayCard.setOnClickListener(v -> onPayClick());

        RechargeHomeFragment parent = home();
        if (parent != null) parent.whenMetaReady(this);
    }

    private void onTypeClick(int position) {
        selectedTypeIndex = position;
        selectedPlanIndex = 0;
        submitTypes();
        submitPlans();
        updatePayButton();
    }

    private void onPlanClick(int position) {
        selectedPlanIndex = position;
        submitPlans();
        updatePayButton();
    }

    private void onModeClick(int position) {
        selectedModeIndex = position;
        submitModes();
        submitPlans();
        updatePayButton();
    }

    private void onPayClick() {
        BenefitCardPlan plan = currentPlan();
        RechargeMode mode = currentMode();
        if (plan == null || mode == null) return;
        if (RechargeMode.MODE_PUBLIC.equals(mode.getId())) {
            showPublicRechargeNotice(() -> createOrderFlow(binding.btnPayCard, plan.getItemId(),
                    plan.getDays(), mode.getId(), this::updatePayButton));
        } else {
            createOrderFlow(binding.btnPayCard, plan.getItemId(), plan.getDays(),
                    mode.getId(), this::updatePayButton);
        }
    }

    @Override
    public void onMetaLoaded(RechargeMeta meta) {
        this.meta = meta;
        if (!isViewAlive()) return;

        renderStatus(statusDouble, R.drawable.ic_bolt, CARD_NAMES[0], CARD_DESCS[0],
                meta.doubleSignValid, meta.doubleSignExpireSeconds);
        renderStatus(statusAuto, R.drawable.ic_task_alt, CARD_NAMES[1], CARD_DESCS[1],
                meta.autoSignValid, meta.autoSignExpireSeconds);

        selectedTypeIndex = 0;
        selectedPlanIndex = 0;
        selectedModeIndex = meta.defaultModeIndex();

        setupChannelDropdown(binding.inputChannel, binding.spinnerChannel);
        submitTypes();
        submitPlans();
        submitModes();
        updatePayButton();
    }

    private void renderStatus(ItemCardStatusBinding status, int iconRes, String name,
                              String desc, boolean valid, long expireSeconds) {
        if (!isViewAlive()) return;
        status.ivCardIcon.setImageResource(iconRes);
        status.tvCardName.setText(name);
        if (valid && expireSeconds > 0) {
            String date = new SimpleDateFormat("M月d日", Locale.getDefault())
                    .format(new Date(expireSeconds * 1000L));
            status.tvCardStatus.setText("已生效 · " + date + "到期");
        } else {
            status.tvCardStatus.setText("未生效 · " + desc);
        }
    }

    private BenefitCardPlan currentPlan() {
        List<BenefitCardPlan> plans = plansForSelectedType();
        if (plans.isEmpty() || selectedPlanIndex < 0 || selectedPlanIndex >= plans.size()) return null;
        return plans.get(selectedPlanIndex);
    }

    private RechargeMode currentMode() {
        if (meta == null || meta.modes.isEmpty()
                || selectedModeIndex < 0 || selectedModeIndex >= meta.modes.size()) return null;
        return meta.modes.get(selectedModeIndex);
    }

    private List<BenefitCardPlan> plansForSelectedType() {
        if (meta == null) return new ArrayList<>();
        List<BenefitCardPlan> plans = meta.cardPlans.get(CARD_IDS[selectedTypeIndex]);
        return plans != null ? plans : new ArrayList<>();
    }

    private void submitTypes() {
        List<OptionCard> cards = new ArrayList<>();
        for (int i = 0; i < CARD_IDS.length; i++) {
            cards.add(OptionCard.of(CARD_NAMES[i], CARD_SUBTITLES[i]));
        }
        typeAdapter.submit(cards, selectedTypeIndex);
    }

    private void submitPlans() {
        List<OptionCard> cards = new ArrayList<>();
        String modeId = currentMode() != null ? currentMode().getId() : "";
        for (BenefitCardPlan plan : plansForSelectedType()) {
            cards.add(OptionCard.spanned(plan.getDays() + " 天", buildPlanPriceSpan(plan, modeId)));
        }
        planAdapter.submit(cards, selectedPlanIndex);
    }

    private void submitModes() {
        List<OptionCard> cards = new ArrayList<>();
        if (meta != null) {
            for (RechargeMode mode : meta.modes) {
                cards.add(OptionCard.of(mode.getName(), mode.getRule()));
            }
        }
        modeAdapter.submit(cards, selectedModeIndex);
    }

    private void updatePayButton() {
        if (!isViewAlive()) return;
        BenefitCardPlan plan = currentPlan();
        RechargeMode mode = currentMode();
        String price = plan != null && mode != null ? plan.getPrice(mode.getId()) : "0";
        binding.btnPayCard.setText("立即支付 ¥" + price);
        binding.btnPayCard.setEnabled(plan != null && mode != null && selectedChannel() != null);
    }

    /** "公益 ¥9 / 普通 ¥18"，当前充值方式的价格加粗高亮。 */
    private CharSequence buildPlanPriceSpan(BenefitCardPlan plan, String modeId) {
        boolean publicSelected = RechargeMode.MODE_PUBLIC.equals(modeId);
        String publicLine = "公益 ¥" + plan.getPublicMoney();
        String normalLine = "普通 ¥" + plan.getNormalMoney();
        String full = publicLine + "\n" + normalLine;
        SpannableString span = new SpannableString(full);

        int start = publicSelected ? 0 : publicLine.length() + 1;
        int end = publicSelected ? publicLine.length() : full.length();

        span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(requireContext().getColor(R.color.md_theme_onSurface)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        statusDouble = null;
        statusAuto = null;
        typeAdapter = null;
        planAdapter = null;
        modeAdapter = null;
        super.onDestroyView();
    }
}
