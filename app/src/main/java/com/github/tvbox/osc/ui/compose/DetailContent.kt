package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

@Immutable
data class DetailUiState(
    val title: String = "",
    val sourceName: String = "",
    val subInfo: String = "",
    val des: String = "",
    val actor: String = "",
    val director: String = "",
    val pic: String = "",
    val isPrivateBrowsing: Boolean = false,
    val isCollected: Boolean = false,
    val lines: List<VodInfo.VodSeriesFlag> = emptyList(),
    val currentLineIndex: Int = 0,
    val episodes: List<VodInfo.VodSeries> = emptyList(),
    val currentEpisodeIndex: Int = 0,
    val isReverseSort: Boolean = false,
    val cachedUrls: Set<String> = emptySet(),
    val showAllEpisodesSheet: Boolean = false,
    val showVideoDetailSheet: Boolean = false,
    val isPlaying: Boolean = false,
    val hasVideo: Boolean = false,
    val hasEmptyPlaylist: Boolean = false,
    val version: Long = 0L
)

interface DetailScreenCallbacks {
    fun onDetailClick()
    fun onChangeSourceClick()
    fun onDownloadClick()
    fun onCollectClick()
    fun onCastClick()
    fun onPrivateBrowsingClick()
    fun onLineSelected(position: Int)
    fun onEpisodeClick(position: Int)
    fun onToggleSort()
    fun onOpenAllEpisodesSheet()
    fun onDismissAllEpisodesSheet()
    fun onOpenVideoDetailSheet()
    fun onDismissVideoDetailSheet()
    fun onCopyVodUrl()
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
        fun onCastClick()
        fun onPrivateBrowsingClick()
        fun onLineSelected(position: Int)
        fun onEpisodeClick(position: Int)
        fun onToggleSort()
        fun onCopyVodUrl()
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
                        override fun onCastClick() = callbacks.onCastClick()
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
                        override fun onOpenVideoDetailSheet() {
                            uiState = uiState.copy(showVideoDetailSheet = true)
                        }
                        override fun onDismissVideoDetailSheet() {
                            uiState = uiState.copy(showVideoDetailSheet = false)
                        }
                        override fun onCopyVodUrl() = callbacks.onCopyVodUrl()
                    }
                )
            }
        }
    }

    fun updateVideoInfo(
        title: String,
        sourceName: String,
        subInfo: String,
        des: String = "",
        actor: String = "",
        director: String = "",
        pic: String = "",
        isPrivateBrowsing: Boolean
    ) {
        uiState = uiState.copy(
            title = title,
            sourceName = sourceName,
            subInfo = subInfo,
            des = des,
            actor = actor,
            director = director,
            pic = pic,
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

    fun updatePlayingState(isPlaying: Boolean) {
        uiState = uiState.copy(
            isPlaying = isPlaying,
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

    fun dismissAllSheets() {
        uiState = uiState.copy(
            showAllEpisodesSheet = false,
            showVideoDetailSheet = false
        )
    }
}

/**
 * Material Design 3 Expressive 竖屏点播详情与选集主面板
 */
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

    // 仅在选集首次加载完成时自动定位到当前播放集
    var hasInitialEpisodeScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.episodes.size, state.currentEpisodeIndex) {
        if (!hasInitialEpisodeScroll && state.episodes.isNotEmpty() && state.currentEpisodeIndex in state.episodes.indices) {
            episodeListState.animateScrollToItem(state.currentEpisodeIndex, horizontalCenterOffsetPx)
            hasInitialEpisodeScroll = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
        // 1. 顶部影片信息卡片 (M3 Expressive surfaceContainerLow 容器)
        VideoInfoHeroCard(
            state = state,
            onTitleOrDetailClick = {
                if (state.des.isNotEmpty() || state.actor.isNotEmpty()) {
                    callbacks.onOpenVideoDetailSheet()
                } else {
                    callbacks.onDetailClick()
                }
            },
            onChangeSourceClick = callbacks::onChangeSourceClick,
            onPrivateBrowsingClick = callbacks::onPrivateBrowsingClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. M3 Expressive 均分药丸操作栏 (换源 / 下载 / 收藏 / 投屏)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ExpressiveActionButton(
                iconRes = R.drawable.ic_swap_source_24,
                label = "换源",
                modifier = Modifier.weight(1f),
                onClick = callbacks::onChangeSourceClick
            )
            M3ExpressiveActionButton(
                iconRes = R.drawable.ic_download_nav,
                label = "下载",
                modifier = Modifier.weight(1f),
                onClick = callbacks::onDownloadClick
            )
            M3ExpressiveActionButton(
                iconRes = if (state.isCollected) R.drawable.ic_collect_filled else R.drawable.ic_colloct_18,
                label = if (state.isCollected) "已收藏" else "收藏",
                isHighlighted = state.isCollected,
                modifier = Modifier.weight(1f),
                onClick = callbacks::onCollectClick
            )
            M3ExpressiveActionButton(
                iconRes = R.drawable.ic_m3_cast,
                label = "投屏",
                modifier = Modifier.weight(1f),
                onClick = callbacks::onCastClick
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 柔和 M3 分割线
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.8.dp
        )

        // 3. 播放列表状态处理
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
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "暂无播放数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 4. 线路切换区域 (Expressive Capsule Tabs)
            if (state.lines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "线路",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "共 ${state.lines.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                        LineCapsuleTab(
                            name = flag.name ?: "线路 ${index + 1}",
                            isSelected = isSelected,
                            onClick = { callbacks.onLineSelected(index) }
                        )
                    }
                }
            }

            // 5. 选集模块 (动感音浪 Equalizer + 缓存角标 + 12dp Squircle)
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "共 ${state.episodes.size} 集",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    // 排序快捷胶囊
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = callbacks::onToggleSort)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isReverseSort) "正序" else "倒序",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sort_18),
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 全部展开抽屉入口
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = callbacks::onOpenAllEpisodesSheet)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_expand_all_18),
                            contentDescription = "全部剧集",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // 横向滑动选集卡片流
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
                        EpisodeSquircleCard(
                            name = item.name ?: "",
                            isSelected = isSelected,
                            isPlaying = isSelected && state.isPlaying,
                            isCached = isCached,
                            onClick = { callbacks.onEpisodeClick(index) }
                        )
                    }
                }
            }
        }
    }
}

    // 6. 全集展开半屏抽屉 (带分段快速切片 1-50, 51-100)
    if (state.showAllEpisodesSheet) {
        AllEpisodesM3BottomSheet(
            state = state,
            callbacks = callbacks
        )
    }

    // 7. 影片完整详情/图文长文抽屉
    if (state.showVideoDetailSheet) {
        VideoDetailM3BottomSheet(
            state = state,
            onDismissRequest = callbacks::onDismissVideoDetailSheet,
            onCopyVodUrl = callbacks::onCopyVodUrl
        )
    }
}

