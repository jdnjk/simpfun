package cn.jdnjk.simpfun;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import cn.jdnjk.simpfun.utils.ThemeUtils;

import com.alipay.sdk.app.PayTask;

import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class SWebView extends AppCompatActivity {
    private WebView webView;
    private static final String DEFAULT_URL = "https://cn.bing.com";
    private static final String PAY_OK_URL = "https://api.simpcloud.cn/pics/pay_ok.png";
    private volatile boolean payOkDialogShown = false;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; U; Android; zh-cn;) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 SimpfunAPP/" + BuildConfig.VERSION_NAME);
        webView.setWebViewClient(new AliPayCompatWebViewClient());
        setupBackNavigation();
        loadUrlFromIntent();
    }

    private void loadUrlFromIntent() {
        String urlFromIntent = getIntent().getStringExtra("url");
        if (urlFromIntent == null || urlFromIntent.trim().isEmpty()) {
            urlFromIntent = DEFAULT_URL;
        }
        if (isValidUrl(urlFromIntent)) {
            setAuthCookieForUrl(urlFromIntent);
            webView.loadUrl(urlFromIntent);
        } else {
            webView.loadUrl(DEFAULT_URL);
        }
    }

    private void setAuthCookieForUrl(String urlString) {
        if (TextUtils.isEmpty(urlString)) return;
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            if (host == null) return;
            host = host.toLowerCase(Locale.ROOT);

            if (!"simpfun.net".equals(host) && !"beta.simpfun.cn".equals(host)) {
                return;
            }

            String token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);
            if (TextUtils.isEmpty(token)) return;

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setCookie("https://" + host + "/",
                    "simpfun-token=" + token + "; Path=/; SameSite=Lax");
            cookieManager.flush();
        } catch (Exception ignored) {
        }
    }

    private void injectLocalStorageTokenForUrl(String urlString) {
        if (TextUtils.isEmpty(urlString)) return;
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            if (!"simpfun.cn".equalsIgnoreCase(host)) {
                return;
            }

            String token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);
            if (TextUtils.isEmpty(token)) return;

            final String quoted = JSONObject.quote(token);
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.evaluateJavascript("localStorage.setItem('token', " + quoted + ");", null);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private boolean isValidUrl(String urlString) {
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void showPayOkDialogOnce() {
        if (payOkDialogShown || isFinishing()) return;
        payOkDialogShown = true;
        runOnUiThread(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(SWebView.this)
                    .setTitle("提示")
                    .setMessage("支付成功")
                    .setCancelable(false)
                    .setPositiveButton("确定", (d, w) -> {
                        d.dismiss();
                        finish();
                    })
                    .show();
        });
    }

    private class AliPayCompatWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(view, request.getUrl().toString());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            setAuthCookieForUrl(url);
            injectLocalStorageTokenForUrl(url);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            injectLocalStorageTokenForUrl(url);
            super.onPageFinished(view, url);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            if (!payOkDialogShown && url != null && (url.equals(PAY_OK_URL) || url.startsWith(PAY_OK_URL + "?"))) {
                showPayOkDialogOnce();
            }
            super.onLoadResource(view, url);
        }

        private boolean handleUrl(final WebView view, final String url) {
            if (TextUtils.isEmpty(url)) return false;

            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                final PayTask task = new PayTask(SWebView.this);
                boolean isIntercepted = task.payInterceptorWithUrl(url, true, result -> {
                    final String returnUrl = result != null ? result.getReturnUrl() : null;
                    if (!TextUtils.isEmpty(returnUrl)) {
                        runOnUiThread(() -> view.loadUrl(returnUrl));
                    }
                });

                if (!isIntercepted) {
                    setAuthCookieForUrl(url);
                    view.loadUrl(url);
                }
            } else {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                }
            }
            return true;
        }
    }
}
