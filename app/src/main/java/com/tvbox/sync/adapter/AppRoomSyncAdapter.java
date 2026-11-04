package com.tvbox.sync.adapter;

import android.text.TextUtils;

import com.github.tvbox.osc.cache.Cache;
import com.github.tvbox.osc.cache.CacheDao;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.cache.VodCollectDao;
import com.github.tvbox.osc.cache.VodRecord;
import com.github.tvbox.osc.cache.VodRecordDao;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.google.gson.Gson;
import com.orhanobut.hawk.Hawk;
import com.tvbox.sync.SyncOpLog;
import com.tvbox.sync.SyncProtocol;
import com.tvbox.sync.SyncTrigger;
import com.tvbox.sync.model.SyncClear;
import com.tvbox.sync.model.SyncCollect;
import com.tvbox.sync.model.SyncConfigGroup;
import com.tvbox.sync.model.SyncHistory;
import com.tvbox.sync.model.SyncPlayer;
import com.tvbox.sync.model.SyncProgress;
import com.tvbox.sync.model.SyncSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppRoomSyncAdapter {
    private static final Gson GSON = new Gson();

    public static void exportDirty(SyncOpLog opLog, String spaceId) {
        if (opLog == null) return;
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");

        // 1. Export Content Config
        SyncConfigGroup.ContentGroup contentGroup = new SyncConfigGroup.ContentGroup();
        contentGroup.apiUrl = apiUrl;
        contentGroup.homeApi = Hawk.get(HawkConfig.HOME_API, "");
        contentGroup.liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        contentGroup.epgUrl = Hawk.get(HawkConfig.EPG_URL, "");
        contentGroup.defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
        opLog.record(new SyncOpLog.SyncOp(0, spaceId, "config.content", null, "upsert", GSON.toJson(contentGroup), System.currentTimeMillis()));

        // 2. Export History records
        Integer index = Hawk.get(HawkConfig.HISTORY_NUM, 0);
        int hisNum = HistoryHelper.getHisNum(index);
        List<VodRecord> recordList = AppDataManager.get().getVodRecordDao().getAll(hisNum);
        if (recordList != null) {
            for (VodRecord r : recordList) {
                SyncHistory h = new SyncHistory();
                h.configHash = SyncProtocol.configHash(apiUrl);
                h.sourceKey = r.sourceKey;
                h.vodId = r.vodId;
                h.id = SyncProtocol.makeId(apiUrl, r.sourceKey, r.vodId);
                h.updatedAt = r.updateTime;
                h.payload = r.dataJson;
                opLog.record(new SyncOpLog.SyncOp(0, spaceId, "history", h.id, "upsert", GSON.toJson(h), r.updateTime));
            }
        }

        // 3. Export Collects
        List<VodCollect> collectList = AppDataManager.get().getVodCollectDao().getAll();
        if (collectList != null) {
            for (VodCollect c : collectList) {
                SyncCollect sc = new SyncCollect();
                sc.configHash = SyncProtocol.configHash(apiUrl);
                sc.sourceKey = c.sourceKey;
                sc.vodId = c.vodId;
                sc.id = SyncProtocol.makeId(apiUrl, c.sourceKey, c.vodId);
                sc.title = c.name;
                sc.pic = c.pic;
                sc.updatedAt = c.updateTime;
                opLog.record(new SyncOpLog.SyncOp(0, spaceId, "collect", sc.id, "upsert", GSON.toJson(sc), c.updateTime));
            }
        }
    }

    public static void recordHistoryUpsert(String sourceKey, VodRecord record) {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_HISTORY, true)) return;
        try {
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncHistory h = new SyncHistory();
            h.configHash = SyncProtocol.configHash(apiUrl);
            h.sourceKey = sourceKey;
            h.vodId = record.vodId;
            h.id = SyncProtocol.makeId(apiUrl, sourceKey, record.vodId);
            h.updatedAt = record.updateTime;
            h.payload = record.dataJson;
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "history", h.id, "upsert", GSON.toJson(h), record.updateTime
            ));
            SyncTrigger.get().triggerDebouncedSync();
        } catch (Exception ignored) {}
    }

    public static void recordHistoryDelete(String sourceKey, String vodId) {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_HISTORY, true)) return;
        try {
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            String id = SyncProtocol.makeId(apiUrl, sourceKey, vodId);
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "history", id, "delete", null, System.currentTimeMillis()
            ));
            SyncTrigger.get().triggerDebouncedSync();
        } catch (Exception ignored) {}
    }

    public static void recordHistoryClear() {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_HISTORY, true)) return;
        try {
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "history", null, "clear", null, System.currentTimeMillis()
            ));
            SyncTrigger.get().triggerFlushSync();
        } catch (Exception ignored) {}
    }

    public static void recordCollectUpsert(String sourceKey, VodCollect collect) {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_COLLECT, true)) return;
        try {
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncCollect sc = new SyncCollect();
            sc.configHash = SyncProtocol.configHash(apiUrl);
            sc.sourceKey = sourceKey;
            sc.vodId = collect.vodId;
            sc.id = SyncProtocol.makeId(apiUrl, sourceKey, collect.vodId);
            sc.title = collect.name;
            sc.pic = collect.pic;
            sc.updatedAt = collect.updateTime;
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "collect", sc.id, "upsert", GSON.toJson(sc), collect.updateTime
            ));
            SyncTrigger.get().triggerDebouncedSync();
        } catch (Exception ignored) {}
    }

    public static void recordCollectDelete(String sourceKey, String vodId) {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_COLLECT, true)) return;
        try {
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            String id = SyncProtocol.makeId(apiUrl, sourceKey, vodId);
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "collect", id, "delete", null, System.currentTimeMillis()
            ));
            SyncTrigger.get().triggerDebouncedSync();
        } catch (Exception ignored) {}
    }

    public static void recordCollectClear() {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_COLLECT, true)) return;
        try {
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "collect", null, "clear", null, System.currentTimeMillis()
            ));
            SyncTrigger.get().triggerFlushSync();
        } catch (Exception ignored) {}
    }

    public static void recordProgressUpsert(String progressKey, long position, long duration) {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_PROGRESS, true)) return;
        try {
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncProgress p = new SyncProgress();
            p.key = progressKey;
            p.position = position;
            p.duration = duration;
            p.updatedAt = System.currentTimeMillis();
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "progress", progressKey, "upsert", GSON.toJson(p), p.updatedAt
            ));
            SyncTrigger.get().triggerDebouncedSync();
        } catch (Exception ignored) {}
    }

    public static void recordProgressDelete(String progressKey) {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_PROGRESS, true)) return;
        try {
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "progress", progressKey, "delete", null, System.currentTimeMillis()
            ));
        } catch (Exception ignored) {}
    }

    public static void recordContentConfigUpsert() {
        if (!Hawk.get(HawkConfig.SYNC_SCOPE_CONFIG, true)) return;
        try {
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            String spaceId = Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID);
            SyncConfigGroup.ContentGroup contentGroup = new SyncConfigGroup.ContentGroup();
            contentGroup.apiUrl = apiUrl;
            contentGroup.homeApi = Hawk.get(HawkConfig.HOME_API, "");
            contentGroup.liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
            contentGroup.epgUrl = Hawk.get(HawkConfig.EPG_URL, "");
            contentGroup.defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            SyncTrigger.get().getOpLog().record(new SyncOpLog.SyncOp(
                    0, spaceId, "config.content", null, "upsert", GSON.toJson(contentGroup), System.currentTimeMillis()
            ));
            SyncTrigger.get().triggerDebouncedSync();
        } catch (Exception ignored) {}
    }

    public static boolean apply(final SyncSnapshot snapshot, final OnConfigChangedListener configListener) {
        if (snapshot == null) return false;

        AppDataManager.get().runInTransaction(new Runnable() {
            @Override
            public void run() {
                VodRecordDao recordDao = AppDataManager.get().getVodRecordDao();
                VodCollectDao collectDao = AppDataManager.get().getVodCollectDao();
                CacheDao cacheDao = AppDataManager.get().getCacheDao();

                // 1. Apply History
                if (Hawk.get(HawkConfig.SYNC_SCOPE_HISTORY, true)) {
                    // Check clear tombstones first
                    if (snapshot.clears != null) {
                        SyncClear historyClear = snapshot.clears.get("history");
                        if (historyClear != null) {
                            List<VodRecord> localHistory = recordDao.getAll(1000);
                            if (localHistory != null) {
                                for (VodRecord r : localHistory) {
                                    if (r.updateTime <= historyClear.updatedAt) {
                                        recordDao.delete(r);
                                    }
                                }
                            }
                        }
                    }

                    if (snapshot.history != null) {
                        List<SyncHistory> sortedList = new ArrayList<SyncHistory>(snapshot.history);
                        Collections.sort(sortedList, new Comparator<SyncHistory>() {
                            @Override
                            public int compare(SyncHistory o1, SyncHistory o2) {
                                return Long.compare(o2.updatedAt, o1.updatedAt);
                            }
                        });

                        Integer index = Hawk.get(HawkConfig.HISTORY_NUM, 0);
                        int hisNum = HistoryHelper.getHisNum(index);
                        int activeCount = 0;

                        for (SyncHistory h : sortedList) {
                            if (h.deleted) {
                                VodRecord existing = recordDao.getVodRecord(h.sourceKey, h.vodId);
                                if (existing != null) {
                                    recordDao.delete(existing);
                                }
                            } else {
                                if (activeCount < hisNum) {
                                    VodRecord existing = recordDao.getVodRecord(h.sourceKey, h.vodId);
                                    if (existing == null) {
                                        existing = new VodRecord();
                                    }
                                    existing.sourceKey = h.sourceKey;
                                    existing.vodId = h.vodId;
                                    existing.updateTime = h.updatedAt;
                                    if (h.payload != null && !h.payload.isEmpty()) {
                                        existing.dataJson = h.payload;
                                    }
                                    recordDao.insert(existing);
                                    activeCount++;
                                }
                            }
                        }
                        recordDao.reserver(hisNum);
                    }
                }

                // 2. Apply Collects
                if (Hawk.get(HawkConfig.SYNC_SCOPE_COLLECT, true)) {
                    // Check clear tombstones first
                    if (snapshot.clears != null) {
                        SyncClear collectClear = snapshot.clears.get("collect");
                        if (collectClear == null) collectClear = snapshot.clears.get("collects");
                        if (collectClear != null) {
                            List<VodCollect> localCollects = collectDao.getAll();
                            if (localCollects != null) {
                                for (VodCollect c : localCollects) {
                                    if (c.updateTime <= collectClear.updatedAt) {
                                        collectDao.delete(c);
                                    }
                                }
                            }
                        }
                    }

                    if (snapshot.collects != null) {
                        for (SyncCollect c : snapshot.collects) {
                            if (c.deleted) {
                                VodCollect existing = collectDao.getVodCollect(c.sourceKey, c.vodId);
                                if (existing != null) {
                                    collectDao.delete(existing);
                                }
                            } else {
                                VodCollect existing = collectDao.getVodCollect(c.sourceKey, c.vodId);
                                if (existing == null) {
                                    existing = new VodCollect();
                                }
                                existing.sourceKey = c.sourceKey;
                                existing.vodId = c.vodId;
                                existing.name = c.title;
                                existing.pic = c.pic;
                                existing.updateTime = c.updatedAt;
                                collectDao.insert(existing);
                            }
                        }
                    }
                }

                // 3. Apply Progress
                if (Hawk.get(HawkConfig.SYNC_SCOPE_PROGRESS, true) && snapshot.progress != null) {
                    for (SyncProgress p : snapshot.progress) {
                        if (p.key == null || p.key.isEmpty()) continue;
                        if (p.deleted) {
                            Cache cache = new Cache();
                            cache.key = p.key;
                            cacheDao.delete(cache);
                        } else {
                            CacheManager.save(p.key, p.position);
                        }
                    }
                }
            }
        });

        // 4. Apply Config Groups outside DB transaction
        boolean configChanged = false;
        if (snapshot.config != null && snapshot.config.groups != null) {
            // Content group
            if (Hawk.get(HawkConfig.SYNC_SCOPE_CONFIG, true) && snapshot.config.groups.containsKey("content")) {
                Object obj = snapshot.config.groups.get("content");
                SyncConfigGroup.ContentGroup cg = (obj instanceof SyncConfigGroup.ContentGroup)
                        ? (SyncConfigGroup.ContentGroup) obj
                        : GSON.fromJson(GSON.toJson(obj), SyncConfigGroup.ContentGroup.class);

                if (cg != null && !TextUtils.isEmpty(cg.apiUrl) && (cg.apiUrl.startsWith("http://") || cg.apiUrl.startsWith("https://"))) {
                    String currentApi = Hawk.get(HawkConfig.API_URL, "");
                    if (!cg.apiUrl.equals(currentApi)) {
                        Hawk.put(HawkConfig.API_URL, cg.apiUrl);
                        configChanged = true;
                    }
                    if (!TextUtils.isEmpty(cg.homeApi)) {
                        Hawk.put(HawkConfig.HOME_API, cg.homeApi);
                    }
                    if (!TextUtils.isEmpty(cg.liveUrl)) {
                        Hawk.put(HawkConfig.LIVE_URL, cg.liveUrl);
                    }
                }
            }

            // Player group
            if (Hawk.get(HawkConfig.SYNC_SCOPE_PLAYER_SETTINGS, true) && snapshot.config.groups.containsKey("player")) {
                Object obj = snapshot.config.groups.get("player");
                SyncPlayer sp = (obj instanceof SyncPlayer)
                        ? (SyncPlayer) obj
                        : GSON.fromJson(GSON.toJson(obj), SyncPlayer.class);

                if (sp != null) {
                    if (sp.render >= 0 && sp.render <= 1) Hawk.put(HawkConfig.PLAY_RENDER, sp.render);
                    if (sp.scale >= 0 && sp.scale <= 5) Hawk.put(HawkConfig.PLAY_SCALE, sp.scale);
                    if (sp.subtitleSize >= 12 && sp.subtitleSize <= 60) Hawk.put(HawkConfig.SUBTITLE_TEXT_SIZE, sp.subtitleSize);
                    if (sp.timeStep >= 1 && sp.timeStep <= 300) Hawk.put(HawkConfig.PLAY_TIME_STEP, sp.timeStep);
                    if (sp.background >= 0 && sp.background <= 2) Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE, sp.background);
                }
            }
        }

        if (configChanged && configListener != null) {
            configListener.onConfigChanged();
        }

        return true;
    }

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }
}
