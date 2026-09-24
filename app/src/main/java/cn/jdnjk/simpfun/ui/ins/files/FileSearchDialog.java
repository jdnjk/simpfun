package cn.jdnjk.simpfun.ui.ins.files;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.FileApi;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.utils.Feedback;
import cn.jdnjk.simpfun.utils.SftpCredentialStore;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

/**
 * 文件搜索对话框：按文件名搜索，支持通配符/正则、子目录递归，
 * 高级选项（自定义大小范围 a 单位 <或≤ x <或≤ b 单位、区分大小写、正则表达式）。
 * 本地面板走本地文件系统；服务器面板在双页模式走 SFTP、单页模式走 FileApi HTTP。
 */
public final class FileSearchDialog {

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB"};
    private static final String[] COMPARE_OPS = {"<", "≤"};
    private static final int MAX_HTTP_DIRS = 500;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 可变的取消标记 */
    private static final class Cancel implements FileSearchEngine.CancelFlag {
        volatile boolean cancelled;

        @Override public boolean isCancelled() { return cancelled; }
    }

    /** 搜索目标面板抽象：双页模式传入选中的那一侧 */
    public interface Target {
        /** 是否本地面板 */
        boolean isLocal();

        /** 搜索起始目录 */
        String currentPath();

        /** 服务器 ID（本地面板忽略） */
        int deviceId();

        /** 服务器面板是否走 SFTP（双页模式 true，单页 HTTP 模式 false） */
        boolean useSftpBackend();

        /** 点击结果后跳转到该目录（主线程调用） */
        void navigateTo(String path);
    }

    private FileSearchDialog() {
    }

