package cn.jdnjk.simpfun.ui.ins.term;

/** 无 Android/JUnit 依赖，可直接使用 javac/java 运行的回归测试。 */
public final class TerminalTabExpanderTest {
    public static void main(String[] args) {
        check("Terraria new world", "n\t\tNew World", "n" + spaces(15) + "New World");
        check("Terraria delete world", "d <number>\tDelete World",
                "d <number>" + spaces(6) + "Delete World");
        check("tab at start", "\ttext", spaces(8) + "text");
        check("tab on stop advances", "12345678\ttext", "12345678" + spaces(8) + "text");
        check("ANSI does not occupy columns", "\u001B[31mn\u001B[0m\t\tNew World",
                "\u001B[31mn\u001B[0m" + spaces(15) + "New World");
        check("extended ANSI color", "\u001B[38;2;1;2;3mn\t\u001B[0m",
                "\u001B[38;2;1;2;3mn" + spaces(7) + "\u001B[0m");
        check("multiline selection", "n\t\tNew World\r\nd <number>\tDelete World",
                "n" + spaces(15) + "New World\r\nd <number>" + spaces(6) + "Delete World");
        check("carriage return resets column", "prefix\rn\ttext", "prefix\rn" + spaces(7) + "text");
        check("wide characters", "世界\ttext", "世界" + spaces(4) + "text");
        check("combining mark", "é\ttext", "é" + spaces(7) + "text");
        check("supplementary wide character", "𠀀\ttext",
                "𠀀" + spaces(6) + "text");
        check("no tab preserves ANSI and spaces", "\u001B[32mhello  world\u001B[0m",
                "\u001B[32mhello  world\u001B[0m");
        check("empty text", "", "");
        String expanded = TerminalTabExpander.expandTabs("n\t\tNew World");
        check("idempotent", expanded, expanded);
        System.out.println("PASS: 14 terminal tab expansion regression tests");
    }

    private static String spaces(int count) {
        return " ".repeat(count);
    }

    private static void check(String name, String input, String expected) {
        String actual = TerminalTabExpander.expandTabs(input);
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected [" + expected + "] but was [" + actual + "]");
        }
    }
}
