package cn.jdnjk.simpfun.ui.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.ins.PowerApi;
import cn.jdnjk.simpfun.model.ServerItem;
import cn.jdnjk.simpfun.MainActivity;
import cn.jdnjk.simpfun.ServerManages;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ServerViewHolder> {

    private final List<ServerItem> serverList;
    private final String token;
    private final MainActivity activity;

    public ServerAdapter(List<ServerItem> serverList, String token, MainActivity activity) {
        this.serverList = serverList;
        this.token = token;
        this.activity = activity;
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

        holder.textName.setText(server.getName());
        holder.textId.setText("#" + server.getId());
        holder.textInfo.setText("CPU " + server.getCpu() + "核 内存 " + server.getRam() + "GB 存储 " + server.getDisk() + "GB");

        holder.btnStart.setOnClickListener(v -> {
            new PowerApi(activity).powerControl(token, server.getId(), PowerApi.Action.START, getPowerCallback(server.getId(), "开机"));
        });

        holder.btnRestart.setOnClickListener(v -> {
            new PowerApi(activity).powerControl(token, server.getId(), PowerApi.Action.RESTART, getPowerCallback(server.getId(), "重启"));
        });

        holder.btnStop.setOnClickListener(v -> {
            new PowerApi(activity).powerControl(token, server.getId(), PowerApi.Action.STOP, getPowerCallback(server.getId(), "关机"));
        });

        holder.btnKill.setOnClickListener(v -> {
            new PowerApi(activity).powerControl(token, server.getId(), PowerApi.Action.KILL, getPowerCallback(server.getId(), "强制关机"));
        });

        holder.itemView.setOnClickListener(v -> {
            int deviceId = server.getId();
            Intent intent = new Intent(activity, ServerManages.class);
            intent.putExtra(ServerManages.EXTRA_DEVICE_ID, deviceId);
            SharedPreferences sp = activity.getSharedPreferences("deviceid", Context.MODE_PRIVATE);
            sp.edit().putInt("device_id", deviceId).apply();
            activity.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return serverList.size();
    }

    static class ServerViewHolder extends RecyclerView.ViewHolder {
        TextView textName, textId, textInfo;
        ImageButton btnStart, btnRestart, btnStop, btnKill;

        public ServerViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_server_name);
            textId = itemView.findViewById(R.id.text_server_id);
            textInfo = itemView.findViewById(R.id.text_server_info);
            btnStart = itemView.findViewById(R.id.btn_start);
            btnRestart = itemView.findViewById(R.id.btn_restart);
            btnStop = itemView.findViewById(R.id.btn_stop);
            btnKill = itemView.findViewById(R.id.btn_kill);
        }
    }

    private PowerApi.Callback getPowerCallback(int serverId, String action) {
        return new PowerApi.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                activity.runOnUiThread(() ->
                        android.widget.Toast.makeText(activity, action + "指令已发送", android.widget.Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onFailure(String errorMsg) {
                activity.runOnUiThread(() ->
                        android.widget.Toast.makeText(activity, action + "失败: " + errorMsg, android.widget.Toast.LENGTH_LONG).show()
                );
            }
        };
    }
}