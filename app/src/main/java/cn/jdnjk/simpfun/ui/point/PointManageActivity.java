package cn.jdnjk.simpfun.ui.point;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.utils.ThemeUtils;

public class PointManageActivity extends AppCompatActivity {

    public static final String EXTRA_TAB = "tab";
    public static final String TAB_POINTS = "points";
    public static final String TAB_DIAMONDS = "diamonds";

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    public void showRecharge(boolean show) {
        if (tabLayout != null) {
            tabLayout.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (viewPager != null) {
            viewPager.setUserInputEnabled(!show);
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(show ? "充值" : "记录");
        }
    }

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
        setContentView(R.layout.activity_point_manage);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        applyWindowInsets();

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return PointHistoryFragment.newInstance(PointHistoryFragment.TYPE_POINTS);
                } else {
                    return PointHistoryFragment.newInstance(PointHistoryFragment.TYPE_DIAMONDS);
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(R.string.tab_points);
            } else {
                tab.setText(R.string.tab_diamonds);
            }
        }).attach();

        String tab = getIntent() != null ? getIntent().getStringExtra(EXTRA_TAB) : null;
        if (TAB_DIAMONDS.equals(tab)) {
            viewPager.setCurrentItem(1, false);
        } else {
            viewPager.setCurrentItem(0, false);
        }
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.point_manage_root);
        View appBar = findViewById(R.id.app_bar_layout);
        int initialAppBarTop = appBar.getPaddingTop();
        int initialViewPagerBottom = viewPager.getPaddingBottom();
        viewPager.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            appBar.setPadding(appBar.getPaddingLeft(), initialAppBarTop + safeInsets.top, appBar.getPaddingRight(), appBar.getPaddingBottom());
            viewPager.setPadding(viewPager.getPaddingLeft(), viewPager.getPaddingTop(), viewPager.getPaddingRight(), initialViewPagerBottom + navigationInsets.bottom);
            return insets;
        });
    }

    @Override
    public void onBackPressed() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFragment instanceof PointHistoryFragment) {
            PointHistoryFragment historyFragment = (PointHistoryFragment) currentFragment;
            if (historyFragment.isRechargeVisible()) {
                historyFragment.toggleRecharge();
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
