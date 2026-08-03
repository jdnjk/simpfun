package cn.jdnjk.simpfun;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.file.FileCallback;
import cn.jdnjk.simpfun.utils.EditorMenuHandler;
import cn.jdnjk.simpfun.utils.Feedback;
import cn.jdnjk.simpfun.utils.ThemeUtils;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.widget.CodeEditor;
import android.graphics.Typeface;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import org.eclipse.tm4e.core.registry.IThemeSource;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import org.json.JSONObject;

public class FileEditorActivity extends AppCompatActivity {

    private CodeEditor codeEditor;
    private TextView tvFilename;
    private TextView tvCursorPosition;
    private ImageView btnUndo;
    private ImageView btnRedo;
    private ImageView btnSave;
    private ProgressBar loadingIndicator;
    private boolean isModified = false;
    private boolean isSaving = false;
    private boolean wordWrapEnabled = true;
    private String fileName;
    private static boolean textMateInited = false;
    private ActivityResultLauncher<android.content.Intent> saveAsLauncher;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final FileApi fileApi = new FileApi();

    private static final Map<String, String> EXTENSION_TO_SCOPE = new HashMap<>();

    static {
        EXTENSION_TO_SCOPE.put(".json", "source.json");

        EXTENSION_TO_SCOPE.put(".log", "text.log");

        EXTENSION_TO_SCOPE.put(".yaml", "source.yaml");
        EXTENSION_TO_SCOPE.put(".yml", "source.yaml");

        EXTENSION_TO_SCOPE.put(".js", "source.js");

        EXTENSION_TO_SCOPE.put(".html", "text.html.basic");
        EXTENSION_TO_SCOPE.put(".htm", "text.html.basic");

        EXTENSION_TO_SCOPE.put(".xml", "text.xml");

        EXTENSION_TO_SCOPE.put(".md", "text.html.markdown");
        EXTENSION_TO_SCOPE.put(".markdown", "text.html.markdown");

        EXTENSION_TO_SCOPE.put(".sh", "source.shell");
        EXTENSION_TO_SCOPE.put(".bash", "source.shell");
        EXTENSION_TO_SCOPE.put(".bashrc", "source.shell");
        EXTENSION_TO_SCOPE.put(".profile", "source.shell");

        EXTENSION_TO_SCOPE.put(".ini", "source.ini");
        EXTENSION_TO_SCOPE.put(".conf", "source.ini");
        EXTENSION_TO_SCOPE.put(".cfg", "source.ini");
        EXTENSION_TO_SCOPE.put(".properties", "source.ini");

        EXTENSION_TO_SCOPE.put(".bat", "source.batchfile");
        EXTENSION_TO_SCOPE.put(".cmd", "source.batchfile");

        EXTENSION_TO_SCOPE.put(".java", "source.java");
        EXTENSION_TO_SCOPE.put(".jav", "source.java");

        EXTENSION_TO_SCOPE.put(".toml", "source.toml");

        EXTENSION_TO_SCOPE.put(".kts", "source.kotlin");
        EXTENSION_TO_SCOPE.put(".kt", "source.kotlin");
        EXTENSION_TO_SCOPE.put(".ktm", "source.kotlin");

        EXTENSION_TO_SCOPE.put(".c", "source.c");
        EXTENSION_TO_SCOPE.put(".h", "source.c");

        EXTENSION_TO_SCOPE.put(".cpp", "source.cpp");
        EXTENSION_TO_SCOPE.put(".cc", "source.cpp");
        EXTENSION_TO_SCOPE.put(".cxx", "source.cpp");
        EXTENSION_TO_SCOPE.put(".c++", "source.cpp");
        EXTENSION_TO_SCOPE.put(".hpp", "source.cpp");
        EXTENSION_TO_SCOPE.put(".hh", "source.cpp");
        EXTENSION_TO_SCOPE.put(".hxx", "source.cpp");
    }