/**
 * 顶部影片信息卡片
 */
@Composable
private fun VideoInfoHeroCard(
    state: DetailUiState,
    onTitleOrDetailClick: () -> Unit,
    onChangeSourceClick: () -> Unit,
    onPrivateBrowsingClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = colorScheme.outline.copy(alpha = 0.15f)
            )
            .background(colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 大标题 + 详情查看指示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTitleOrDetailClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title.ifEmpty { "暂无影片信息" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "详情",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onPrimaryContainer
                    )
                }
            }

            // 元数据胶囊 Tag 流 (来源 / 年份 / 地区 / 分类 / 无痕)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 来源胶囊
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onChangeSourceClick)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "来源: " + state.sourceName.ifEmpty { "默认源" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.subInfo.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = state.subInfo,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (state.isPrivateBrowsing) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.primaryContainer)
                            .clickable(onClick = onPrivateBrowsingClick)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_private_browsing),
                                contentDescription = "无痕模式",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "无痕",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 简介折叠预览 (若有剧情简介)
            if (state.des.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.des.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onTitleOrDetailClick)
                )
            }
        }
    }
}

/**
 * M3 Expressive 均分胶囊药丸操作按钮 (带 Spring 物理回弹触感)
 */
@Composable
private fun M3ExpressiveActionButton(
    iconRes: Int,
    label: String,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "btnScale"
    )

    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isHighlighted) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val contentColor = if (isHighlighted) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val borderColor = if (isHighlighted) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(40.dp)
            .shadow(
                elevation = if (isHighlighted) 3.dp else 1.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = if (isHighlighted) colorScheme.primary.copy(alpha = 0.25f) else colorScheme.outline.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.5.sp
                ),
                color = contentColor
            )
        }
    }
}

/**
 * 线路选择 Capsule Tab
 */
@Composable
private fun LineCapsuleTab(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colorScheme.surfaceContainerLow,
            labelColor = colorScheme.onSurfaceVariant,
            selectedContainerColor = colorScheme.primaryContainer,
            selectedLabelColor = colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
            selectedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
            borderWidth = 1.dp
        ),
        modifier = Modifier.height(34.dp)
    )
}

/**
 * 选集 Squircle 卡片 (带动感音浪 Equalizer 与已缓存指示)
 */
@Composable
private fun EpisodeSquircleCard(
    name: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    isCached: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isShort = name.length <= 4
    val cardModifier = if (isShort) {
        modifier.width(68.dp)
    } else {
        modifier.widthIn(min = 68.dp, max = 135.dp)
    }

    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val contentColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val borderColor = if (isSelected) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = cardModifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 当前选中播放中展示动感音浪跳动
            if (isSelected) {
                EqualizerIndicator(
                    isPlaying = isPlaying,
                    color = contentColor,
                    modifier = Modifier
                        .size(width = 14.dp, height = 14.dp)
                        .padding(end = 3.dp)
                )
            }

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.5.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // 缓存成功角标
        if (isCached) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary)
            )
        }
    }
}

