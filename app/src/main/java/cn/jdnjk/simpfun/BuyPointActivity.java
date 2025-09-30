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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BuyPointActivity extends AppCompatActivity {

    private RadioGroup radioGroup, radioPayment;
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

        radioGroup = findViewById(R.id.radio_group);
        radioPayment = findViewById(R.id.radio_payment);
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

        radioPayment.setOnCheckedChangeListener((group, checkedId) -> {
            selectedPaymentMethod = checkedId == R.id.radio_alipay ? "ali_pay_1" : "wx_pay_2";
        });

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

                URL url = new URL("https://api.simpfun.cn/api/recharge");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("authorization", token);
                conn.setDoOutput(true);

                int point = POINT_OPTIONS[selectedPointIndex];
                String params = "point=" + point + "&method=" + selectedPaymentMethod;

                OutputStream os = conn.getOutputStream();
                os.write(params.getBytes("UTF-8"));
                os.flush();
                os.close();

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.getInt("code") == 200) {
                        payUrl = json.getString("url");

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