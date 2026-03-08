package cn.jdnjk.simpfun.ui.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;

import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.ServerManages;
import cn.jdnjk.simpfun.model.ServerItem;
import cn.jdnjk.simpfun.model.ServerStatsSnapshot;
import cn.jdnjk.simpfun.utils.ServerStatsFormatter;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ServerViewHolder> {

    private final List<ServerItem> serverList;
    private final MainActivity activity;
    private boolean useModernStyle;

    public ServerAdapter(List<ServerItem> serverList, MainActivity activity, boolean useModernStyle) {
        this.serverList = serverList;
        this.activity = activity;
        this.useModernStyle = useModernStyle;
    }

    public void setUseModernStyle(boolean useModernStyle) {
        this.useModernStyle = useModernStyle;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ServerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_server, parent, false);
        return new ServerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServerViewHolder holder, int position) {
        ServerItem server = serverList.get(position);
        ServerStatsSnapshot stats = server.getStats();
        boolean showModern = useModernStyle && stats != null && !"offline".equalsIgnoreCase(stats.getState());

        holder.oldLayout.setVisibility(showModern ? View.GONE : View.VISIBLE);
        holder.modernLayout.setVisibility(showModern ? View.VISIBLE : View.GONE);

        holder.textName.setText(server.getName());
        holder.textId.setText("#" + server.getId());
        holder.textInfo.setText("CPU " + server.getCpu() + "核 内存 " + server.getRam() + "GB 存储 " + server.getDisk() + "GB");

        holder.modernName.setText(server.getName());
        holder.modernId.setText("ID: " + server.getId());
        bindModernStats(holder, server);

        holder.itemView.setOnClickListener(v -> {
            int deviceId = server.getId();
            Intent intent = new Intent(activity, ServerManages.class);
            intent.putExtra(ServerManages.EXTRA_DEVICE_ID, deviceId);
            SharedPreferences sp = activity.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            sp.edit().putInt("device_id", deviceId).apply();
            activity.startActivity(intent);
        });
    }

    private void bindModernStats(@NonNull ServerViewHolder holder, @NonNull ServerItem server) {
        ServerStatsSnapshot stats = server.getStats();
        if (stats == null) {
            holder.uptime.setText("--");
            holder.uploadSpeed.setText("--");
            holder.downloadSpeed.setText("--");
            holder.cpuPercent.setText("--");
            holder.memoryPercent.setText("--");
            holder.cpuProgress.setProgressCompat(0, false);
            holder.memoryProgress.setProgressCompat(0, false);
            return;
        }

        int cpuPercent = ServerStatsFormatter.toCpuPercent(stats.getCpuAbsolute(), server.getCpuLimit());
        int memoryPercent = ServerStatsFormatter.toMemoryPercent(stats.getMemoryBytes(), stats.getMemoryLimitBytes());

        holder.uptime.setText(ServerStatsFormatter.formatUptime(stats.getUptimeMillis()));
        holder.uploadSpeed.setText(ServerStatsFormatter.formatSpeed(stats.getUploadBytesPerSecond()));
        holder.downloadSpeed.setText(ServerStatsFormatter.formatSpeed(stats.getDownloadBytesPerSecond()));
        holder.cpuPercent.setText(ServerStatsFormatter.formatPercentText(cpuPercent));
        holder.memoryPercent.setText(ServerStatsFormatter.formatPercentText(memoryPercent));
        holder.cpuProgress.setProgressCompat(cpuPercent, false);
        holder.memoryProgress.setProgressCompat(memoryPercent, false);
    }

    @Override
    public int getItemCount() {
        return serverList.size();
    }

    static class ServerViewHolder extends RecyclerView.ViewHolder {
        View oldLayout, modernLayout;
        TextView textName, textId, textInfo;
        TextView modernName, modernId, uptime, uploadSpeed, downloadSpeed, cpuPercent, memoryPercent;
        CircularProgressIndicator cpuProgress, memoryProgress;

        public ServerViewHolder(@NonNull View itemView) {
            super(itemView);
            oldLayout = itemView.findViewById(R.id.layout_server_old);
            modernLayout = itemView.findViewById(R.id.layout_server_modern);
            textName = itemView.findViewById(R.id.text_server_name);
            textId = itemView.findViewById(R.id.text_server_id);
            textInfo = itemView.findViewById(R.id.text_server_info);
            modernName = itemView.findViewById(R.id.tv_server_name_modern);
            modernId = itemView.findViewById(R.id.tv_server_id_modern);
            uptime = itemView.findViewById(R.id.tv_server_uptime);
            uploadSpeed = itemView.findViewById(R.id.tv_upload_speed);
            downloadSpeed = itemView.findViewById(R.id.tv_download_speed);
            cpuPercent = itemView.findViewById(R.id.tv_cpu_percent);
            memoryPercent = itemView.findViewById(R.id.tv_memory_percent);
            cpuProgress = itemView.findViewById(R.id.progress_cpu);
            memoryProgress = itemView.findViewById(R.id.progress_memory);
        }
    }
}