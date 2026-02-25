package cn.jdnjk.simpfun;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.graphics.Color;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    private ActivityResultLauncher<android.content.Intent> saveAsLauncher;

    private static final Map<String, String> EXTENSION_TO_SCOPE = new HashMap<>();

    static {
        EXTENSION_TO_SCOPE.put(".json", "source.json");
        EXTENSION_TO_SCOPE.put(".log", "text.log");
        EXTENSION_TO_SCOPE.put(".yaml", "source.yaml");
        EXTENSION_TO_SCOPE.put(".yml", "source.yaml");
        // EXTENSION_TO_SCOPE.put(".py", "source.python");
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

            // Load themes
            var themes = new String[]{"darcula", "ayu-dark", "quietlight", "solarized_dark"};
            var themeRegistry = ThemeRegistry.getInstance();
            boolean themeLoaded = false;

            for (String name : themes) {
                var themeAssetsPath = "editor/textmate/" + name + ".json";
                var themeStream = FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath);

                if (themeStream != null) {
                    var model = new ThemeModel(
                            IThemeSource.fromInputStream(themeStream, themeAssetsPath, null),
                            name
                    );
                    if (!"quietlight".equals(name)) {
                        model.setDark(true);
                    }
                    themeRegistry.loadTheme(model);
                    themeLoaded = true;
                }
            }

            if (!themeLoaded) {
                 Log.w("FileEditorActivity", "未找到任何主题文件, 将禁用 TextMate");
                 textMateInited = false;
                 return;
            }

            themeRegistry.setTheme("quietlight"); // Default theme

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

    private int serverId = -1;
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
        // getWindow().setStatusBarColor(Color.TRANSPARENT); // Deprecated
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setAppearanceLightStatusBars(false); // Dark theme usually

        setContentView(R.layout.activity_file_editor);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize new views
        tvFilename = findViewById(R.id.tv_filename);
        tvCursorPosition = findViewById(R.id.tv_cursor_position);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);

        codeEditor = findViewById(R.id.code_editor);
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
        btnMore.setOnClickListener(v -> new cn.jdnjk.simpfun.utils.EditorMenuHandler(this, codeEditor, localPath)
                .showMenu(v));

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
        localPath = getIntent().getStringExtra("local_path");
        fileName = getIntent().getStringExtra("file_name");
        remotePath = getIntent().getStringExtra("remote_path");
        serverId = getIntent().getIntExtra("server_id", -1);

        if (localPath != null) {
            File file = new File(localPath);
            if (fileName == null) fileName = file.getName();
            loadLocalFile();
            applyLanguageForCurrentFile();
        }

        updateUIState();
    }

    private void loadLocalFile() {
        if (localPath == null) return;
        try {
            File file = new File(localPath);
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            // Increase buffer size for large files
            StringBuilder text = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line).append('\n');
                }
            }
            // Remove last newline if added
            if (text.length() > 0 && text.charAt(text.length() - 1) == '\n') {
                text.setLength(text.length() - 1);
            }

            codeEditor.setText(text);
            isModified = false; // Initial load is not modified
        } catch (Exception e) {
            Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("FileEditorActivity", "File load error", e);
        }
    }


    public void launchSaveAs() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_TITLE, fileName != null ? fileName : "untitled.txt");
        try {
            saveAsLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "未找到文件管理器", Toast.LENGTH_SHORT).show();
        }
    }


    private void saveContentToUri(android.net.Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                String text = codeEditor.getText().toString();
                os.write(text.getBytes());
                os.close();
                Toast.makeText(this, "另存为成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "无法打开输出流", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("FileEditorActivity", "Save as error", e);
        }
    }
}
