package cn.jdnjk.simpfun.ui.point;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.alipay.sdk.app.PayTask;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.PayApi;
import cn.jdnjk.simpfun.databinding.DialogRechargeNoticeBinding;
import cn.jdnjk.simpfun.model.RechargeMeta;
import cn.jdnjk.simpfun.utils.ClipboardUtils;

/**
 * 充值子页基类：meta 消费、支付渠道下拉、支付宝隐藏 WebView 拉起、微信复制链接、
 * 公益充值 15 秒须知、支付按钮 10 秒防重复。
 */
public abstract class BaseRechargeFragment extends Fragment implements RechargeHomeFragment.MetaListener {

    protected static final String PAY_METHOD_ALIPAY = "ali_pay_1";
    protected static final String PAY_METHOD_WECHAT = "wx_pay_2";
    private static final String PAY_OK_URL = "https://api.simpfun.cn/pics/pay_ok.png";
    private static final long PAY_BUTTON_LOCK_MS = 10_000L;
    private static final String TAG = "BaseRechargeFragment";

    protected final PayApi payApi = new PayApi();
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    protected RechargeMeta meta;

    private final List<RechargeMeta.PayChannel> enabledChannels = new ArrayList<>();
    private int selectedChannelIndex = 0;
    private WebView hiddenPayWebView;
    private boolean paySuccessHandled;
    private AlertDialog noticeDialog;

    protected RechargeHomeFragment home() {
        return getParentFragment() instanceof RechargeHomeFragment
                ? (RechargeHomeFragment) getParentFragment() : null;
    }

    protected String getToken() {
        Context context = getContext();
        if (context == null) return "";
        return context.getApplicationContext()
                .getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
    }

    // ---------------------------------------------------------------- 渠道

    /** 把可用渠道填进 ExposedDropdownMenu；全部不可用时置灰。 */
    protected void setupChannelDropdown(TextInputLayout layout, MaterialAutoCompleteTextView spinner) {
        enabledChannels.clear();
        if (meta != null) {
            for (RechargeMeta.PayChannel channel : meta.channels) {
                if (channel.enabled) enabledChannels.add(channel);
            }
        }
        if (enabledChannels.isEmpty()) {
            layout.setEnabled(false);
            layout.setHint("暂无可用支付渠道");
            return;
        }
        layout.setEnabled(true);
        String[] labels = new String[enabledChannels.size()];
        for (int i = 0; i < enabledChannels.size(); i++) {
            labels[i] = enabledChannels.get(i).label();
        }
        // 默认选中支付宝：先精确匹配 ali_pay_1，再按名称模糊匹配"支付宝"，都不在则回落第一个
        selectedChannelIndex = 0;
        for (int i = 0; i < enabledChannels.size(); i++) {
            if (PAY_METHOD_ALIPAY.equals(enabledChannels.get(i).id)) {
                selectedChannelIndex = i;
                break;
            }
        }
        if (!PAY_METHOD_ALIPAY.equals(enabledChannels.get(selectedChannelIndex).id)) {
            for (int i = 0; i < enabledChannels.size(); i++) {
                if (enabledChannels.get(i).name.contains("支付宝")) {
                    selectedChannelIndex = i;
                    break;
                }
            }
        }
        spinner.setSimpleItems(labels);
        spinner.setText(labels[selectedChannelIndex], false);
        spinner.setOnItemClickListener((parent, view, position, id) -> selectedChannelIndex = position);
    }

    @Nullable
    protected RechargeMeta.PayChannel selectedChannel() {
        if (enabledChannels.isEmpty()
                || selectedChannelIndex < 0
                || selectedChannelIndex >= enabledChannels.size()) {
            return null;
        }
        return enabledChannels.get(selectedChannelIndex);
    }

    // ---------------------------------------------------------------- 下单

