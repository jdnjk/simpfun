package cn.jdnjk.simpfun.utils;

public final class FilePathUtils {
    private FilePathUtils() {
    }

    public static String sanitizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String getParentPath(String path) {
        if (path == null || "/".equals(path)) {
            return "/";
        }
        String value = path;
        if (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        int idx = value.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return value.substring(0, idx);
    }

    public static String appendPath(String base, String name) {
        if (name == null || name.isEmpty()) {
            return sanitizePath(base);
        }
        String safeBase = sanitizePath(base);
        if ("/".equals(safeBase)) {
            return safeBase + name;
        }
        return safeBase + "/" + name;
    }

    public static boolean isSafeEntryName(String name) {
        if (name == null) {
            return false;
        }
        String value = name.trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '/' || ch == '\\' || Character.isISOControl(ch)) {
                return false;
            }
        }
        return true;
    }

    public static String sanitizeFileName(String name, String fallback) {
        String fallbackName = fallback == null || fallback.trim().isEmpty() ? "file" : fallback.trim();
        if (name == null) {
            return fallbackName;
        }
        String value = name.trim();
        if (value.isEmpty()) {
            return fallbackName;
        }
        int lastSlash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < value.length() - 1) {
            value = value.substring(lastSlash + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}/\\\\]+", "_").trim();
        if (!isSafeEntryName(value)) {
            return fallbackName;
        }
        return value;
    }
}

