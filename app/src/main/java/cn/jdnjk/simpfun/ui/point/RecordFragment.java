package cn.jdnjk.simpfun.ui.point;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.PayApi;
import cn.jdnjk.simpfun.databinding.FragmentRecordBinding;
import cn.jdnjk.simpfun.model.RechargeMeta;

/**
 * 明细页：余额卡（资产 + 签到卡状态）、积分/钻石两套流水、右下角 FAB 进入充值。
 */
public class RecordFragment extends Fragment {

    private static final String ARG_INITIAL_TAB = "initial_tab";
    private static final String TAG = "RecordFragment";

    private FragmentRecordBinding binding;
    private final PayApi payApi = new PayApi();
    private int initialTab;

    public static RecordFragment newInstance(int initialTab) {
        RecordFragment fragment = new RecordFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INITIAL_TAB, initialTab);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            initialTab = getArguments().getInt(ARG_INITIAL_TAB, 0);
        }
        binding = FragmentRecordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

        ViewCompat.setOnApplyWindowInsetsListener(binding.recordRoot, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.recordAppbar.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        binding.pagerRecords.setAdapter(new FragmentStateAdapter(getChildFragmentManager(), getLifecycle()) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return RecordPageFragment.newInstance(
                        position == 0 ? RecordPageFragment.TYPE_POINTS : RecordPageFragment.TYPE_DIAMONDS);
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(binding.tabCurrency, binding.pagerRecords, (tab, position) ->
                tab.setText(position == 0 ? R.string.tab_points : R.string.tab_diamonds)).attach();

        binding.pagerRecords.setCurrentItem(Math.max(0, Math.min(1, initialTab)), false);

        binding.fabRecharge.setOnClickListener(v -> {
            if (getActivity() instanceof PointManageActivity) {
                ((PointManageActivity) requireActivity()).openRecharge(0, -1);
            }
        });

        loadMeta();
    }

    /** 充值成功后由宿主 Activity 回调：刷新余额卡，当前流水页自行下拉或自动刷新。 */
    public void refreshAll() {
        loadMeta();
        Fragment fragment = getChildFragmentManager().findFragmentByTag("f" + binding.pagerRecords.getCurrentItem());
        if (fragment instanceof RecordPageFragment) {
            ((RecordPageFragment) fragment).refresh();
        }
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
                        renderBalance(RechargeMeta.parse(payload));
                    }
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "loadMeta failed: " + errorMsg);
            }
        });
    }

    private void renderBalance(RechargeMeta meta) {
        if (!isViewAlive()) return;
        binding.tvBalance.setText(String.format(Locale.getDefault(), "%,d 积分 · %,d 钻石", meta.point, meta.diamond));
        binding.tvBenefitStatus.setText(buildBenefitStatus(meta));
    }

    private String buildBenefitStatus(RechargeMeta meta) {
        return describeCard("双倍积分卡", meta.doubleSignValid, meta.doubleSignExpireSeconds)
                + " · "
                + describeCard("自动签到卡", meta.autoSignValid, meta.autoSignExpireSeconds);
    }

    private String describeCard(String name, boolean valid, long expireSeconds) {
        if (valid && expireSeconds > 0) {
            String date = new SimpleDateFormat("M月d日", Locale.getDefault())
                    .format(new Date(expireSeconds * 1000L));
            return name + " " + date + "到期";
        }
        return name + " 未生效";
    }

    private boolean isViewAlive() {
        return binding != null && isAdded();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
