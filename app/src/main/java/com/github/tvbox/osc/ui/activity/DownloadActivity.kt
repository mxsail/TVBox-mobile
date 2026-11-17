package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.cache.DownloadEpisode
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.download.DownloadStorage
import com.github.tvbox.osc.download.DownloadTaskManager
import com.github.tvbox.osc.event.DownloadEvent
import com.github.tvbox.osc.ui.adapter.DownloadAdapter
import com.github.tvbox.osc.ui.compose.FloatingActionIsland
import com.github.tvbox.osc.ui.compose.M3ConfirmDialog
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxManageTopBar
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.TvBoxTopBar
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import com.github.tvbox.osc.util.Utils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 下载详情页(Compose): 某剧的集数列表, 离线播放/暂停/继续/重试/删除。
 * 播放复用在线播放页(DetailActivity 详情快照秒开+选集打钩), 已下载集命中本地文件,
 * 未下载集在线播放可直接连播; 裸进入(无 groupKey)时转跳"我的下载" tab。
 * 业务逻辑(实时进度/后台加载)原样保留。
 */
class DownloadActivity : BaseActivity() {

    companion object {
        const val EXTRA_GROUP_KEY = "groupKey"
    }

    /** 集数行渲染模型(状态文案逻辑自 DownloadAdapter.convert 移植) */
    data class EpRow(
        val id: Int,
        val name: String,
        val status: Int,
        val stateText: String,
        val actionText: String,
        val actionVisible: Boolean,
        /** -1 = 不显示进度条; -2 = 不确定动画; 0..100 = 确定进度 */
        val progress: Int
    )

    data class HeaderData(
        val seriesName: String,
        val pic: String,
        val yearText: String?,
        val typeText: String?,
        val areaText: String?,
        val actorText: String?
    )

    private val epRows = mutableStateOf<List<EpRow>>(emptyList())
    private val header = mutableStateOf<HeaderData?>(null)
    private var titleState = mutableStateOf("下载详情")

    private var detailGroupKey: String? = null
    private var detailSeriesName: String? = null
    private var lastReloadTime = 0L

