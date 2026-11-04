package com.tvbox.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SharedPrefsSyncStore {
    private static final String PREF_NAME = "sync_meta";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_LAST_REVISION = "last_revision";
    private static final String KEY_LAST_ACK_REVISION = "last_ack_revision";
    private static final String KEY_LAST_HASH = "last_hash";
    private static final String KEY_LAST_SYNC_TS = "last_sync_ts";
    private static final String KEY_OPLOG = "oplog_json";

    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    public SharedPrefsSyncStore(Context context) {
        this.sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized String getOrCreateDeviceId() {
        String id = sp.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public synchronized String getDeviceName() {
        String name = sp.getString(KEY_DEVICE_NAME, null);
        if (name == null || name.isEmpty()) {
            name = Build.MODEL != null ? Build.MODEL : "Android Device";
            sp.edit().putString(KEY_DEVICE_NAME, name).apply();
        }
        return name;
    }

    public synchronized void setDeviceName(String name) {
        sp.edit().putString(KEY_DEVICE_NAME, name).apply();
    }

    public synchronized long getLastRevision() {
        return sp.getLong(KEY_LAST_REVISION, 0);
    }

    public synchronized void setLastRevision(long rev) {
        sp.edit().putLong(KEY_LAST_REVISION, rev).apply();
    }

    public synchronized long getLastAckRevision() {
        return sp.getLong(KEY_LAST_ACK_REVISION, 0);
    }

    public synchronized void setLastAckRevision(long rev) {
        sp.edit().putLong(KEY_LAST_ACK_REVISION, rev).apply();
    }

    public synchronized long getLastSyncTs() {
        return sp.getLong(KEY_LAST_SYNC_TS, 0);
    }

    public synchronized void setLastSyncTs(long ts) {
        sp.edit().putLong(KEY_LAST_SYNC_TS, ts).apply();
    }

    public synchronized void saveOpLog(SyncOpLog opLog) {
        if (opLog == null) return;
        List<SyncOpLog.SyncOp> list = opLog.readOps(null, 0);
        String json = gson.toJson(list);
        sp.edit().putString(KEY_OPLOG, json).apply();
    }

    public synchronized void loadOpLog(SyncOpLog opLog) {
        if (opLog == null) return;
        String json = sp.getString(KEY_OPLOG, null);
        if (json != null && !json.isEmpty()) {
            try {
                Type type = new TypeToken<List<SyncOpLog.SyncOp>>() {}.getType();
                List<SyncOpLog.SyncOp> list = gson.fromJson(json, type);
                if (list != null) {
                    for (SyncOpLog.SyncOp op : list) {
                        opLog.record(op);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
