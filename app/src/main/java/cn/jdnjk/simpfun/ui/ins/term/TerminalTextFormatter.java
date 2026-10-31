package cn.jdnjk.simpfun.ui.ins.term;

import java.util.regex.Pattern;

/** 日志式终端只渲染文本和 SGR 样式，不把终端查询、光标控制等交给 ANSI 文本控件。 */
final class TerminalTextFormatter {
    private static final Pattern OSC = Pattern.compile("\\x1B\\][\\s\\S]*?(?:\\x07|\\x1B\\\\)");
    private static final Pattern CONTROL_STRING = Pattern.compile("\\x1B[P\\^_]([\\s\\S]*?)(?:\\x1B\\\\|\\x07)");
    // CSI 的最终字节范围是 @ 到 ~；只保留最终字节为 m 的 SGR 颜色/样式序列。
    // 必须整段移除 ESC[6n 等查询，避免 AnsiParser 留下可见的 [6n。
    private static final Pattern NON_STYLE_CSI = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-ln-~]");
    private static final Pattern ESC_SHORT = Pattern.compile("\\x1B[=>78]");
    private static final Pattern CTRL_DISPLAY = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1A\\x1C-\\x1F\\x7F]");
    private static final Pattern CSI_ANY = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern ESC_ANY = Pattern.compile("\\x1B[ -/]*[@-~]");
    private static final Pattern CTRL_ALL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private TerminalTextFormatter() {}

    static String normalizeForDisplay(String line) {
        if (line == null || line.isEmpty()) return "";
        String text = line;
        if (text.indexOf('\u001B') >= 0) {
            text = OSC.matcher(text).replaceAll("");
            text = CONTROL_STRING.matcher(text).replaceAll("");
            text = NON_STYLE_CSI.matcher(text).replaceAll("");
            text = ESC_SHORT.matcher(text).replaceAll("");
        }
        // 大多数日志是普通文本，不必每次都跑整套 ANSI/控制字符正则。
        if (hasDisplayControlCharacters(text)) {
            text = CTRL_DISPLAY.matcher(text).replaceAll("");
        }
        return TerminalTabExpander.expandTabs(text);
    }

    static String stripAnsiForLogs(String line) {
        String text = normalizeForDisplay(line);
        if (text.indexOf('\u001B') < 0) return text;
        text = CSI_ANY.matcher(text).replaceAll("");
        text = ESC_ANY.matcher(text).replaceAll("");
        return CTRL_ALL.matcher(text).replaceAll("");
    }

    private static boolean hasDisplayControlCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\u001B')
                    || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
