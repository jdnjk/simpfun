package cn.jdnjk.simpfun.ui.setting;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import cn.jdnjk.simpfun.BuildConfig;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.GitHubApi;
import cn.jdnjk.simpfun.model.GitCommitItem;
import cn.jdnjk.simpfun.utils.ThemeUtils;

public class VersionHistoryActivity extends AppCompatActivity {
    private final GitHubApi gitHubApi = new GitHubApi();
    private final GitCommitAdapter adapter = new GitCommitAdapter();

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private TextView emptyView;
    private View errorContainer;
    private TextView errorView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_history);
        ThemeUtils.applyEdgeToEdge(this);
        ThemeUtils.applyRootInsets(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView currentVersion = findViewById(R.id.tv_current_version);
        currentVersion.setText(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        TextView currentCommit = findViewById(R.id.tv_current_commit);
        currentCommit.setText("当前提交：" + BuildConfig.GIT_COMMIT);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        recycler = findViewById(R.id.recycler_commits);
        emptyView = findViewById(R.id.tv_empty);
        errorContainer = findViewById(R.id.error_container);
        errorView = findViewById(R.id.tv_error);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        adapter.setCurrentCommit(BuildConfig.GIT_COMMIT);

        swipeRefresh.setOnRefreshListener(this::loadCommits);
        findViewById(R.id.btn_retry).setOnClickListener(v -> loadCommits());
        loadCommits();
    }

    private void loadCommits() {
        swipeRefresh.setRefreshing(true);
        errorContainer.setVisibility(View.GONE);
        gitHubApi.fetchCommits(new GitHubApi.CommitsCallback() {
            @Override
            public void onSuccess(List<GitCommitItem> commits) {
                swipeRefresh.setRefreshing(false);
                adapter.submitList(commits);
                emptyView.setVisibility(commits.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(commits.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(String message) {
                swipeRefresh.setRefreshing(false);
                recycler.setVisibility(View.GONE);
                emptyView.setVisibility(View.GONE);
                errorContainer.setVisibility(View.VISIBLE);
                errorView.setText(message);
            }
        });
    }
}
