package cn.jdnjk.simpfun.ui.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
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

    private LinearProgressIndicator progressIndicator;
    private LinearLayout layoutStepUsername, layoutStepPassword, layoutUserCapsule;
    private LinearLayout layoutErrorUsername, layoutErrorPassword;
    private TextView textWelcomeUser, textSubtitle, textLoginTitle;
    private TextInputEditText editTextUsername, editTextPassword;
    private MaterialCheckBox checkBoxShowPassword;
    private MaterialButton buttonNext;
    private TextView textRegisterLink, textForgotUsername, textForgotPassword;
    private TextView textViewAgreement;

    private int currentStep = 1;
    private String savedUsername = "";
    private int pendingServerId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        initViews();
        setupClickListeners();
        //setupAgreement();

        // 读取深链待跳转数据
        pendingServerId = getIntent().getIntExtra(SplashActivity.EXTRA_DEEP_SERVER_ID, -1);
    }

    private void initViews() {
        progressIndicator = findViewById(R.id.progressIndicator);
        layoutStepUsername = findViewById(R.id.layoutStepUsername);
        layoutStepPassword = findViewById(R.id.layoutStepPassword);
        layoutUserCapsule = findViewById(R.id.layoutUserCapsule);
        layoutErrorUsername = findViewById(R.id.layoutErrorUsername);
        layoutErrorPassword = findViewById(R.id.layoutErrorPassword);
        textWelcomeUser = findViewById(R.id.textWelcomeUser);
        textSubtitle = findViewById(R.id.textSubtitle);
        textLoginTitle = findViewById(R.id.textLoginTitle);
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        checkBoxShowPassword = findViewById(R.id.checkBoxShowPassword);
        buttonNext = findViewById(R.id.buttonNext);
        textRegisterLink = findViewById(R.id.textRegisterLink);
        textForgotUsername = findViewById(R.id.textForgotUsername);
        textForgotPassword = findViewById(R.id.textForgotPassword);
        textViewAgreement = findViewById(R.id.textViewAgreement);
    }

    private void setupClickListeners() {
        buttonNext.setOnClickListener(v -> {
            if (currentStep == 1) {
                handleStep1();
            } else {
                handleStep2();
            }
        });

        checkBoxShowPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                editTextPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                editTextPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            editTextPassword.setSelection(editTextPassword.length());
        });

        textRegisterLink.setOnClickListener(v -> {
            // 跳转到注册页面 (可以实现或者提示)
            Toast.makeText(this, "正在为您跳转注册...", Toast.LENGTH_SHORT).show();
        });

        textForgotUsername.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("忘记账号")
                    .setMessage("如果您忘记了账号，请在微信小程序中查看。")
                    .setPositiveButton("确定", null)
                    .show();
        });

        textForgotPassword.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("忘记密码")
                    .setMessage("如果您忘记了密码，可以通过小程序进行重置密码")
                    .setPositiveButton("确定", null)
                    .show();
        });
    }

    /*private void setupAgreement() {
        String htmlText = "我已阅读并同意《<a href='https://www.yuque.com/simpfun/sfe/tos'>简幻欢用户协议</a>》和《<a href='https://github.com/jdnjk/simpfun/blob/master/eula/README.md'>软件许可协议</a>》";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textViewAgreement.setText(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT));
        } else {
            textViewAgreement.setText(Html.fromHtml(htmlText));
        }
        textViewAgreement.setMovementMethod(LinkMovementMethod.getInstance());
    }*/

    @Override
    public void onBackPressed() {
        if (currentStep == 2) {
            switchToStep1();
        } else {
            super.onBackPressed();
        }
    }

    private void switchToStep1() {
        if (currentStep == 1) return;

        Animation slideOutRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_right);
        Animation slideInLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_left);

        layoutStepPassword.startAnimation(slideOutRight);
        layoutUserCapsule.startAnimation(slideOutRight);

        slideOutRight.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                layoutStepPassword.setVisibility(View.GONE);
                layoutUserCapsule.setVisibility(View.GONE);

                layoutStepUsername.setVisibility(View.VISIBLE);
                textSubtitle.setVisibility(View.VISIBLE);
                textLoginTitle.setVisibility(View.VISIBLE);
                layoutStepUsername.startAnimation(slideInLeft);
                textSubtitle.startAnimation(slideInLeft);
                textLoginTitle.startAnimation(slideInLeft);

                currentStep = 1;
                // 重置密码输入
                editTextPassword.setText("");
                layoutErrorPassword.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    private void handleStep1() {
        String username = Objects.requireNonNull(editTextUsername.getText()).toString().trim();
        if (TextUtils.isEmpty(username)) {
            editTextUsername.setError("请输入账户");
            return;
        }

        showLoading(true);
        layoutErrorUsername.setVisibility(View.GONE);

        GetToken getToken = new GetToken(this);
        // 使用 00000000 尝试登录
        getToken.login(username, "00000000", new GetToken.Callback() {
            @Override
            public void onSuccess(String token) {
                // 竟然成功了？说明密码刚好是00000000
                showLoading(false);
                onAuthSuccess("登录成功");
            }

            @Override
            public void onFailure(int code, String errorMsg) {
                showLoading(false);
                if ("账号或密码错误".equals(errorMsg)) {
                    layoutErrorUsername.setVisibility(View.VISIBLE);
                } else if ("密码错误".equals(errorMsg)) {
                    // 进入第二步
                    savedUsername = username;
                    switchToStep2();
                } else if (code == 401) {
                    showWhitelistDialog();
                } else {
                    Toast.makeText(AuthActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void switchToStep2() {
        if (currentStep == 2) return;

        Animation slideOutLeft = AnimationUtils.loadAnimation(this, R.anim.slide_out_left);
        Animation slideInRight = AnimationUtils.loadAnimation(this, R.anim.slide_in_right);

        layoutStepUsername.startAnimation(slideOutLeft);
        textSubtitle.startAnimation(slideOutLeft);
        textLoginTitle.startAnimation(slideOutLeft);

        slideOutLeft.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                layoutStepUsername.setVisibility(View.GONE);
                textSubtitle.setVisibility(View.GONE);
                textLoginTitle.setVisibility(View.GONE);

                layoutStepPassword.setVisibility(View.VISIBLE);
                layoutUserCapsule.setVisibility(View.VISIBLE);
                textWelcomeUser.setText("欢迎 " + savedUsername);

                layoutStepPassword.startAnimation(slideInRight);
                layoutUserCapsule.startAnimation(slideInRight);

                currentStep = 2;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    private void handleStep2() {
        String password = Objects.requireNonNull(editTextPassword.getText()).toString().trim();
        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("请输入密码");
            return;
        }

        showLoading(true);
        layoutErrorPassword.setVisibility(View.GONE);

        GetToken getToken = new GetToken(this);
        getToken.login(savedUsername, password, new GetToken.Callback() {
            @Override
            public void onSuccess(String token) {
                showLoading(false);
                onAuthSuccess("登录成功");
            }

            @Override
            public void onFailure(int code, String errorMsg) {
                showLoading(false);
                if ("密码错误".equals(errorMsg) || "账号或密码错误".equals(errorMsg)) {
                    layoutErrorPassword.setVisibility(View.VISIBLE);
                } else if (code == 401) {
                    showWhitelistDialog();
                } else {
                    Toast.makeText(AuthActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        });
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
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonNext.setEnabled(!loading);
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

