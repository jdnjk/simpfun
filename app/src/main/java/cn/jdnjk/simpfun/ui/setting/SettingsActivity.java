package cn.jdnjk.simpfun.ui.setting;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.appbar.MaterialToolbar;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SWebView;
import cn.jdnjk.simpfun.ui.troubleshoot.TroubleshootActivity;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

public class SettingsActivity extends AppCompatActivity {
    private static final String TUTORIAL_DOCUMENTATION_URL = "https://www.yuque.com/simpfox/simpdoc/main";
    private static final int DEBUG_TAP_THRESHOLD = 5;
    private static final long DEBUG_TAP_WINDOW_MS = 1000L;
    private int debugTapCount = 0;
    private final Handler debugTapHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetTapRunnable = () -> debugTapCount = 0;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(true);
        }

        setContentView(R.layout.activity_settings);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings_help) {
                openTutorialDocumentation();
                return true;
            }
            return false;
        });
        toolbar.setOnClickListener(v -> {
            debugTapCount++;
            debugTapHandler.removeCallbacks(resetTapRunnable);
            debugTapHandler.postDelayed(resetTapRunnable, DEBUG_TAP_WINDOW_MS);
            if (debugTapCount >= DEBUG_TAP_THRESHOLD) {
                debugTapHandler.removeCallbacks(resetTapRunnable);
                debugTapCount = 0;
                openDebugPage();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }
    }

    private void openTutorialDocumentation() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TUTORIAL_DOCUMENTATION_URL)));
        } catch (Exception e) {
            openTutorialDocumentationInWebView();
        }
    }

    private void openTutorialDocumentationInWebView() {
        try {
            Intent intent = new Intent(this, SWebView.class);
            intent.putExtra("url", TUTORIAL_DOCUMENTATION_URL);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开教程文档", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDebugPage() {
        Fragment debugFragment = new DebugFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, debugFragment)
                .addToBackStack("debug")
                .commit();
    }

    public void openTroubleshootPage() {
        startActivity(new Intent(this, TroubleshootActivity.class));
    }
}