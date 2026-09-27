package cn.jdnjk.simpfun.ui.profile;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.AnnouncementApi;
import cn.jdnjk.simpfun.model.AnnouncementItem;

public class AnnouncementListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private AnnouncementAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_announcements);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_announcements);
        progressBar = findViewById(R.id.progress_announcements);
        tvEmpty = findViewById(R.id.tv_announcements_empty);

        adapter = new AnnouncementAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadAnnouncements();
    }

    private void loadAnnouncements() {
        SharedPreferences auth = getSharedPreferences("token", MODE_PRIVATE);
        String token = auth.getString("token", null);

        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        new AnnouncementApi().getAnnouncements(token, new AnnouncementApi.Callback() {
            @Override
            public void onSuccess(List<AnnouncementItem> list) {
                progressBar.setVisibility(View.GONE);
                if (list.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    adapter.submitItems(list);
                }
            }

            @Override
            public void onFailure(String errorMsg) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AnnouncementListActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
