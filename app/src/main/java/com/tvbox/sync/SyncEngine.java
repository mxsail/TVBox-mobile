package com.tvbox.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tvbox.sync.model.SyncSnapshot;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class SyncEngine {
    public enum SyncMode {
        BIDIRECTIONAL,
        UPLOAD_ONLY,
        DOWNLOAD_ONLY
    }

    public interface SyncCallback {
        void onApplied(SyncSnapshot snapshot) throws Exception;
        void onError(String message, Throwable error);
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_RETRIES = 5;

    private final SyncStorage storage;
    private final SyncOpLog opLog;
    private final String deviceId;
    private final String platform;
    private final String deviceName;
    private final String spaceId;

    // Rate limiting cooldown (5-10s)
    private long lastSuccessfulWriteTs = 0;
    private static final long MIN_WRITE_COOLDOWN_MS = 5000;

    // HTTP 429 cooldown
    private long coolDownUntilTs = 0;

    public SyncEngine(SyncStorage storage, SyncOpLog opLog, String deviceId, String platform, String deviceName, String spaceId) {
        this.storage = storage;
        this.opLog = opLog;
        this.deviceId = deviceId;
        this.platform = platform;
        this.deviceName = deviceName;
        this.spaceId = (spaceId == null || spaceId.isEmpty()) ? SyncProtocol.DEFAULT_SPACE_ID : spaceId;
    }

    public synchronized boolean sync(SyncMode mode, SyncCallback callback) {
        long now = System.currentTimeMillis();
        if (now < coolDownUntilTs) {
            if (callback != null) {
                callback.onError("WebDAV rate limited (429 cooldown active)", null);
            }
            return false;
        }

        try {
            storage.ensure();
        } catch (IOException e) {
            if (callback != null) callback.onError("Failed to ensure remote directory: " + e.getMessage(), e);
            return false;
        }

        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                // 1. Read cloud snapshot
                byte[] cloudBytes = storage.read("sync.json");
                SyncSnapshot cloud = null;
                String etag = null;

                if (cloudBytes != null) {
                    etag = storage.etag("sync.json");
                    String json = new String(cloudBytes, "UTF-8");
                    cloud = GSON.fromJson(json, SyncSnapshot.class);
                    if (!SyncProtocol.verifyChecksum(cloud)) {
                        // Checksum mismatch, attempt to read lastgood.json
                        byte[] lastGoodBytes = storage.read("lastgood.json");
                        if (lastGoodBytes != null) {
                            SyncSnapshot fallback = GSON.fromJson(new String(lastGoodBytes, "UTF-8"), SyncSnapshot.class);
                            if (SyncProtocol.verifyChecksum(fallback)) {
                                cloud = fallback;
                            } else {
                                throw new IOException("Cloud snapshot corrupted and fallback invalid");
                            }
                        } else {
                            throw new IOException("Cloud snapshot checksum verification failed");
                        }
                    }
                }

                // 2. Freeze ops
                long frozenSeq = opLog.getLatestSeq();
                List<SyncOpLog.SyncOp> dirtyOps = opLog.readOps(spaceId, frozenSeq);

                // 3. Merge
                SyncSnapshot merged = SyncMerger.merge(cloud, dirtyOps, deviceId, platform, deviceName, now);

                if (mode == SyncMode.DOWNLOAD_ONLY) {
                    if (callback != null && cloud != null) {
                        callback.onApplied(cloud);
                    }
                    return true;
                }

                // 4. Check if cloud write is needed
                String mergedJson = GSON.toJson(merged);
                byte[] mergedBytes = mergedJson.getBytes("UTF-8");

                boolean isCreate = (cloud == null);
                String tmpName = "sync.json.tmp-" + UUID.randomUUID().toString();

                // Atomic write via tmp -> MOVE
                boolean writeOk = storage.write(tmpName, mergedBytes, null, true);
                if (writeOk) {
                    boolean moveOk = storage.move(tmpName, "sync.json");
                    if (!moveOk) {
                        // Fallback direct PUT
                        storage.delete(tmpName);
                        writeOk = storage.write("sync.json", mergedBytes, etag, isCreate);
                    }
                } else {
                    writeOk = storage.write("sync.json", mergedBytes, etag, isCreate);
                }

                if (!writeOk) {
                    // Retry optimistic lock
                    Thread.sleep(50 + (int)(Math.random() * 100));
                    continue;
                }

                // 5. Best-effort lastgood copy
                try {
                    storage.copy("sync.json", "lastgood.json");
                } catch (Exception ignored) {
                }

                // 6. Apply to local DB
                if (callback != null && mode != SyncMode.UPLOAD_ONLY) {
                    callback.onApplied(merged);
                }

                // 7. Ack oplog
                opLog.ack(spaceId, frozenSeq);
                lastSuccessfulWriteTs = System.currentTimeMillis();

                return true;

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    coolDownUntilTs = System.currentTimeMillis() + 60000; // 60s cooldown
                    if (callback != null) callback.onError("Received HTTP 429 Too Many Requests, pausing sync for 60s", e);
                    return false;
                }
                if (attempt >= MAX_RETRIES) {
                    if (callback != null) callback.onError("Sync failed after " + MAX_RETRIES + " retries: " + e.getMessage(), e);
                    return false;
                }
            }
        }

        return false;
    }

    public long getLastSuccessfulWriteTs() {
        return lastSuccessfulWriteTs;
    }
}
