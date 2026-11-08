package com.tvbox.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.orhanobut.hawk.Hawk;
import com.tvbox.sync.adapter.AppRoomSyncAdapter;
import com.tvbox.sync.model.SyncSnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Credentials;

public class SyncTrigger {
    private static SyncTrigger instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final SyncOpLog opLog = new SyncOpLog();
    private final SharedPrefsSyncStore store;
    private SyncEngine engine;

    private final Runnable debounceRunnable = new Runnable() {
        @Override
        public void run() {
            // 触发前先落盘操作日志: 若 30s 去抖窗口内进程被杀, 重启后这些变更仍能随下次同步上行
            store.saveOpLog(opLog);
            performSync(SyncEngine.SyncMode.BIDIRECTIONAL, null);
        }
    };

    private SyncTrigger(Context context) {
        this.context = context.getApplicationContext();
        this.store = new SharedPrefsSyncStore(this.context);
        this.store.loadOpLog(opLog);
    }

    public static synchronized SyncTrigger get() {
        if (instance == null) {
            instance = new SyncTrigger(App.getInstance());
        }
        return instance;
    }

    public static void init() {
        get().scheduleStartup();
    }

    private void scheduleStartup() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isSyncAllowed()) {
                    triggerStartupPull();
                }
            }
        }, 2000);
    }

    public SyncOpLog getOpLog() {
        return opLog;
    }

    public SharedPrefsSyncStore getStore() {
        return store;
    }

    public synchronized SyncEngine getOrCreateEngine() {
        String url = SyncProtocol.buildSyncUrl(
                Hawk.get(HawkConfig.SYNC_URL, ""), Hawk.get(HawkConfig.SYNC_DIR, ""));
        String user = Hawk.get(HawkConfig.SYNC_USER, "");
        String pwd = Hawk.get(HawkConfig.SYNC_PASSWORD, "");
        String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);

        String auth = "";
        if (!user.isEmpty() || !pwd.isEmpty()) {
            auth = Credentials.basic(user, pwd);
        }

        SyncHttp http = new OkHttpSyncHttp(OkGoHelper.getDefaultClient());
        SyncStorage storage = new WebDavStorage(http, url, spaceId, auth);

        String deviceId = store.getOrCreateDeviceId();
        String deviceName = store.getDeviceName();

        this.engine = new SyncEngine(storage, opLog, deviceId, "mobile", deviceName, spaceId);
        return this.engine;
    }

    public boolean isSyncAllowed() {
        boolean enabled = Hawk.get(HawkConfig.SYNC_ENABLED, false);
        if (!enabled) return false;

        boolean privateBrowsing = Hawk.get(HawkConfig.PRIVATE_BROWSING, false);
        if (privateBrowsing) return false;

        boolean wifiOnly = Hawk.get(HawkConfig.SYNC_WIFI_ONLY, false);
        if (wifiOnly && !isWifiConnected()) return false;

        String url = Hawk.get(HawkConfig.SYNC_URL, "");
        return url != null && !url.isEmpty();
    }

    private boolean isWifiConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo active = cm.getActiveNetworkInfo();
            return active != null && active.isConnected() && active.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Exception e) {
            return false;
        }
    }

    public void triggerStartupPull() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (!isSyncAllowed()) return;
                SyncEngine eng = getOrCreateEngine();
                eng.sync(SyncEngine.SyncMode.BIDIRECTIONAL, new SyncEngine.SyncCallback() {
                    @Override
                    public void onApplied(SyncSnapshot snapshot) throws Exception {
                        AppRoomSyncAdapter.apply(snapshot, new AppRoomSyncAdapter.OnConfigChangedListener() {
                            @Override
                            public void onConfigChanged() {
                                SyncUiReloader.onConfigApplied();
                            }
                        });
                        store.saveOpLog(opLog);
                    }

                    @Override
                    public void onError(String message, Throwable error) {
                    }
                });
            }
        });
    }

    public void triggerDebouncedSync() {
        if (!isSyncAllowed()) return;
        mainHandler.removeCallbacks(debounceRunnable);
        mainHandler.postDelayed(debounceRunnable, 30000);
    }

    public void triggerFlushSync() {
        if (!isSyncAllowed()) return;
        mainHandler.removeCallbacks(debounceRunnable);
        performSync(SyncEngine.SyncMode.BIDIRECTIONAL, null);
    }

    public void performSync(final SyncEngine.SyncMode mode, final SyncEngine.SyncCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (!isSyncAllowed()) {
                    if (callback != null) callback.onError("Sync not enabled or not allowed in current state", null);
                    return;
                }
                SyncEngine eng = getOrCreateEngine();
                eng.sync(mode, new SyncEngine.SyncCallback() {
                    @Override
                    public void onApplied(SyncSnapshot snapshot) throws Exception {
                        AppRoomSyncAdapter.apply(snapshot, new AppRoomSyncAdapter.OnConfigChangedListener() {
                            @Override
                            public void onConfigChanged() {
                                SyncUiReloader.onConfigApplied();
                            }
                        });
                        store.saveOpLog(opLog);
                        store.setLastSyncTs(System.currentTimeMillis());
                        if (callback != null) callback.onApplied(snapshot);
                    }

                    @Override
                    public void onError(String message, Throwable error) {
                        if (callback != null) callback.onError(message, error);
                    }
                });
            }
        });
    }
}
