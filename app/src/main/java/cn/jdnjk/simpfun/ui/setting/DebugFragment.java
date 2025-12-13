package cn.jdnjk.simpfun.ui.setting;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import cn.jdnjk.simpfun.R;
import com.tencent.bugly.crashreport.CrashReport;
import cn.jdnjk.simpfun.SWebView;

public class DebugFragment extends Fragment {

    private static final String SP_DEBUG = "debug_settings";
    private static final String KEY_BUGLY_ENABLED = "bugly_enabled";

    private static final String SP_TOKEN = "token";
    private static final String KEY_TOKEN = "token";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_debug, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        SharedPreferences spDebug = ctx.getSharedPreferences(SP_DEBUG, Context.MODE_PRIVATE);
        SharedPreferences spToken = ctx.getSharedPreferences(SP_TOKEN, Context.MODE_PRIVATE);

        Button btnOpenSWebView = view.findViewById(R.id.btn_open_swebview);
        SwitchMaterial swBugly = view.findViewById(R.id.switch_bugly);
        EditText etToken = view.findViewById(R.id.et_token);
        EditText etWebUrl = view.findViewById(R.id.et_web_url);
        Button btnSave = view.findViewById(R.id.btn_save_token);
        Button btnClear = view.findViewById(R.id.btn_clear_token);

        boolean buglyEnabled = spDebug.getBoolean(KEY_BUGLY_ENABLED, true);
        swBugly.setChecked(buglyEnabled);

        btnOpenSWebView.setOnClickListener(v -> {
            String input = etWebUrl.getText() == null ? null : etWebUrl.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(ctx, "请输入 URL", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = input.contains("://") ? input : ("https://" + input);

            Intent intent = new Intent(ctx, SWebView.class);
            intent.putExtra("url", url);
            startActivity(intent);
        });

        swBugly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spDebug.edit().putBoolean(KEY_BUGLY_ENABLED, isChecked).apply();
            if (!isChecked) {
                try { CrashReport.closeBugly(); } catch (Throwable ignored) {}
            }
        });

        String token = spToken.getString(KEY_TOKEN, "");
        etToken.setText(token);

        btnSave.setOnClickListener(v -> {
            String newToken = etToken.getText() == null ? null : etToken.getText().toString();
            if (TextUtils.isEmpty(newToken)) {
                Toast.makeText(ctx, "Token 为空", Toast.LENGTH_SHORT).show();
                return;
            }
            spToken.edit().putString(KEY_TOKEN, newToken).apply();
            Toast.makeText(ctx, "Token 已保存", Toast.LENGTH_SHORT).show();
        });

        btnClear.setOnClickListener(v -> {
            spToken.edit().remove(KEY_TOKEN).apply();
            etToken.setText("");
            Toast.makeText(ctx, "Token 已清空", Toast.LENGTH_SHORT).show();
        });
    }
}
