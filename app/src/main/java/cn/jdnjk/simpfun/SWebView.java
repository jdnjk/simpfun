package cn.jdnjk.simpfun;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alipay.sdk.app.PayTask;

import java.net.MalformedURLException;
import java.net.URL;

public class SWebView extends AppCompatActivity {
    private WebView webView;
    private static final String DEFAULT_URL = "https://cn.bing.com";
    private static final String PAY_OK_URL = "https://api.simpcloud.cn/pics/pay_ok.png";
    private volatile boolean payOkDialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; U; Android; zh-cn;) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 SimpfunAPP/1.0");
        webView.setWebViewClient(new AliPayCompatWebViewClient());
        loadUrlFromIntent();
    }

    private void loadUrlFromIntent() {
        String urlFromIntent = getIntent().getStringExtra("url");
        if (urlFromIntent == null || urlFromIntent.trim().isEmpty()) {
            urlFromIntent = DEFAULT_URL;
        }
        if (isValidUrl(urlFromIntent)) {
            webView.loadUrl(urlFromIntent);
        } else {
            webView.loadUrl(DEFAULT_URL);
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

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
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
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(view, request.getUrl().toString());
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
            if (url.startsWith("http") || url.startsWith("https")) {
                final PayTask task = new PayTask(SWebView.this);
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
            } else {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        }
    }
}