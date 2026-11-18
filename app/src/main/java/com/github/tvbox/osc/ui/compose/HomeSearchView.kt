package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean

/**
 * 首页 M3 Expressive Search View 全屏覆盖层
 * 支持历史搜索、全网热门、实时联想、多源检索结果呈现
 */
@Composable
fun HomeSearchView(
    controller: HomeSearchController,
    topBarHeight: Dp,
    onOpenVideo: (Movie.Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestListState = rememberLazyListState()
    val suggestScrollState = rememberScrollState()
    val resultListState = rememberLazyListState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isHistoryDeleteMode by remember { mutableStateOf(false) }
    var isHistoryCollapsed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (controller.showSuggestPage.value) {
            HomeSuggestPage(
                controller = controller,
                topBarHeight = topBarHeight,
                listState = suggestListState,
                scrollState = suggestScrollState,
                isHistoryCollapsed = isHistoryCollapsed,
                onToggleHistoryCollapsed = { isHistoryCollapsed = !isHistoryCollapsed },
                isHistoryDeleteMode = isHistoryDeleteMode,
                onToggleHistoryDeleteMode = { isHistoryDeleteMode = it },
                onShowClearConfirm = { showClearConfirmDialog = true }
            )
        } else {
            HomeResultPage(
                controller = controller,
                topBarHeight = topBarHeight,
                listState = resultListState,
                onOpenVideo = onOpenVideo
            )
        }

        // 清空历史记录确认对话框
        M3ConfirmDialog(
            visible = showClearConfirmDialog,
            title = "清空历史记录",
            message = "确定要删除所有搜索历史记录吗？",
            confirmText = "清空",
            cancelText = "取消",
            onConfirm = {
                controller.clearAllHistory()
                isHistoryDeleteMode = false
                showClearConfirmDialog = false
            },
            onDismissRequest = {
                showClearConfirmDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeSuggestPage(
    controller: HomeSearchController,
    topBarHeight: Dp,
    listState: LazyListState,
    scrollState: ScrollState,
    isHistoryCollapsed: Boolean,
    onToggleHistoryCollapsed: () -> Unit,
    isHistoryDeleteMode: Boolean,
    onToggleHistoryDeleteMode: (Boolean) -> Unit,
    onShowClearConfirm: () -> Unit
) {
    val suggestions = controller.suggestWords.value
    val hasInput = controller.inputText.value.isNotBlank()

    if (hasInput) {
        // 输入关键词时：展示实时联想词
        if (suggestions.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topBarHeight + 6.dp, bottom = 28.dp)
            ) {
                itemsIndexed(suggestions) { _, suggest ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { controller.search(suggest) }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_search_18),
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            suggest,
                            color = colorScheme.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    } else {
        // 输入框为空时：展示搜索历史与全网热搜
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 8.dp))

            // 1. 搜索历史
            if (controller.historyWords.value.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：标题 + 折叠/展开小箭头
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleHistoryCollapsed() }
                            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp)
                    ) {
                        Text(
                            "搜索历史",
                            color = colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isHistoryCollapsed) -90f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "home_history_arrow"
                        )
                        Icon(
                            painterResource(R.drawable.ic_arrow_down),
                            contentDescription = if (isHistoryCollapsed) "展开历史" else "收起历史",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(arrowRotation)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 右侧：常态单个【删除】按钮 / 管理态【全部删除】+【完成】
                    if (!isHistoryDeleteMode) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onToggleHistoryDeleteMode(true) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_outline_24),
                                    contentDescription = "管理历史",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "删除",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onShowClearConfirm() }
                            ) {
                                Text(
                                    "全部删除",
                                    color = colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onToggleHistoryDeleteMode(false) }
                            ) {
                                Text(
                                    "完成",
                                    color = colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // 历史搜索词流式布局
                AnimatedVisibility(
                    visible = !isHistoryCollapsed,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(180)),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(tween(150))
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        controller.historyWords.value.forEach { word ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (!isHistoryDeleteMode) {
                                            controller.search(word)
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        start = 12.dp,
                                        end = if (isHistoryDeleteMode) 6.dp else 12.dp,
                                        top = 6.dp,
                                        bottom = 6.dp
                                    )
                                ) {
                                    Text(
                                        word,
                                        color = colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    if (isHistoryDeleteMode) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                .clickable { controller.removeHistoryWord(word) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_clear),
                                                contentDescription = "删除记录",
                                                tint = colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. 全网热门搜索
            if (controller.hotWords.value.isNotEmpty()) {
                Text(
                    "全网热门搜索",
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (controller.historyWords.value.isNotEmpty()) 22.dp else 6.dp)
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    controller.hotWords.value.forEachIndexed { index, word ->
                        val isTop3 = index < 3
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTop3) colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { controller.search(word) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = if (isTop3) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    word,
                                    color = colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HomeResultPage(
    controller: HomeSearchController,
    topBarHeight: Dp,
    listState: LazyListState,
    onOpenVideo: (Movie.Video) -> Unit
) {
    val items = controller.displayed()
    when {
        controller.loadState.intValue == 1 && items.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight)
            ) {
                HomeSearchLoadingHint()
            }
        }
        controller.loadState.intValue == 3 && items.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight)
            ) {
                HomeSearchEmptyHint()
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topBarHeight + 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { video ->
                    HomeSearchResultItem(
                        video = video,
                        onClick = { onOpenVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeSearchResultItem(
    video: Movie.Video,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "home_search_item_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧海报区: 固定宽 85dp，纵横比 3:4
            Box(
                modifier = Modifier
                    .width(85.dp)
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainer)
            ) {
                PosterImage(
                    url = video.pic ?: "",
                    modifier = Modifier.fillMaxSize()
                )

                // 来源标签
                val sourceName = try {
                    ApiConfig.get().getSource(video.sourceKey)?.name ?: ""
                } catch (_: Exception) { "" }
                if (sourceName.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                    ) {
                        Text(
                            text = sourceName,
                            color = colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // 备注/集数
                if (!video.note.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .widthIn(max = 75.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x99000000), Color(0xCC000000))
                                ),
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = video.note,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // 右侧信息区
            Column(
                modifier = Modifier
                    .height(113.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. 标题
                Text(
                    text = video.name ?: "",
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                // 2. 年份 · 类型 · 国家 · 语言
                val metaInfo = remember(video) {
                    listOfNotNull(
                        if (video.year > 0) video.year.toString() else null,
                        video.type?.takeIf { it.isNotBlank() },
                        video.area?.takeIf { it.isNotBlank() },
                        video.lang?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                }
                if (metaInfo.isNotEmpty()) {
                    Text(
                        text = metaInfo,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. 主演
                val actors = video.actor?.trim()
                if (!actors.isNullOrEmpty()) {
                    Text(
                        text = actors,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                } else {
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSearchSourceFilterPanel(
    allSources: List<SourceBean>,
    checkedKeys: Set<String>,
    onToggleKey: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit
) {
    val drawerShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(bottom = 12.dp)
            .shadow(elevation = 12.dp, shape = drawerShape)
            .background(color = colorScheme.surface, shape = drawerShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索源配置",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "已选 ${checkedKeys.size}/${allSources.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 搜索源 Chips 流式布局
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allSources.forEach { source ->
                        val isSelected = checkedKeys.contains(source.key)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleKey(source.key) },
                            label = {
                                Text(
                                    source.name,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primaryContainer,
                                selectedLabelColor = colorScheme.onPrimaryContainer,
                                containerColor = colorScheme.surfaceContainerHigh,
                                labelColor = colorScheme.onSurfaceVariant
                            ),
                            border = null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 底部操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onSelectAll() }
                    ) {
                        Text(
                            "全选",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onClearAll() }
                    ) {
                        Text(
                            "清空",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onClose() }
                ) {
                    Text(
                        "确定并检索",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeSearchLoadingHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = colorScheme.primary,
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "正在全网检索影视资源…",
            color = colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
fun HomeSearchEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.ic_search_18),
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "未检索到相关内容",
            color = colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "请尝试更换关键词，或在右上角开启更多搜索源",
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
