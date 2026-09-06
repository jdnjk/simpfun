package cn.jdnjk.simpfun.ui.setting;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.SWebView;
import cn.jdnjk.simpfun.ui.troubleshoot.TroubleshootActivity;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import androidx.fragment.app.Fragment;

public class SettingsActivity extends AppCompatActivity {
    public static final String TUTORIAL_DOCUMENTATION_URL = "https://www.yuque.com/simpfox/simpdoc/main";
    public static final String QQ_GROUP_HELP_URL = "https://www.yuque.com/simpfox/simpdoc/joinqqgroup";
    private static final int DEBUG_TAP_THRESHOLD = 5;
    private static final long DEBUG_TAP_WINDOW_MS = 1000L;
    private static final String DEFAULT_TITLE = "设置";
    private int debugTapCount = 0;
    private final Handler debugTapHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetTapRunnable = () -> debugTapCount = 0;
    private MaterialToolbar toolbar;
    private MenuItem helpMenuItem;
    /** 顶栏问号当前打开的目标。子页面可覆盖（如 QQ 绑定页指向加群文档），离开后复原。 */
    private String helpUrl = TUTORIAL_DOCUMENTATION_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        ThemeUtils.applyEdgeToEdge(this);

        setContentView(R.layout.activity_settings);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setTitle(DEFAULT_TITLE);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings_help) {
                openTutorialDocumentation();
                return true;
            }
            if (id == R.id.action_add_action) {
                Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (f instanceof QuickCommandEditorFragment) {
                    ((QuickCommandEditorFragment) f).addAction();
                }
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

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }
    }

    /**
     * 子 Fragment 在 toolbar 上显示"添加动作" + 按钮，隐藏问号。
     */
    public void showAddActionToolbarButton() {
        if (toolbar == null) return;
        Menu menu = toolbar.getMenu();
        helpMenuItem = menu.findItem(R.id.action_settings_help);
        if (helpMenuItem != null) helpMenuItem.setVisible(false);
        menu.add(Menu.NONE, R.id.action_add_action, 0, "添加动作")
                .setIcon(R.drawable.ic_add_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    /**
     * 恢复默认问号按钮，移除 + 按钮。
     */
    public void restoreToolbarButtons() {
        if (toolbar == null) return;
        Menu menu = toolbar.getMenu();
        MenuItem add = menu.findItem(R.id.action_add_action);
        if (add != null) menu.removeItem(R.id.action_add_action);
        if (helpMenuItem != null) helpMenuItem.setVisible(true);
    }

    /**
     * 子页面覆盖顶栏问号打开的链接（如 QQ 绑定页指向加群文档）。
     * 页面 onDestroyView 时应调 {@link #restoreDefaultHelpUrl()} 复原。
     */
    public void overrideHelpUrl(String url) {
        helpUrl = (url == null || url.trim().isEmpty()) ? TUTORIAL_DOCUMENTATION_URL : url;
    }

    /** 复原顶栏问号到默认教程链接。 */
    public void restoreDefaultHelpUrl() {
        helpUrl = TUTORIAL_DOCUMENTATION_URL;
    }

    private void openTutorialDocumentation() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl)));
        } catch (Exception e) {
            openTutorialDocumentationInWebView();
        }
    }

    private void openTutorialDocumentationInWebView() {
        try {
            Intent intent = new Intent(this, SWebView.class);
            intent.putExtra("url", helpUrl);
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

    /**
     * 子 Fragment 复用的动态标题。
     */
    public void setAppBarTitle(String title) {
        if (toolbar != null) toolbar.setTitle(title);
    }

    @Override
    public void onBackPressed() {
        Fragment top = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (top instanceof QuickCommandEditorFragment) {
            // 编辑页有未保存改动时弹窗询问，而非直接返回
            if (((QuickCommandEditorFragment) top).handleBackPress()) {
                return;
            }
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}