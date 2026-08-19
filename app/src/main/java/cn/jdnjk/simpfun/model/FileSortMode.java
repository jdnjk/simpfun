package cn.jdnjk.simpfun.model;

/**
 * 文件列表排序方式。
 */
public enum FileSortMode {
    NAME_ASC("名称 ↑"),
    NAME_DESC("名称 ↓"),
    SIZE_ASC("大小 ↑"),
    SIZE_DESC("大小 ↓"),
    DATE_ASC("日期 ↑"),
    DATE_DESC("日期 ↓"),
    TYPE_ASC("类型 ↑"),
    TYPE_DESC("类型 ↓");

    private final String label;

    FileSortMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按枚举名解析，未知值回退到名称升序。
     */
    public static FileSortMode fromName(String name) {
        for (FileSortMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return NAME_ASC;
    }
}