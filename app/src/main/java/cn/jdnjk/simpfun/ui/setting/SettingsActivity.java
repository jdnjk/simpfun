package cn.jdnjk.simpfun.ui.setting;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import cn.jdnjk.simpfun.R;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }
    }
}