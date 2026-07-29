package cn.jdnjk.simpfun.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import cn.jdnjk.simpfun.utils.Feedback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.GetToken;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.SplashActivity;
import cn.jdnjk.simpfun.utils.ThemeUtils;

import java.util.Objects;

public class AuthActivity extends AppCompatActivity {
    private static final String SP_TOKEN = "token";
    private static final String SP_USER_INFO = "user_info";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";

    private LinearProgressIndicator progressIndicator;
    private LinearLayout layoutStepUsername, layoutStepPassword, layoutUserCapsule;
    private LinearLayout layoutErrorUsername, layoutErrorPassword;
    private TextView textWelcomeUser, textSubtitle, textLoginTitle;
    private TextInputEditText editTextUsername, editTextPassword;
    private MaterialCheckBox checkBoxShowPassword;
    private MaterialButton buttonNext;
    private TextView textRegisterLink, textForgotUsername, textForgotPassword;

    private int currentStep = 1;
    private String savedUsername = "";
    private int pendingServerId = -1;
    private AlertDialog activeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        ThemeUtils.applyEdgeToEdge(this);
        ThemeUtils.applyRootInsets(this);

        initViews();
        setupClickListeners();
        setupBackNavigation();
        //setupAgreement();

        // 读取深链待跳转数据
        pendingServerId = getIntent().getIntExtra(SplashActivity.EXTRA_DEEP_SERVER_ID, -1);
        tryRestorePasswordStep();
    }

    @Override
    protected void onDestroy() {
        if (activeDialog != null) {
            if (activeDialog.isShowing()) {
                activeDialog.dismiss();
            }
            activeDialog = null;
        }
        super.onDestroy();
    }

    /** 页面是否还能安全地操作视图 / 弹窗。 */
    private boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private View contentRoot() {
        return findViewById(android.R.id.content);
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
            // TODO:跳转到注册页面 (可以实现或者提示)
        });

        textForgotUsername.setOnClickListener(v -> {
            activeDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("忘记账号")
                    .setMessage("如果您忘记了账号，请在微信小程序中查看。")
                    .setPositiveButton("确定", null)
                    .show();
            activeDialog.show();
        });

        textForgotPassword.setOnClickListener(v -> {
            activeDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("忘记密码")
                    .setMessage("如果您忘记了密码，可以通过小程序进行重置密码")
                    .setPositiveButton("确定", null)
                    .show();
            activeDialog.show();
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep == 2) {
                    switchToStep1();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
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
                    Feedback.error(contentRoot(), errorMsg, "重试", AuthActivity.this::handleStep1);
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

    private void showPasswordStepDirectly() {
        currentStep = 2;
        layoutStepUsername.setVisibility(View.GONE);
        textSubtitle.setVisibility(View.GONE);
        textLoginTitle.setVisibility(View.GONE);
        layoutStepPassword.setVisibility(View.VISIBLE);
        layoutUserCapsule.setVisibility(View.VISIBLE);
        textWelcomeUser.setText("欢迎 " + savedUsername);
        layoutErrorPassword.setVisibility(View.GONE);
        editTextUsername.setText(savedUsername);
    }

    private void tryRestorePasswordStep() {
        SharedPreferences tokenPrefs = getSharedPreferences(SP_TOKEN, MODE_PRIVATE);
        String token = tokenPrefs.getString(KEY_TOKEN, "");
        if (TextUtils.isEmpty(token)) {
            return;
        }

        SharedPreferences userInfoPrefs = getSharedPreferences(SP_USER_INFO, MODE_PRIVATE);
        String username = userInfoPrefs.getString(KEY_USERNAME, "");
        if (TextUtils.isEmpty(username)) {
            return;
        }

        savedUsername = username;
        showPasswordStepDirectly();
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
                if (!isAlive()) return;
                showLoading(false);
                onAuthSuccess("登录成功");
            }

            @Override
            public void onFailure(int code, String errorMsg) {
                if (!isAlive()) return;
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
        if (!isAlive()) return;
        String title = "需要微信小程序验证";
        String networkWarningHtml = buildWhitelistNetworkWarningHtml();
        String contentHtml = "<div>" +
                "<p>当前登录环境需要进行 <b>IP 白名单验证</b> 才能登录。请按以下步骤操作：</p>" +
                networkWarningHtml +
                "<ol>" +
                "<li><b>在本设备或同一网络下</b> 打开微信小程序 简幻欢，有效登录后会自动放行登录 IP。</li>" +
                "<li>保持 <b>仅一种网络连接</b>（不要同时连 Wi‑Fi/移动数据/有线），避免 IP 不一致导致登录失败，推荐只用 WLAN。</li>" +
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
                    Feedback.error(this, "打开微信失败");
                }
            });
        }

        AlertDialog dialog = builder.create();
        activeDialog = dialog;
        dialog.show();
    }

    private String buildWhitelistNetworkWarningHtml() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return "";
        Network network = cm.getActiveNetwork();
        if (network == null) return "";
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) return "";

        boolean hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        boolean hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (!hasVpn && !hasCellular) return "";

        StringBuilder warning = new StringBuilder();
        warning.append("<p><b>当前网络提醒：</b>");
        if (hasVpn) {
            warning.append("检测到 VPN 已开启，白名单验证可能获取到错误的 IP。请关闭 VPN 后再登录。");
        }
        if (hasCellular) {
            if (hasVpn) warning.append("<br>");
            warning.append("检测到正在使用 移动网络。请确认微信小程序验证和本应用登录使用的是同一个移动网络；如果同时开着 WLAN，请先关闭其中一个网络。");
        }
        warning.append("</p>");
        return warning.toString();
    }
}
