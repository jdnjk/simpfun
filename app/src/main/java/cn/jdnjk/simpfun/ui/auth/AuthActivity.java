package cn.jdnjk.simpfun.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.GetToken;

public class AuthActivity extends AppCompatActivity {

    private TextView textLogin, textRegister;
    private TextInputLayout layoutInviteCode;
    private TextInputEditText editTextUsername, editTextPassword, editTextInviteCode;
    private CheckBox checkBoxAgree;
    private Button buttonSubmit;

    private boolean isRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        TextView textViewAgreement = findViewById(R.id.textViewAgreement);
        String htmlText = "我已阅读并同意《<a href='https://www.yuque.com/simpfun/sfe/tos'>简幻欢用户协议</a>》和《<a href='https://github.com/jdnjk/simpfun/blob/master/eula/README.md'>软件许可协议</a>》";
        textViewAgreement.setText(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT));
        textViewAgreement.setMovementMethod(LinkMovementMethod.getInstance());

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
        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String inviteCode = isRegisterMode ? editTextInviteCode.getText().toString().trim() : null;

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
                public void onFailure(String errorMsg) {
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
                public void onFailure(String errorMsg) {
                    showLoading(false);
                    Toast.makeText(AuthActivity.this, "登录失败: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void onAuthSuccess(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean loading) {
        buttonSubmit.setEnabled(!loading);
        buttonSubmit.setText(loading ? (isRegisterMode ? "注册中..." : "登录中...") : (isRegisterMode ? "注册" : "登录"));
    }
}