    /** 下载中集数的实时进度(EventBus 事件驱动, 不走数据库) */
    private val liveProgress = ConcurrentHashMap<Int, DownloadAdapter.Live>()
    private val loadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val loadRunning = AtomicBoolean(false)
    private val loadPending = AtomicBoolean(false)

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { DownloadScreen() } }
    }

    override fun init() {
        detailGroupKey = intent.getStringExtra(EXTRA_GROUP_KEY)
        if (detailGroupKey == null) {
            val intent = Intent(this, CacheActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
            return
        }
        loadData()
    }

    private fun handleEpisodeAction(ep: DownloadEpisode) {
        when (ep.status) {
            DownloadEpisode.STATUS_DOWNLOADING,
            DownloadEpisode.STATUS_RESOLVING,
            DownloadEpisode.STATUS_WAITING -> {
                DownloadTaskManager.get().pause(ep.getId())
                ToastUtils.showShort("已请求暂停")
            }
            DownloadEpisode.STATUS_PAUSED,
            DownloadEpisode.STATUS_FAILED -> DownloadTaskManager.get().resume(ep.getId())
            else -> confirmDelete(ep)
        }
    }

    private fun playFirstOfGroup(groupKey: String) {
        val eps = RoomDataManger.getAllDownloadEpisodes()
        var first: DownloadEpisode? = null
        for (ep in eps) {
            if (groupKey == ep.groupKey() && ep.status == DownloadEpisode.STATUS_DONE) {
                if (first == null || ep.episodeIndex < first!!.episodeIndex) first = ep
            }
        }
        if (first == null) {
            ToastUtils.showShort("还没有下载完成的剧集")
            return
        }
        launchOnlinePlay(first!!)
    }

    /**
     * 复用在线播放页(DetailActivity): 详情快照秒开、选集打钩、已下载集自动播本地文件,
     * 未下载集在线播放,播完可直接连播下一集,无需退出。
     */
    private fun launchOnlinePlay(ep: DownloadEpisode) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra("id", ep.vodId)
        intent.putExtra("sourceKey", ep.sourceKey)
        intent.putExtra("autoPlay", true)
        intent.putExtra("playUrl", ep.rawUrl)
        startActivity(intent)
    }

    private fun executeDeleteBatch(ids: Set<Int>, onFinished: () -> Unit = {}) {
        if (ids.isEmpty()) return
        loadExecutor.execute {
            try {
                for (id in ids) {
                    DownloadTaskManager.get().cancel(id)
                    val task = RoomDataManger.getDownloadEpisode(id)
                    if (task != null) {
                        if (task.localDir != null) {
                            DownloadStorage.deleteRecursive(File(task.localDir))
                            DownloadStorage.cleanEmptyParents(File(task.localDir))
                        }
                        RoomDataManger.deleteDownloadEpisode(task)
                    }
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
            CacheActivity.markSizesDirty()
            org.greenrobot.eventbus.EventBus.getDefault()
                .post(com.github.tvbox.osc.event.DownloadEvent(com.github.tvbox.osc.event.DownloadEvent.TYPE_TASKS_CHANGED))
            runOnUiThread {
                onFinished()
                loadData()
            }
        }
    }

    private fun confirmDeleteSeries(groupKey: String?, seriesName: String?) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除下载")
            .setMessage("确定删除《" + (seriesName ?: "") + "》的全部下载文件吗?")
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
                    CacheActivity.markSizesDirty()
                    // 通知缓存 tab 重查数据库(删除不产生下载事件, 不发则底部数量不刷新)
                    org.greenrobot.eventbus.EventBus.getDefault()
                        .post(com.github.tvbox.osc.event.DownloadEvent(com.github.tvbox.osc.event.DownloadEvent.TYPE_TASKS_CHANGED))
                    runOnUiThread {
                        if (detailGroupKey != null) finish() else loadData()
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(ep: DownloadEpisode) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除下载")
            .setMessage("确定删除 " + ep.displayTitle() + " 的下载文件吗?")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                loadExecutor.execute {
                    try {
                        DownloadTaskManager.get().cancel(ep.getId())
                        val task = RoomDataManger.getDownloadEpisode(ep.getId())
                        if (task != null) {
                            if (task.localDir != null) {
                                DownloadStorage.deleteRecursive(File(task.localDir))
                                DownloadStorage.cleanEmptyParents(File(task.localDir))
                            }
                            RoomDataManger.deleteDownloadEpisode(task)
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                    CacheActivity.markSizesDirty()
                    org.greenrobot.eventbus.EventBus.getDefault()
                        .post(com.github.tvbox.osc.event.DownloadEvent(com.github.tvbox.osc.event.DownloadEvent.TYPE_TASKS_CHANGED))
                    loadData()
                }
            }
            .show()
    }

    @Suppress("unused")
    @androidx.annotation.Keep
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        val now = System.currentTimeMillis()
        if (event.type == DownloadEvent.TYPE_DONE) {
            liveProgress.remove(event.episodeId)
            CacheActivity.markSizesDirty()
            loadData()
            return
        }
        if (event.type == DownloadEvent.TYPE_MERGING) {
            liveProgress.getOrPut(event.episodeId) { DownloadAdapter.Live() }.merging = true
            loadData()
            return
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
        if (now - lastReloadTime < 400) return
        lastReloadTime = now
        loadData()
    }

    private fun loadData() {
        loadPending.set(true)
        if (loadExecutor.isShutdown()) return
        if (!loadRunning.compareAndSet(false, true)) return
        try {
            loadExecutor.execute {
                try {
                    while (loadPending.getAndSet(false)) {
                        applyDetail(RoomDataManger.getAllDownloadEpisodes())
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

    /** 单集实际占用(与网格 tab 共享缓存) */
    private fun episodeSize(ep: DownloadEpisode): Long {
        val active = ep.status == DownloadEpisode.STATUS_DOWNLOADING
            || ep.status == DownloadEpisode.STATUS_RESOLVING
        if (!active) {
            CacheActivity.cachedEpisodeSize(ep.getId())?.let { return it }
        }
        val live = if (active) liveProgress[ep.getId()] else null
        val size = if (live != null && live.downloadedBytes > 0) live.downloadedBytes
        else CacheActivity.diskEpisodeSize(ep)
        CacheActivity.cacheEpisodeSize(ep.getId(), size)
        return size
    }

    /** 详情模式: 只显示该剧的分组头 + 集数列表(渲染模型构建) */
    private fun applyDetail(all: List<DownloadEpisode>) {
        val key = detailGroupKey ?: return
        val eps = all.filter { key == it.groupKey() }
        if (eps.isEmpty()) {
            runOnUiThread { finish() }
            return
        }
        val sorted = eps.sortedBy { it.episodeIndex }
        if (detailSeriesName == null) {
            detailSeriesName = CacheActivity.displayNameOf(sorted[0])
        }
        val first = sorted[0]
        val vodInfo = RoomDataManger.getVodInfo(first.sourceKey, first.vodId)
        val yearStr = if (vodInfo != null && vodInfo.year > 0) "${vodInfo.year}" else null
        val typeStr = vodInfo?.type?.takeIf { it.isNotBlank() }
        val areaStr = vodInfo?.area?.takeIf { it.isNotBlank() }
        val actorStr = vodInfo?.actor?.takeIf { it.isNotBlank() }

        runOnUiThread {
            titleState.value = detailSeriesName ?: "下载详情"
            header.value = HeaderData(
                seriesName = CacheActivity.displayNameOf(first),
                pic = first.vodPic ?: "",
                yearText = yearStr,
                typeText = typeStr,
                areaText = areaStr,
                actorText = actorStr
            )
            epRows.value = sorted.map { ep -> toRow(ep) }
        }
    }

    /** 状态文案/操作按钮逻辑(自 DownloadAdapter.convert 移植) */
    private fun toRow(ep: DownloadEpisode): EpRow {
        val live = liveProgress[ep.getId()]
        when (ep.status) {
            DownloadEpisode.STATUS_WAITING -> return EpRow(
                ep.getId(), ep.episodeName, ep.status,
                if (TextUtils.isEmpty(ep.errMsg)) "等待中…" else ep.errMsg!!,
                "删除", true, -1
            )
            DownloadEpisode.STATUS_RESOLVING -> return EpRow(
                ep.getId(), ep.episodeName, ep.status, "正在解析播放地址…", "删除", true, -2
            )
            DownloadEpisode.STATUS_DOWNLOADING -> {
                if (live != null && live.merging) {
                    return EpRow(ep.getId(), ep.episodeName, ep.status, "正在合成视频…", "", false, -2)
                }
                val doneB = if (live != null && live.downloadedBytes > 0) live.downloadedBytes else ep.downloadedBytes
                val speed = live?.speedBytes ?: 0
                val speedText = if (speed > 0) " · " + DownloadStorage.formatSize(speed) + "/s" else ""
                val state: String
                val progress: Int
                if (ep.totalBytes > 0) {
                    progress = ((doneB * 100 / ep.totalBytes).toInt()).coerceAtMost(100)
                    state = DownloadStorage.formatSize(doneB) + "/" + DownloadStorage.formatSize(ep.totalBytes) + speedText
                } else if (live != null && live.segmentsTotal > 0) {
                    progress = ((live.segmentsDone * 100 / live.segmentsTotal)).coerceAtMost(100)
                    state = "分片 " + live.segmentsDone + "/" + live.segmentsTotal +
                        " · 已下载 " + DownloadStorage.formatSize(doneB) + speedText
                } else {
                    progress = -2
                    state = if (doneB > 0) "已下载 " + DownloadStorage.formatSize(doneB) else "正在下载…"
                }
                return EpRow(ep.getId(), ep.episodeName, ep.status, state, "暂停", true, progress)
            }
            DownloadEpisode.STATUS_PAUSED -> {
                val progress = if (ep.totalBytes > 0) (((ep.downloadedBytes * 100 / ep.totalBytes).toInt()).coerceAtMost(100)) else 0
                var pausedText = if (ep.totalBytes > 0) "已暂停 $progress%" else "已暂停"
                if (ep.totalBytes <= 0 && ep.downloadedBytes > 0) {
                    pausedText += " · 已下载 " + DownloadStorage.formatSize(ep.downloadedBytes)
                }
                if (!TextUtils.isEmpty(ep.errMsg)) pausedText += " · " + ep.errMsg
                return EpRow(ep.getId(), ep.episodeName, ep.status, pausedText, "继续", true, progress)
            }
            DownloadEpisode.STATUS_FAILED -> return EpRow(
                ep.getId(), ep.episodeName, ep.status,
                if (TextUtils.isEmpty(ep.errMsg)) "下载失败" else ep.errMsg!!,
                "重试", true, -1
            )
            else -> return EpRow(
                ep.getId(), ep.episodeName, DownloadEpisode.STATUS_DONE,
                "已下载 · " + DownloadStorage.formatSize(ep.totalBytes)
                    + (if (ep.localFilePath != null && ep.localFilePath.endsWith(".m3u8")) " (分片模式)" else ""),
                "删除", true, -1
            )
        }
    }

    override fun onDestroy() {
        loadExecutor.shutdownNow()
        super.onDestroy()
    }

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun DownloadScreen() {
        val isDark = isAppDarkTheme()
        val isSelectionMode = remember { mutableStateOf(false) }
        val selectedEpisodeIds = remember { mutableStateOf<Set<Int>>(emptySet()) }
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        val allIds = remember(epRows.value) { epRows.value.map { it.id }.toSet() }
        val isAllSelected = allIds.isNotEmpty() && selectedEpisodeIds.value.size == allIds.size

        BackHandler(isSelectionMode.value) {
            isSelectionMode.value = false
            selectedEpisodeIds.value = emptySet()
        }

        val listState = rememberLazyListState()
        val backdrop = rememberLayerBackdrop()
        val isScrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 44.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            val listBottomPadding by animateDpAsState(
                targetValue = if (isSelectionMode.value) 92.dp else 16.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "download_bottom_padding"
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topBarPadding + 8.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                header.value?.let { hd ->
                    item {
                        // M3 Expressive 剧集信息 Hero 卡 (更换成年份/类型/国家 以及演员列表)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White
                            ),
                            shape = RoundedCornerShape(22.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = if (!isDark) BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                PosterImage(
                                    hd.pic,
                                    Modifier
                                        .width(88.dp)
                                        .height(124.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colorScheme.surfaceContainerHigh)
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp)
                                ) {
                                    Text(
                                        hd.seriesName,
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 年份 / 类型 / 国家 标签胶囊
                                    val metaTags = listOfNotNull(
                                        hd.yearText?.takeIf { it.isNotBlank() },
                                        hd.typeText?.takeIf { it.isNotBlank() },
                                        hd.areaText?.takeIf { it.isNotBlank() }
                                    )
                                    if (metaTags.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            metaTags.forEach { tag ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isDark) colorScheme.surfaceContainerHigh else colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        tag,
                                                        color = if (isDark) colorScheme.onSurfaceVariant else colorScheme.onSecondaryContainer,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 演员列表
                                    if (!hd.actorText.isNullOrBlank()) {
                                        Text(
                                            text = "主演: ${hd.actorText}",
                                            color = colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                items(epRows.value.size) { index ->
                    val row = epRows.value[index]
                    val isSelected = selectedEpisodeIds.value.contains(row.id)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                colorScheme.primaryContainer.copy(alpha = if (isDark) 0.3f else 0.45f)
                            } else if (isDark) {
                                colorScheme.surfaceContainerLow
                            } else {
                                Color.White
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, colorScheme.primary)
                        } else if (!isDark) {
                            BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode.value) {
                                        if (isSelected) selectedEpisodeIds.value = selectedEpisodeIds.value - row.id
                                        else selectedEpisodeIds.value = selectedEpisodeIds.value + row.id
                                    } else if (row.status == DownloadEpisode.STATUS_DONE) {
                                        // 已下载集: 复用在线播放页,本地命中直接播
                                        val ep = RoomDataManger.getDownloadEpisode(row.id) ?: return@combinedClickable
                                        if (ep.localFilePath != null && File(ep.localFilePath).exists()) {
                                            launchOnlinePlay(ep)
                                        } else {
                                            ToastUtils.showShort("下载文件不存在")
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode.value) {
                                        isSelectionMode.value = true
                                        selectedEpisodeIds.value = setOf(row.id)
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode.value) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedEpisodeIds.value = selectedEpisodeIds.value + row.id
                                        else selectedEpisodeIds.value = selectedEpisodeIds.value - row.id
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colorScheme.primary,
                                        checkmarkColor = colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }

                            // 状态图标底座 (Squircle)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(statusContainerTint(row.status)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(statusIcon(row.status)),
                                    contentDescription = null,
                                    tint = statusTint(row.status),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    row.name,
                                    color = colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    row.stateText,
                                    color = if (row.status == DownloadEpisode.STATUS_FAILED) colorScheme.error
                                    else colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                when (row.progress) {
                                    -2 -> LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                    in 0..100 -> LinearProgressIndicator(
                                        progress = { row.progress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            if (!isSelectionMode.value && row.actionVisible) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (row.actionText == "删除") colorScheme.errorContainer.copy(alpha = 0.15f)
                                    else colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            val ep = RoomDataManger.getDownloadEpisode(row.id) ?: return@clickable
                                            handleEpisodeAction(ep)
                                        }
                                ) {
                                    Text(
                                        row.actionText,
                                        color = if (row.actionText == "删除") colorScheme.error else colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            FloatingActionIsland(
                visible = isSelectionMode.value && epRows.value.isNotEmpty(),
                isAllSelected = isAllSelected,
                selectedCount = selectedEpisodeIds.value.size,
                onToggleSelectAll = {
                    selectedEpisodeIds.value = if (isAllSelected) emptySet() else allIds
                },
                onDeleteClick = {
                    if (selectedEpisodeIds.value.isNotEmpty()) {
                        showDeleteConfirmDialog = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 顶层悬浮动态毛玻璃顶栏 (穿顶滑动 + 覆盖通知栏)
            TvBoxManageTopBar(
                title = titleState.value,
                isEditMode = isSelectionMode.value,
                selectedCount = selectedEpisodeIds.value.size,
                canManage = epRows.value.isNotEmpty(),
                editTitle = "选择剧集",
                isScrolled = isScrolled,
                backdrop = backdrop,
                onBack = {
                    if (isSelectionMode.value) {
                        isSelectionMode.value = false
                        selectedEpisodeIds.value = emptySet()
                    } else {
                        finish()
                    }
                },
                onToggleEditMode = {
                    if (isSelectionMode.value) {
                        isSelectionMode.value = false
                        selectedEpisodeIds.value = emptySet()
                    } else {
                        isSelectionMode.value = true
                        selectedEpisodeIds.value = emptySet()
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

    M3ConfirmDialog(
        visible = showDeleteConfirmDialog,
        title = "删除确认",
        message = "确定删除选中的 ${selectedEpisodeIds.value.size} 个剧集下载文件吗？",
        confirmText = "删除",
        cancelText = "取消",
        isDestructive = true,
        iconRes = R.drawable.ic_delete_outline_24,
        onDismissRequest = { showDeleteConfirmDialog = false },
        onConfirm = {
            showDeleteConfirmDialog = false
            val toDelete = selectedEpisodeIds.value
            isSelectionMode.value = false
            selectedEpisodeIds.value = emptySet()
            executeDeleteBatch(toDelete) {}
        }
    )
    }

    /** 集数行状态图标 */
    private fun statusIcon(status: Int): Int = when (status) {
        DownloadEpisode.STATUS_DONE -> R.drawable.ic_check_circle
        DownloadEpisode.STATUS_FAILED -> R.drawable.icon_error
        DownloadEpisode.STATUS_PAUSED -> R.drawable.ic_m3_pause
        else -> R.drawable.ic_download_nav
    }

    /** 集数行状态图标颜色 */
    @androidx.compose.runtime.Composable
    private fun statusTint(status: Int): androidx.compose.ui.graphics.Color = when (status) {
        DownloadEpisode.STATUS_DONE -> colorScheme.primary
        DownloadEpisode.STATUS_FAILED -> colorScheme.error
        DownloadEpisode.STATUS_PAUSED -> colorScheme.onSurfaceVariant
        else -> colorScheme.primary
    }

    /** 集数行图标底座颜色 */
    @androidx.compose.runtime.Composable
    private fun statusContainerTint(status: Int): androidx.compose.ui.graphics.Color {
        val isDark = isAppDarkTheme()
        return when (status) {
            DownloadEpisode.STATUS_DONE -> colorScheme.primaryContainer.copy(alpha = if (isDark) 0.5f else 0.8f)
            DownloadEpisode.STATUS_FAILED -> colorScheme.errorContainer.copy(alpha = if (isDark) 0.4f else 0.7f)
            DownloadEpisode.STATUS_PAUSED -> if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF0F0F2)
            else -> colorScheme.primaryContainer.copy(alpha = if (isDark) 0.5f else 0.8f)
        }
    }
}