    public static void show(Context context, Target target) {
        String rootPath = target.currentPath();
        if (rootPath == null || rootPath.trim().isEmpty()) {
            rootPath = "/";
        }
        final String searchRoot = rootPath;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        container.setPadding(pad, dp(context, 8), pad, 0);

        final EditText etPattern = new EditText(context);
        etPattern.setSingleLine(true);
        etPattern.setHint("文件名，支持 * ? 通配符");
        container.addView(etPattern);

        final CheckBox cbSubdirs = new CheckBox(context);
        cbSubdirs.setText("搜索子目录");
        container.addView(cbSubdirs);

        final CheckBox cbAdvanced = new CheckBox(context);
        cbAdvanced.setText("高级选项");
        container.addView(cbAdvanced);

        final LinearLayout advancedBox = new LinearLayout(context);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.setPadding(dp(context, 24), 0, 0, 0);
        advancedBox.setVisibility(View.GONE);
        container.addView(advancedBox);

        final CheckBox cbSizeFilter = new CheckBox(context);
        cbSizeFilter.setText("自定义大小范围（不勾选为任意大小）");
        advancedBox.addView(cbSizeFilter);

        final LinearLayout sizeBox = new LinearLayout(context);
        sizeBox.setOrientation(LinearLayout.VERTICAL);
        sizeBox.setPadding(dp(context, 24), 0, 0, 0);
        sizeBox.setVisibility(View.GONE);
        advancedBox.addView(sizeBox);

        // 最小值行：a 单位 <或≤ x
        LinearLayout rowMin = new LinearLayout(context);
        rowMin.setOrientation(LinearLayout.HORIZONTAL);
        rowMin.setGravity(Gravity.CENTER_VERTICAL);
        final EditText etMin = numberInput(context);
        final Spinner spMinUnit = smallSpinner(context, SIZE_UNITS, 1); // 默认 KB
        final Spinner spMinOp = smallSpinner(context, COMPARE_OPS, 1);  // 默认 ≤
        rowMin.addView(etMin);
        rowMin.addView(spMinUnit);
        rowMin.addView(spMinOp);
        rowMin.addView(boldLabel(context, " x"));
        sizeBox.addView(rowMin);

        // 最大值行：x <或≤ b 单位
        LinearLayout rowMax = new LinearLayout(context);
        rowMax.setOrientation(LinearLayout.HORIZONTAL);
        rowMax.setGravity(Gravity.CENTER_VERTICAL);
        final Spinner spMaxOp = smallSpinner(context, COMPARE_OPS, 1); // 默认 ≤
        final EditText etMax = numberInput(context);
        final Spinner spMaxUnit = smallSpinner(context, SIZE_UNITS, 1);
        rowMax.addView(boldLabel(context, "x "));
        rowMax.addView(spMaxOp);
        rowMax.addView(etMax);
        rowMax.addView(spMaxUnit);
        sizeBox.addView(rowMax);

        final CheckBox cbCaseSensitive = new CheckBox(context);
        cbCaseSensitive.setText("区分大小写");
        advancedBox.addView(cbCaseSensitive);

        final CheckBox cbRegex = new CheckBox(context);
        cbRegex.setText("正则表达式（不勾选为通配符）");
        advancedBox.addView(cbRegex);

        cbAdvanced.setOnCheckedChangeListener((b, checked) ->
                advancedBox.setVisibility(checked ? View.VISIBLE : View.GONE));
        cbSizeFilter.setOnCheckedChangeListener((b, checked) ->
                sizeBox.setVisibility(checked ? View.VISIBLE : View.GONE));

        // MD1（AppCompat 经典）外观：显式传 AppCompat 对话框主题，规避 Material3 主题校验
        new androidx.appcompat.app.AlertDialog.Builder(context,
                androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog_Alert)
                .setTitle("搜索文件")
                .setMessage("搜索范围：" + searchRoot)
                .setView(container)
                .setPositiveButton("搜索", (dialog, which) -> {
                    FileSearchEngine.Options options = collectOptions(
                            etPattern, cbSubdirs, cbCaseSensitive, cbRegex,
                            cbSizeFilter, etMin, spMinUnit, spMinOp, etMax, spMaxUnit, spMaxOp);
                    if (options != null) {
                        runSearch(context, target, searchRoot, options);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- 选项收集与校验 ----------

    private static FileSearchEngine.Options collectOptions(
            EditText etPattern, CheckBox cbSubdirs, CheckBox cbCaseSensitive, CheckBox cbRegex,
            CheckBox cbSizeFilter, EditText etMin, Spinner spMinUnit, Spinner spMinOp,
            EditText etMax, Spinner spMaxUnit, Spinner spMaxOp) {
        FileSearchEngine.Options o = new FileSearchEngine.Options();
        o.pattern = etPattern.getText().toString().trim();
        if (o.pattern.isEmpty()) {
            Feedback.error(etPattern, "请输入要搜索的文件名");
            return null;
        }
        o.recursive = cbSubdirs.isChecked();
        o.caseSensitive = cbCaseSensitive.isChecked();
        o.regex = cbRegex.isChecked();
        if (cbSizeFilter.isChecked()) {
            try {
                String minText = etMin.getText().toString().trim();
                String maxText = etMax.getText().toString().trim();
                if (!minText.isEmpty()) {
                    double minVal = parseNumber(minText);
                    o.minBytes = (long) (minVal * FileSearchEngine.Options.unitToBytes(unit(spMinUnit)));
                    o.minInclusive = "≤".equals(op(spMinOp));
                } else {
                    o.minBytes = -1L;
                }
                if (!maxText.isEmpty()) {
                    double maxVal = parseNumber(maxText);
                    o.maxBytes = (long) (maxVal * FileSearchEngine.Options.unitToBytes(unit(spMaxUnit)));
                    o.maxInclusive = "≤".equals(op(spMaxOp));
                } else {
                    o.maxBytes = -1L;
                }
                o.sizeEnabled = o.minBytes >= 0 || o.maxBytes >= 0; // 两侧都没填=任意大小
            } catch (NumberFormatException e) {
                Feedback.error(etMin, "大小范围请输入有效的非负数字");
                return null;
            }
        }
        try {
            FileSearchEngine.buildMatcher(o);
        } catch (PatternSyntaxException e) {
            Feedback.error(etPattern, "表达式无效：" + e.getDescription());
            return null;
        }
        return o;
    }

    private static double parseNumber(String text) throws NumberFormatException {
        double v = Double.parseDouble(text);
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) {
            throw new NumberFormatException(text);
        }
        return v;
    }

    private static String unit(Spinner sp) {
        Object item = sp.getSelectedItem();
        return item == null ? "B" : item.toString();
    }

    private static String op(Spinner sp) {
        Object item = sp.getSelectedItem();
        return item == null ? "<" : item.toString();
    }

    // ---------- 搜索执行 ----------

    private static void runSearch(Context context, Target target, String root,
                                  FileSearchEngine.Options options) {
        Cancel cancel = new Cancel();
        // UI（对话框/提示）必须用 Activity 上下文（Material 主题要求），API 调用用 appContext
        final Context ui = context;
        final Context app = context.getApplicationContext();

        // MD1（AppCompat 经典）外观
        final androidx.appcompat.app.AlertDialog waitDialog = new androidx.appcompat.app.AlertDialog.Builder(ui,
                androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog_Alert)
                .setTitle("搜索文件")
                .setMessage("搜索中…（范围：" + root + "）")
                .setCancelable(false)
                .setNegativeButton("取消", (d, w) -> cancel.cancelled = true)
                .create();
        waitDialog.show();

        List<FileSearchEngine.Match> results = new ArrayList<>();

        if (target.isLocal()) {
            Thread t = new Thread(() -> {
                String error = null;
                try {
                    FileSearchEngine.Matcher matcher = FileSearchEngine.buildMatcher(options);
                    FileSearchEngine.searchLocal(new File(root), options, matcher, cancel,
                            results, FileSearchEngine.DEFAULT_MAX_RESULTS);
                } catch (Exception e) {
                    error = e.getMessage() == null ? "搜索失败" : e.getMessage();
                }
                final String err = error;
                MAIN.post(() -> onSearchDone(ui, target, waitDialog, results, err));
            }, "file-search-local");
            t.start();
            return;
        }

        if (target.useSftpBackend()) {
            startSftpSearch(ui, app, target, root, options, cancel, results, waitDialog);
            return;
        }

        // 单页服务器面板：FileApi HTTP 递归搜索（回调链驱动，全部在主线程）
        HttpSearch http = new HttpSearch(ui, app, target, root, options, cancel, results, waitDialog);
        http.step();
    }

    private static void onSearchDone(Context context, Target target, androidx.appcompat.app.AlertDialog waitDialog,
                                     List<FileSearchEngine.Match> results, String error) {
        try {
            waitDialog.dismiss();
        } catch (Exception ignored) {
        }
        if (error != null) {
            Feedback.error(decorView(context), "搜索失败：" + error);
            return;
        }
        showResults(context, target, results);
    }

    // ---------- SFTP 搜索 ----------

    private static void startSftpSearch(Context ui, Context app, Target target, String root,
                                        FileSearchEngine.Options options, Cancel cancel,
                                        List<FileSearchEngine.Match> results,
                                        androidx.appcompat.app.AlertDialog waitDialog) {
        String instanceId = String.valueOf(target.deviceId());
        SftpCredentialStore.Credential cached = SftpCredentialStore.get(app).getValid(instanceId);
        if (cached != null) {
            runSftpSearch(ui, app, target, root, options, cancel, results, waitDialog, cached);
            return;
        }
        String token = app.getSharedPreferences("token", Context.MODE_PRIVATE).getString("token", "");
        if (token.isEmpty()) {
            waitDialog.dismiss();
            Feedback.error(decorView(ui), "未登录，无法获取 SFTP 信息");
            return;
        }
        new MainApi(app).getSftp(token, instanceId, new MainApi.Callback() {
            @Override public void onSuccess(JSONObject data) {
                SftpCredentialStore.Credential credential =
                        SftpCredentialStore.Credential.fromApiJson(instanceId, data);
                if (credential == null) {
                    waitDialog.dismiss();
                    Feedback.error(decorView(ui), "SFTP 信息无效");
                    return;
                }
                runSftpSearch(ui, app, target, root, options, cancel, results, waitDialog, credential);
            }

            @Override public void onFailure(String errorMsg) {
                waitDialog.dismiss();
                Feedback.error(decorView(ui), "获取 SFTP 信息失败: " + errorMsg);
            }
        });
    }

    private static void runSftpSearch(Context ui, Context app, Target target, String root,
                                      FileSearchEngine.Options options, Cancel cancel,
                                      List<FileSearchEngine.Match> results,
                                      androidx.appcompat.app.AlertDialog waitDialog,
                                      SftpCredentialStore.Credential credential) {
        Thread t = new Thread(() -> {
            String error = null;
            try {
                FileSearchEngine.Matcher matcher = FileSearchEngine.buildMatcher(options);
                SftpTransferCoordinator.ensureBouncyCastleRegistered();
                try (SSHClient ssh = new SSHClient()) {
                    ssh.addHostKeyVerifier(new PromiscuousVerifier());
                    ssh.connect(credential.host, credential.port);
                    ssh.authPassword(credential.username, credential.password);
                    try (SFTPClient sftp = ssh.newSFTPClient()) {
                        FileSearchEngine.searchSftp(sftp, root, options, matcher, cancel,
                                results, FileSearchEngine.DEFAULT_MAX_RESULTS);
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "SFTP 搜索失败" : e.getMessage();
            }
            final String err = error;
            MAIN.post(() -> onSearchDone(ui, target, waitDialog, results, err));
        }, "file-search-sftp");
        t.start();
    }

    // ---------- HTTP 递归搜索（单页服务器面板） ----------

    private static final class HttpSearch {
        private final Context ui;
        private final Context app;
        private final Target target;
        private final FileSearchEngine.Options options;
        private final Cancel cancel;
        private final List<FileSearchEngine.Match> results;
        private final androidx.appcompat.app.AlertDialog waitDialog;
        private final FileSearchEngine.Matcher matcher;
        private final FileApi api = new FileApi();
        private final List<String> queue = new ArrayList<>();
        private final Set<String> visited = new HashSet<>();

        HttpSearch(Context ui, Context app, Target target, String root, FileSearchEngine.Options options,
                   Cancel cancel, List<FileSearchEngine.Match> results, androidx.appcompat.app.AlertDialog waitDialog)
                throws PatternSyntaxException {
            this.ui = ui;
            this.app = app;
            this.target = target;
            this.options = options;
            this.cancel = cancel;
            this.results = results;
            this.waitDialog = waitDialog;
            this.matcher = FileSearchEngine.buildMatcher(options);
            queue.add(root);
        }

        void step() {
            if (cancel.isCancelled()
                    || results.size() >= FileSearchEngine.DEFAULT_MAX_RESULTS
                    || visited.size() >= MAX_HTTP_DIRS
                    || queue.isEmpty()) {
                MAIN.post(() -> onSearchDone(ui, target, waitDialog, results, null));
                return;
            }
            String dir = queue.remove(0);
            if (!visited.add(dir)) {
                step();
                return;
            }
            api.getFileList(app, target.deviceId(), dir, new FileApi.Callback() {
                @Override public void onSuccess(JSONObject data) {
                    if (cancel.isCancelled()) {
                        MAIN.post(() -> onSearchDone(ui, target, waitDialog, results, null));
                        return;
                    }
                    try {
                        JSONArray list = data.getJSONArray("list");
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject obj = list.getJSONObject(i);
                            String name = obj.optString("name");
                            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;
                            boolean isFile = obj.optBoolean("file", true);
                            long size = isFile ? obj.optLong("size", 0L) : 0L;
                            if (matcher.matches(name) && FileSearchEngine.sizeMatches(options, size)) {
                                results.add(new FileSearchEngine.Match(
                                        FileSearchEngine.joinPath(dir, name), name, !isFile, size));
                            }
                            if (options.recursive && !isFile) {
                                queue.add(FileSearchEngine.joinPath(dir, name));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    step();
                }

                @Override public void onFailure(String errorMsg) {
                    // 单个目录失败跳过，继续搜索其余目录
                    step();
                }
            });
        }
    }

    // ---------- 结果展示 ----------

    private static void showResults(Context context, Target target, List<FileSearchEngine.Match> results) {
        if (results.isEmpty()) {
            Feedback.info(decorView(context), "未找到匹配的文件");
            return;
        }
        String root = target.currentPath() == null ? "/" : target.currentPath();
        final androidx.appcompat.app.AlertDialog[] resultHolder = new androidx.appcompat.app.AlertDialog[1];
        List<String> lines = new ArrayList<>();
        for (FileSearchEngine.Match m : results) {
            String rel = m.path;
            if (root.length() > 1 && rel.startsWith(root)) {
                rel = rel.substring(root.length());
            }
            if (rel.startsWith("/")) rel = rel.substring(1);
            String sizeText = m.dir ? "目录" : formatSize(m.size);
            lines.add(m.name + "  (" + sizeText + ")\n" + rel);
        }
        ListView listView = new ListView(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_list_item_1, lines) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            FileSearchEngine.Match m = results.get(position);
            int cut = m.path.lastIndexOf('/');
            String parentDir = cut > 0 ? m.path.substring(0, cut + 1) : "/";
            target.navigateTo(parentDir);
            if (resultHolder[0] != null) {
                try {
                    resultHolder[0].dismiss();
                } catch (Exception ignored) {
                }
            }
        });
        // MD1（AppCompat 经典）外观
        resultHolder[0] = new androidx.appcompat.app.AlertDialog.Builder(context,
                androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog_Alert)
                .setTitle("搜索结果（" + results.size()
                        + (results.size() >= FileSearchEngine.DEFAULT_MAX_RESULTS ? "+）" : "）"))
                .setView(listView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static View decorView(Context context) {
        return context instanceof android.app.Activity activity
                ? activity.getWindow().getDecorView() : null;
    }

    private static String formatSize(long bytes) {
        if (bytes < FileSearchEngine.UNIT_KB) return bytes + " B";
        if (bytes < FileSearchEngine.UNIT_MB) return String.format(Locale.ROOT, "%.1f KB", bytes / (double) FileSearchEngine.UNIT_KB);
        if (bytes < FileSearchEngine.UNIT_GB) return String.format(Locale.ROOT, "%.1f MB", bytes / (double) FileSearchEngine.UNIT_MB);
        return String.format(Locale.ROOT, "%.2f GB", bytes / (double) FileSearchEngine.UNIT_GB);
    }

    // ---------- 小部件工厂 ----------

    private static EditText numberInput(Context context) {
        EditText et = new EditText(context);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("数字");
        et.setMinimumWidth(dp(context, 72));
        et.setSingleLine(true);
        return et;
    }

    private static Spinner smallSpinner(Context context, String[] items, int defaultIndex) {
        Spinner sp = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setSelection(Math.min(defaultIndex, items.length - 1));
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return sp;
    }

    private static TextView boldLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return tv;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
