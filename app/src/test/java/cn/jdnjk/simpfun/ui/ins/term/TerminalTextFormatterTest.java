package cn.jdnjk.simpfun.ui.ins.term;

/** 无 Android/JUnit 依赖，直接验证传入 AnsiParser 前的完整显示清理链路。 */
public final class TerminalTextFormatterTest {
    private static int checks;

    public static void main(String[] args) {
        String title = "Terraria Server v1.4.5.8";
        String rawTitle = "\u001B[?1h\u001B=\u001B[6n\u001B[H\u001B[2J\u001B]0;"
                + title + "\u0007" + title;
        check("exact Terraria startup payload", rawTitle, title);
        check("cursor position query", "\u001B[6n" + title, title);
        check("query between visible text", "before\u001B[6nafter", "beforeafter");
        check("device status query", "\u001B[5nready", "ready");
        check("private cursor query", "\u001B[?6nready", "ready");
        check("device attributes query", "\u001B[>0cready", "ready");
        check("cursor and private modes", "\u001B[?25l\u001B[2K\u001B[1;1Hready\u001B[?25h", "ready");
        check("SGR colors preserved", "\u001B[33m\u001B[1mready\u001B[0m",
                "\u001B[33m\u001B[1mready\u001B[0m");
        check("RGB and style preserved", "\u001B[38;2;1;2;3mready\u001B[0m",
                "\u001B[38;2;1;2;3mready\u001B[0m");
        check("multiple OSC titles with ST", "\u001B]0;first\u001B\\visible\u001B]0;second\u001B\\tail",
                "visibletail");
        check("control string", "\u001BPignored\u001B\\ready", "ready");
        check("complete Terraria menu", rawTitle + "\nn\t\tNew World\nd <number>\tDelete World",
                title + "\nn" + " ".repeat(15) + "New World\nd <number>" + " ".repeat(6) + "Delete World");
        check("query does not alter tab stops", "\u001B[6nn\t\tNew World", "n" + " ".repeat(15) + "New World");
        check("null", null, "");
        check("empty", "", "");
        String cleanLog = TerminalTextFormatter.stripAnsiForLogs("\u001B[6n\u001B[31mn\t\tNew World\u001B[0m");
        if (!("n" + " ".repeat(15) + "New World").equals(cleanLog)) {
            throw new AssertionError("plain log/copy cleanup: " + cleanLog);
        }
        checks++;
        String plainText = "[INFO] 普通日志 without ANSI 123";
        if (TerminalTextFormatter.normalizeForDisplay(plainText) != plainText
                || TerminalTextFormatter.stripAnsiForLogs(plainText) != plainText) {
            throw new AssertionError("plain text fast path must reuse the original string");
        }
        checks++;
        for (int c = 0; c <= 0x7F; c++) {
            String input = "a" + (char) c + "z";
            String expected;
            if (c == '\t') {
                expected = "a" + " ".repeat(7) + "z";
            } else if ((c < 0x20 && c != '\n' && c != '\r' && c != 0x1B) || c == 0x7F) {
                expected = "az";
            } else {
                expected = input;
            }
            check("ASCII/control fast path " + c, input, expected);
            String expectedLog = c == 0x1B ? "a" : expected;
            if (!expectedLog.equals(TerminalTextFormatter.stripAnsiForLogs(input))) {
                throw new AssertionError("plain log control character " + c);
            }
            checks++;
        }
        System.out.println("PASS: " + checks + " terminal display formatting regression tests");
    }

    private static void check(String name, String input, String expected) {
        String actual = TerminalTextFormatter.normalizeForDisplay(input);
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected [" + expected + "] but was [" + actual + "]");
        }
        checks++;
    }
}
