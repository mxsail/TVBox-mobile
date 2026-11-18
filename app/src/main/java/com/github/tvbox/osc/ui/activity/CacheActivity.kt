package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.cache.DownloadEpisode
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.download.DownloadStorage
import com.github.tvbox.osc.download.DownloadTaskManager
import com.github.tvbox.osc.event.DownloadEvent
import com.github.tvbox.osc.ui.adapter.DownloadAdapter
import com.github.tvbox.osc.ui.compose.DownloadSettingsSheet
import com.github.tvbox.osc.ui.compose.FloatingActionIsland
import com.github.tvbox.osc.ui.compose.M3ConfirmDialog
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxManageTopBar
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 独立「我的下载」页面 (符合 Material Design 3 Expressive 规范):
 * 沉浸式顶栏返回导航、批量清空与下载设置入口，展示离线剧集海报卡片列表。
 */
class CacheActivity : BaseActivity() {

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

    private val cards = mutableStateOf<List<CacheCard>>(emptyList())
    private val storageText = mutableStateOf("0MB")
    private var lastReloadTime = 0L
    private var isResumedState = false

    private val liveProgress = ConcurrentHashMap<Int, DownloadAdapter.Live>()
    private val loadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val loadRunning = AtomicBoolean(false)
    private val loadPending = AtomicBoolean(false)
    private val dataDirty = AtomicBoolean(true)

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        me.jessyan.autosize.AutoSize.autoConvertDensity(this, 360f, true)
        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            TvBoxTheme {
                CacheActivityScreen()
            }
        }
    }

    override fun init() {
        storageText.value = "已占用: …"
        loadData()
    }

    override fun onResume() {
        super.onResume()
        me.jessyan.autosize.AutoSize.autoConvertDensity(this, 360f, true)
        isResumedState = true
        if (dataDirty.get() || consumeSizeDirty()) {
            dataDirty.set(false)
            loadData()
        }
    }

    override fun onPause() {
        super.onPause()
        isResumedState = false
    }

    override fun onDestroy() {
        loadExecutor.shutdownNow()
        super.onDestroy()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        val now = System.currentTimeMillis()
        val visible = isResumedState
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

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun CacheActivityScreen() {
        val listState = rememberLazyListState()
        val isDark = isAppDarkTheme()
        val isSelectionMode = remember { mutableStateOf(false) }
        val selectedKeys = remember { mutableStateOf<Set<String>>(emptySet()) }
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        val allKeys = remember(cards.value) { cards.value.map { it.groupKey }.toSet() }
        val isAllSelected = allKeys.isNotEmpty() && selectedKeys.value.size == allKeys.size

        BackHandler(isSelectionMode.value) {
            isSelectionMode.value = false
            selectedKeys.value = emptySet()
        }

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
                if (cards.value.isEmpty()) {
                    // Expressive 空态
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_download_nav),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            "暂无离线内容",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            "在影视详情页面点击「缓存」即可离线观看",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(this@CacheActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra(MainActivity.EXTRA_TAB, 0)
                                }
                                startActivity(intent)
                                finish()
                            },
                            shape = RoundedCornerShape(percent = 50),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("去发现好片", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    val listBottomPadding by animateDpAsState(
                        targetValue = if (isSelectionMode.value) 92.dp else 24.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "list_bottom_padding"
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topBarPadding + 8.dp, bottom = listBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .align(Alignment.TopCenter)
                            .layerBackdrop(backdrop)
                    ) {
                    items(cards.value, key = { it.groupKey }) { card ->
                        val supporting = listOfNotNull(
                            card.note,
                            card.sizeText,
                            if (card.isDownloading) card.speedText else null
                        ).joinToString(" · ")

                        val isSelected = selectedKeys.value.contains(card.groupKey)

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else if (isDark) {
                                    colorScheme.surfaceContainerLow
                                } else {
                                    Color.White
                                }
                            ),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, colorScheme.primary)
                            } else if (!isDark) {
                                BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode.value) {
                                            if (isSelected) selectedKeys.value = selectedKeys.value - card.groupKey
                                            else selectedKeys.value = selectedKeys.value + card.groupKey
                                        } else {
                                            val intent = Intent(this@CacheActivity, DownloadActivity::class.java)
                                            intent.putExtra(DownloadActivity.EXTRA_GROUP_KEY, card.groupKey)
                                            startActivity(intent)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode.value) {
                                            isSelectionMode.value = true
                                            selectedKeys.value = setOf(card.groupKey)
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode.value) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedKeys.value = selectedKeys.value + card.groupKey
                                            else selectedKeys.value = selectedKeys.value - card.groupKey
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colorScheme.primary,
                                            checkmarkColor = colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }

                                // 左侧海报 (2:3 优雅比例与平滑圆角)
                                PosterImage(
                                    card.pic,
                                    Modifier
                                        .width(70.dp)
                                        .height(98.dp)
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
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
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
                                                        painterResource(R.drawable.ic_download_nav),
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
                                                color = if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_check_circle),
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
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    if (card.isDownloading && card.progress != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colorScheme.surfaceContainerHighest)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = card.progress.coerceIn(0.01f, 1f))
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(colorScheme.primary)
                                            )
                                        }
                                    }
                                }

                                if (!isSelectionMode.value) {
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                FloatingActionIsland(
                    visible = isSelectionMode.value && cards.value.isNotEmpty(),
                    isAllSelected = isAllSelected,
                    selectedCount = selectedKeys.value.size,
                    onToggleSelectAll = {
                        selectedKeys.value = if (isAllSelected) emptySet() else allKeys
                    },
                    onDeleteClick = {
                        if (selectedKeys.value.isNotEmpty()) {
                            showDeleteConfirmDialog = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // 顶层悬浮动态毛玻璃顶栏 (穿顶滑动 + 覆盖通知栏)
                TvBoxManageTopBar(
                    title = "下载",
                    isEditMode = isSelectionMode.value,
                    selectedCount = selectedKeys.value.size,
                    canManage = cards.value.isNotEmpty(),
                    editTitle = "选择项目",
                    isScrolled = isScrolled,
                    backdrop = backdrop,
                    onBack = {
                        if (isSelectionMode.value) {
                            isSelectionMode.value = false
                            selectedKeys.value = emptySet()
                        } else {
                            finish()
                        }
                    },
                    onToggleEditMode = {
                        if (isSelectionMode.value) {
                            isSelectionMode.value = false
                            selectedKeys.value = emptySet()
                        } else {
                            isSelectionMode.value = true
                            selectedKeys.value = emptySet()
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

    M3ConfirmDialog(
            visible = showDeleteConfirmDialog,
            title = "删除确认",
            message = "确定删除选中的 ${selectedKeys.value.size} 项下载吗？",
            confirmText = "删除",
            cancelText = "取消",
            isDestructive = true,
            iconRes = R.drawable.ic_delete_outline_24,
            onDismissRequest = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                val toDelete = selectedKeys.value
                isSelectionMode.value = false
                selectedKeys.value = emptySet()
                executeDeleteBatch(toDelete)
            }
        )
    }

    private fun loadData() {
        loadPending.set(true)
        if (loadExecutor.isShutdown) return
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
                if (loadPending.get() && !loadExecutor.isShutdown) {
                    loadData()
                }
            }
        } catch (th: Throwable) {
            loadRunning.set(false)
            th.printStackTrace()
        }
    }

    fun markSizesDirtyPublic() = markSizesDirty()

    private fun applyGrid(all: List<DownloadEpisode>) {
        val groups = LinkedHashMap<String, MutableList<DownloadEpisode>>()
        for (ep in all) {
            groups.getOrPut(ep.groupKey()) { ArrayList() }.add(ep)
        }
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
        runOnUiThread {
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
            runOnUiThread { cards.value = updated }
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
        runOnUiThread {
            storageText.value = DownloadStorage.formatSize(total)
        }
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

    private fun executeDeleteBatch(keys: Set<String>, onDone: () -> Unit = {}) {
        if (keys.isEmpty()) return
        loadExecutor.execute {
            try {
                val eps = RoomDataManger.getAllDownloadEpisodes()
                for (ep in eps) {
                    if (keys.contains(ep.groupKey())) {
                        DownloadTaskManager.get().cancel(ep.getId())
                        if (ep.localDir != null) {
                            DownloadStorage.deleteRecursive(File(ep.localDir))
                        }
                        RoomDataManger.deleteDownloadEpisode(ep)
                    }
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
            markSizesDirty()
            loadData()
            runOnUiThread { onDone() }
        }
    }

    companion object {
        // 供 DownloadActivity 共用的进程级大小缓存
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

        /** 网格角标文案: 已下 x/共 y(解析中/下载中/暂停/失败) */
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