    private void ensureTextMateInited() {
        if (textMateInited) return;
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getApplicationContext().getAssets()));

            var themeRegistry = ThemeRegistry.getInstance();
            boolean themeLoaded = false;

            var vscodeThemes = new String[]{"2026-dark", "2026-light"};
            for (String name : vscodeThemes) {
                var themeAssetsPath = "editor/themes/" + name + ".json";
                var themeStream = FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath);

                if (themeStream != null) {
                    var model = new ThemeModel(
                            IThemeSource.fromInputStream(themeStream, themeAssetsPath, null),
                            name
                    );
                    model.setDark(name.contains("dark"));
                    themeRegistry.loadTheme(model);
                    themeLoaded = true;
                }
            }

            // 根据应用主题设置选择默认编辑器主题
            String defaultTheme = ThemeUtils.isEffectiveDarkMode(this) ? "2026-dark" : "2026-light";
            themeRegistry.setTheme(defaultTheme);

            if (!themeLoaded) {
                 Log.w("FileEditorActivity", "未找到任何主题文件, 将禁用 TextMate");
                 textMateInited = false;
                 return;
            }

            // 加载语法定义
            var languagesPath = "editor/textmate/languages.json";
            try (var langStream = FileProviderRegistry.getInstance().tryGetInputStream(languagesPath)) {
                if (langStream == null) {
                    Log.w("FileEditorActivity", "未找到语法定义文件: " + languagesPath + ", 将禁用 TextMate");
                    textMateInited = false;
                    return;
                }
            }
            // GrammarRegistry.loadGrammars 会自行读取文件内容，这里仅用于存在性检查
            GrammarRegistry.getInstance().loadGrammars(languagesPath);
            textMateInited = true;
        } catch (Exception e) {
            Log.w("FileEditorActivity", "TextMate初始化失败", e);
            Toast.makeText(this, "TextMate初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            textMateInited = false;
        } catch (Throwable th) {
            Log.e("FileEditorActivity", "TextMate初始化严重错误", th);
            Toast.makeText(this, "TextMate初始化严重错误: " + th.getMessage(), Toast.LENGTH_LONG).show();
            textMateInited = false;
        }
    }

    private void applyLanguageForCurrentFile() {
        if (remotePath == null) return;
        try {
            ensureTextMateInited();
            // 仅在 TextMate 初始化成功后应用颜色方案与语言
            if (textMateInited) {
                // 始终根据当前应用主题设置编辑器主题
                applyEditorThemeByAppTheme();
            }

            String lower = remotePath.toLowerCase();
            String scope = null;

            // 根据文件扩展名查找对应的scope
            for (Map.Entry<String, String> entry : EXTENSION_TO_SCOPE.entrySet()) {
                if (lower.endsWith(entry.getKey())) {
                    scope = entry.getValue();
                    break;
                }
            }

            setLanguage(scope);

        } catch (Exception e) {
            Log.w("FileEditorActivity", "语言应用失败", e);
            Toast.makeText(this, "语言应用失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            Log.e("FileEditorActivity", "语言应用严重错误", th);
            Toast.makeText(this, "语言应用严重错误: " + th.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void applyLanguageAuto() {
        applyLanguageForCurrentFile();
    }

    public void setLanguage(String scope) {
        if (textMateInited) {
             if (scope != null) {
                 try {
                     var language = TextMateLanguage.create(scope, true);
                     codeEditor.setEditorLanguage(language);
                 } catch (IllegalArgumentException e) {
                     // 捕获 GrammarRegistry 抛出的 scope 未找到异常
                     Log.w("FileEditorActivity", "语言scope未找到: " + scope);
                     Toast.makeText(this, "该语言语法尚未加载: " + scope, Toast.LENGTH_SHORT).show();
                     codeEditor.setEditorLanguage(null);
                 } catch (Exception e) {
                     Log.e("FileEditorActivity", "设置语言失败", e);
                     Toast.makeText(this, "设置语言失败", Toast.LENGTH_SHORT).show();
                 }
             } else {
                 codeEditor.setEditorLanguage(null);
             }
        }
    }

    /**
     * 根据当前应用主题切换编辑器主题
     */
    private void applyEditorThemeByAppTheme() {
        if (!textMateInited) return;
        try {
            var themeRegistry = ThemeRegistry.getInstance();
            String targetTheme = ThemeUtils.isEffectiveDarkMode(this) ? "2026-dark" : "2026-light";
            themeRegistry.setTheme(targetTheme);
            codeEditor.setColorScheme(TextMateColorScheme.create(themeRegistry));
        } catch (Exception e) {
            Log.w("FileEditorActivity", "切换编辑器主题失败", e);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 应用主题变化时自动切换编辑器主题
        applyEditorThemeByAppTheme();
    }

    private int serverId = -1;
    private String remotePath;

    private void applyWindowInsets() {
        View root = findViewById(R.id.root_file_editor);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void updateUIState() {
        // Filename
        String displayFileName = (isModified ? "*" : "") + (fileName == null ? "" : fileName);
        if (tvFilename != null) {
            tvFilename.setText(displayFileName);
        }

        // Save Button
        if (btnSave != null) {
            btnSave.setEnabled(isModified);
            btnSave.setColorFilter(isModified ? Color.WHITE : Color.GRAY);
        }

        // Undo/Redo
        if (codeEditor != null) {
            boolean canUndo = codeEditor.canUndo();
            boolean canRedo = codeEditor.canRedo();

            if (btnUndo != null) {
                btnUndo.setEnabled(canUndo);
                btnUndo.setColorFilter(canUndo ? Color.WHITE : Color.GRAY);
            }
            if (btnRedo != null) {
                btnRedo.setEnabled(canRedo);
                btnRedo.setColorFilter(canRedo ? Color.WHITE : Color.GRAY);
            }
        }
    }

    /**
     * 通过 API 保存文件内容到服务器。
     */
    private void saveFile() {
        if (remotePath == null || serverId <= 0) {
            Feedback.error(this, "路径无效");
            return;
        }
        if (isSaving) {
            return; // 已有保存在进行中，避免并发保存
        }
        isSaving = true;
        final String text = getTextToSave();
        setEditorEnabled(false);

        fileApi.saveFileContent(this, serverId, remotePath, text, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                isSaving = false;
                if (isFinishing() || isDestroyed()) return;
                isModified = false;
                setEditorEnabled(true);
                updateUIState();
            }

            @Override
            public void onFailure(String errorMsg) {
                isSaving = false;
                if (isFinishing() || isDestroyed()) return;
                setEditorEnabled(true);
                updateUIState();
                Feedback.error(FileEditorActivity.this, "保存失败: " + errorMsg);
            }
        });
    }

    /**
     * 获取待保存文本。.bat/.cmd 依赖 CRLF，不做换行符转换；其余统一转换为 LF。
     */
    private String getTextToSave() {
        String text = codeEditor.getText().toString();
        if (fileName != null) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".bat") || lower.endsWith(".cmd")) {
                return text;
            }
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private void setEditorEnabled(boolean enabled) {
        if (codeEditor != null) {
            codeEditor.setEnabled(enabled);
        }
        if (btnSave != null) {
            btnSave.setEnabled(enabled);
        }
    }

    @Override
    public void onBackPressed() {
        if (isSaving) {
            // 正在保存，忽略返回键，避免发起并发保存
            return;
        }
        if (isModified) {
            showExitConfirmDialog();
        } else {
            // 未修改时直接退出。不要用 getOnBackPressedDispatcher().onBackPressed()，
            // 它在部分场景下不会结束当前 Activity（表现为不编辑无法退出）。
            finish();
        }
    }

    private void showExitConfirmDialog() {
        String title = fileName != null ? fileName : "";
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage("是否保存文件?")
                .setNegativeButton("不保存", (d, which) -> finish())
                .setPositiveButton("保存并退出", (d, which) -> saveFileAndFinish())
                .show();
        // 标题单行显示，超出部分省略
        TextView titleView = dialog.findViewById(android.R.id.title);
        if (titleView != null) {
            titleView.setSingleLine(true);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
    }

    /**
     * 保存文件后退出（不提示保存结果）
     */
    private void saveFileAndFinish() {
        if (remotePath == null || serverId <= 0) {
            finish();
            return;
        }
        if (isSaving) {
            return; // 已有保存在进行中，避免并发保存
        }
        isSaving = true;
        final String text = getTextToSave();
        setEditorEnabled(false);

        fileApi.saveFileContent(this, serverId, remotePath, text, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                isSaving = false;
                if (isFinishing() || isDestroyed()) return;
                isModified = false;
                finish();
            }

            @Override
            public void onFailure(String errorMsg) {
                isSaving = false;
                if (isFinishing() || isDestroyed()) return;
                setEditorEnabled(true);
                Feedback.error(FileEditorActivity.this, "保存失败: " + errorMsg);
            }
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        ThemeUtils.applyEdgeToEdge(this);

        setContentView(R.layout.activity_file_editor);
        applyWindowInsets();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Initialize views
        tvFilename = findViewById(R.id.tv_filename);
        tvCursorPosition = findViewById(R.id.tv_cursor_position);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);
        loadingIndicator = findViewById(R.id.loading_indicator);

        codeEditor = findViewById(R.id.code_editor);
        codeEditor.setWordwrap(wordWrapEnabled);
        codeEditor.setEnabled(false); // 加载前禁用编辑
        try {
            codeEditor.setTypefaceText(Typeface.createFromAsset(getAssets(), "editor/JetBrainsMonoNL-Regular.ttf"));
        } catch (Exception e) {
            codeEditor.setTypefaceText(Typeface.MONOSPACE);
        }

        // Setup listeners
        btnUndo.setOnClickListener(v -> {
            if (codeEditor != null) codeEditor.undo();
        });
        btnRedo.setOnClickListener(v -> {
            if (codeEditor != null) codeEditor.redo();
        });
        btnSave.setOnClickListener(v -> saveFile());

        ImageView btnMore = findViewById(R.id.btn_more);
        btnMore.setOnClickListener(v -> new EditorMenuHandler(this, codeEditor, remotePath, wordWrapEnabled,
                enabled -> wordWrapEnabled = enabled).showMenu(v));

        codeEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            isModified = true;
            updateUIState();
        });

        codeEditor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> {
            if (tvCursorPosition != null) {
                var cursor = event.getLeft();
                tvCursorPosition.setText(String.format(Locale.getDefault(), "%d:%d", cursor.line + 1, cursor.column + 1));
            }
        });

        // Initialize ActivityResultLauncher
        saveAsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                         saveContentToUri(result.getData().getData());
                    }
                }
        );

        // Intent data
        fileName = getIntent().getStringExtra("file_name");
        remotePath = getIntent().getStringExtra("remote_path");
        serverId = getIntent().getIntExtra("server_id", -1);

        if (remotePath != null && serverId > 0) {
            if (fileName == null) {
                // 从 remotePath 提取文件名
                int lastSlash = remotePath.lastIndexOf('/');
                fileName = lastSlash >= 0 ? remotePath.substring(lastSlash + 1) : remotePath;
            }
            applyLanguageForCurrentFile();
            fetchContent();
        } else {
            Feedback.error(this, "参数无效");
            finish();
        }

        updateUIState();
    }

    /**
     * 通过 API 从服务器获取文件内容
     */
    private void fetchContent() {
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }
        fileApi.fetchFileContent(this, serverId, remotePath, new FileCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                String content = data.optString("content", "");
                codeEditor.setText(content);
                codeEditor.setEnabled(true);
                isModified = false;
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }
                updateUIState();
            }

            @Override
            public void onFailure(String errorMsg) {
                if (isFinishing() || isDestroyed()) return;
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }
                // 弹窗说明失败原因，避免一闪而过又退回文件列表
                new MaterialAlertDialogBuilder(FileEditorActivity.this)
                        .setTitle("无法打开文件")
                        .setMessage(errorMsg)
                        .setNegativeButton("重试", (d, which) -> fetchContent())
                        .setPositiveButton("返回", (d, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });
    }


    public void launchSaveAs() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_TITLE, fileName != null ? fileName : "untitled.txt");
        try {
            saveAsLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Feedback.error(this, "未找到文件管理器");
        }
    }


    private void saveContentToUri(android.net.Uri uri) {
        final String text = codeEditor.getText().toString();
        ioExecutor.execute(() -> {
            String failure = null;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) {
                    failure = "无法打开输出流";
                } else {
                    os.write(text.getBytes());
                }
            } catch (Exception e) {
                Log.e("FileEditorActivity", "Save as error", e);
                failure = "保存失败: " + e.getMessage();
            }
            final String message = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                updateUIState();
                if (message != null) {
                    Feedback.error(FileEditorActivity.this, message);
                } else {
                    Feedback.info(FileEditorActivity.this, "另存为成功");
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
