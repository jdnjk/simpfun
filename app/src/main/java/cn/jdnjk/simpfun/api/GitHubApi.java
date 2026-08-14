package cn.jdnjk.simpfun.api;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.model.GitCommitItem;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class GitHubApi {
    private static final String COMMITS_URL =
            "https://api.github.com/repos/jdnjk/simpfun/commits?per_page=50";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CommitsCallback {
        void onSuccess(List<GitCommitItem> commits);
        void onFailure(String message);
    }

    public void fetchCommits(CommitsCallback callback) {
        Request request = new Request.Builder()
                .url(COMMITS_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SimpfunAPP")
                .build();

        ApiClient.getInstance().getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onFailure("GitHub 请求失败: HTTP " + response.code()));
                        return;
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    List<GitCommitItem> items = parseCommits(body);
                    mainHandler.post(() -> callback.onSuccess(items));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onFailure("解析提交历史失败: " + e.getMessage()));
                }
            }
        });
    }

    private List<GitCommitItem> parseCommits(String body) throws Exception {
        JSONArray array = new JSONArray(body);
        List<GitCommitItem> items = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String sha = item.getString("sha");
            JSONObject commit = item.getJSONObject("commit");
            String message = commit.getString("message");
            String firstLine = message.split("\\R", 2)[0].trim();
            String author = commit.getJSONObject("author").optString("name", "Unknown");
            String date = commit.getJSONObject("author").optString("date", "");
            String displayDate = date;
            try {
                displayDate = OffsetDateTime.parse(date)
                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .format(formatter);
            } catch (Exception ignored) {
            }
            items.add(new GitCommitItem(sha, firstLine, author, displayDate));
        }
        return items;
    }
}
