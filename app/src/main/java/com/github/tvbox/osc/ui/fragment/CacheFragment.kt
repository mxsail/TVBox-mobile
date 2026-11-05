package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.github.tvbox.osc.R
import com.github.tvbox.osc.cache.DownloadEpisode
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.download.DownloadStorage
import com.github.tvbox.osc.download.DownloadTaskManager
import com.github.tvbox.osc.event.DownloadEvent
import com.github.tvbox.osc.ui.activity.DownloadActivity
import com.github.tvbox.osc.ui.adapter.DownloadAdapter
import com.github.tvbox.osc.ui.compose.ObserveBarHideDeltas
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.VodCardItem
import com.github.tvbox.osc.ui.compose.VodGridScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set

/**
 * "我的缓存" tab(Compose 渲染层): 每部剧一张海报卡, 点卡片进详情(DownloadActivity 详情模式),
 * 长按删剧, 右上清空。下载业务逻辑(实时进度/两段渲染/大小缓存/节流)原样保留, 仅视图换 Compose。
 * 供 DownloadActivity(Java) 复用的静态缓存方法以 @JvmStatic 保留。
 */
class CacheFragment : Fragment() {

    data class CacheCard(
        val groupKey: String,
        val name: String,
        val pic: String,
        val note: String?,
        val sizeText: String?
    )

    /** 网格数据(后台线程构建, 主线程更新) */
    private val cards = mutableStateOf<List<CacheCard>>(emptyList())
    private val storageText = mutableStateOf("已占用: …")
    private var lastReloadTime = 0L
    private var eventBusRegistered = false
    private var userVisible = false

