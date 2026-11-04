package com.tvbox.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tvbox.sync.model.SyncClear;
import com.tvbox.sync.model.SyncCollect;
import com.tvbox.sync.model.SyncConfigGroup;
import com.tvbox.sync.model.SyncDevice;
import com.tvbox.sync.model.SyncHistory;
import com.tvbox.sync.model.SyncPlayer;
import com.tvbox.sync.model.SyncProgress;
import com.tvbox.sync.model.SyncSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SyncMerger {
    private static final Gson GSON = new GsonBuilder().create();
    private static final long NINETY_DAYS_MS = 90L * 24 * 3600 * 1000;
    private static final int MAX_DEVICES = 20;

    public static SyncSnapshot merge(SyncSnapshot cloud, List<SyncOpLog.SyncOp> dirtyOps, String writerId, String platform, String deviceName, long now) {
        SyncSnapshot merged;
        long targetRev;
        if (cloud == null) {
            merged = new SyncSnapshot();
            targetRev = 1;
        } else {
            // Clone cloud via JSON serialization
            String json = GSON.toJson(cloud);
            merged = GSON.fromJson(json, SyncSnapshot.class);
            targetRev = cloud.revision + 1;
        }

        merged.revision = targetRev;
        merged.writer = writerId;
        merged.updatedAt = now;

        // 1. Update Devices Registry
        if (merged.devices == null) {
            merged.devices = new HashMap<String, SyncDevice>();
        }
        SyncDevice currentDevice = merged.devices.get(writerId);
        if (currentDevice == null) {
            currentDevice = new SyncDevice(deviceName, platform, now, targetRev);
            merged.devices.put(writerId, currentDevice);
        } else {
            currentDevice.name = deviceName;
            currentDevice.platform = platform;
            currentDevice.lastSeen = now;
            currentDevice.lastAckRevision = targetRev;
        }
        pruneDevices(merged.devices, now);

        if (merged.clears == null) {
            merged.clears = new HashMap<String, SyncClear>();
        }
        if (merged.history == null) {
            merged.history = new ArrayList<SyncHistory>();
        }
        if (merged.collects == null) {
            merged.collects = new ArrayList<SyncCollect>();
        }
        if (merged.progress == null) {
            merged.progress = new ArrayList<SyncProgress>();
        }
        if (merged.config == null) {
            merged.config = new SyncConfigGroup();
        }

        // Map indices for O(1) lookups
        Map<String, SyncHistory> historyMap = new HashMap<String, SyncHistory>();
        for (SyncHistory h : merged.history) {
            if (h.id != null) historyMap.put(h.id, h);
        }

        Map<String, SyncCollect> collectMap = new HashMap<String, SyncCollect>();
        for (SyncCollect c : merged.collects) {
            if (c.id != null) collectMap.put(c.id, c);
        }

        Map<String, SyncProgress> progressMap = new HashMap<String, SyncProgress>();
        for (SyncProgress p : merged.progress) {
            if (p.key != null) progressMap.put(p.key, p);
        }

        // 2. Process dirty ops
        if (dirtyOps != null) {
            for (SyncOpLog.SyncOp op : dirtyOps) {
                if (op == null || op.entity == null) continue;

                if ("clears".equals(op.op) || "clear".equals(op.op)) {
                    SyncClear clear = new SyncClear(targetRev, writerId, op.ts > 0 ? op.ts : now);
                    merged.clears.put(op.entity, clear);
                    continue;
                }

                if ("history".equals(op.entity)) {
                    if ("delete".equals(op.op)) {
                        SyncHistory h = historyMap.get(op.id);
                        if (h == null) {
                            h = new SyncHistory();
                            h.id = op.id;
                            historyMap.put(op.id, h);
                        }
                        h.deleted = true;
                        h.rev = targetRev;
                        h.updatedAt = op.ts > 0 ? op.ts : now;
                        h.payload = null;
                    } else if ("upsert".equals(op.op) && op.data != null) {
                        try {
                            SyncHistory incoming = GSON.fromJson(op.data, SyncHistory.class);
                            incoming.rev = targetRev;
                            if (incoming.updatedAt <= 0) incoming.updatedAt = op.ts > 0 ? op.ts : now;
                            historyMap.put(incoming.id, incoming);
                        } catch (Exception ignored) {
                        }
                    }
                } else if ("collect".equals(op.entity) || "collects".equals(op.entity)) {
                    if ("delete".equals(op.op)) {
                        SyncCollect c = collectMap.get(op.id);
                        if (c == null) {
                            c = new SyncCollect();
                            c.id = op.id;
                            collectMap.put(op.id, c);
                        }
                        c.deleted = true;
                        c.rev = targetRev;
                        c.updatedAt = op.ts > 0 ? op.ts : now;
                    } else if ("upsert".equals(op.op) && op.data != null) {
                        try {
                            SyncCollect incoming = GSON.fromJson(op.data, SyncCollect.class);
                            incoming.rev = targetRev;
                            if (incoming.updatedAt <= 0) incoming.updatedAt = op.ts > 0 ? op.ts : now;
                            collectMap.put(incoming.id, incoming);
                        } catch (Exception ignored) {
                        }
                    }
                } else if ("progress".equals(op.entity)) {
                    if ("delete".equals(op.op)) {
                        SyncProgress p = progressMap.get(op.id);
                        if (p == null) {
                            p = new SyncProgress();
                            p.key = op.id;
                            progressMap.put(op.id, p);
                        }
                        p.deleted = true;
                        p.rev = targetRev;
                        p.updatedAt = op.ts > 0 ? op.ts : now;
                    } else if ("upsert".equals(op.op) && op.data != null) {
                        try {
                            SyncProgress incoming = GSON.fromJson(op.data, SyncProgress.class);
                            incoming.rev = targetRev;
                            if (incoming.updatedAt <= 0) incoming.updatedAt = op.ts > 0 ? op.ts : now;
                            progressMap.put(incoming.key, incoming);
                        } catch (Exception ignored) {
                        }
                    }
                } else if (op.entity.startsWith("config.")) {
                    String groupName = op.entity.substring("config.".length());
                    if ("content".equals(groupName) && op.data != null) {
                        try {
                            SyncConfigGroup.ContentGroup contentGroup = GSON.fromJson(op.data, SyncConfigGroup.ContentGroup.class);
                            contentGroup.rev = targetRev;
                            contentGroup.writer = writerId;
                            merged.config.groups.put("content", contentGroup);
                        } catch (Exception ignored) {
                        }
                    } else if ("player".equals(groupName) && op.data != null) {
                        try {
                            SyncPlayer player = GSON.fromJson(op.data, SyncPlayer.class);
                            player.rev = targetRev;
                            player.writer = writerId;
                            merged.config.groups.put("player", player);
                        } catch (Exception ignored) {
                        }
                    } else if ("network".equals(groupName) && op.data != null) {
                        try {
                            SyncConfigGroup.NetworkGroup networkGroup = GSON.fromJson(op.data, SyncConfigGroup.NetworkGroup.class);
                            networkGroup.rev = targetRev;
                            networkGroup.writer = writerId;
                            merged.config.groups.put("network", networkGroup);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        // 3. Apply global clears to history & collects
        SyncClear historyClear = merged.clears.get("history");
        if (historyClear != null) {
            for (SyncHistory h : historyMap.values()) {
                if (h.rev <= historyClear.rev) {
                    h.deleted = true;
                    h.payload = null;
                }
            }
        }

        SyncClear collectClear = merged.clears.get("collect");
        if (collectClear != null) {
            for (SyncCollect c : collectMap.values()) {
                if (c.rev <= collectClear.rev) {
                    c.deleted = true;
                }
            }
        }

        // Reassemble lists
        merged.history = new ArrayList<SyncHistory>(historyMap.values());
        merged.collects = new ArrayList<SyncCollect>(collectMap.values());
        merged.progress = new ArrayList<SyncProgress>(progressMap.values());

        // 4. Update checksum
        merged.checksum = SyncProtocol.calculateChecksum(merged);

        return merged;
    }

    private static void pruneDevices(Map<String, SyncDevice> devices, long now) {
        if (devices == null || devices.size() <= MAX_DEVICES) return;

        // Remove devices inactive > 90 days
        Iterator<Map.Entry<String, SyncDevice>> it = devices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SyncDevice> entry = it.next();
            if (entry.getValue() != null && (now - entry.getValue().lastSeen > NINETY_DAYS_MS)) {
                it.remove();
            }
        }

        // If still > MAX_DEVICES, retain top MAX_DEVICES by lastSeen
        if (devices.size() > MAX_DEVICES) {
            List<Map.Entry<String, SyncDevice>> list = new ArrayList<Map.Entry<String, SyncDevice>>(devices.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<String, SyncDevice>>() {
                @Override
                public int compare(Map.Entry<String, SyncDevice> o1, Map.Entry<String, SyncDevice> o2) {
                    long t1 = o1.getValue() != null ? o1.getValue().lastSeen : 0;
                    long t2 = o2.getValue() != null ? o2.getValue().lastSeen : 0;
                    return Long.compare(t2, t1); // descending
                }
            });

            devices.clear();
            for (int i = 0; i < MAX_DEVICES && i < list.size(); i++) {
                devices.put(list.get(i).getKey(), list.get(i).getValue());
            }
        }
    }
}
