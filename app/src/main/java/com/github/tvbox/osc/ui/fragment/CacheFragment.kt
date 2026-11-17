package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.adapter.DownloadAdapter
import com.github.tvbox.osc.ui.compose.DownloadSettingsSheet
import com.github.tvbox.osc.ui.compose.ObserveBarHideDeltas
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
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
 * "我的下载" tab(Compose 渲染层): 每部剧一张海报卡, 点卡片进详情(DownloadActivity 详情模式),
 * 长按删剧, 右上清空。下载业务逻辑(实时进度/两段渲染/大小缓存/节流)原样保留, 仅视图换 Compose。
 * 供 DownloadActivity(Java) 复用的静态缓存方法以 @JvmStatic 保留。
 */
class CacheFragment : Fragment() {

    data class CacheCard(
        val groupKey: String,
        val name: String,
        val pic: String,
        val note: String?,
        val sizeText: String?,
        val isDownloading: Boolean = false,
        val doneCount: Int = 0,
        val totalCount: Int = 0,
        val speedText: String? = null,
        val progress: Float? = null
    )

    /** 网格数据(后台线程构建, 主线程更新) */
    private val cards = mutableStateOf<List<CacheCard>>(emptyList())
    private val storageText = mutableStateOf("0MB")
    private val usedBytesState = mutableStateOf(0L)
    private val freeBytesState = mutableStateOf(0L)
    private val totalBytesState = mutableStateOf(0L)
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
        activity?.let { me.jessyan.autosize.AutoSize.autoConvertDensity(it, 360f, true) }
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

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun CacheScreen(
        bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
        onScrollDelta: ((Int) -> Unit)? = null
    ) {
        val listState = rememberLazyListState()
        listState.ObserveBarHideDeltas { dy ->
            onScrollDelta?.invoke(dy) ?: (activity as? com.github.tvbox.osc.view.BarAutoHide.ScrollBarHost)?.onMainScrollDelta(dy)
        }
        val currentContext = androidx.compose.ui.platform.LocalContext.current
        val currentActivity = (activity ?: currentContext as? android.app.Activity)
        val isDark = isAppDarkTheme()

        Column(
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .statusBarsPadding()
        ) {
            // 顶部 M3 Expressive 存储空间 Hero 卡片
            val showSettingsSheet = remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                StorageHeroCard(
                    cardsCount = cards.value.size,
                    usedText = storageText.value,
                    freeBytes = freeBytesState.value,
                    usedBytes = usedBytesState.value,
                    totalBytes = totalBytesState.value,
                    onOpenSettings = { showSettingsSheet.value = true },
                    onClearAll = { currentActivity?.let { confirmClearAll(it) } }
                )
            }

            if (cards.value.isEmpty()) {
                // Expressive 空态
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_download_nav),
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        "暂无离线内容",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "在影视详情页面点击「缓存」即可离线观看",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(
                        onClick = { (activity as? MainActivity)?.goToTab(0) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("去发现好片", fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(cards.value, key = { it.groupKey }) { card ->
                        val supporting = listOfNotNull(
                            card.note,
                            card.sizeText,
                            if (card.isDownloading) card.speedText else null
                        ).joinToString(" · ")
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            val act = currentActivity ?: return@combinedClickable
                                            val intent = Intent(act, DownloadActivity::class.java)
                                            intent.putExtra(DownloadActivity.EXTRA_GROUP_KEY, card.groupKey)
                                            act.startActivity(intent)
                                        },
                                        onLongClick = {
                                            currentActivity?.let { confirmDeleteSeries(it, card.groupKey, card.name) }
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左侧海报(2:3 优雅比例与平滑圆角)
                                com.github.tvbox.osc.ui.compose.PosterImage(
                                    card.pic,
                                    Modifier
                                        .width(66.dp)
                                        .height(92.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colorScheme.surfaceContainerHigh)
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp)
                                ) {
                                    Text(
                                        card.name,
                                        color = colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Expressive 状态胶囊徽标
                                    Row(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (card.isDownloading) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = colorScheme.primaryContainer
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        androidx.compose.ui.res.painterResource(R.drawable.ic_download_nav),
                                                        contentDescription = null,
                                                        tint = colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "下载中 ${card.doneCount}/${card.totalCount}",
                                                        color = colorScheme.onPrimaryContainer,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        } else if (card.totalCount > 0 && card.doneCount == card.totalCount) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF0F0F2)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        androidx.compose.ui.res.painterResource(R.drawable.ic_check_circle),
                                                        contentDescription = null,
                                                        tint = colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "已完成 ${card.doneCount}集",
                                                        color = colorScheme.onSurfaceVariant,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (supporting.isNotEmpty()) {
                                        Text(
                                            supporting,
                                            color = colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    if (card.isDownloading && card.progress != null) {
                                        Spacer(Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(colorScheme.surfaceContainerHighest)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = card.progress.coerceIn(0.01f, 1f))
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(colorScheme.primary)
                                            )
                                        }
                                    }
                                }

                                Icon(
                                    androidx.compose.ui.res.painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            DownloadSettingsSheet(
                visible = showSettingsSheet.value,
                onDismissRequest = { showSettingsSheet.value = false }
            )
        }
    }

    @Composable
    private fun StorageHeroCard(
        cardsCount: Int,
        usedText: String,
        freeBytes: Long,
        usedBytes: Long,
        totalBytes: Long,
        onOpenSettings: () -> Unit,
        onClearAll: () -> Unit
    ) {
        val isDark = isAppDarkTheme()
        val progress = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f) else 0.05f

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_cache_48),
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "离线缓存空间",
                            color = colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val freeText = if (freeBytes > 0) "可用 " + DownloadStorage.formatSize(freeBytes) else ""
                        Text(
                            "已缓存 $usedText" + (if (freeText.isNotEmpty()) " · $freeText" else ""),
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onOpenSettings() }
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(R.drawable.ic_m3_settings),
                                    contentDescription = "下载设置",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (cardsCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.errorContainer.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onClearAll() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        androidx.compose.ui.res.painterResource(R.drawable.ic_delete_outline_24),
                                        contentDescription = "清空全部",
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "清空",
                                        color = colorScheme.error,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 双色平滑圆角进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorScheme.primary)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "共 $cardsCount 部离线视频",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        "长按剧集卡片可单独删除",
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
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

    /** 切回本 tab: 无条件重查一次(loadExecutor 内部有合并), 保证新入队/下载中/刚完成的集立即可见 */
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        userVisible = isVisibleToUser
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && isAdded() && view != null) {
            dataDirty.set(false)
            loadData()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.let { me.jessyan.autosize.AutoSize.autoConvertDensity(it, 360f, true) }
        if (userVisible) {
            dataDirty.set(false)
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
            var done = 0; var waiting = 0; var resolving = 0; var downloading = 0; var paused = 0; var failed = 0
            var totalLiveSpeed = 0L
            var activeProgressSum = 0f
            for (ep in eps) {
                when (ep.status) {
                    DownloadEpisode.STATUS_WAITING -> waiting++
                    DownloadEpisode.STATUS_RESOLVING -> resolving++
                    DownloadEpisode.STATUS_DOWNLOADING -> downloading++
                    DownloadEpisode.STATUS_PAUSED -> paused++
                    DownloadEpisode.STATUS_FAILED -> failed++
                    else -> done++
                }
                val live = liveProgress[ep.getId()]
                if (live != null) {
                    if (live.speedBytes > 0) {
                        totalLiveSpeed += live.speedBytes
                    }
                    if (live.segmentsTotal > 0 && live.segmentsDone >= 0) {
                        activeProgressSum += (live.segmentsDone.toFloat() / live.segmentsTotal.toFloat()).coerceIn(0f, 1f)
                    } else if (live.totalBytes > 0 && live.downloadedBytes >= 0) {
                        activeProgressSum += (live.downloadedBytes.toFloat() / live.totalBytes.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
            val isDownloading = downloading > 0 || resolving > 0 || waiting > 0
            val hasActive = downloading > 0 || resolving > 0

            var sizeText: String? = null
            if (hasActive) {
                // 有下载中集数时，实时计算已下载总字节，跳过静态缓存以实时更新显示
                var liveGroupSize = 0L
                for (ep in eps) {
                    liveGroupSize += episodeSize(ep)
                }
                if (liveGroupSize > 0) {
                    sizeText = DownloadStorage.formatSize(liveGroupSize)
                }
            } else {
                val cached = cachedGroupSize(entry.key)
                if (cached != null) {
                    sizeText = if (cached > 0) DownloadStorage.formatSize(cached) else null
                } else if (!needsWalk) {
                    sizeText = computeGroupSize(entry.key, eps)
                } else {
                    pendingGroups.add(entry.key)
                }
            }

            val speedText = if (isDownloading && totalLiveSpeed > 0) {
                DownloadStorage.formatSize(totalLiveSpeed) + "/s"
            } else null

            val progressFraction: Float? = if (isDownloading && eps.isNotEmpty()) {
                val overall = (done.toFloat() + activeProgressSum) / eps.size.toFloat()
                overall.coerceIn(0f, 1f)
            } else null

            items.add(
                CacheCard(
                    groupKey = entry.key,
                    name = displayNameOf(eps[0]),
                    pic = eps[0].vodPic ?: "",
                    note = buildNote(done, eps.size, waiting, resolving, downloading, paused, failed),
                    sizeText = sizeText,
                    isDownloading = isDownloading,
                    doneCount = done,
                    totalCount = eps.size,
                    speedText = speedText,
                    progress = progressFraction
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
        val base = DownloadStorage.baseDir()
        val free = try { base.usableSpace } catch (_: Throwable) { 0L }
        val diskTotal = try { base.totalSpace } catch (_: Throwable) { 0L }
        runOnUi {
            usedBytesState.value = total
            freeBytesState.value = free
            totalBytesState.value = diskTotal
            storageText.value = DownloadStorage.formatSize(total)
        }
    }

    private fun runOnUi(action: () -> Unit) {
        val act = activity ?: return
        act.runOnUiThread(action)
    }

    private fun confirmDeleteSeries(act: android.app.Activity, groupKey: String, seriesName: String) {
        MaterialAlertDialogBuilder(act)
            .setTitle("删除下载")
            .setMessage("确定删除《${seriesName ?: ""}》的全部下载文件吗?")
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

    private fun confirmClearAll(act: android.app.Activity) {
        MaterialAlertDialogBuilder(act)
            .setTitle("清空下载")
            .setMessage("确定删除全部下载文件吗?")
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

        /** 网格角标文案: 已下 x/共 y(解析中/下载中/暂停/失败) —— 移除 等待x */
        @JvmStatic
        fun buildNote(
            done: Int, total: Int, waiting: Int, resolving: Int,
            downloading: Int, paused: Int, failed: Int
        ): String {
            val sb = StringBuilder("已下").append(done).append("/").append(total)
            if (downloading > 0) sb.append(" 下载中").append(downloading)
            if (resolving > 0) sb.append(" 解析中").append(resolving)
            if (paused > 0) sb.append(" 暂停").append(paused)
            if (failed > 0) sb.append(" 失败").append(failed)
            return sb.toString()
        }
    }
}
