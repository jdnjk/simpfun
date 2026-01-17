package cn.jdnjk.simpfun.ui.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.GetToken;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.SplashActivity;

import java.util.Objects;

public class AuthActivity extends AppCompatActivity {

    private TextView textLogin, textRegister;
    private TextInputLayout layoutInviteCode;
    private TextInputEditText editTextUsername, editTextPassword, editTextInviteCode;
    private CheckBox checkBoxAgree;
    private Button buttonSubmit;

    private boolean isRegisterMode = false;
    private int pendingServerId = -1; // 待跳转服务器ID（来自深链）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        TextView textViewAgreement = findViewById(R.id.textViewAgreement);
        String htmlText = "我已阅读并同意《<a href='https://www.yuque.com/simpfun/sfe/tos'>简幻欢用户协议</a>》和《<a href='https://github.com/jdnjk/simpfun/blob/master/eula/README.md'>软件许可协议</a>》";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textViewAgreement.setText(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT));
        }
        textViewAgreement.setMovementMethod(LinkMovementMethod.getInstance());

        // 读取深链待跳转数据
        pendingServerId = getIntent().getIntExtra(SplashActivity.EXTRA_DEEP_SERVER_ID, -1);

        initViews();
        setupClickListeners();
        setLoginMode();
    }

    private void initViews() {
        textLogin = findViewById(R.id.textLogin);
        textRegister = findViewById(R.id.textRegister);
        layoutInviteCode = findViewById(R.id.layoutInviteCode);
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextInviteCode = findViewById(R.id.editTextInviteCode);
        checkBoxAgree = findViewById(R.id.checkBoxAgree);
        buttonSubmit = findViewById(R.id.buttonSubmit);
    }

    private void setupClickListeners() {
        textLogin.setOnClickListener(v -> setLoginMode());
        textRegister.setOnClickListener(v -> setRegisterMode());

        buttonSubmit.setOnClickListener(v -> onSubmit());
    }

    private void setLoginMode() {
        isRegisterMode = false;
        textLogin.setTextColor(0xFF007BFF);
        textLogin.setAlpha(1.0f);
        textRegister.setTextColor(0xFF555555);
        textRegister.setAlpha(0.7f);
        layoutInviteCode.setVisibility(View.GONE);
        buttonSubmit.setText("登录");
    }

    private void setRegisterMode() {
        isRegisterMode = true;
        textLogin.setTextColor(0xFF555555);
        textLogin.setAlpha(0.7f);
        textRegister.setTextColor(0xFF007BFF);
        textRegister.setAlpha(1.0f);
        layoutInviteCode.setVisibility(View.VISIBLE);
        buttonSubmit.setText("注册");
    }

    private void onSubmit() {
        String username = Objects.requireNonNull(editTextUsername.getText()).toString().trim();
        String password = Objects.requireNonNull(editTextPassword.getText()).toString().trim();
        String inviteCode = isRegisterMode ? Objects.requireNonNull(editTextInviteCode.getText()).toString().trim() : null;

        if (TextUtils.isEmpty(username)) {
            editTextUsername.setError("请输入账户");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("请输入密码");
            return;
        }
        if (password.length() < 6) {
            editTextPassword.setError("密码至少6位");
            return;
        }
        if (!checkBoxAgree.isChecked()) {
            Toast.makeText(this, "请同意用户协议", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        GetToken getToken = new GetToken(this);
        if (isRegisterMode) {
            getToken.register(username, password, inviteCode, new GetToken.Callback() {
                @Override
                public void onSuccess(String token) {
                    showLoading(false);
                    onAuthSuccess("注册成功");
                }

                @Override
                public void onFailure(int code, String errorMsg) {
                    showLoading(false);
                    Toast.makeText(AuthActivity.this, "注册失败: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            getToken.login(username, password, new GetToken.Callback() {
                @Override
                public void onSuccess(String token) {
                    showLoading(false);
                    onAuthSuccess("登录成功");
                }

                @Override
                public void onFailure(int code, String errorMsg) {
                    if (code == 401) {
                        showLoading(false);
                        showWhitelistDialog();
                    } else {
                        showLoading(false);
                        Toast.makeText(AuthActivity.this, "登录失败: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void onAuthSuccess(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Intent intent;
        if (pendingServerId != -1) {
            intent = new Intent(this, ServerManages.class);
            intent.putExtra(ServerManages.EXTRA_DEVICE_ID, pendingServerId);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean loading) {
        buttonSubmit.setEnabled(!loading);
        buttonSubmit.setText(loading ? (isRegisterMode ? "注册中..." : "登录中...") : (isRegisterMode ? "注册" : "登录"));
    }

    private void showWhitelistDialog() {
        // 标题与内容：更清晰、可读性更好；保留链接和操作
        String title = "需要微信小程序验证";
        String contentHtml = "<div>" +
                "<p>当前登录环境需要进行 <b>IP 白名单验证</b> 才能登录。请按以下步骤操作：</p>" +
                "<ol>" +
                "<li><b>在本设备或同一网络</b> 打开微信并进入小程序，有效登录后会自动放行登录 IP。</li>" +
                "<li>保持 <b>仅一种网络连接</b>（不要同时连 Wi‑Fi/移动数据/有线），以免 IP 不一致导致登录失败。</li>" +
                "<li>返回本应用，点击登录重试。</li>" +
                "</ol>" +
                "<p>仍然遇到问题？加入 QQ 群获取帮助：<a href='mqqopensdkapi://bizAgent/qm/qr?url=https%3a%2f%2fqm.qq.com%2fq%2frtfBSuFGUM'>465468467</a></p>" +
                "</div>";

        Spanned spanned;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            spanned = Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_COMPACT);
        } else {
            spanned = Html.fromHtml(contentHtml);
        }

        TextView tv = new TextView(this);
        tv.setText(spanned);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(padding, padding, padding, padding);
        // 提升可读性：设置行距
        tv.setLineSpacing(0f, 1.2f);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(tv);

        // 使用 MD3 风格的 MaterialAlertDialogBuilder（具体外观由应用主题决定）
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(title)
                .setView(scrollView)
                .setPositiveButton("我知道了", (d, which) -> d.dismiss());

        // 检查是否安装了微信
        Intent wechatIntent = new Intent(Intent.ACTION_VIEW);
        wechatIntent.setData(android.net.Uri.parse("weixin://"));
        if (wechatIntent.resolveActivity(getPackageManager()) != null) {
            builder.setNegativeButton("打开微信", (d, which) -> {
                try {
                    startActivity(wechatIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "打开微信失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}