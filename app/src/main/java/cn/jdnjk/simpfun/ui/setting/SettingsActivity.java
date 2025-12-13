package cn.jdnjk.simpfun.ui.setting;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import cn.jdnjk.simpfun.R;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

public class SettingsActivity extends AppCompatActivity {
    private static final int DEBUG_TAP_THRESHOLD = 5;
    private static final long DEBUG_TAP_WINDOW_MS = 1000L;
    private int debugTapCount = 0;
    private final Handler debugTapHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetTapRunnable = () -> debugTapCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
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

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment())
                    .commit();
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
}