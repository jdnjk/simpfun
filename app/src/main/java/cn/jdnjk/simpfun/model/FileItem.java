package cn.jdnjk.simpfun.model;

public class FileItem {
    public static final String PARENT_DIR_NAME = "..";

    private final String name;
    private final boolean file;
    private final long size;
    private final String mime;
    private final String modifiedAt;

    public FileItem(String name, boolean file, long size, String mime, String modifiedAt) {
        this.name = name;
        this.file = file;
        this.size = size;
        this.mime = mime;
        this.modifiedAt = modifiedAt;
    }

    public String getName() {
        return name;
    }

    public boolean isFile() {
        return file;
    }

    public long getSize() {
        return size;
    }

    public String getMime() {
        return mime;
    }

    public String getModifiedAt() {
        return modifiedAt;
    }

    public boolean isParentEntry() {
        return PARENT_DIR_NAME.equals(name);
    }
}

