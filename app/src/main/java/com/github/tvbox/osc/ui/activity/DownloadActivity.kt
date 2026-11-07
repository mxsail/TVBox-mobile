package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VideoInfo
import com.github.tvbox.osc.cache.DownloadEpisode
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.download.DownloadStorage
import com.github.tvbox.osc.download.DownloadTaskManager
import com.github.tvbox.osc.event.DownloadEvent
import com.github.tvbox.osc.ui.adapter.DownloadAdapter
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxTheme
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
 * 缓存详情页(Compose): 某剧的集数列表, 离线播放/暂停/继续/重试/删除。
 * 裸进入(无 groupKey)时转跳"我的下载" tab。业务逻辑(实时进度/后台加载)原样保留。
 */
class DownloadActivity : BaseActivity() {

    companion object {
        const val EXTRA_GROUP_KEY = "groupKey"
    }

    /** 集数行渲染模型(状态文案逻辑自 DownloadAdapter.convert 移植) */
    data class EpRow(
        val id: Int,
        val name: String,
        val stateText: String,
        val actionText: String,
        val actionVisible: Boolean,
        /** -1 = 不显示进度条; -2 = 不确定动画; 0..100 = 确定进度 */
        val progress: Int
    )

    data class HeaderData(
        val seriesName: String,
        val pic: String,
        val doneCount: Int,
        val totalCount: Int,
        val sizeText: String,
        val failedCount: Int
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
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(MainActivity.EXTRA_TAB, 2)
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
        for (ep in eps) {
            if (groupKey == ep.groupKey() && ep.status == DownloadEpisode.STATUS_DONE) {
                playEpisode(groupKey, ep.getId())
                return
            }
        }
        ToastUtils.showShort("还没有下载完成的剧集")
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
                    com.github.tvbox.osc.ui.fragment.CacheFragment.markSizesDirty()
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
                    com.github.tvbox.osc.ui.fragment.CacheFragment.markSizesDirty()
                    loadData()
                }
            }
            .show()
    }

    /** 从同一剧集组中已完成的集数构建离线播放列表(复用本地播放器) */
    private fun playEpisode(groupKey: String, episodeId: Int) {
        val eps = RoomDataManger.getAllDownloadEpisodes()
        val doneList = ArrayList<DownloadEpisode>()
        var clicked: DownloadEpisode? = null
        for (ep in eps) {
            if (groupKey != ep.groupKey()) continue
            if (ep.getId() == episodeId) clicked = ep
            if (ep.status == DownloadEpisode.STATUS_DONE && ep.localFilePath != null
                && File(ep.localFilePath).exists()
            ) {
                doneList.add(ep)
            }
        }
        if (clicked == null || doneList.isEmpty()) {
            ToastUtils.showShort("下载文件不存在")
            loadData()
            return
        }
        doneList.sortWith { a, b -> a.episodeIndex.compareTo(b.episodeIndex) }
        val videoList = ArrayList<VideoInfo>()
        var position = 0
        for (i in doneList.indices) {
            val ep = doneList[i]
            if (ep.getId() == clicked.getId()) position = i
            val vi = VideoInfo()
            vi.path = ep.localFilePath
            vi.displayName = ep.displayTitle()
            vi.size = ep.totalBytes
            videoList.add(vi)
        }
        val intent = Intent(this, LocalPlayActivity::class.java)
        intent.putExtra("videoList", GsonUtils.toJson(videoList))
        intent.putExtra("position", position)
        startActivity(intent)
    }

    @Suppress("unused")
    @androidx.annotation.Keep
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        val now = System.currentTimeMillis()
        if (event.type == DownloadEvent.TYPE_DONE) {
            liveProgress.remove(event.episodeId)
            com.github.tvbox.osc.ui.fragment.CacheFragment.markSizesDirty()
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
            com.github.tvbox.osc.ui.fragment.CacheFragment.cachedEpisodeSize(ep.getId())?.let { return it }
        }
        val live = if (active) liveProgress[ep.getId()] else null
        val size = if (live != null && live.downloadedBytes > 0) live.downloadedBytes
        else com.github.tvbox.osc.ui.fragment.CacheFragment.diskEpisodeSize(ep)
        com.github.tvbox.osc.ui.fragment.CacheFragment.cacheEpisodeSize(ep.getId(), size)
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
            detailSeriesName = com.github.tvbox.osc.ui.fragment.CacheFragment.displayNameOf(sorted[0])
        }
        val first = sorted[0]
        var done = 0
        var size = 0L
        var failed = 0
        for (ep in sorted) {
            if (ep.status == DownloadEpisode.STATUS_DONE) done++
            if (ep.status == DownloadEpisode.STATUS_FAILED) failed++
            size += episodeSize(ep)
        }
        runOnUiThread {
            titleState.value = detailSeriesName ?: "下载详情"
            header.value = HeaderData(
                seriesName = (com.github.tvbox.osc.ui.fragment.CacheFragment.displayNameOf(first)
                    + " · " + first.flag),
                pic = first.vodPic ?: "",
                doneCount = done,
                totalCount = sorted.size,
                sizeText = DownloadStorage.formatSize(size),
                failedCount = failed
            )
            epRows.value = sorted.map { ep -> toRow(ep) }
        }
    }

    /** 状态文案/操作按钮逻辑(自 DownloadAdapter.convert 移植) */
    private fun toRow(ep: DownloadEpisode): EpRow {
        val live = liveProgress[ep.getId()]
        when (ep.status) {
            DownloadEpisode.STATUS_WAITING -> return EpRow(
                ep.getId(), ep.episodeName,
                if (TextUtils.isEmpty(ep.errMsg)) "等待中…" else ep.errMsg!!,
                "删除", true, -1
            )
            DownloadEpisode.STATUS_RESOLVING -> return EpRow(
                ep.getId(), ep.episodeName, "正在解析播放地址…", "删除", true, -2
            )
            DownloadEpisode.STATUS_DOWNLOADING -> {
                if (live != null && live.merging) {
                    return EpRow(ep.getId(), ep.episodeName, "正在合成视频…", "", false, -2)
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
                return EpRow(ep.getId(), ep.episodeName, state, "暂停", true, progress)
            }
            DownloadEpisode.STATUS_PAUSED -> {
                val progress = if (ep.totalBytes > 0) (((ep.downloadedBytes * 100 / ep.totalBytes).toInt()).coerceAtMost(100)) else 0
                var pausedText = if (ep.totalBytes > 0) "已暂停 $progress%" else "已暂停"
                if (ep.totalBytes <= 0 && ep.downloadedBytes > 0) {
                    pausedText += " · 已下载 " + DownloadStorage.formatSize(ep.downloadedBytes)
                }
                if (!TextUtils.isEmpty(ep.errMsg)) pausedText += " · " + ep.errMsg
                return EpRow(ep.getId(), ep.episodeName, pausedText, "继续", true, progress)
            }
            DownloadEpisode.STATUS_FAILED -> return EpRow(
                ep.getId(), ep.episodeName,
                if (TextUtils.isEmpty(ep.errMsg)) "下载失败" else ep.errMsg!!,
                "重试", true, -1
            )
            else -> return EpRow(
                ep.getId(), ep.episodeName,
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

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun DownloadScreen() {
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(titleState.value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { confirmDeleteSeries(detailGroupKey, detailSeriesName) }) {
                            Icon(
                                painterResource(R.drawable.ic_clear),
                                contentDescription = "删除全剧下载",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                header.value?.let { hd ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PosterImage(
                                hd.pic,
                                Modifier
                                    .width(56.dp)
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorScheme.surfaceContainerHigh)
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    hd.seriesName,
                                    color = colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "已下载 ${hd.doneCount}/${hd.totalCount} 集 · " + hd.sizeText,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { playFirstOfGroup(detailGroupKey ?: return@TextButton) }) {
                                Text("播放全部", color = colorScheme.primary)
                            }
                            if (hd.failedCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    val n = DownloadTaskManager.get().retryAllFailed()
                                    if (n > 0) ToastUtils.showShort("已重新排队 $n 个失败集")
                                }) {
                                    Text("重试失败(${hd.failedCount})", color = colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                items(epRows.value.size) { index ->
                    val row = epRows.value[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.name,
                                color = colorScheme.onSurface,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                row.stateText,
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            when (row.progress) {
                                -2 -> LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                )
                                in 0..100 -> LinearProgressIndicator(
                                    progress = { row.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                )
                            }
                        }
                        if (row.actionVisible) {
                            TextButton(onClick = {
                                val ep = RoomDataManger.getDownloadEpisode(row.id) ?: return@TextButton
                                handleEpisodeAction(ep)
                            }) {
                                Text(row.actionText, color = colorScheme.primary)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
