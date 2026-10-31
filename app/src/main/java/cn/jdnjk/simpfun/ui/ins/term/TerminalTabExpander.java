package cn.jdnjk.simpfun.ui.ins.term;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将终端 Tab 展开为每 8 列一个的制表位，不让 TextView 使用像素制表位。 */
final class TerminalTabExpander {
    private static final int TAB_WIDTH = 8;
    private static final Pattern ANSI_CSI = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]");

    private TerminalTabExpander() {}

    /** 输入为已清理非显示控制序列的文本；保留 ANSI 颜色码，但不计入列宽。 */
    static String expandTabs(String text) {
        if (text.indexOf('\t') < 0) return text;

        StringBuilder result = new StringBuilder(text.length());
        Matcher ansi = ANSI_CSI.matcher(text);
        int column = 0;
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '\u001B' && ansi.region(i, text.length()).lookingAt()) {
                result.append(text, i, ansi.end());
                i = ansi.end();
                continue;
            }

            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\t') {
                int spaces = TAB_WIDTH - column % TAB_WIDTH;
                for (int j = 0; j < spaces; j++) result.append(' ');
                column += spaces;
            } else {
                result.appendCodePoint(codePoint);
                if (codePoint == '\n' || codePoint == '\r') {
                    column = 0;
                } else {
                    column += columnWidth(codePoint);
                }
            }
        }
        return result.toString();
    }

    private static int columnWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (Character.isISOControl(codePoint) || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK || type == Character.FORMAT) {
            return 0;
        }
        // 常见东亚宽字符占两列；不能用 UTF-16 字符个数计算制表位。
        if (codePoint >= 0x1100 && (codePoint <= 0x115F
                || codePoint == 0x2329 || codePoint == 0x232A
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF && codePoint != 0x303F)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE19)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF01 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD))) {
            return 2;
        }
        return 1;
    }
}
