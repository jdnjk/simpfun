package cn.jdnjk.simpfun.ui.point;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.PayApi;
import cn.jdnjk.simpfun.databinding.FragmentRechargeHomeBinding;
import cn.jdnjk.simpfun.model.RechargeMeta;

/**
 * 充值主页：充积分 / 签到卡 / 充流量 三个平铺标签页，可左右滑动切换；
 * meta 接口在此统一加载后分发给子页。
 */
public class RechargeHomeFragment extends Fragment {

    public interface MetaListener {
        void onMetaLoaded(RechargeMeta meta);
    }

    /** 由宿主 Activity 实现：支付/购买成功后刷新明细。 */
    public interface OnPayFlowListener {
        void onPayFlowSuccess();
    }

    private static final String ARG_RECHARGE_TAB = "recharge_tab";
    private static final String ARG_PRESELECT_INSTANCE = "preselect_instance";
    private static final String TAG = "RechargeHomeFragment";

    private FragmentRechargeHomeBinding binding;
    private final PayApi payApi = new PayApi();
    private RechargeMeta meta;
    private final List<MetaListener> metaListeners = new ArrayList<>();
    private int argRechargeTab;
    private int argPreselectInstance = -1;
    private boolean suppressTabEvent;
    /** 本次进入充值页期间已同意过充值前须知（退出充值页后重置）。 */
    private boolean publicNoticeConfirmed;

    public static RechargeHomeFragment newInstance(int rechargeTab, int preselectInstance) {
        RechargeHomeFragment fragment = new RechargeHomeFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_RECHARGE_TAB, rechargeTab);
        args.putInt(ARG_PRESELECT_INSTANCE, preselectInstance);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            argRechargeTab = getArguments().getInt(ARG_RECHARGE_TAB, 0);
            argPreselectInstance = getArguments().getInt(ARG_PRESELECT_INSTANCE, -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRechargeHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.toolbar.setNavigationOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rechargeRoot, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.rechargeAppbar.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        binding.pagerRecharge.setAdapter(new FragmentStateAdapter(getChildFragmentManager(), getLifecycle()) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 1:
                        return new CardsRechargeFragment();
                    case 2:
                        return new TrafficRechargeFragment();
                    default:
                        return new PointsRechargeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });
        binding.pagerRecharge.setOffscreenPageLimit(2);

        setupTabs();

        if (argRechargeTab >= 0 && argRechargeTab < binding.tabCategory.getTabCount()) {
            binding.tabCategory.selectTab(binding.tabCategory.getTabAt(argRechargeTab));
        }

        loadMeta();
    }

    private void setupTabs() {
        String[] titles = {
                getString(R.string.tab_recharge_points),
                getString(R.string.tab_recharge_cards),
                getString(R.string.tab_recharge_traffic)
        };
        for (String title : titles) {
            binding.tabCategory.addTab(binding.tabCategory.newTab().setText(title));
        }

        // 点击标签：淡入切换；滑动标签页：指示条跟随
        binding.tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (suppressTabEvent) return;
                fadeSwitchTo(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.pagerRecharge.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                suppressTabEvent = true;
                binding.tabCategory.selectTab(binding.tabCategory.getTabAt(position));
                suppressTabEvent = false;
            }
        });
    }

    /** 点击标签时以淡入方式切换内容（左右滑动手势仍由 ViewPager2 原生跟手）。 */
    private void fadeSwitchTo(int position) {
        if (binding == null || binding.pagerRecharge.getCurrentItem() == position) return;
        ViewPager2 pager = binding.pagerRecharge;
        pager.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            if (!isViewAlive()) return;
            pager.setCurrentItem(position, false);
            pager.animate().alpha(1f).setDuration(200).start();
        }).start();
    }

    private void loadMeta() {
        Context context = getContext();
        if (context == null) return;
        String token = context.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) return;

        payApi.getRechargeMeta(token, new PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewAlive()) return;
                if (data.optInt("code") == 200) {
                    JSONObject payload = data.optJSONObject("data");
                    if (payload != null) {
                        meta = RechargeMeta.parse(payload);
                        dispatchMeta();
                    }
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "loadMeta failed: " + errorMsg);
                if (!isViewAlive()) return;
                View v = getView();
                if (v != null) {
                    Snackbar.make(v, "加载充值信息失败: " + errorMsg, Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void dispatchMeta() {
        if (meta == null) return;
        for (MetaListener listener : new ArrayList<>(metaListeners)) {
            listener.onMetaLoaded(meta);
        }
    }

    /** 子页在 onViewCreated 调用：meta 已就绪立即回调；之后每次 reloadMeta 都会重新回调。 */
    public void whenMetaReady(MetaListener listener) {
        if (!isAdded()) return;
        if (!metaListeners.contains(listener)) {
            metaListeners.add(listener);
        }
        if (meta != null) {
            listener.onMetaLoaded(meta);
        }
    }

    /** 重新拉取 meta（支付成功 / 购买流量后调用），并通知明细页刷新。 */
    public void reloadMetaAndNotify() {
        loadMeta();
        Activity activity = getActivity();
        if (activity instanceof OnPayFlowListener) {
            ((OnPayFlowListener) activity).onPayFlowSuccess();
        }
    }

    public int getPreselectInstance() {
        return argPreselectInstance;
    }

    /** 是否已同意过充值前须知（仅限本次充值页会话）。 */
    public boolean isPublicNoticeConfirmed() {
        return publicNoticeConfirmed;
    }

    public void setPublicNoticeConfirmed(boolean confirmed) {
        this.publicNoticeConfirmed = confirmed;
    }

    private boolean isViewAlive() {
        return binding != null && isAdded();
    }

    @Override
    public void onDestroyView() {
        metaListeners.clear();
        binding = null;
        super.onDestroyView();
    }
}
