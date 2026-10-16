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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.OptionCardAdapter;
import cn.jdnjk.simpfun.databinding.FragmentRechargePointsBinding;
import cn.jdnjk.simpfun.model.OptionCard;
import cn.jdnjk.simpfun.model.RechargeMeta;
import cn.jdnjk.simpfun.model.RechargeMode;
import cn.jdnjk.simpfun.model.RechargeTier;

/**
 * 充值 · 充积分：档位 → 充值方式（公益需先读须知）→ 支付渠道 → 立即支付。
 */
public class PointsRechargeFragment extends BaseRechargeFragment {

    private FragmentRechargePointsBinding binding;
    private OptionCardAdapter tierAdapter;
    private OptionCardAdapter modeAdapter;
    private int selectedTierIndex = 0;
    private int selectedModeIndex = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRechargePointsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tierAdapter = new OptionCardAdapter(this::onTierClick);
        tierAdapter.setCardHeightDp(96);
        binding.recyclerTiers.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerTiers.setAdapter(tierAdapter);

        modeAdapter = new OptionCardAdapter(this::onModeClick);
        modeAdapter.setCardHeightDp(96);
        binding.recyclerModes.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerModes.setAdapter(modeAdapter);

        binding.btnNotice.setOnClickListener(v -> showPublicRechargeNotice(null));
        binding.btnPay.setOnClickListener(v -> onPayClick());

        RechargeHomeFragment parent = home();
        if (parent != null) parent.whenMetaReady(this);
    }

    private void onTierClick(int position) {
        selectedTierIndex = position;
        submitTiers();
        updatePayButton();
    }

    private void onModeClick(int position) {
        selectedModeIndex = position;
        submitModes();
        submitTiers();
        updatePayButton();
    }

    private void onPayClick() {
        RechargeTier tier = currentTier();
        RechargeMode mode = currentMode();
        if (tier == null || mode == null) return;
        if (RechargeMode.MODE_PUBLIC.equals(mode.getId())) {
            showPublicRechargeNotice(() -> createOrderFlow(binding.btnPay, "point",
                    tier.getPoint(), mode.getId(), this::updatePayButton));
        } else {
            createOrderFlow(binding.btnPay, "point", tier.getPoint(), mode.getId(), this::updatePayButton);
        }
    }

    @Override
    public void onMetaLoaded(RechargeMeta meta) {
        this.meta = meta;
        if (!isViewAlive()) return;

        binding.tvPointBalance.setText(String.format(Locale.getDefault(), "当前积分 %,d", meta.point));
        binding.tvPointHint.setText("提示：充值不会改变账户Pro状态，若您有变更为Pro的需求，请提前在小程序完成变更。");

        selectedTierIndex = 0;
        selectedModeIndex = meta.defaultModeIndex();

        setupChannelDropdown(binding.inputChannel, binding.spinnerChannel);
        submitTiers();
        submitModes();
        updatePayButton();
    }

    private RechargeTier currentTier() {
        if (meta == null || meta.pointTiers.isEmpty()
                || selectedTierIndex < 0 || selectedTierIndex >= meta.pointTiers.size()) {
            return null;
        }
        return meta.pointTiers.get(selectedTierIndex);
    }

    private RechargeMode currentMode() {
        if (meta == null || meta.modes.isEmpty()
                || selectedModeIndex < 0 || selectedModeIndex >= meta.modes.size()) {
            return null;
        }
        return meta.modes.get(selectedModeIndex);
    }

    private void submitTiers() {
        List<OptionCard> cards = new ArrayList<>();
        String modeId = currentMode() != null ? currentMode().getId() : "";
        if (meta != null) {
            for (RechargeTier tier : meta.pointTiers) {
                String title = String.format(Locale.getDefault(), "%,d 积分", tier.getPoint());
                cards.add(OptionCard.spanned(title, buildTierPriceSpan(tier, modeId)));
            }
        }
        tierAdapter.submit(cards, selectedTierIndex);
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
        RechargeTier tier = currentTier();
        RechargeMode mode = currentMode();
        String price = tier != null && mode != null ? tier.getPrice(mode.getId()) : "0";
        binding.btnPay.setText("立即支付 ¥" + price);
        binding.btnPay.setEnabled(tier != null && mode != null && selectedChannel() != null);
    }

    /** "公益 ¥6 / 普通 ¥12"，当前充值方式的价格加粗高亮。 */
    private CharSequence buildTierPriceSpan(RechargeTier tier, String modeId) {
        boolean normalSelected = RechargeMode.MODE_NORMAL.equals(modeId);
        String publicLine = "公益 ¥" + tier.getPublicMoney();
        String normalLine = "普通 ¥" + tier.getProMoney();
        String full = publicLine + "\n" + normalLine;
        SpannableString span = new SpannableString(full);

        int start = normalSelected ? publicLine.length() + 1 : 0;
        int end = normalSelected ? full.length() : publicLine.length();

        span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(requireContext().getColor(R.color.md_theme_onSurface)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        tierAdapter = null;
        modeAdapter = null;
        super.onDestroyView();
    }
}
