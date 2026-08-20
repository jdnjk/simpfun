package cn.jdnjk.simpfun.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class LogCapture {

    private static final int MAX_CHARS = 2_000_000;

    private LogCapture() {}

    public static String read() throws IOException {
        int myPid = android.os.Process.myPid();
        java.lang.Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "logcat",
                    "-b", "main",
                    "--pid=" + myPid,
                    "-v", "threadtime",
                    "*:D",
                    "-d"
            );
            process = pb.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder sb = new StringBuilder(64 * 1024);
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf, 0, buf.length)) != -1) {
                if (sb.length() + read > MAX_CHARS) {
                    // 防止超长导出文件，截断尾部（最近日志）
                    int keep = MAX_CHARS / 2;
                    String tail = sb.substring(sb.length() - keep);
                    sb.setLength(0);
                    sb.append("... (日志过长已截断，仅保留最近部分) ...\n").append(tail);
                    append(sb, buf, read);
                    break;
                }
                append(sb, buf, read);
            }
            return sb.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void append(StringBuilder sb, char[] buf, int len) {
        if (len > 0) {
            if (buf[len - 1] == '\n') {
                sb.append(buf, 0, len);
            } else {
                sb.append(buf, 0, len).append('\n');
            }
        }
    }
}