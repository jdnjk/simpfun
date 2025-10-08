package cn.jdnjk.simpfun.ui.files;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.*;
import java.util.Objects;

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

public class FileEditorActivity extends AppCompatActivity {

    private CodeEditor codeEditor;
    private String localPath;
    private String fileName;
    private static boolean textMateInited = false;

    private void ensureTextMateInited() {
        if (textMateInited) return;
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getApplicationContext().getAssets()));
            // 主题注册
            var themeRegistry = ThemeRegistry.getInstance();
            var name = "theme";
            var themeAssetsPath = "textmate/" + name + ".json";
            var model = new ThemeModel(
                    IThemeSource.fromInputStream(
                            Objects.requireNonNull(FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath)), themeAssetsPath, null
                    ),
                    name
            );
            themeRegistry.loadTheme(model);
            themeRegistry.setTheme(name);
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
        } catch (Throwable e) {
            Log.w("FileEditorActivity", "TextMate初始化失败", e);
        }
    }

    private void applyLanguageForCurrentFile() {
        if (localPath == null) return;
        try {
            ensureTextMateInited();
            // 使用正确的类名
            codeEditor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
            String lower = localPath.toLowerCase();
            String scope = null;
            if (lower.endsWith(".java")) {
                scope = "source.java";
            } else if (lower.endsWith(".kt") || lower.endsWith(".kts")) {
                scope = "source.kotlin";
            } else if (lower.endsWith(".json")) {
                scope = "source.json";
            } else if (lower.endsWith(".log")) {
                scope = "source.log";
            } else if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                scope = "source.yaml";
            }
            if (scope != null) {
                var language = TextMateLanguage.create(scope, true);
                codeEditor.setEditorLanguage(language);
            }
        } catch (Throwable e) {
            Log.w("FileEditorActivity", "不支持该语言", e);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        codeEditor = findViewById(R.id.code_editor);
        codeEditor.setTypefaceText(Typeface.MONOSPACE);
        codeEditor.setWordwrap(true);

        localPath = getIntent().getStringExtra("local_path");
        fileName = getIntent().getStringExtra("file_name");
        if (fileName != null) {
            toolbar.setTitle(fileName);
        }
        loadLocalFile();
        applyLanguageForCurrentFile();
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
            codeEditor.setTypefaceText(Typeface.createFromAsset(getAssets(), "JetBrainsMonoNL-Regular.ttf"));
        } catch (Exception e) {
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        }
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (codeEditor != null) {
            codeEditor.release();
        }
    }
}
