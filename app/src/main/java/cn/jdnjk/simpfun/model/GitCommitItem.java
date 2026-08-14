package cn.jdnjk.simpfun.model;

public class GitCommitItem {
    private final String sha;
    private final String message;
    private final String author;
    private final String date;

    public GitCommitItem(String sha, String message, String author, String date) {
        this.sha = sha;
        this.message = message;
        this.author = author;
        this.date = date;
    }

    public String getSha() {
        return sha;
    }

    public String getShortSha() {
        return sha == null || sha.length() <= 8 ? sha : sha.substring(0, 8);
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getDate() {
        return date;
    }
}
