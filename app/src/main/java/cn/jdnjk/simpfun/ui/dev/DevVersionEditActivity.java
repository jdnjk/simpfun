package cn.jdnjk.simpfun.ui.dev;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONObject;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.api.dev.DevApi;

public class DevVersionEditActivity extends AppCompatActivity {

    public static final String EXTRA_MIRROR_ID = "extra_mirror_id";
    public static final String EXTRA_VERSION_ID = "extra_version_id";
    public static final String EXTRA_STATE = "extra_state";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_DESCRIPTION = "extra_description";
    public static final String EXTRA_RECOMMEND_SETTING = "extra_recommend_setting";
    public static final String EXTRA_ONE_KEY = "extra_one_key";
    public static final String EXTRA_PROPERTIES = "extra_properties";
    public static final String EXTRA_RESTORE_TIMES = "extra_restore_times";
    public static final String EXTRA_LIKE = "extra_like";
    public static final String EXTRA_DISLIKE = "extra_dislike";

    private DevApi devApi;
    private String token;
    private int mirrorId;
    private long versionId;

    private MaterialSwitch switchState;
    private EditText etName, etDescription, etRecommend, etOneKey, etProperties;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_version_edit);

        devApi = new DevApi();
        token = getSharedPreferences("token", MODE_PRIVATE).getString("token", null);
        mirrorId = getIntent().getIntExtra(EXTRA_MIRROR_ID, -1);
        versionId = readVersionIdExtra(getIntent());

        if (mirrorId <= 0 || versionId <= 0 || token == null) {
            Toast.makeText(this, "无效的版本", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar_version_edit);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        toolbar.setTitle(name == null || name.isEmpty() ? "版本编辑" : name);
        toolbar.setNavigationOnClickListener(v -> finish());

        switchState = findViewById(R.id.switch_version_state);
        etName = findViewById(R.id.et_version_name);
        etDescription = findViewById(R.id.et_version_description);
        etRecommend = findViewById(R.id.et_version_recommend);
        etOneKey = findViewById(R.id.et_version_one_key);
        etProperties = findViewById(R.id.et_version_properties);

        Intent intent = getIntent();
        switchState.setChecked(intent.getBooleanExtra(EXTRA_STATE, false));
        etName.setText(intent.getStringExtra(EXTRA_NAME));
        etDescription.setText(intent.getStringExtra(EXTRA_DESCRIPTION));
        etRecommend.setText(intent.getStringExtra(EXTRA_RECOMMEND_SETTING));
        etOneKey.setText(intent.getStringExtra(EXTRA_ONE_KEY));
        etProperties.setText(intent.getStringExtra(EXTRA_PROPERTIES));

        ((TextView) findViewById(R.id.tv_version_restore_times))
                .setText(String.valueOf(intent.getIntExtra(EXTRA_RESTORE_TIMES, 0)));
        ((TextView) findViewById(R.id.tv_version_like))
                .setText(String.valueOf(intent.getIntExtra(EXTRA_LIKE, 0)));
        ((TextView) findViewById(R.id.tv_version_dislike))
                .setText(String.valueOf(intent.getIntExtra(EXTRA_DISLIKE, 0)));

        findViewById(R.id.btn_save_version).setOnClickListener(v -> saveVersion());
    }

    private void saveVersion() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "版本名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        devApi.updateVersion(token, mirrorId, versionId, switchState.isChecked(), name,
                etDescription.getText().toString(),
                etRecommend.getText().toString(),
                etOneKey.getText().toString(),
                etProperties.getText().toString(),
                new DevApi.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        Toast.makeText(DevVersionEditActivity.this,
                                response.optString("msg", "保存成功"), Toast.LENGTH_SHORT).show();
                        setResult(Activity.RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        Toast.makeText(DevVersionEditActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static long readVersionIdExtra(Intent intent) {
        try {
            return intent.getLongExtra(EXTRA_VERSION_ID, -1L);
        } catch (ClassCastException e) {
            return intent.getIntExtra(EXTRA_VERSION_ID, -1);
        }
    }
}
