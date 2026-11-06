package com.github.tvbox.osc.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo

@Immutable
data class DetailUiState(
    val title: String = "",
    val sourceName: String = "",
    val subInfo: String = "",
    val isPrivateBrowsing: Boolean = false,
    val isCollected: Boolean = false,
    val lines: List<VodInfo.VodSeriesFlag> = emptyList(),
    val currentLineIndex: Int = 0,
    val episodes: List<VodInfo.VodSeries> = emptyList(),
    val currentEpisodeIndex: Int = 0,
    val isReverseSort: Boolean = false,
    val cachedUrls: Set<String> = emptySet(),
    val showAllEpisodesSheet: Boolean = false,
    val hasVideo: Boolean = false,
    val hasEmptyPlaylist: Boolean = false,
    val version: Long = 0L
)

interface DetailScreenCallbacks {
    fun onDetailClick()
    fun onChangeSourceClick()
    fun onDownloadClick()
    fun onCollectClick()
    fun onPrivateBrowsingClick()
    fun onLineSelected(position: Int)
    fun onEpisodeClick(position: Int)
    fun onToggleSort()
    fun onOpenAllEpisodesSheet()
    fun onDismissAllEpisodesSheet()
}

class DetailComposeBridge(
    private val composeView: ComposeView,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onDetailClick()
        fun onChangeSourceClick()
        fun onDownloadClick()
        fun onCollectClick()
        fun onPrivateBrowsingClick()
        fun onLineSelected(position: Int)
        fun onEpisodeClick(position: Int)
        fun onToggleSort()
    }

    private var uiState by mutableStateOf(DetailUiState())

    init {
        composeView.setContent {
            TvBoxTheme {
                DetailScreen(
                    state = uiState,
                    callbacks = object : DetailScreenCallbacks {
                        override fun onDetailClick() = callbacks.onDetailClick()
                        override fun onChangeSourceClick() = callbacks.onChangeSourceClick()
                        override fun onDownloadClick() = callbacks.onDownloadClick()
                        override fun onCollectClick() = callbacks.onCollectClick()
                        override fun onPrivateBrowsingClick() = callbacks.onPrivateBrowsingClick()
                        override fun onLineSelected(position: Int) = callbacks.onLineSelected(position)
                        override fun onEpisodeClick(position: Int) = callbacks.onEpisodeClick(position)
                        override fun onToggleSort() = callbacks.onToggleSort()
                        override fun onOpenAllEpisodesSheet() {
                            uiState = uiState.copy(showAllEpisodesSheet = true)
                        }
                        override fun onDismissAllEpisodesSheet() {
                            uiState = uiState.copy(showAllEpisodesSheet = false)
                        }
                    }
                )
            }
        }
    }

    fun updateVideoInfo(
        title: String,
        sourceName: String,
        subInfo: String,
        isPrivateBrowsing: Boolean
    ) {
        uiState = uiState.copy(
            title = title,
            sourceName = sourceName,
            subInfo = subInfo,
            isPrivateBrowsing = isPrivateBrowsing,
            hasVideo = true,
            version = uiState.version + 1
        )
    }

    fun updateCollectState(isCollected: Boolean) {
        uiState = uiState.copy(
            isCollected = isCollected,
            version = uiState.version + 1
        )
    }

    fun updateLines(lines: List<VodInfo.VodSeriesFlag>, currentIndex: Int) {
        uiState = uiState.copy(
            lines = ArrayList(lines),
            currentLineIndex = currentIndex,
            version = uiState.version + 1
        )
    }

    fun updateEpisodes(
        episodes: List<VodInfo.VodSeries>,
        currentIndex: Int,
        isReverseSort: Boolean,
        cachedUrls: Set<String>
    ) {
        uiState = uiState.copy(
            episodes = ArrayList(episodes),
            currentEpisodeIndex = currentIndex,
            isReverseSort = isReverseSort,
            cachedUrls = cachedUrls,
            hasEmptyPlaylist = episodes.isEmpty(),
            version = uiState.version + 1
        )
    }

    fun setEmptyPlaylist(isEmpty: Boolean) {
        uiState = uiState.copy(
            hasEmptyPlaylist = isEmpty,
            version = uiState.version + 1
        )
    }

    fun showAllEpisodesDialog() {
        uiState = uiState.copy(showAllEpisodesSheet = true)
    }

    fun dismissAllEpisodesDialog() {
        uiState = uiState.copy(showAllEpisodesSheet = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    callbacks: DetailScreenCallbacks
) {
    val episodeListState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val cardWidthPx = with(density) { 68.dp.roundToPx() }
    val horizontalCenterOffsetPx = -((screenWidthPx - cardWidthPx) / 2)

    LaunchedEffect(state.currentEpisodeIndex, state.isReverseSort, state.episodes.size) {
        if (state.episodes.isNotEmpty() && state.currentEpisodeIndex in state.episodes.indices) {
            episodeListState.animateScrollToItem(state.currentEpisodeIndex, horizontalCenterOffsetPx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // 标题与详情
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title.ifEmpty { "暂无信息" },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = callbacks::onDetailClick)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "详情",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = callbacks::onDetailClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        // 第一行：来源信息 + 无痕图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "来源：" + (state.sourceName.ifEmpty { "未知" }),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = callbacks::onChangeSourceClick)
            )
            if (state.isPrivateBrowsing) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_private_browsing),
                    contentDescription = "无痕模式",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = callbacks::onPrivateBrowsingClick)
                )
            }
        }

        // 第二行：年份 / 地区 / 类型 / 语言
        if (state.subInfo.isNotEmpty()) {
            Text(
                text = state.subInfo,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 3个居中操作按钮：换源、下载、收藏（图标在上，文字在下）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                iconRes = R.drawable.ic_change_20,
                label = "换源",
                onClick = callbacks::onChangeSourceClick
            )
            ActionButton(
                iconRes = R.drawable.ic_download_18,
                label = "下载",
                onClick = callbacks::onDownloadClick
            )
            ActionButton(
                iconRes = R.drawable.ic_colloct_18,
                label = if (state.isCollected) "已收藏" else "收藏",
                isHighlighted = state.isCollected,
                onClick = callbacks::onCollectClick
            )
        }

        // 分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )

        // 暂无播放数据
        if (state.hasEmptyPlaylist) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icon_empty),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "暂无播放数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 线路区域（已去除快捷换线按钮）
            if (state.lines.isNotEmpty()) {
                Text(
                    text = "线路",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = state.lines,
                        key = { index, flag -> "${state.version}_${flag.name}_$index" }
                    ) { index, flag ->
                        val isSelected = index == state.currentLineIndex || flag.selected
                        LineCard(
                            name = flag.name ?: "",
                            isSelected = isSelected,
                            onClick = { callbacks.onLineSelected(index) }
                        )
                    }
                }
            }

            // 选集区域
            if (state.episodes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选集",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // 排序按钮（点击立即生效）
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = callbacks::onToggleSort)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isReverseSort) "正序" else "倒序",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sort_18),
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // 全部展开按钮
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = callbacks::onOpenAllEpisodesSheet)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_expand_all_18),
                            contentDescription = "全部",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 横向紧凑选集卡片
                LazyRow(
                    state = episodeListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(
                        items = state.episodes,
                        key = { index, item -> "${state.isReverseSort}_${state.version}_${item.url ?: item.name}_$index" }
                    ) { index, item ->
                        val isSelected = index == state.currentEpisodeIndex || item.selected
                        val isCached = item.url != null && state.cachedUrls.contains(item.url)
                        EpisodeCard(
                            name = item.name ?: "",
                            isSelected = isSelected,
                            isCached = isCached,
                            onClick = { callbacks.onEpisodeClick(index) }
                        )
                    }
                }
            }
        }
    }

    // 全集展开半屏 ModalBottomSheet
    if (state.showAllEpisodesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val configuration = LocalConfiguration.current
        val sheetHeight = (configuration.screenHeightDp * 0.58f).dp
        val episodeGridState = rememberLazyGridState()

        LaunchedEffect(state.showAllEpisodesSheet, state.currentEpisodeIndex, state.isReverseSort) {
            if (state.showAllEpisodesSheet && state.episodes.isNotEmpty() && state.currentEpisodeIndex in state.episodes.indices) {
                episodeGridState.scrollToItem(state.currentEpisodeIndex)
            }
        }

        ModalBottomSheet(
            onDismissRequest = callbacks::onDismissAllEpisodesSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(horizontal = 16.dp)
            ) {
                // 顶部栏：左侧切换排序、中间共XX集、右侧关闭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左上角：排序按钮（点击立即生效）
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = callbacks::onToggleSort)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sort_18),
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isReverseSort) "正序" else "倒序",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 中间：标题
                    Text(
                        text = "选集 (共 ${state.episodes.size} 集)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // 右上角：关闭按钮
                    IconButton(
                        onClick = callbacks::onDismissAllEpisodesSheet,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close_24),
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 选集网格
                LazyVerticalGrid(
                    state = episodeGridState,
                    columns = GridCells.Adaptive(minSize = 68.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = state.episodes,
                        key = { index, item -> "${state.isReverseSort}_${state.version}_${item.url ?: item.name}_$index" }
                    ) { index, item ->
                        val isSelected = index == state.currentEpisodeIndex || item.selected
                        val isCached = item.url != null && state.cachedUrls.contains(item.url)
                        EpisodeCard(
                            name = item.name ?: "",
                            isSelected = isSelected,
                            isCached = isCached,
                            onClick = {
                                callbacks.onEpisodeClick(index)
                                callbacks.onDismissAllEpisodesSheet()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    iconRes: Int,
    label: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val tintColor = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val textColor = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
private fun LineCard(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeCard(
    name: String,
    isSelected: Boolean,
    isCached: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isShort = name.length <= 4
    val cardModifier = if (isShort) {
        modifier.width(68.dp)
    } else {
        modifier.widthIn(min = 68.dp, max = 110.dp)
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Box(
        modifier = cardModifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val displayText = if (isCached) "✓ $name" else name
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
