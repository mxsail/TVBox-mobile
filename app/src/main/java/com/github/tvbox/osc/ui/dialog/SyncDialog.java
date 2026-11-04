package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.orhanobut.hawk.Hawk;
import com.tvbox.sync.OkHttpSyncHttp;
import com.tvbox.sync.SyncEngine;
import com.tvbox.sync.SyncHttp;
import com.tvbox.sync.SyncProtocol;
import com.tvbox.sync.SyncTrigger;
import com.tvbox.sync.WebDavStorage;
import com.tvbox.sync.adapter.AppRoomSyncAdapter;
import com.tvbox.sync.model.SyncSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Credentials;

public class SyncDialog extends BaseDialog {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SwitchMaterial switchSync;
    private EditText etSyncUrl;
    private EditText etSyncUser;
    private EditText etSyncPassword;
    private EditText etSyncSpaceId;
    private CheckBox cbWifiOnly;
    private CheckBox cbScopeConfig;
    private CheckBox cbScopeHistory;
    private CheckBox cbScopeCollect;
    private CheckBox cbScopeProgress;
    private CheckBox cbScopePlayer;
    private TextView tvSyncStatus;
    private MaterialButton btnTest;
    private MaterialButton btnSyncNow;
    private MaterialButton btnSave;

    public SyncDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_sync);
        initViews();
        loadData();
    }

    private void initViews() {
        switchSync = findViewById(R.id.switchSync);
        etSyncUrl = findViewById(R.id.etSyncUrl);
        etSyncUser = findViewById(R.id.etSyncUser);
        etSyncPassword = findViewById(R.id.etSyncPassword);
        etSyncSpaceId = findViewById(R.id.etSyncSpaceId);
        cbWifiOnly = findViewById(R.id.cbWifiOnly);
        cbScopeConfig = findViewById(R.id.cbScopeConfig);
        cbScopeHistory = findViewById(R.id.cbScopeHistory);
        cbScopeCollect = findViewById(R.id.cbScopeCollect);
        cbScopeProgress = findViewById(R.id.cbScopeProgress);
        cbScopePlayer = findViewById(R.id.cbScopePlayer);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);
        btnTest = findViewById(R.id.btnTest);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        btnSave = findViewById(R.id.btnSave);

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });

        btnSyncNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncNow();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveData();
                dismiss();
            }
        });
    }

    private void loadData() {
        switchSync.setChecked(Hawk.get(HawkConfig.SYNC_ENABLED, false));
        etSyncUrl.setText(Hawk.get(HawkConfig.SYNC_URL, ""));
        etSyncUser.setText(Hawk.get(HawkConfig.SYNC_USER, ""));
        etSyncPassword.setText(Hawk.get(HawkConfig.SYNC_PASSWORD, ""));
        etSyncSpaceId.setText(Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID));

        cbWifiOnly.setChecked(Hawk.get(HawkConfig.SYNC_WIFI_ONLY, false));
        cbScopeConfig.setChecked(Hawk.get(HawkConfig.SYNC_SCOPE_CONFIG, true));
        cbScopeHistory.setChecked(Hawk.get(HawkConfig.SYNC_SCOPE_HISTORY, true));
        cbScopeCollect.setChecked(Hawk.get(HawkConfig.SYNC_SCOPE_COLLECT, true));
        cbScopeProgress.setChecked(Hawk.get(HawkConfig.SYNC_SCOPE_PROGRESS, true));
        cbScopePlayer.setChecked(Hawk.get(HawkConfig.SYNC_SCOPE_PLAYER_SETTINGS, false));

        long lastSync = SyncTrigger.get().getStore().getLastSyncTs();
        if (lastSync > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            tvSyncStatus.setText("状态: 上次同步于 " + sdf.format(new Date(lastSync)));
        } else {
            tvSyncStatus.setText("状态: 尚未同步");
        }
    }

    private void saveData() {
        boolean enabled = switchSync.isChecked();
        String url = etSyncUrl.getText().toString().trim();
        String user = etSyncUser.getText().toString().trim();
        String pass = etSyncPassword.getText().toString().trim();
        String spaceId = etSyncSpaceId.getText().toString().trim();
        if (TextUtils.isEmpty(spaceId)) spaceId = SyncProtocol.DEFAULT_SPACE_ID;

        Hawk.put(HawkConfig.SYNC_ENABLED, enabled);
        Hawk.put(HawkConfig.SYNC_URL, url);
        Hawk.put(HawkConfig.SYNC_USER, user);
        Hawk.put(HawkConfig.SYNC_PASSWORD, pass);
        Hawk.put(HawkConfig.SYNC_SPACE_ID, spaceId);

        Hawk.put(HawkConfig.SYNC_WIFI_ONLY, cbWifiOnly.isChecked());
        Hawk.put(HawkConfig.SYNC_SCOPE_CONFIG, cbScopeConfig.isChecked());
        Hawk.put(HawkConfig.SYNC_SCOPE_HISTORY, cbScopeHistory.isChecked());
        Hawk.put(HawkConfig.SYNC_SCOPE_COLLECT, cbScopeCollect.isChecked());
        Hawk.put(HawkConfig.SYNC_SCOPE_PROGRESS, cbScopeProgress.isChecked());
        Hawk.put(HawkConfig.SYNC_SCOPE_PLAYER_SETTINGS, cbScopePlayer.isChecked());

        if (enabled && !TextUtils.isEmpty(url)) {
            AppRoomSyncAdapter.exportDirty(SyncTrigger.get().getOpLog(), spaceId);
            SyncTrigger.get().triggerDebouncedSync();
        }

        Toast.makeText(getContext(), "同步设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        final String url = etSyncUrl.getText().toString().trim();
        final String user = etSyncUser.getText().toString().trim();
        final String pass = etSyncPassword.getText().toString().trim();
        String tempSpace = etSyncSpaceId.getText().toString().trim();
        final String spaceId = TextUtils.isEmpty(tempSpace) ? SyncProtocol.DEFAULT_SPACE_ID : tempSpace;

        if (TextUtils.isEmpty(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            tvSyncStatus.setText("状态: WebDAV 地址无效，须以 http:// 或 https:// 开头");
            return;
        }

        tvSyncStatus.setText("状态: 正在测试 WebDAV 连接...");
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String auth = "";
                    if (!user.isEmpty() || !pass.isEmpty()) {
                        auth = Credentials.basic(user, pass);
                    }
                    SyncHttp http = new OkHttpSyncHttp(OkGoHelper.getDefaultClient());
                    WebDavStorage storage = new WebDavStorage(http, url, spaceId, auth);
                    boolean ok = storage.ensure();
                    if (ok) {
                        postStatus("状态: 连接测试成功！空间可用并已就绪。");
                    } else {
                        postStatus("状态: 连接测试失败，服务器无法创建或访问空间。");
                    }
                } catch (Exception e) {
                    postStatus("状态: 连接失败: " + e.getMessage());
                }
            }
        });
    }

    private void syncNow() {
        saveData();
        if (!SyncTrigger.get().isSyncAllowed()) {
            tvSyncStatus.setText("状态: 当前网络环境或配置不允许同步 (请检查开关与 Wi-Fi 限制)");
            return;
        }

        tvSyncStatus.setText("状态: 正在同步中...");
        btnSyncNow.setEnabled(false);

        SyncTrigger.get().performSync(SyncEngine.SyncMode.BIDIRECTIONAL, new SyncEngine.SyncCallback() {
            @Override
            public void onApplied(final SyncSnapshot snapshot) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        btnSyncNow.setEnabled(true);
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        tvSyncStatus.setText("状态: 同步成功！(" + sdf.format(new Date()) + ")");
                        Toast.makeText(getContext(), "数据同步完成", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(final String message, final Throwable error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        btnSyncNow.setEnabled(true);
                        tvSyncStatus.setText("状态: 同步失败: " + message);
                        Toast.makeText(getContext(), "同步失败: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void postStatus(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvSyncStatus.setText(text);
            }
        });
    }
}
