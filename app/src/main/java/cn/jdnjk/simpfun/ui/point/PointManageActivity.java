package cn.jdnjk.simpfun.ui.point;

import android.os.Bundle;
import android.view.MenuItem;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import cn.jdnjk.simpfun.R;

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_point_manage);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

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
