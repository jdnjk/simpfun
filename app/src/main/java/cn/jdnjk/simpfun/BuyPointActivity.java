package cn.jdnjk.simpfun;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BuyPointActivity extends AppCompatActivity {

    private Button btnPay, btnCopyUrl;
    private TextView tvHint;

    private final int[] POINT_OPTIONS = {200, 600, 3300, 12000, 40000};
    private String payUrl = null;
    private int selectedPointIndex = 0;
    private String selectedPaymentMethod = "ali_pay_1";

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buy_point);

        RadioGroup radioGroup = findViewById(R.id.radio_group);
        RadioGroup radioPayment = findViewById(R.id.radio_payment);
        btnPay = findViewById(R.id.btn_pay);
        btnCopyUrl = findViewById(R.id.btn_copy_url);
        tvHint = findViewById(R.id.tv_hint);

        radioGroup.check(R.id.radio_0);
        radioPayment.check(R.id.radio_alipay);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int index = group.indexOfChild(findViewById(checkedId));
            if (index >= 0 && index < POINT_OPTIONS.length) {
                selectedPointIndex = index;
            }
        });

        radioPayment.setOnCheckedChangeListener((group, checkedId) -> selectedPaymentMethod = checkedId == R.id.radio_alipay ? "ali_pay_1" : "wx_pay_2");

        btnPay.setOnClickListener(v -> createOrder());

        btnCopyUrl.setOnClickListener(v -> {
            if (payUrl != null && !payUrl.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("支付链接", payUrl));
                Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请先发起支付", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createOrder() {
        btnPay.setEnabled(false);
        btnPay.setText("请求中...");

        new Thread(() -> {
            try {
                String token = getSharedPreferences("token", MODE_PRIVATE).getString("token", "");
                if (token.isEmpty()) {
                    showToast("未登录");
                    finish();
                    return;
                }

                // 使用 ApiClient + OkHttp 发起请求
                OkHttpClient client = ApiClient.getInstance().getClient();

                int point = POINT_OPTIONS[selectedPointIndex];
                RequestBody formBody = new FormBody.Builder()
                        .add("point", String.valueOf(point))
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
                                btnCopyUrl.setVisibility(View.VISIBLE);
                                tvHint.setVisibility(View.VISIBLE);

                                if ("ali_pay_1".equals(selectedPaymentMethod)) {
                                    try {
                                        String aliUrl = "alipays://platformapi/startapp?appId=20000917&url=" + Uri.encode(payUrl);
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(aliUrl)));
                                        showToast("打开支付宝...");
                                    } catch (Exception e) {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payUrl)));
                                        showToast("支付宝未安装，已打开网页");
                                    }
                                } else {
                                    showToast("请复制链接在微信中打开");
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
                runOnUiThread(() -> {
                    btnPay.setEnabled(true);
                    btnPay.setText("立即支付");
                });
            }
        }).start();
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}