package com.github.tvbox.osc.cache;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.MD5;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧集详情快照: 详情页加载成功时把完整 VodInfo(含线路/选集)序列化进 CacheManager。
 * 下载页/离线场景打开播放页时先用快照即时渲染,详情接口随后在线刷新覆盖;
 * 接口失败时保留快照界面不回落空态。
 */
public class VodSnapshotStore {

    private static String key(String sourceKey, String vodId) {
        return MD5.string2MD5("vodsnap_" + sourceKey + vodId);
    }

    public static void save(VodInfo vodInfo) {
        try {
            if (vodInfo == null || vodInfo.sourceKey == null || vodInfo.id == null
                    || vodInfo.seriesMap == null || vodInfo.seriesMap.isEmpty()) {
                return;
            }
            CacheManager.save(key(vodInfo.sourceKey, vodInfo.id), vodInfo);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static VodInfo load(String sourceKey, String vodId) {
        try {
            Object obj = CacheManager.getCache(key(sourceKey, vodId));
            return obj instanceof VodInfo ? (VodInfo) obj : null;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    /**
     * 老版本缓存的剧集没有快照,用下载任务表兜底建一份(剧名/海报/已下载集),
     * 在线详情拉到数据后会覆盖保存完整快照。
     */
    public static VodInfo buildFromEpisodes(String sourceKey, String vodId, List<DownloadEpisode> all) {
        if (all == null || all.isEmpty()) return null;
        LinkedHashMap<String, List<DownloadEpisode>> groups = new LinkedHashMap<>();
        DownloadEpisode first = null;
        for (DownloadEpisode ep : all) {
            if (sourceKey != null && !sourceKey.equals(ep.sourceKey)) continue;
            if (vodId != null && !vodId.equals(ep.vodId)) continue;
            if (ep.status != DownloadEpisode.STATUS_DONE || ep.localFilePath == null) continue;
            List<DownloadEpisode> list = groups.get(ep.flag);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(ep.flag, list);
            }
            list.add(ep);
            if (first == null) first = ep;
        }
        if (groups.isEmpty() || first == null) return null;
        VodInfo vodInfo = new VodInfo();
        vodInfo.sourceKey = sourceKey;
        vodInfo.id = vodId;
        vodInfo.name = first.vodName;
        vodInfo.pic = first.vodPic;
        LinkedHashMap<String, List<VodInfo.VodSeries>> seriesMap = new LinkedHashMap<>();
        ArrayList<VodInfo.VodSeriesFlag> seriesFlags = new ArrayList<>();
        for (Map.Entry<String, List<DownloadEpisode>> entry : groups.entrySet()) {
            List<DownloadEpisode> list = entry.getValue();
            Collections.sort(list, (a, b) -> Integer.compare(a.episodeIndex, b.episodeIndex));
            List<VodInfo.VodSeries> series = new ArrayList<>();
            for (DownloadEpisode ep : list) {
                series.add(new VodInfo.VodSeries(ep.episodeName, ep.rawUrl));
            }
            seriesMap.put(entry.getKey(), series);
            seriesFlags.add(new VodInfo.VodSeriesFlag(entry.getKey()));
        }
        vodInfo.seriesMap = seriesMap;
        vodInfo.seriesFlags = seriesFlags;
        return vodInfo;
    }
}
