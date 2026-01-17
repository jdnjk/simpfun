package cn.jdnjk.simpfun.ui.ins.files;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.graphics.Color;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import cn.jdnjk.simpfun.R;
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
import android.view.View;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;

public class FileEditorActivity extends AppCompatActivity {

    private CodeEditor codeEditor;
    private TextView tvFilename;
    private TextView tvCursorPosition;
    private ImageView btnUndo;
    private ImageView btnRedo;
    private ImageView btnSave;
    private boolean isModified = false;
    private String localPath;
    private String fileName;
    private static boolean textMateInited = false;

    private static final Map<String, String> EXTENSION_TO_SCOPE = new HashMap<>();

    static {
        EXTENSION_TO_SCOPE.put(".json", "source.json");
        EXTENSION_TO_SCOPE.put(".log", "text.log");
        EXTENSION_TO_SCOPE.put(".yaml", "source.yaml");
        EXTENSION_TO_SCOPE.put(".yml", "source.yaml");
        EXTENSION_TO_SCOPE.put(".py", "source.python");
        EXTENSION_TO_SCOPE.put(".js", "source.js");
        EXTENSION_TO_SCOPE.put(".html", "text.html.basic");
        EXTENSION_TO_SCOPE.put(".htm", "text.html.basic");
        EXTENSION_TO_SCOPE.put(".xml", "text.xml");
        EXTENSION_TO_SCOPE.put(".md", "text.html.markdown");
        EXTENSION_TO_SCOPE.put(".markdown", "text.html.markdown");
        // EXTENSION_TO_SCOPE.put(".css", "source.css");
        // EXTENSION_TO_SCOPE.put(".php", "source.php");
        // EXTENSION_TO_SCOPE.put(".sql", "source.sql");
        // EXTENSION_TO_SCOPE.put(".sh", "source.shell");
        // EXTENSION_TO_SCOPE.put(".bash", "source.shell");
    }

    private void ensureTextMateInited() {
        if (textMateInited) return;
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getApplicationContext().getAssets()));
            // 主题注册
            var themeRegistry = ThemeRegistry.getInstance();
            var name = "quietlight";
            var themeAssetsPath = "editor/textmate/" + name + ".json";
            // 在尝试加载主题前检查资源是否存在
            var themeStream = FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath);
            if (themeStream == null) {
                Log.w("FileEditorActivity", "未找到主题文件: " + themeAssetsPath + ", 将禁用 TextMate");
                textMateInited = false;
                return;
            }
            var model = new ThemeModel(
                    IThemeSource.fromInputStream(themeStream, themeAssetsPath, null),
                    name
            );
            themeRegistry.loadTheme(model);
            themeRegistry.setTheme(name);
            // 加载语法定义
            var languagesPath = "editor/textmate/languages.json";
            var langStream = FileProviderRegistry.getInstance().tryGetInputStream(languagesPath);
            if (langStream == null) {
                Log.w("FileEditorActivity", "未找到语法定义文件: " + languagesPath + ", 将禁用 TextMate");
                textMateInited = false;
                return;
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
        if (localPath == null) return;
        try {
            ensureTextMateInited();
            // 仅在 TextMate 初始化成功后应用颜色方案与语言
            if (textMateInited) {
                codeEditor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
            }

            String lower = localPath.toLowerCase();
            String scope = null;

            // 根据文件扩展名查找对应的scope
            for (Map.Entry<String, String> entry : EXTENSION_TO_SCOPE.entrySet()) {
                if (lower.endsWith(entry.getKey())) {
                    scope = entry.getValue();
                    break;
                }
            }

            if (scope != null && textMateInited) {
                var language = TextMateLanguage.create(scope, true);
                codeEditor.setEditorLanguage(language);
            } else {
                // 对于不支持的语言或 TextMate 未就绪，使用空语言以提高性能
                codeEditor.setEditorLanguage(null);
            }
        } catch (Exception e) {
            Log.w("FileEditorActivity", "语言应用失败", e);
            Toast.makeText(this, "语言应用失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            Log.e("FileEditorActivity", "语言应用严重错误", th);
            Toast.makeText(this, "语言应用严重错误: " + th.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int serverId = -1;
    private String remoteDir;
    private String remotePath;

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

    private void saveFile() {
        if (localPath == null) {
            Toast.makeText(this, "路径无效", Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(localPath)) {
            String text = codeEditor.getText().toString();
            fos.write(text.getBytes());
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        } catch (OutOfMemoryError error) {
            Toast.makeText(this, "文本过大，内存不足", Toast.LENGTH_LONG).show();
            Log.e("FileEditorActivity", "内存溢出错误", error);
            return;
        }
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        // 返回结果给调用者，由 Pane 负责上传
        android.content.Intent result = new android.content.Intent();
        result.putExtra("local_path", localPath);
        result.putExtra("remote_path", remotePath);
        result.putExtra("server_id", serverId);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
        }

        setContentView(R.layout.activity_file_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize new views
        tvFilename = findViewById(R.id.tv_filename);
        tvCursorPosition = findViewById(R.id.tv_cursor_position);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);

        codeEditor = findViewById(R.id.code_editor);
        codeEditor.setTypefaceText(Typeface.MONOSPACE);

        // Setup listeners
        btnUndo.setOnClickListener(v -> {
            if (codeEditor != null) codeEditor.undo();
        });
        btnRedo.setOnClickListener(v -> {
            if (codeEditor != null) codeEditor.redo();
        });
        btnSave.setOnClickListener(v -> saveFile());

        codeEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            isModified = true;
            updateUIState();
        });

        codeEditor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> {
            if (tvCursorPosition != null) {
                var cursor = codeEditor.getCursor();
                tvCursorPosition.setText((cursor.getLeftLine() + 1) + ":" + (cursor.getLeftColumn() + 1));
            }
        });

        // 设置更好的行间距
        codeEditor.setLineSpacing(2f, 1.1f);

        // 设置非打印字符绘制标志
        codeEditor.setNonPrintablePaintingFlags(
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING |
                        CodeEditor.FLAG_DRAW_LINE_SEPARATOR |
                        CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION
        );

        // 启用粘性滚动
        codeEditor.getProps().stickyScroll = true;

        // 启用自动换行
        codeEditor.setWordwrap(true);

        localPath = getIntent().getStringExtra("local_path");
        fileName = getIntent().getStringExtra("file_name");
        // 读取可选的上传配置
        serverId = getIntent().getIntExtra("server_id", -1);
        remotePath = getIntent().getStringExtra("remote_path");
        // remoteDir 不再使用，上传交给 Pane

        if (fileName != null) {
            tvFilename.setText(fileName);
        }

        loadLocalFile();
        applyLanguageForCurrentFile();
        updateUIState();
    }

    private void loadLocalFile() {
        if (localPath == null) {
            Toast.makeText(this, "本地路径无效", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(localPath);
        if (!f.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            codeEditor.setText(sb.toString());
            // 尝试加载自定义字体
            try {
                codeEditor.setTypefaceText(Typeface.createFromAsset(getAssets(), "editor/JetBrainsMonoNL-Regular.ttf"));
            } catch (Exception e) {
                Log.w("FileEditorActivity", "无法加载自定义字体，使用默认等宽字体", e);
                codeEditor.setTypefaceText(Typeface.MONOSPACE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (OutOfMemoryError error) {
            Toast.makeText(this, "文件过大，内存不足", Toast.LENGTH_LONG).show();
            Log.e("FileEditorActivity", "内存溢出错误", error);
        }
    }
}
