package cn.jdnjk.simpfun.ui.point;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.utils.ThemeUtils;

/**
 * 明细 + 充值 宿主 Activity。
 * 明细页常驻；充值页以从底部滑入的方式覆盖在明细页上，返回时反向播放。
 */
public class PointManageActivity extends AppCompatActivity implements RechargeHomeFragment.OnPayFlowListener {

    public static final String EXTRA_TAB = "tab";
    public static final String TAB_POINTS = "points";
    public static final String TAB_DIAMONDS = "diamonds";

    public static final String EXTRA_RECHARGE_TAB = "recharge_tab";
    /** 充值页标签：0=充积分 1=签到卡 2=充流量 */
    public static final int RECHARGE_TAB_TRAFFIC = 2;
    public static final String EXTRA_PRESELECT_INSTANCE = "preselect_instance";

    /** SharedPreferences key：支付/购买成功后置位，ProfileFragment.onResume 据此刷新个人主页。 */
    private static final String KEY_POINT_DIRTY = "point_dirty";

    private static final String TAG_RECHARGE = "recharge";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        ThemeUtils.applyEdgeToEdge(this);
        setContentView(R.layout.activity_point_manage);

        View root = findViewById(R.id.fragment_container);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        int initialTab = TAB_DIAMONDS.equals(getIntent() != null ? getIntent().getStringExtra(EXTRA_TAB) : null) ? 1 : 0;
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, RecordFragment.newInstance(initialTab))
                    .commit();
        }

        if (getIntent() != null && getIntent().hasExtra(EXTRA_RECHARGE_TAB)) {
            int rechargeTab = getIntent().getIntExtra(EXTRA_RECHARGE_TAB, 0);
            int preselectInstance = getIntent().getIntExtra(EXTRA_PRESELECT_INSTANCE, -1);
            openRecharge(rechargeTab, preselectInstance);
        }
    }

    /** 从底部滑入充值页；已在充值页时忽略。 */
    public void openRecharge(int rechargeTab, int preselectInstance) {
        if (getSupportFragmentManager().findFragmentByTag(TAG_RECHARGE) != null) return;
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_bottom, R.anim.fade_out,
                        R.anim.fade_in, R.anim.slide_out_bottom)
                .add(R.id.fragment_container,
                        RechargeHomeFragment.newInstance(rechargeTab, preselectInstance), TAG_RECHARGE)
                .addToBackStack(TAG_RECHARGE)
                .commit();
    }

    /** 充值流程结束后（支付成功/流量包购买成功）刷新明细，并标记个人主页待刷新。
     *  RechargeHomeFragment 以 add 方式叠加在 RecordFragment 之上，findFragmentById
     *  返回的是栈顶的 RechargeHomeFragment，因此必须遍历 fragments 才能找到 RecordFragment。 */
    @Override
    public void onPayFlowSuccess() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof RecordFragment) {
                ((RecordFragment) fragment).refreshAll();
            }
        }
        markPointDirty(this);
    }

    /** 标记个人主页需要在 onResume 时刷新用户资产。 */
    public static void markPointDirty(@NonNull Context context) {
        context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_POINT_DIRTY, true).apply();
    }

    /** ProfileFragment.onResume 调用：读取并清除标记。 */
    public static boolean consumePointDirty(@NonNull Context context) {
        android.content.SharedPreferences sp =
                context.getSharedPreferences("user_info", Context.MODE_PRIVATE);
        boolean dirty = sp.getBoolean(KEY_POINT_DIRTY, false);
        if (dirty) {
            sp.edit().putBoolean(KEY_POINT_DIRTY, false).apply();
        }
        return dirty;
    }

    @Override
    public boolean onSupportNavigateUp() {
        getSupportFragmentManager().popBackStack();
        return true;
    }
}
