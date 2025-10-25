package cn.jdnjk.simpfun.model;

public class TaskItem {
    private final int id;
    private final int status;
    private final String comment;
    private final String createTime;

    public TaskItem(int id, int status, String comment, String createTime) {
        this.id = id;
        this.status = status;
        this.comment = comment;
        this.createTime = createTime;
    }

    public int getId() { return id; }
    public int getStatus() { return status; }
    public String getComment() { return comment; }
    public String getCreateTime() { return createTime; }
}

