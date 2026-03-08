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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.*;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.PayApi;
import cn.jdnjk.simpfun.model.RechargeMode;
import cn.jdnjk.simpfun.model.RechargeTier;
import cn.jdnjk.simpfun.utils.DialogUtils;

public class RechargeFragment extends Fragment {

    private TextView tvUsername, tvUid, tvCurrentPoint;
    private TextView tvDoubleSignCard, tvAutoSignCard;
    private TabLayout tabCategory;
    private View layoutRechargePoints;
    private TextView tvUnderConstruction;
    private RecyclerView recyclerTiers, recyclerModes;
    private RadioButton radioWechat, radioAlipay;
    private MaterialButton btnPay;
    private TextView tvWechatName, tvAlipayName;

    private WebView hiddenPayWebView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<RechargeTier> tiers = new ArrayList<>();
    private final List<RechargeMode> modes = new ArrayList<>();
    private TierAdapter tierAdapter;
    private ModeAdapter modeAdapter;

    private int selectedTierIndex = 0;
    private int selectedModeIndex = 0;
    private String selectedPaymentMethod = "ali_pay_1";

    private final PayApi payApi = new PayApi();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recharge_layout, container, false);

        tvUsername = view.findViewById(R.id.tv_username);
        tvUid = view.findViewById(R.id.tv_uid);
        tvCurrentPoint = view.findViewById(R.id.tv_current_point);
        tvDoubleSignCard = view.findViewById(R.id.tv_double_sign_card);
        tvAutoSignCard = view.findViewById(R.id.tv_auto_sign_card);
        tabCategory = view.findViewById(R.id.tab_category);
        layoutRechargePoints = view.findViewById(R.id.layout_recharge_points);
        tvUnderConstruction = view.findViewById(R.id.tv_under_construction);
        recyclerTiers = view.findViewById(R.id.recycler_tiers);
        recyclerModes = view.findViewById(R.id.recycler_modes);
        radioWechat = view.findViewById(R.id.radio_wechat);
        radioAlipay = view.findViewById(R.id.radio_alipay);
        btnPay = view.findViewById(R.id.btn_pay);
        tvWechatName = view.findViewById(R.id.tv_wechat_name);
        tvAlipayName = view.findViewById(R.id.tv_alipay_name);

        setupTabs();
        setupRecyclers();
        setupListeners(view);
        initHiddenPayWebView();
        fetchData();

        return view;
    }

    private void setupTabs() {
        tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutRechargePoints.setVisibility(View.VISIBLE);
                    tvUnderConstruction.setVisibility(View.GONE);
                } else {
                    layoutRechargePoints.setVisibility(View.GONE);
                    tvUnderConstruction.setVisibility(View.VISIBLE);
                }
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
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.btn_wechat_pay).setOnClickListener(v -> {
            selectedPaymentMethod = "wx_pay_2";
            updatePaymentSelection();
        });
        view.findViewById(R.id.btn_alipay).setOnClickListener(v -> {
            selectedPaymentMethod = "ali_pay_1";
            updatePaymentSelection();
        });
        btnPay.setOnClickListener(v -> createOrder());
    }

    private void updatePaymentSelection() {
        radioAlipay.setChecked("ali_pay_1".equals(selectedPaymentMethod));
        radioWechat.setChecked("wx_pay_2".equals(selectedPaymentMethod));
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
                        updateUI(resultData);
                    }
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("RechargeFragment", "fetchData failed: " + errorMsg);
            }
        });
    }

    private void updateUI(JSONObject data) {
        try {
            JSONObject user = data.optJSONObject("user");
            if (user != null) {
                tvUid.setText("UID: " + user.optInt("uid"));
                tvCurrentPoint.setText("当前积分: " + user.optInt("point"));

                if (getContext() != null) {
                    SharedPreferences userInfoSp = getContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                    tvUsername.setText(userInfoSp.getString("username", "用户"));
                }

                long doubleSignTime = user.optLong("double_sign_card_valid_time", 0);
                boolean doubleSignValid = user.optBoolean("double_sign_card_is_valid", false);
                if (doubleSignTime > 0) {
                    tvDoubleSignCard.setVisibility(View.VISIBLE);
                    String dateStr = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE).format(new Date(doubleSignTime * 1000));
                    tvDoubleSignCard.setText("双倍积分卡 " + dateStr + "过期 " + (doubleSignValid ? "已生效" : "未生效"));
                } else {
                    tvDoubleSignCard.setVisibility(View.GONE);
                }

                long autoSignTime = user.optLong("auto_sign_card_valid_time", 0);
                boolean autoSignValid = user.optBoolean("auto_sign_card_is_valid", false);
                if (autoSignTime > 0) {
                    tvAutoSignCard.setVisibility(View.VISIBLE);
                    String dateStr = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE).format(new Date(autoSignTime * 1000));
                    tvAutoSignCard.setText("自动签到卡 " + dateStr + "过期 " + (autoSignValid ? "已生效" : "未生效"));
                } else {
                    tvAutoSignCard.setVisibility(View.GONE);
                }
            }

            JSONArray channels = data.optJSONArray("channels");
            if (channels != null) {
                for (int i = 0; i < channels.length(); i++) {
                    JSONObject c = channels.optJSONObject(i);
                    String id = c.optString("id");
                    String name = c.optString("name");
                    if ("wx_pay_2".equals(id)) tvWechatName.setText(name);
                    if ("ali_pay_1".equals(id)) tvAlipayName.setText(name);
                }
            }

            JSONObject itemPriceTiers = data.optJSONObject("item_price_tiers");
            JSONArray pointTiers = itemPriceTiers != null ? itemPriceTiers.optJSONArray("point") : data.optJSONArray("point_price_tiers");
            tiers.clear();
            if (pointTiers != null) {
                for (int i = 0; i < pointTiers.length(); i++) {
                    JSONObject t = pointTiers.getJSONObject(i);
                    tiers.add(new RechargeTier(t.optInt("point"), t.optString("public_recharge_money"), t.optString("pro_recharge_money")));
                }
            }
            tierAdapter.notifyDataSetChanged();

            JSONObject rechargeModes = data.optJSONObject("recharge_modes");
            modes.clear();
            if (rechargeModes != null) {
                JSONObject pub = rechargeModes.optJSONObject("public_recharge");
                if (pub != null) modes.add(new RechargeMode("public", pub.optString("name"), pub.optString("rule")));
                JSONObject pro = rechargeModes.optJSONObject("pro_recharge");
                if (pro != null) {
                    modes.add(new RechargeMode("normal", pro.optString("name"), pro.optString("rule")));
                    selectedModeIndex = 1;
                }
            }
            modeAdapter.notifyDataSetChanged();

            updatePayButtonText();
            updatePaymentSelection();

        } catch (Exception e) {
            Log.e("RechargeFragment", "updateUI failed", e);
        }
    }

    private void updatePayButtonText() {
        if (tiers.isEmpty() || selectedTierIndex >= tiers.size()) return;
        RechargeTier t = tiers.get(selectedTierIndex);
        RechargeMode m = modes.size() > selectedModeIndex ? modes.get(selectedModeIndex) : null;
        String price = t.getPrice(m != null ? m.getId() : "");
        btnPay.setText("立即支付 " + price + "元");
    }

    private void createOrder() {
        if (getActivity() == null || tiers.isEmpty() || modes.isEmpty()) return;

        RechargeMode m = modes.get(selectedModeIndex);
        if ("public".equals(m.getId())) {
            showPublicRechargeNotice();
        } else {
            executeCreateOrder();
        }
    }

    private void showPublicRechargeNotice() {
        String content = "（下滑查看更多）<br><br>" +
                "简幻欢目前仍为公益项目，<b>并非以盈利为目的</b>，初衷是给广大用户提供一个<b>「好用」</b>的服务器平台，达成<b>「人人都可免费开属于自己的服务器」</b>的目标。<br><br>" +
                "然而在项目实际运行时，发现：<br>" +
                "1. 部分用户有更高的性能需求，并且需要连续使用，这需要更高的成本。<br>" +
                "2. 用户不会关注实际性能需求、使用需求，在无约束使用时只会选择最高配置并持续占用，给项目造成了资源和金钱的浪费。<br><br>" +
                "为了能够满足不同用户的实际需求，同时节省部分项目开销，连续使用更高性能服务器将需要用户自行承担部分服务器成本，余下成本由我方承担。<br><br>" +
                "即：<b>服务器成本 = 您承担一部分 + 我方承担一部分</b><br><br>" +
                "若需支持我们，可考虑关闭公益模式（变更至 Pro），或直接选择普通充值，收益将会用于简幻欢的项目维护、设备升级等。<br><br>" +
                "我们尽力提供更多的服务器资源，但您应当理解：出于项目预算有限，我们无法承担过多成本。项目设置了全平台范围的每日充值上限，我们不保证您能够进行充值，也不保证您所需配置有足够剩余资源。<br><br>" +
                "您悉知：<b>充值为您的个人意愿，是您有更高的需求，并非本项目所提倡的。您的充值并不能覆盖服务器运行成本，也不能帮助到我们业务。出于项目预算有限，无法保证您能够进行充值或使用到预期配置的服务器。如果您想支持我们，可考虑变更至 Pro 或直接进行普通充值。</b>";

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("充值前须知")
                .setMessage(Html.fromHtml(content))
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("已阅并继续 (15s)", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        final int[] countdown = {15};
        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    countdown[0]--;
                    positiveButton.setText("已阅并继续 (" + countdown[0] + "s)");
                    handler.postDelayed(this, 1000);
                } else {
                    positiveButton.setText("已阅并继续");
                    positiveButton.setEnabled(true);
                    positiveButton.setOnClickListener(v -> {
                        executeCreateOrder();
                        dialog.dismiss();
                    });
                }
            }
        };
        handler.postDelayed(runnable, 1000);
    }

    private void executeCreateOrder() {
        btnPay.setEnabled(false);
        btnPay.setText("请求中...");

        RechargeTier t = tiers.get(selectedTierIndex);
        RechargeMode m = modes.get(selectedModeIndex);
        String token = Objects.requireNonNull(getActivity()).getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        String item = "point";

        payApi.createOrder(token, item, t.getPoint(), selectedPaymentMethod, m.getId(), new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
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
                btnPay.setEnabled(true);
                updatePayButtonText();
            }

            @Override
            public void onFailure(String errorMsg) {
                showToast("请求失败: " + errorMsg);
                btnPay.setEnabled(true);
                updatePayButtonText();
            }
        });
    }

    private void copyToClipboard(String text) {
        if (getContext() != null) {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
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
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http") || url.startsWith("https")) {
                    final PayTask task = new PayTask(getActivity());
                    boolean isIntercepted = task.payInterceptorWithUrl(url, true, result -> {
                        String returnUrl = result.getReturnUrl();
                        if (!TextUtils.isEmpty(returnUrl)) mainHandler.post(() -> view.loadUrl(returnUrl));
                    });
                    if (!isIntercepted) view.loadUrl(url);
                    return true;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
                return true;
            }
        });
    }

    @Override
    public void onDestroy() {
        if (hiddenPayWebView != null) {
            hiddenPayWebView.destroy();
            hiddenPayWebView = null;
        }
        super.onDestroy();
    }
    private class TierAdapter extends RecyclerView.Adapter<TierAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_tier, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            RechargeTier t = tiers.get(position);
            holder.tvPoints.setText(t.getPoint() + "积分");
            RechargeMode m = modes.size() > selectedModeIndex ? modes.get(selectedModeIndex) : null;
            String price = t.getPrice(m != null ? m.getId() : "");
            holder.tvMoney.setText(price + "元");
            boolean selected = selectedTierIndex == position;
            holder.card.setStrokeColor(selected ? getResources().getColor(R.color.md_theme_primary) : Color.TRANSPARENT);
            holder.card.setStrokeWidth(selected ? 4 : 0);
            holder.card.setOnClickListener(v -> {
                selectedTierIndex = holder.getAdapterPosition();
                notifyDataSetChanged();
                updatePayButtonText();
            });
        }
        @Override public int getItemCount() { return tiers.size(); }
        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card; TextView tvPoints, tvMoney;
            VH(View v) { super(v); card = (MaterialCardView)v; tvPoints = v.findViewById(R.id.tv_tier_points); tvMoney = v.findViewById(R.id.tv_tier_money); }
        }
    }

    private class ModeAdapter extends RecyclerView.Adapter<ModeAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recharge_mode, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            RechargeMode m = modes.get(position);
            holder.tvName.setText(m.getName());
            holder.tvRule.setText(m.getRule());
            boolean selected = selectedModeIndex == position;
            holder.card.setStrokeColor(selected ? getResources().getColor(R.color.md_theme_primary) : Color.TRANSPARENT);
            holder.card.setStrokeWidth(selected ? 4 : 0);
            holder.card.setOnClickListener(v -> {
                selectedModeIndex = holder.getAdapterPosition();
                notifyDataSetChanged();
                tierAdapter.notifyDataSetChanged();
                updatePayButtonText();
            });
        }
        @Override public int getItemCount() { return modes.size(); }
        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card; TextView tvName, tvRule;
            VH(View v) { super(v); card = (MaterialCardView)v; tvName = v.findViewById(R.id.tv_mode_name); tvRule = v.findViewById(R.id.tv_mode_rule); }
        }
    }
}