/**
 * 轻量级低功耗动感音浪指示器
 * - isPlaying = false 时为静态 3 条柱, 零重组 CPU 消耗
 * - isPlaying = true 时使用 Canvas 绘制平滑呼吸跳动
 */
@Composable
private fun EqualizerIndicator(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(Modifier.width(2.5.dp).height(5.dp).clip(RoundedCornerShape(1.dp)).background(color))
            Box(Modifier.width(2.5.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(color))
            Box(Modifier.width(2.5.dp).height(6.dp).clip(RoundedCornerShape(1.dp)).background(color))
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
        val anim1 by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val anim2 by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val anim3 by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )

        Canvas(modifier = modifier) {
            val totalWidth = size.width
            val height = size.height
            val barWidth = 2.5.dp.toPx()
            val corner = 1.dp.toPx()
            val spacing = 1.5.dp.toPx()
            val startX = (totalWidth - (3 * barWidth + 2 * spacing)) / 2f

            val h1 = (height * anim1).coerceAtLeast(3.dp.toPx())
            val h2 = (height * anim2).coerceAtLeast(3.dp.toPx())
            val h3 = (height * anim3).coerceAtLeast(3.dp.toPx())

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, height - h1),
                size = Size(barWidth, h1),
                cornerRadius = CornerRadius(corner, corner)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + barWidth + spacing, height - h2),
                size = Size(barWidth, h2),
                cornerRadius = CornerRadius(corner, corner)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + 2 * (barWidth + spacing), height - h3),
                size = Size(barWidth, h3),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
    }
}

/**
 * 全集半屏抽屉 (带分段快速切片 1-50, 51-100)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllEpisodesM3BottomSheet(
    state: DetailUiState,
    callbacks: DetailScreenCallbacks
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val sheetHeight = (configuration.screenHeightDp * 0.65f).dp
    val episodeGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val episodeCount = state.episodes.size
    val pageSize = 50
    val pageCount = if (episodeCount > 0) ((episodeCount - 1) / pageSize) + 1 else 1
    var selectedSectionIndex by remember { mutableIntStateOf(state.currentEpisodeIndex / pageSize) }

    LaunchedEffect(state.showAllEpisodesSheet) {
        if (state.episodes.isNotEmpty() && state.currentEpisodeIndex in state.episodes.indices) {
            episodeGridState.scrollToItem(state.currentEpisodeIndex)
            selectedSectionIndex = (state.currentEpisodeIndex / pageSize).coerceIn(0, pageCount - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = callbacks::onDismissAllEpisodesSheet,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 16.dp)
        ) {
            // 顶栏：标题、排序与关闭
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部剧集 (${state.episodes.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 排序按钮
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .clickable(onClick = callbacks::onToggleSort)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isReverseSort) "正序" else "倒序",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sort_18),
                        contentDescription = "排序",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = callbacks::onDismissAllEpisodesSheet,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_24),
                        contentDescription = "关闭",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            // 若剧集超过 50 集，展示分段切片选择器 (1-50, 51-100, ...)
            if (pageCount > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(pageCount) { pIndex ->
                        val startEp = pIndex * pageSize + 1
                        val endEp = ((pIndex + 1) * pageSize).coerceAtMost(episodeCount)
                        val isSectionSelected = pIndex == selectedSectionIndex
                        val tabBg = if (isSectionSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
                        val tabText = if (isSectionSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tabBg)
                                .clickable {
                                    selectedSectionIndex = pIndex
                                    coroutineScope.launch {
                                        episodeGridState.scrollToItem(pIndex * pageSize)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$startEp-$endEp",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSectionSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = tabText
                            )
                        }
                    }
                }
            }

            // 剧集网格瀑布流
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
                    EpisodeSquircleCard(
                        name = item.name ?: "",
                        isSelected = isSelected,
                        isPlaying = isSelected && state.isPlaying,
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

/**
 * 影片图文完整详情抽屉 (28dp 顶部大圆角)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoDetailM3BottomSheet(
    state: DetailUiState,
    onDismissRequest: () -> Unit,
    onCopyVodUrl: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题与关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title.ifEmpty { "影片详情" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_24),
                        contentDescription = "关闭",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 属性胶囊组
            if (state.subInfo.isNotEmpty()) {
                Text(
                    text = "基本信息: " + state.subInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (state.director.isNotEmpty()) {
                Text(
                    text = "导演: " + state.director,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (state.actor.isNotEmpty()) {
                Text(
                    text = "演员: " + state.actor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 剧情简介
            Text(
                text = "剧情简介",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.des.isNotEmpty()) state.des.trim() else "暂无详细简介",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 底部快捷操作胶囊 (复制播放直链)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onCopyVodUrl)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "复制当前播放链接",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.primary
                )
            }
        }
    }
}