    /**
     * 创建订单并拉起支付。按钮先锁定 10 秒防重复提交。
     *
     * @param item       商品（point / double_sign / auto_sign）
     * @param num        数量（积分档位值或卡天数）
     * @param modeId     充值模式（public / normal）
     * @param resetText  锁定结束后恢复按钮文案
     */
    protected void createOrderFlow(MaterialButton button, String item, int num, String modeId, Runnable resetText) {
        RechargeMeta.PayChannel channel = selectedChannel();
        if (channel == null) {
            showSnack("暂无可用支付渠道");
            return;
        }
        String token = getToken();
        if (token.isEmpty()) {
            showSnack("请先登录");
            return;
        }

        button.setEnabled(false);
        button.setText("请求中...");
        mainHandler.postDelayed(() -> {
            if (!isViewAlive()) return;
            button.setEnabled(true);
            resetText.run();
        }, PAY_BUTTON_LOCK_MS);

        String method = channel.id;
        payApi.createOrder(token, item, num, method, modeId, new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!isViewAlive()) return;
                handlePayOrderResponse(json, channel);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "createOrder failed: " + errorMsg);
                if (!isViewAlive()) return;
                showSnack("请求失败: " + errorMsg);
                button.setEnabled(true);
                resetText.run();
            }
        });
    }

    /** 按渠道 ID 判断是否为微信支付（wx_pay_1_native / wx_pay_2 等均以 wx 开头）。 */
    protected static boolean isWechatChannel(RechargeMeta.PayChannel channel) {
        return channel.id != null && channel.id.startsWith("wx");
    }

    private void handlePayOrderResponse(JSONObject json, RechargeMeta.PayChannel channel) {
        if (json.optInt("code") != 200) {
            showSnack("下单失败: " + json.optString("msg", "未知错误"));
            return;
        }
        if (!isWechatChannel(channel)) {
            // 支付宝：只用顶层 url，走隐藏 WebView 拉起
            String url = json.optString("url");
            if (url.trim().isEmpty()) {
                showSnack("下单失败: 未返回支付地址");
                return;
            }
            paySuccessHandled = false;
            ensureHiddenPayWebView().loadUrl(url);
            showSnack("正在拉起支付宝...");
        } else {
            // 微信：只用 data.code_url（weixin:// 协议链接），复制后在微信中打开
            JSONObject data = json.optJSONObject("data");
            String codeUrl = data != null ? data.optString("code_url") : "";
            if (codeUrl.trim().isEmpty()) {
                showSnack("下单失败: 未返回支付链接");
                return;
            }
            if (ClipboardUtils.copyPlainText(getContext(), "微信支付链接", codeUrl, null)) {
                showSnack("已复制支付链接，请在微信中打开并粘在任意聊天框点击支付");
            } else {
                showSnack("复制支付链接失败");
            }
        }
    }

    /** 支付成功（回调图命中）后：提示 + 重新拉取 meta + 通知明细刷新。 */
    protected void handlePaySuccess() {
        if (paySuccessHandled) return;
        paySuccessHandled = true;
        mainHandler.post(() -> {
            if (!isViewAlive()) return;
            showSnack("支付成功");
            RechargeHomeFragment parent = home();
            if (parent != null) parent.reloadMetaAndNotify();
        });
    }

    // ---------------------------------------------------------------- 须知对话框

    /** 弹窗正文：strings.xml 中带 <b> 标记的 HTML。 */
    private CharSequence loadNoticeContent() {
        String html = getString(R.string.recharge_notice_content);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY);
        }
        return android.text.Html.fromHtml(html);
    }

    /**
     * 公益充值前须知：15 秒倒计时结束后才能点【已阅并继续】。
     * 本次进入充值页期间同意过一次后，后续支付直接放行；退出充值页后重新要求。
     * onConfirm 为 null 时仅作信息展示，始终弹窗。
     */
    protected void showPublicRechargeNotice(Runnable onConfirm) {
        if (!isViewAlive()) return;
        if (onConfirm != null && home() != null && home().isPublicNoticeConfirmed()) {
            onConfirm.run();
            return;
        }

        DialogRechargeNoticeBinding noticeBinding = DialogRechargeNoticeBinding.inflate(getLayoutInflater());
        noticeBinding.tvNoticeBody.setText(loadNoticeContent());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(noticeBinding.getRoot())
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("已阅并继续 (15s)", null)
                .create();
        noticeDialog = dialog;
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (312 * getResources().getDisplayMetrics().density),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.setEnabled(false);
        final int[] countdown = {15};
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!isViewAlive() || !dialog.isShowing()) return;
            countdown[0]--;
            if (countdown[0] > 0) {
                positive.setText("已阅并继续 (" + countdown[0] + "s)");
                mainHandler.postDelayed(tick[0], 1000);
            } else {
                positive.setText("已阅并继续");
                positive.setEnabled(true);
                positive.setOnClickListener(v -> {
                    dialog.dismiss();
                    RechargeHomeFragment parent = home();
                    if (parent != null && onConfirm != null) {
                        parent.setPublicNoticeConfirmed(true);
                    }
                    if (onConfirm != null) onConfirm.run();
                });
            }
        };
        dialog.setOnDismissListener(d -> {
            mainHandler.removeCallbacks(tick[0]);
            if (noticeDialog == d) noticeDialog = null;
        });
        mainHandler.postDelayed(tick[0], 1000);
    }

    // ---------------------------------------------------------------- 支付宝隐藏 WebView

    private WebView ensureHiddenPayWebView() {
        if (hiddenPayWebView == null && getContext() != null) {
            hiddenPayWebView = new WebView(requireContext().getApplicationContext());
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
                    if (isPayOkUrl(url)) handlePaySuccess();
                    super.onLoadResource(view, url);
                }
            });
        }
        return hiddenPayWebView;
    }

    private boolean handlePayUrl(WebView view, @Nullable String url) {
        if (url == null) return false;
        if (isPayOkUrl(url)) {
            handlePaySuccess();
            return true;
        }
        if (url.startsWith("http")) {
            android.app.Activity activity = getActivity();
            if (activity == null) {
                view.loadUrl(url);
                return true;
            }
            new Thread(() -> {
                final PayTask task = new PayTask(activity);
                boolean intercepted = task.payInterceptorWithUrl(url, true, result -> {
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
                if (!intercepted) {
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

    // ---------------------------------------------------------------- 其他

    protected void showSnack(String msg) {
        View view = getView();
        if (view == null || !isAdded()) return;
        Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show();
    }

    protected boolean isViewAlive() {
        return isAdded() && getView() != null;
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        if (noticeDialog != null) {
            noticeDialog.dismiss();
            noticeDialog = null;
        }
        if (hiddenPayWebView != null) {
            hiddenPayWebView.setWebViewClient(null);
            hiddenPayWebView.destroy();
            hiddenPayWebView = null;
        }
        super.onDestroyView();
    }
}
