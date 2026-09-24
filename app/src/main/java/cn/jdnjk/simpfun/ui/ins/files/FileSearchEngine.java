package cn.jdnjk.simpfun.ui.ins.files;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

/**
 * 文件名搜索引擎：通配符/正则匹配 + 大小过滤 + 子目录递归。
 * 供文件页搜索对话框与内置 MCP file_search 工具共用。
 */
public final class FileSearchEngine {

    public static final int DEFAULT_MAX_RESULTS = 500;
    /** 递归最大深度，防止过深目录树/符号链接循环拖死搜索 */
    private static final int MAX_DEPTH = 40;
    public static final long UNIT_KB = 1024L;
    public static final long UNIT_MB = 1024L * 1024L;
    public static final long UNIT_GB = 1024L * 1024L * 1024L;

    private FileSearchEngine() {
    }

    /** 搜索条件 */
    public static class Options {
        public String pattern = "";
        /** 是否搜索子目录 */
        public boolean recursive;
        /** true=按正则表达式解析，false=按通配符（* ?）解析 */
        public boolean regex;
        /** 是否区分大小写 */
        public boolean caseSensitive;
        /** 是否启用自定义大小过滤（默认任意大小） */
        public boolean sizeEnabled;
        /** 最小值比较符：true=使用 ≤（x ≥ min），false=使用 <（x > min） */
        public boolean minInclusive;
        /** 最大值比较符：true=使用 ≤（x ≤ max），false=使用 <（x < max） */
        public boolean maxInclusive;
        public long minBytes = -1L;
        public long maxBytes = -1L;

        public static long unitToBytes(String unit) {
            if (unit == null) return 1L;
            switch (unit) {
                case "KB": return UNIT_KB;
                case "MB": return UNIT_MB;
                case "GB": return UNIT_GB;
                default: return 1L; // B
            }
        }
    }

    /** 单条搜索结果 */
    public static class Match {
        public final String path;
        public final String name;
        public final boolean dir;
        public final long size;

        public Match(String path, String name, boolean dir, long size) {
            this.path = path;
            this.name = name;
            this.dir = dir;
            this.size = size;
        }
    }

    /** 文件名匹配器 */
    public interface Matcher {
        boolean matches(String name);
    }

    /** 取消标记 */
    public interface CancelFlag {
        CancelFlag NONE = () -> false;

        boolean isCancelled();
    }

    /** 构建文件名匹配器：pattern 非空时才调用 */
    public static Matcher buildMatcher(Options o) throws PatternSyntaxException {
        int flags = o.caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        String regex = o.regex ? o.pattern : wildcardToRegex(o.pattern);
        final Pattern p = Pattern.compile(regex, flags | Pattern.DOTALL);
        return name -> p.matcher(name).matches();
    }

    /** 通配符转正则：* 匹配任意序列，? 匹配单个字符，其余字符按字面量转义 */
    public static String wildcardToRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append('.'); break;
                case '\\': case '.': case '[': case ']': case '(': case ')':
                case '{': case '}': case '+': case '^': case '$': case '|':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 大小过滤：sizeEnabled=false 时恒匹配；min/max 为 -1 表示该侧不限制 */
    public static boolean sizeMatches(Options o, long size) {
        if (!o.sizeEnabled) {
            return true;
        }
        if (o.minBytes >= 0 && (o.minInclusive ? size < o.minBytes : size <= o.minBytes)) {
            return false;
        }
        if (o.maxBytes >= 0 && (o.maxInclusive ? size > o.maxBytes : size >= o.maxBytes)) {
            return false;
        }
        return true;
    }

    // ---------- 本地文件搜索 ----------

    public static void searchLocal(File root, Options o, Matcher m, CancelFlag cancel,
                                   List<Match> out, int maxResults) {
        searchLocal(root, o, m, cancel, out, maxResults, 0);
    }

    private static void searchLocal(File dir, Options o, Matcher m, CancelFlag cancel,
                                    List<Match> out, int maxResults, int depth) {
        if (dir == null || depth > MAX_DEPTH || out.size() >= maxResults || cancel.isCancelled()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (out.size() >= maxResults || cancel.isCancelled()) {
                return;
            }
            boolean isDir = f.isDirectory();
            long size = isDir ? 0L : Math.max(0L, f.length());
            if (m.matches(f.getName()) && sizeMatches(o, size)) {
                out.add(new Match(f.getAbsolutePath(), f.getName(), isDir, size));
            }
            if (o.recursive && isDir) {
                searchLocal(f, o, m, cancel, out, maxResults, depth + 1);
            }
        }
    }

    // ---------- SFTP 远程搜索 ----------

    public static void searchSftp(SFTPClient sftp, String rootPath, Options o, Matcher m,
                                  CancelFlag cancel, List<Match> out, int maxResults)
            throws java.io.IOException {
        searchSftp(sftp, rootPath, o, m, cancel, out, maxResults, 0);
    }

    private static void searchSftp(SFTPClient sftp, String dir, Options o, Matcher m,
                                   CancelFlag cancel, List<Match> out, int maxResults, int depth)
            throws java.io.IOException {
        if (depth > MAX_DEPTH || out.size() >= maxResults || cancel.isCancelled()) {
            return;
        }
        List<RemoteResourceInfo> list;
        try {
            list = new java.util.ArrayList<>(sftp.ls(dir));
        } catch (java.io.IOException e) {
            return; // 目录不可读（权限等），跳过
        }
        for (RemoteResourceInfo info : list) {
            if (out.size() >= maxResults || cancel.isCancelled()) {
                return;
            }
            String name = info.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            boolean isDir = info.isDirectory()
                    && info.getAttributes().getType() != FileMode.Type.SYMLINK;
            long size = info.isDirectory() ? 0L : Math.max(0L, info.getAttributes().getSize());
            if (m.matches(name) && sizeMatches(o, size)) {
                out.add(new Match(joinPath(dir, name), name, isDir, size));
            }
            if (o.recursive && isDir) {
                searchSftp(sftp, joinPath(dir, name), o, m, cancel, out, maxResults, depth + 1);
            }
        }
    }

    public static String joinPath(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }
}