    /** 下载中集数的实时进度(EventBus, 数据库不落盘) */
    private val liveProgress = ConcurrentHashMap<Int, DownloadAdapter.Live>()
    private val loadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val loadRunning = AtomicBoolean(false)
    private val loadPending = AtomicBoolean(false)
    private val dataDirty = AtomicBoolean(true)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        me.jessyan.autosize.AutoSize.autoConvertDensity(activity, 360f, true)
        val composeView = ComposeView(requireContext())
        composeView.setContent { TvBoxTheme { CacheScreen() } }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!eventBusRegistered) {
            eventBusRegistered = true
            EventBus.getDefault().register(this)
        }
        storageText.value = "已占用: …"
        loadData()
    }

    @Composable
    private fun CacheScreen() {
        val gridState = rememberLazyGridState()
        gridState.ObserveBarHideDeltas { dy ->
            (activity as? com.github.tvbox.osc.view.BarAutoHide.ScrollBarHost)?.onMainScrollDelta(dy)
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    storageText.value,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { confirmClearAll() }) {
                    Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_clear),
                        contentDescription = "清空缓存",
                        tint = colorScheme.onSurface
                    )
                }
            }
            if (cards.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂无缓存", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    state = gridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(cards.value, key = { it.groupKey }) { card ->
                        VodCardItem(
                            com.github.tvbox.osc.ui.compose.VodCard(
                                key = card.groupKey,
                                name = card.name,
                                pic = card.pic,
                                badge = card.sizeText ?: "",
                                note = card.note
                            ),
                            onClick = {
                                val intent = Intent(requireActivity(), DownloadActivity::class.java)
                                intent.putExtra(DownloadActivity.EXTRA_GROUP_KEY, card.groupKey)
                                startActivity(intent)
                            },
                            onLongClick = { confirmDeleteSeries(card.groupKey, card.name) }
                        )
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        val now = System.currentTimeMillis()
        val visible = userVisible && isAdded()
        if (event.type != DownloadEvent.TYPE_PROGRESS) {
            dataDirty.set(true)
        }
        if (event.type == DownloadEvent.TYPE_DONE) {
            liveProgress.remove(event.episodeId)
            markSizesDirty()
            if (visible) loadData()
            return
        }
        if (event.type == DownloadEvent.TYPE_MERGING) {
            val live = liveProgress.getOrPut(event.episodeId) { DownloadAdapter.Live() }
            live.merging = true
        }
        if (event.type == DownloadEvent.TYPE_PROGRESS) {
            val live = liveProgress.getOrPut(event.episodeId) { DownloadAdapter.Live() }
            live.merging = false
            live.downloadedBytes = event.downloadedBytes
            live.totalBytes = event.totalBytes
            live.speedBytes = event.speedBytes
            live.segmentsDone = event.segmentsDone
            live.segmentsTotal = event.segmentsTotal
        }
        if (!visible) return
        if (now - lastReloadTime < 400) return
        lastReloadTime = now
        loadData()
    }

    /** 切回本 tab: 有结构性变化或存在下载中集数时才重绑 */
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        userVisible = isVisibleToUser
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && isAdded() && view != null) {
            if (dataDirty.compareAndSet(true, false) || liveProgress.isNotEmpty()) {
                loadData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        me.jessyan.autosize.AutoSize.autoConvertDensity(activity, 360f, true)
        if (userVisible && (dataDirty.compareAndSet(true, false) || liveProgress.isNotEmpty())) {
            loadData()
        }
    }

    private fun loadData() {
        loadPending.set(true)
        if (loadExecutor.isShutdown()) return
        if (!loadRunning.compareAndSet(false, true)) return
        try {
            loadExecutor.execute {
                try {
                    while (loadPending.getAndSet(false)) {
                        val all = RoomDataManger.getAllDownloadEpisodes()
                        applyGrid(all)
                        refreshTotalUsed()
                    }
                } catch (th: Throwable) {
                    th.printStackTrace()
                } finally {
                    loadRunning.set(false)
                }
                if (loadPending.get() && !loadExecutor.isShutdown()) {
                    loadData()
                }
            }
        } catch (th: Throwable) {
            loadRunning.set(false)
            th.printStackTrace()
        }
    }

    /** 结构性变化(完成/删除/清空)后大小缓存全部失效 */
    fun markSizesDirtyPublic() = markSizesDirty()

    @Suppress("MemberVisibilityCanBePrivate")
    private fun applyGrid(all: List<DownloadEpisode>) {
        val groups = LinkedHashMap<String, MutableList<DownloadEpisode>>()
        for (ep in all) {
            groups.getOrPut(ep.groupKey()) { ArrayList() }.add(ep)
        }
        // 只保留 下载/解析 中的实时进度
        val activeIds = HashSet<Int>()
        for (list in groups.values) {
            for (ep in list) {
                if (ep.status == DownloadEpisode.STATUS_DOWNLOADING
                    || ep.status == DownloadEpisode.STATUS_RESOLVING
                ) {
                    activeIds.add(ep.getId())
                }
            }
        }
        liveProgress.keys.retainAll(activeIds.toSet())

        val needsWalk = consumeSizeDirty()
        val items = ArrayList<CacheCard>()
        val pendingGroups = HashSet<String>()
        for (entry in groups.entries) {
            val eps = entry.value
            var sizeText: String? = null
            val cached = cachedGroupSize(entry.key)
            if (cached != null) {
                sizeText = if (cached > 0) DownloadStorage.formatSize(cached) else null
            } else if (!needsWalk) {
                sizeText = computeGroupSize(entry.key, eps)
            } else {
                pendingGroups.add(entry.key)
            }
            var done = 0; var waiting = 0; var resolving = 0; var downloading = 0; var paused = 0; var failed = 0
            for (ep in eps) {
                when (ep.status) {
                    DownloadEpisode.STATUS_WAITING -> waiting++
                    DownloadEpisode.STATUS_RESOLVING -> resolving++
                    DownloadEpisode.STATUS_DOWNLOADING -> downloading++
                    DownloadEpisode.STATUS_PAUSED -> paused++
                    DownloadEpisode.STATUS_FAILED -> failed++
                    else -> done++
                }
            }
            items.add(
                CacheCard(
                    groupKey = entry.key,
                    name = displayNameOf(eps[0]),
                    pic = eps[0].vodPic ?: "",
                    note = buildNote(done, eps.size, waiting, resolving, downloading, paused, failed),
                    sizeText = sizeText
                )
            )
        }
        runOnUi {
            cards.value = items
        }
        if (pendingGroups.isNotEmpty()) {
            for (entry in groups.entries) {
                if (pendingGroups.contains(entry.key)) {
                    computeGroupSize(entry.key, entry.value)
                }
            }
            val updated = items.map { item ->
                val size = cachedGroupSize(item.groupKey)
                item.copy(sizeText = if (size != null && size > 0) DownloadStorage.formatSize(size) else null)
            }
            runOnUi { cards.value = updated }
        }
    }

    private fun computeGroupSize(groupKey: String, eps: List<DownloadEpisode>): String? {
        var size = 0L
        for (ep in eps) size += episodeSize(ep)
        cacheGroupSize(groupKey, size)
        return if (size > 0) DownloadStorage.formatSize(size) else null
    }

    private fun episodeSize(ep: DownloadEpisode): Long {
        val active = ep.status == DownloadEpisode.STATUS_DOWNLOADING
            || ep.status == DownloadEpisode.STATUS_RESOLVING
        if (!active) {
            cachedEpisodeSize(ep.getId())?.let { return it }
        }
        val live = if (active) liveProgress[ep.getId()] else null
        val size = if (live != null && live.downloadedBytes > 0) live.downloadedBytes else diskEpisodeSize(ep)
        cacheEpisodeSize(ep.getId(), size)
        return size
    }

    private fun refreshTotalUsed() {
        val now = System.currentTimeMillis()
        if (cachedTotalBytes >= 0 && now - lastTotalCalc < 2000) {
            applyTotalUsed(cachedTotalBytes)
            return
        }
        lastTotalCalc = now
        cachedTotalBytes = DownloadStorage.totalUsedBytes()
        applyTotalUsed(cachedTotalBytes)
    }

    private fun applyTotalUsed(total: Long) {
        runOnUi { storageText.value = "已占用: " + DownloadStorage.formatSize(total) }
    }

    private fun runOnUi(action: () -> Unit) {
        if (!isAdded()) return
        requireActivity().runOnUiThread(action)
    }

    private fun confirmDeleteSeries(groupKey: String, seriesName: String) {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("删除缓存")
            .setMessage("确定删除《${seriesName ?: ""}》的全部缓存文件吗?")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                loadExecutor.execute {
                    try {
                        val eps = RoomDataManger.getAllDownloadEpisodes()
                        for (ep in eps) {
                            if (groupKey != ep.groupKey()) continue
                            DownloadTaskManager.get().cancel(ep.getId())
                            if (ep.localDir != null) {
                                DownloadStorage.deleteRecursive(File(ep.localDir))
                            }
                            RoomDataManger.deleteDownloadEpisode(ep)
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                    markSizesDirty()
                    loadData()
                }
            }
            .show()
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("清空缓存")
            .setMessage("确定删除全部缓存文件吗?")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                loadExecutor.execute {
                    try {
                        val all = RoomDataManger.getAllDownloadEpisodes()
                        for (ep in all) {
                            DownloadTaskManager.get().cancel(ep.getId())
                            if (ep.localDir != null) {
                                DownloadStorage.deleteRecursive(File(ep.localDir))
                            }
                            RoomDataManger.deleteDownloadEpisode(ep)
                        }
                        DownloadStorage.deleteRecursive(DownloadStorage.baseDir())
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                    markSizesDirty()
                    loadData()
                }
            }
            .show()
    }

    override fun onDestroy() {
        if (eventBusRegistered) {
            eventBusRegistered = false
            EventBus.getDefault().unregister(this)
        }
        loadExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        // ---- 供 DownloadActivity(Java) 共用的进程级大小缓存(逻辑原样保留) ----
        private val groupSizeCache = ConcurrentHashMap<String, Long>()
        private val episodeSizeCache = ConcurrentHashMap<Int, Long>()
        private var sizeDirty = true
        private var cachedTotalBytes: Long = -1
        private var lastTotalCalc = 0L

        @JvmStatic
        fun markSizesDirty() {
            sizeDirty = true
            groupSizeCache.clear()
            episodeSizeCache.clear()
            lastTotalCalc = 0
        }

        @JvmStatic
        fun cachedGroupSize(groupKey: String?): Long? = groupKey?.let { groupSizeCache[it] }

        @JvmStatic
        fun cacheGroupSize(groupKey: String, size: Long) {
            groupSizeCache[groupKey] = size
        }

        @JvmStatic
        fun cachedEpisodeSize(episodeId: Int): Long? = episodeSizeCache[episodeId]

        @JvmStatic
        fun cacheEpisodeSize(episodeId: Int, size: Long) {
            episodeSizeCache[episodeId] = size
        }

        @JvmStatic
        fun consumeSizeDirty(): Boolean {
            val dirty = sizeDirty
            if (dirty) sizeDirty = false
            return dirty
        }

        /** 显示名: 剧名为空时回退线路名 */
        @JvmStatic
        fun displayNameOf(ep: DownloadEpisode): String {
            if (ep.vodName != null && ep.vodName.trim().isNotEmpty()) return ep.vodName
            return ep.flag ?: ""
        }

        /** 单集磁盘占用(详情模式共用) */
        @JvmStatic
        fun diskEpisodeSize(ep: DownloadEpisode): Long {
            if (ep.localFilePath != null) {
                val f = File(ep.localFilePath)
                if (f.exists()) {
                    return if (f.name.endsWith(".m3u8") && ep.localDir != null) {
                        maxOf(f.length(), DownloadStorage.dirSize(File(ep.localDir)))
                    } else {
                        f.length()
                    }
                }
            }
            if (ep.localDir != null) {
                val d = File(ep.localDir)
                if (d.exists()) return DownloadStorage.dirSize(d)
            }
            return ep.totalBytes
        }

        /** 网格角标文案: 已下 x/共 y(等待/解析中/下载中/暂停/失败) */
        @JvmStatic
        fun buildNote(
            done: Int, total: Int, waiting: Int, resolving: Int,
            downloading: Int, paused: Int, failed: Int
        ): String {
            val sb = StringBuilder("已下").append(done).append("/").append(total)
            if (downloading > 0) sb.append(" 下载中").append(downloading)
            if (resolving > 0) sb.append(" 解析中").append(resolving)
            if (waiting > 0) sb.append(" 等待").append(waiting)
            if (paused > 0) sb.append(" 暂停").append(paused)
            if (failed > 0) sb.append(" 失败").append(failed)
            return sb.toString()
        }
    }
}
