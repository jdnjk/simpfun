package cn.jdnjk.simpfun.ui.point;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alipay.sdk.app.PayTask;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ApiClient;
import cn.jdnjk.simpfun.api.PointApi;
import cn.jdnjk.simpfun.adapter.PointRecordAdapter;
import cn.jdnjk.simpfun.model.PointRecord;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BuyPointActivity extends AppCompatActivity {

    public static final String EXTRA_TAB = "tab";
    public static final String TAB_POINTS = "points";
    public static final String TAB_DIAMONDS = "diamonds";

    private TabLayout tabLayout;
    private View layoutPoints, layoutDiamonds;
    private ChipGroup chipGroupAmount;
    private LinearLayout btnWechatPay, btnAlipay;
    private RadioButton radioWechat, radioAlipay;
    private Button btnPay;
    private ImageView btnHelp;
    private RecyclerView recyclerPointHistory, recyclerDiamondHistory;
    private MaterialCardView cardBottomPay;
    private View layoutRechargeSection;

    private int selectedPoint = 200;
    private String selectedPaymentMethod = "ali_pay_1"; // Default Alipay
    private String payUrl = null;

    // Data list
    private final List<PointRecord> pointRecords = new ArrayList<>();
    private final List<PointRecord> diamondRecords = new ArrayList<>();
    private PointRecordAdapter pointAdapter, diamondAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PointApi pointApi = new PointApi();

    private WebView hiddenPayWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buy_point);

        initViews();
        setupListeners();
        setupHistoryLists();

        updatePaymentSelection();
        applyRechargeUiVisible(false);

        initHiddenPayWebView();

        String tab = getIntent() != null ? getIntent().getStringExtra(EXTRA_TAB) : null;
        if (TAB_DIAMONDS.equals(tab) && tabLayout != null) {
            TabLayout.Tab t = tabLayout.getTabAt(1);
            if (t != null) t.select();
        } else if (tabLayout != null) {
            TabLayout.Tab t = tabLayout.getTabAt(0);
            if (t != null) t.select();
        }

        loadHistoryData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tab_layout);
        layoutPoints = findViewById(R.id.layout_points);
        layoutDiamonds = findViewById(R.id.layout_diamonds);
        chipGroupAmount = findViewById(R.id.chip_group_amount);

        btnWechatPay = findViewById(R.id.btn_wechat_pay);
        btnAlipay = findViewById(R.id.btn_alipay);
        radioWechat = findViewById(R.id.radio_wechat);
        radioAlipay = findViewById(R.id.radio_alipay);

        btnPay = findViewById(R.id.btn_pay);

        btnHelp = findViewById(R.id.btn_recharge_plus);
        layoutRechargeSection = findViewById(R.id.layout_recharge_section);

        recyclerPointHistory = findViewById(R.id.recycler_point_history);
        recyclerDiamondHistory = findViewById(R.id.recycler_diamond_history);

        cardBottomPay = findViewById(R.id.card_bottom_pay);
    }

    private void setupListeners() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutPoints.setVisibility(View.VISIBLE);
                    layoutDiamonds.setVisibility(View.GONE);
                    cardBottomPay.setVisibility(isRechargeUiVisible() ? View.VISIBLE : View.GONE);
                } else {
                    layoutPoints.setVisibility(View.GONE);
                    layoutDiamonds.setVisibility(View.VISIBLE);
                    cardBottomPay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        chipGroupAmount.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = findViewById(checkedId);
            if (chip != null) {
                try {
                    String text = chip.getText().toString().replace(",", "");
                    selectedPoint = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    Log.w("BuyPoint", "Invalid chip value", e);
                }
            }
        });

        View.OnClickListener wechatListener = v -> {
            selectedPaymentMethod = "wx_pay_2";
            updatePaymentSelection();
        };
        View.OnClickListener alipayListener = v -> {
            selectedPaymentMethod = "ali_pay_1";
            updatePaymentSelection();
        };

        btnWechatPay.setOnClickListener(wechatListener);
        btnAlipay.setOnClickListener(alipayListener);
        btnPay.setOnClickListener(v -> createOrder());
        btnHelp.setOnClickListener(v -> applyRechargeUiVisible(!isRechargeUiVisible()));
    }

    private boolean isRechargeUiVisible() {
        return layoutRechargeSection != null && layoutRechargeSection.getVisibility() == View.VISIBLE;
    }

    private void applyRechargeUiVisible(boolean visible) {
        if (layoutRechargeSection != null) {
            layoutRechargeSection.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        if (cardBottomPay != null) {
            boolean inPointsTab = tabLayout != null && tabLayout.getSelectedTabPosition() == 0;
            cardBottomPay.setVisibility(inPointsTab && visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePaymentSelection() {
        boolean isAlipay = "ali_pay_1".equals(selectedPaymentMethod);
        radioAlipay.setChecked(isAlipay);
        radioWechat.setChecked(!isAlipay);
    }

    private void setupHistoryLists() {

        recyclerPointHistory.setLayoutManager(new LinearLayoutManager(this));
        pointAdapter = new PointRecordAdapter(pointRecords);
        recyclerPointHistory.setAdapter(pointAdapter);

        recyclerDiamondHistory.setLayoutManager(new LinearLayoutManager(this));
        diamondAdapter = new PointRecordAdapter(diamondRecords);
        recyclerDiamondHistory.setAdapter(diamondAdapter);
    }

    private void loadHistoryData() {
        String token = getSharedPreferences("token", MODE_PRIVATE).getString("token", "");
        if (token.isEmpty())
            return;

        pointApi.getPointHistory(token, new PointApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                parseRecordResponse(response, pointRecords, pointAdapter);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("BuyPoint", "Point History Error: " + errorMsg);
            }
        });

        pointApi.getDiamondHistory(token, new PointApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                parseRecordResponse(response, diamondRecords, diamondAdapter);
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e("BuyPoint", "Diamond History Error: " + errorMsg);
            }
        });
    }

    private void parseRecordResponse(JSONObject response, List<PointRecord> targetList, PointRecordAdapter adapter) {
        try {
            JSONArray list = response.optJSONArray("list");
            if (list == null) list = response.optJSONArray("data");
            if (list == null) list = response.optJSONArray("rows");
            if (list == null) return;

            targetList.clear();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;

                String desc = item.optString("comment", "");
                if (desc.trim().isEmpty()) {
                    desc = item.optString("description", item.optString("reason", "变动"));
                }

                String amount = "0";
                if (item.has("point")) {
                    amount = String.valueOf(item.optInt("point", 0));
                } else if (item.has("diamond")) {
                    amount = String.valueOf(item.optInt("diamond", 0));
                } else if (item.has("amount")) {
                    amount = item.optString("amount", "0");
                } else if (item.has("num")) {
                    amount = item.optString("num", "0");
                } else if (item.has("change")) {
                    amount = item.optString("change", "0");
                }

                String rawTime = item.optString("create_time", "");
                if (rawTime.trim().isEmpty()) {
                    rawTime = item.optString("created_at", item.optString("time", ""));
                }
                String time = PointRecord.formatTime(rawTime);

                int left = 0;
                if (item.has("point_left")) left = item.optInt("point_left", 0);
                else if (item.has("diamond_left")) left = item.optInt("diamond_left", 0);
                else if (item.has("left")) left = item.optInt("left", 0);
                else if (item.has("balance")) left = item.optInt("balance", 0);

                targetList.add(new PointRecord(desc, amount, time, left));
            }

            runOnUiThread(adapter::notifyDataSetChanged);
        } catch (Exception e) {
            Log.e("BuyPoint", "parseRecordResponse failed", e);
        }
    }

    private void initHiddenPayWebView() {
        if (hiddenPayWebView != null) return;

        hiddenPayWebView = new WebView(this);
        hiddenPayWebView.setVisibility(View.GONE);

        WebSettings s = hiddenPayWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; U; Android; zh-cn;) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 SimpfunAPP/1.0");

        hiddenPayWebView.setWebViewClient(new AliPayCompatWebViewClient());

        View root = findViewById(android.R.id.content);
        if (root instanceof LinearLayout) {
            ((LinearLayout) root).addView(hiddenPayWebView, 0, new LinearLayout.LayoutParams(1, 1));
        } else if (root instanceof ViewGroup) {
            ((ViewGroup) root).addView(hiddenPayWebView, 0, new ViewGroup.LayoutParams(1, 1));
        }
    }

    @Override
    protected void onDestroy() {
        if (hiddenPayWebView != null) {
            try {
                hiddenPayWebView.stopLoading();
                hiddenPayWebView.loadUrl("about:blank");
                hiddenPayWebView.setWebViewClient(new WebViewClient());
                hiddenPayWebView.destroy();
            } catch (Throwable ignore) {
            }
            hiddenPayWebView = null;
        }
        super.onDestroy();
    }

    private void createOrder() {
        setPaymentButtonsEnabled(false);

        new Thread(() -> {
            try {
                String token = getSharedPreferences("token", MODE_PRIVATE).getString("token", "");
                if (token.isEmpty()) {
                    showToast("未登录");
                    finish();
                    return;
                }

                OkHttpClient client = ApiClient.getInstance().getClient();

                RequestBody formBody = new FormBody.Builder()
                        .add("point", String.valueOf(selectedPoint))
                        .add("method", selectedPaymentMethod)
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.simpfun.cn/api/recharge")
                        .post(formBody)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .addHeader("authorization", token)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        if (json.optInt("code") == 200) {
                            payUrl = json.optString("url", null);

                            runOnUiThread(() -> {
                                if (payUrl == null || payUrl.trim().isEmpty()) {
                                    showToast("支付链接为空");
                                    return;
                                }

                                if ("ali_pay_1".equals(selectedPaymentMethod)) {
                                    if (hiddenPayWebView != null) {
                                        hiddenPayWebView.loadUrl(payUrl);
                                    } else {
                                        showToast("初始化支付失败");
                                    }
                                } else {
                                    copyToClipboard(payUrl);
                                    showToast("已复制微信支付链接，请在微信中打开");
                                }
                            });
                        } else {
                            showToast("失败：" + json.optString("msg", "未知错误"));
                        }
                    } else {
                        showToast("网络错误");
                    }
                }
            } catch (Exception e) {
                Log.e("BuyPointActivity", "创建订单失败", e);
                showToast("请求失败");
            } finally {
                runOnUiThread(() -> setPaymentButtonsEnabled(true));
            }
        }).start();
    }

    private void setPaymentButtonsEnabled(boolean enabled) {
        btnPay.setEnabled(enabled);
        if (enabled) {
            btnPay.setText(R.string.btn_pay);
        } else {
            btnPay.setText("请求中...");
        }
    }

    private void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("支付链接", text));
            Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class AliPayCompatWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        private boolean handleUrl(final WebView view, final String url) {
            if (TextUtils.isEmpty(url)) return false;

            if (url.startsWith("http") || url.startsWith("https")) {
                final PayTask task = new PayTask(BuyPointActivity.this);
                boolean isIntercepted = task.payInterceptorWithUrl(url, true, result -> {
                    final String returnUrl = result != null ? result.getReturnUrl() : null;
                    if (!TextUtils.isEmpty(returnUrl)) {
                        runOnUiThread(() -> view.loadUrl(returnUrl));
                    }
                });

                if (!isIntercepted) {
                    view.loadUrl(url);
                }
                return true;
            }
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
            return true;
        }
    }
}
