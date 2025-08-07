package cn.jdnjk.simpfun;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import java.net.MalformedURLException;
import java.net.URL;

public class SWebView extends AppCompatActivity {

    private WebView webView;
    private static final String DEFAULT_URL = "https://cn.bing.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        webView = findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; U; Android; zh-cn;) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/138.0.0.0 MQQBrowser/9.1 Mobile Safari/537.36 SimpfunAPP/1.0");

        webView.setWebViewClient(new WebViewClient());
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
}