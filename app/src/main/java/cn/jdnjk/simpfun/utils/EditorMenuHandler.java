package cn.jdnjk.simpfun.utils;

import android.app.Activity;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.event.PublishSearchResultEvent;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

public class EditorMenuHandler {

    private final Activity activity;
    private final CodeEditor codeEditor;
    private final String localPath;
    private Charset currentEncoding = StandardCharsets.UTF_8;
    private static final String MS_TRANSLATOR_REGION = "global";

    public EditorMenuHandler(Activity activity, CodeEditor codeEditor, String localPath) {
        this.activity = activity;
        this.codeEditor = codeEditor;
        this.localPath = localPath;
    }

    public void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenuInflater().inflate(R.menu.editor_menu, popup.getMenu());

        // 强制显示图标
        try {
            java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            java.lang.reflect.Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceShowIcon.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_save_as) {
                handleSaveAs();
                return true;
            } else if (itemId == R.id.action_reload) {
                handleReload(currentEncoding);
                return true;
            } else if (itemId == R.id.action_reload_encoding) {
                showEncodingDialog(true);
                return true;
            } else if (itemId == R.id.action_save_encoding) {
                showEncodingDialog(false);
                return true;
            } else if (itemId == R.id.action_newline) {
                showNewlineDialog();
                return true;
            } else if (itemId == R.id.action_search) {
                showSearchDialog();
                return true;
            } else if (itemId == R.id.action_syntax) {
                 showSyntaxDialog();
                return true;
            } else if (itemId == R.id.action_wordwrap) {
                boolean checked = !item.isChecked();
                item.setChecked(checked);
                codeEditor.setWordwrap(checked);
                return true;
            } else if (itemId == R.id.action_translate) {
                handleTranslateComments();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void handleSaveAs() {
        if (activity instanceof cn.jdnjk.simpfun.FileEditorActivity) {
            ((cn.jdnjk.simpfun.FileEditorActivity) activity).launchSaveAs();
        } else {
             Toast.makeText(activity, "不支持此操作", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleReload(Charset encoding) {
        if (localPath == null) return;
        try {
            File file = new File(localPath);
            if (!file.exists()) {
                Toast.makeText(activity, "文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String text = new String(data, encoding);
            codeEditor.setText(text);
            currentEncoding = encoding;
            Toast.makeText(activity, "已重新加载 (" + encoding.displayName() + ")", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(activity, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEncodingDialog(boolean isReload) {
        String[] encodings = {"UTF-8", "GBK", "GB2312", "ISO-8859-1", "US-ASCII", "UTF-16"};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(isReload ? "选择编码以重新加载" : "选择保存编码")
                .setItems(encodings, (dialog, which) -> {
                    Charset charset;
                    try {
                        charset = Charset.forName(encodings[which]);
                    } catch (Exception e) {
                        charset = StandardCharsets.UTF_8;
                    }
                    if (isReload) {
                        handleReload(charset);
                    } else {
                        currentEncoding = charset;
                        Toast.makeText(activity, "保存编码已设为: " + charset.displayName(), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showNewlineDialog() {
        String[] newlines = {"LF (Unix/Linux/macOS)", "CRLF (Windows)"};
        new MaterialAlertDialogBuilder(activity)
            .setTitle("换行符设置")
            .setItems(newlines, (dialog, which) -> {
                String text = codeEditor.getText().toString();
                String newText;
                if (which == 0) { // LF
                    newText = text.replace("\r\n", "\n").replace("\r", "\n");
                } else { // CRLF
                    newText = text.replace("\r\n", "\n").replace("\n", "\r\n");
                }
                codeEditor.setText(newText);
                Toast.makeText(activity, "换行符已替换", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showSyntaxDialog() {
        String[] displayNames = {
            "Auto Detect", "Java", "Python", "JavaScript", "HTML", "JSON",
            "XML", "YAML", "Markdown", "Shell Script"
        };
        // 对应的 scope 值，需要与 displayNames 一一对应
        String[] scopes = {
            null, // Auto
            "source.java", // Java
            "source.python", // Python
            "source.js", // JS
            "text.html.basic", // HTML
            "source.json", // JSON
            "text.xml", // XML
            "source.yaml", // YAML
            "text.html.markdown", // Markdown
            "source.shell" // Shell
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("选择语法")
                .setItems(displayNames, (dialog, which) -> {
                    String scope = scopes[which];
                    if (scope == null) {
                        if (activity instanceof cn.jdnjk.simpfun.FileEditorActivity) {
                             ((cn.jdnjk.simpfun.FileEditorActivity) activity).applyLanguageAuto();
                        }
                    } else {
                        if (activity instanceof cn.jdnjk.simpfun.FileEditorActivity) {
                            ((cn.jdnjk.simpfun.FileEditorActivity) activity).setLanguage(scope);
                        }
                    }
                })
                .show();
    }

    private void showSearchDialog() {
        if (activity.findViewById(R.id.editor_search_panel) != null) {
            View searchPanel = activity.findViewById(R.id.editor_search_panel);
            searchPanel.setVisibility(View.VISIBLE);

            final View replaceLayout = searchPanel.findViewById(R.id.layout_replace);
            final android.widget.EditText etSearch = searchPanel.findViewById(R.id.et_search_text);
            final android.widget.EditText etReplace = searchPanel.findViewById(R.id.et_replace_text);
            final android.widget.TextView tvResult = searchPanel.findViewById(R.id.tv_search_result);

            android.widget.CheckBox cbCase = searchPanel.findViewById(R.id.cb_case_sensitive);
            android.widget.CheckBox cbWord = searchPanel.findViewById(R.id.cb_whole_word);
            android.widget.CheckBox cbRegex = searchPanel.findViewById(R.id.cb_regex);

            searchPanel.findViewById(R.id.btn_close_search).setOnClickListener(v -> searchPanel.setVisibility(View.GONE));

            Runnable doSearch = () -> {
                String query = etSearch.getText().toString();
                if (query.isEmpty()) {
                    codeEditor.getSearcher().stopSearch();
                    tvResult.setText("0/0");
                    return;
                }
                try {
                    EditorSearcher.SearchOptions options = new EditorSearcher.SearchOptions(
                        cbRegex.isChecked(),
                        cbWord.isChecked()
                    );
                    codeEditor.getSearcher().search(query, options);
                } catch (Exception e) {
                    // Ignore
                }
            };

            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(android.text.Editable s) { doSearch.run(); }
            });

            // Case sensitivity not supported by reference logic (regex, wholeWord) unless it's handled differently.
            // Leaving cbCase inert or remove it? Keeping it but it won't affect search unless search options change.
            cbCase.setOnCheckedChangeListener((pkg, v) -> doSearch.run());
            cbWord.setOnCheckedChangeListener((pkg, v) -> doSearch.run());
            cbRegex.setOnCheckedChangeListener((pkg, v) -> {
                if (cbRegex.isChecked()) {
                    // Regex and whole word usually exclusive or whole word not relevant in regex mode
                    // Reference MainActivity.kt:205 disables whole word if regex is checked.
                    cbWord.setChecked(false);
                }
                doSearch.run();
            });
            // Also if Word checked, unchecked Regex logic?
            cbWord.setOnCheckedChangeListener((pkg, v) -> {
                if (cbWord.isChecked()) {
                    cbRegex.setChecked(false);
                }
                doSearch.run();
            });

            // Subscribe to search events to update result count
            codeEditor.subscribeEvent(PublishSearchResultEvent.class, (event, unsubscribe) -> {
                if (searchPanel.getVisibility() != View.VISIBLE) return;
                EditorSearcher searcher = codeEditor.getSearcher();
                if (searcher.hasQuery()) {
                    int idx = searcher.getCurrentMatchedPositionIndex();
                    int count = searcher.getMatchedPositionCount();
                    String text;
                    if (count == 0) {
                        text = "0/0";
                    } else {
                        text = (idx + 1) + "/" + count;
                    }
                    tvResult.setText(text);
                } else {
                    tvResult.setText("0/0");
                }
            });

            searchPanel.findViewById(R.id.btn_prev).setOnClickListener(v -> {
                String query = etSearch.getText().toString();
                if (query.isEmpty()) return;
                try {
                    codeEditor.getSearcher().gotoPrevious();
                } catch (Exception e) {}
            });

            searchPanel.findViewById(R.id.btn_next).setOnClickListener(v -> {
                String query = etSearch.getText().toString();
                if (query.isEmpty()) return;
                try {
                    codeEditor.getSearcher().gotoNext();
                } catch (Exception e) {}
            });

            searchPanel.findViewById(R.id.btn_replace).setOnClickListener(v -> {
                try {
                    if (etSearch.getText().length() > 0) {
                        codeEditor.getSearcher().replaceThis(etReplace.getText().toString());
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "替换失败", Toast.LENGTH_SHORT).show();
                }
            });

            searchPanel.findViewById(R.id.btn_replace_all).setOnClickListener(v -> {
               try {
                   if (etSearch.getText().length() > 0) {
                       codeEditor.getSearcher().replaceAll(etReplace.getText().toString());
                   }
               } catch (Exception e) {
                   Toast.makeText(activity, "替换全部失败", Toast.LENGTH_SHORT).show();
               }
            });

            return;
        }

        final android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint("输入搜索内容");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("搜索")
                .setView(input) // 简单起见，不自定义Layout
                .setPositiveButton("搜索", (dialog, which) -> {
                    String query = input.getText().toString();
                    if (!query.isEmpty()) {
                        try {
                            // 搜索选项: 不区分大小写(true), 非正则(false) - 根据版本可能参数不同
                            // 假设 SearchOptions(boolean ignoreCase, boolean regex)
                            EditorSearcher.SearchOptions options = new EditorSearcher.SearchOptions(true, false);
                            codeEditor.getSearcher().search(query, options);
                        } catch (Exception e) {
                            // 如果 API 不匹配，回退提示
                            Toast.makeText(activity, "搜索API不兼容或错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleTranslateComments() {
        String content = codeEditor.getText().toString();
        // 简单正则匹配 Java/C++ 风格注释 (//... 和 /*...*/) 以及 Python/Shell 风格 (#...)
        // 注意：这可能误匹配字符串中的内容
        String regex = "(//.*?$)|(/\\*[\\s\\S]*?\\*/)|(#.*?$)";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        List<String> commentsToTranslate = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        while (matcher.find()) {
            String comment = matcher.group();
            // 过滤掉纯符号的注释，减少翻译量
            if (comment.replaceAll("[/*#\\s]", "").isEmpty()) continue;

            commentsToTranslate.add(comment);
            starts.add(matcher.start());
            ends.add(matcher.end());
        }

        if (commentsToTranslate.isEmpty()) {
            Toast.makeText(activity, "未找到可翻译的注释", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(activity, "正在翻译 " + commentsToTranslate.size() + " 条注释...", Toast.LENGTH_SHORT).show();

        translateBatch(commentsToTranslate, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "翻译请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "翻译API错误: " + response.code(), Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String respBody = response.body().string();
                    JSONArray jsonArray = new JSONArray(respBody);

                    // 构建新的文本
                    StringBuilder newContent = new StringBuilder(content);
                    // 倒序替换，防止索引偏移
                    for (int i = commentsToTranslate.size() - 1; i >= 0; i--) {
                        JSONObject item = jsonArray.getJSONObject(i);
                        JSONArray translations = item.getJSONArray("translations");
                        if (translations.length() > 0) {
                            String translatedText = translations.getJSONObject(0).getString("text");
                            // 保留原有格式稍显困难，简单替换
                            // 如果是 // 注释，保留 //
                            // 如果是 /* 注释，保留 /* */
                            // 如果是 # 注释，保留 #

                            String original = commentsToTranslate.get(i);
                            String replacement = restoreCommentFormat(original, translatedText);
                            newContent.replace(starts.get(i), ends.get(i), replacement);
                        }
                    }

                    activity.runOnUiThread(() -> {
                         codeEditor.setText(newContent.toString());
                         Toast.makeText(activity, "翻译完成", Toast.LENGTH_SHORT).show();
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "解析翻译结果失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private String restoreCommentFormat(String original, String translated) {
        if (original.startsWith("//")) {
            return "// " + translated;
        } else if (original.startsWith("#")) {
            return "# " + translated;
        } else if (original.startsWith("/*")) {
            return "/* " + translated + " */";
        }
        return translated;
    }

    private void translateBatch(List<String> texts, Callback callback) {
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        JSONArray jsonBody = new JSONArray();
        for (String text : texts) {
            // 去除注释符号，只翻译内容
            String cleanText = text.replaceAll("^//\\s*|^/\\*\\s*|^#\\s*|\\s*\\*/$", "");
            JSONObject obj = new JSONObject();
            try {
                obj.put("Text", cleanText);
            } catch (Exception e) {}
            jsonBody.put(obj);
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        // 目标语言 zh-Hans
        String url = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=zh-Hans";

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Ocp-Apim-Subscription-Key", BuildConfig.mst_ID)
                .addHeader("Content-Type", "application/json");

        if (!MS_TRANSLATOR_REGION.isEmpty()) {
            builder.addHeader("Ocp-Apim-Subscription-Region", MS_TRANSLATOR_REGION);
        }

        client.newCall(builder.build()).enqueue(callback);
    }
}
