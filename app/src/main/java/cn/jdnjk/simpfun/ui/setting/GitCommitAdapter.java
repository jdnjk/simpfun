package cn.jdnjk.simpfun.ui.setting;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.model.GitCommitItem;

public class GitCommitAdapter extends RecyclerView.Adapter<GitCommitAdapter.CommitViewHolder> {
    private final List<GitCommitItem> items = new ArrayList<>();
    private String currentCommit;

    public void setCurrentCommit(String currentCommit) {
        this.currentCommit = currentCommit == null ? "" : currentCommit.trim().toLowerCase();
    }

    public void submitList(List<GitCommitItem> commits) {
        items.clear();
        if (commits != null) {
            items.addAll(commits);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_commit, parent, false);
        return new CommitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommitViewHolder holder, int position) {
        GitCommitItem item = items.get(position);
        holder.sha.setText(item.getShortSha());
        holder.message.setText(item.getMessage());
        holder.meta.setText(item.getAuthor() + " · " + item.getDate());
        boolean isCurrent = currentCommit != null
                && (item.getSha().toLowerCase().startsWith(currentCommit)
                    || currentCommit.startsWith(item.getSha().toLowerCase()));
        holder.badge.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CommitViewHolder extends RecyclerView.ViewHolder {
        final TextView sha;
        final TextView message;
        final TextView meta;
        final TextView badge;

        CommitViewHolder(@NonNull View itemView) {
            super(itemView);
            sha = itemView.findViewById(R.id.tv_commit_sha);
            message = itemView.findViewById(R.id.tv_commit_message);
            meta = itemView.findViewById(R.id.tv_commit_meta);
            badge = itemView.findViewById(R.id.tv_current_badge);
        }
    }
}
