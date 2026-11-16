package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.fragment.LiveFragment

/** Java 侧(LiveFragment)的 setContent 桥接: @Composable lambda 无法从 Java 传入 */
fun attachLiveScreen(fragment: LiveFragment, composeView: androidx.compose.ui.platform.ComposeView) {
    composeView.setContent { TvBoxTheme { LiveScreen(fragment, fragment.uiState) } }
}

/**
 * 直播页 Compose 渲染层 (Material Design 3 Expressive):
 * 1. 视频区 (MyVideoView 内嵌) + 右侧 M3 Expressive 设置面板
 * 2. 交互型 EPG 媒体卡片 (LIVE 徽标 + 线路切换胶囊 Chips + 投屏/全屏/设置按钮)
 * 3. 频道浏览器架构 (左分组右频道，高亮有机胶囊 PrimaryContainer，频道序号方形徽标)
 */
@Composable
fun LiveScreen(fragment: LiveFragment, uiState: LiveUiState) {
    val statusBarPad = with(LocalDensity.current) {
        val viaCompose = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this)
        val fallback = if (viaCompose > 0) 0 else {
            val root = (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)?.window?.decorView
            androidx.core.view.ViewCompat.getRootWindowInsets(root ?: return@with 0.toDp())
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        (viaCompose + fallback).toDp()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        LoadStateBox(state = uiState.loadState, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = statusBarPad)
            ) {
                // 视频区: 高度由 uiState 驱动
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { uiState.videoHeightPx.toDp() })
                ) {
                    AndroidView(
                        factory = { fragment.videoView },
                        modifier = Modifier.fillMaxSize()
                    )
                    LiveSettingsPanel(fragment, uiState, Modifier.align(Alignment.CenterEnd))
                }
                LiveEpgBar(fragment, uiState)
                LiveChannelBrowser(fragment, uiState, Modifier.weight(1f))
            }
        }
    }
}

/** 右侧滑入 M3 Expressive 设置面板: 线路/比例/解码/超时/偏好 五组 */
@Composable
private fun LiveSettingsPanel(fragment: LiveFragment, uiState: LiveUiState, modifier: Modifier) {
    AnimatedVisibility(
        visible = uiState.settingVisible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            // 参数项
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(uiState.settingItems, key = { _, item -> item.itemIndex }) { _, item ->
                    val selected = item.itemIndex == uiState.settingItemSelectedIdx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { fragment.clickSettingItem(item.itemIndex) }
                    ) {
                        Text(
                            item.itemName,
                            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                        )
                    }
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            // 分组项
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(uiState.settingGroups, key = { _, group -> group.groupIndex }) { _, group ->
                    val selected = group.groupIndex == uiState.settingGroupIdx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { fragment.selectSettingGroup(group.groupIndex, false) }
                    ) {
                        Text(
                            group.groupName,
                            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 交互型 EPG 媒体卡片: LIVE 标志 + 频道名 + 投屏/全屏/设置入口 + 线路胶囊 */
@Composable
private fun LiveEpgBar(fragment: LiveFragment, uiState: LiveUiState) {
    val isDark = isAppDarkTheme()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // LIVE 红色高亮胶囊
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE53935)
                ) {
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    uiState.epgName,
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee()
                        .clickable { fragment.scrollChannelListToSelection() }
                )

                // 投屏
                IconButton(
                    onClick = { fragment.showCastDialog() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_cast),
                        contentDescription = "投屏",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 全屏
                IconButton(
                    onClick = {
                        try {
                            fragment.videoView.startFullScreen()
                        } catch (_: Throwable) {}
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_m3_fullscreen),
                        contentDescription = "全屏",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 设置
                IconButton(
                    onClick = { fragment.showSettingDialog(false) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_player_setting),
                        contentDescription = "设置",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 线路切换 Expressive Chips
            if (uiState.sourceNames.size > 0) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    itemsIndexed(uiState.sourceNames) { index, name ->
                        val selected = index == uiState.sourceIdx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) colorScheme.primaryContainer
                            else if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF1F1F4),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { fragment.switchingLine2Replay(index) }
                        ) {
                            Text(
                                name,
                                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 常驻频道浏览区: 左分组右频道双列 (Material Design 3 Expressive) */
@Composable
private fun LiveChannelBrowser(fragment: LiveFragment, uiState: LiveUiState, modifier: Modifier) {
    val isDark = isAppDarkTheme()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 8.dp)
        ) {
            // 分组列 (左侧 40% 宽度)
            LazyColumn(
                state = uiState.groupListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            ) {
                itemsIndexed(uiState.groups, key = { _, group -> group.groupIndex }) { _, group ->
                    val selected = group.groupIndex == uiState.selectedGroupIdx
                    val locked = group.groupPassword.isNotEmpty()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { fragment.selectChannelGroup(group.groupIndex, false, -1) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                group.groupName,
                                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (locked) {
                                Icon(
                                    painterResource(R.drawable.ic_lock),
                                    contentDescription = "已加密",
                                    tint = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 中间优雅细分隔线
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colorScheme.outlineVariant.copy(alpha = 0.35f))
            )

            // 频道列 (右侧 58% 宽度)
            LazyColumn(
                state = uiState.channelListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .padding(start = 6.dp)
            ) {
                itemsIndexed(uiState.channels, key = { _, channel -> channel.channelIndex }) { index, channel ->
                    val selected = index == uiState.selectedChannelIdx
                    val channelNumStr = if (channel.channelNum > 0) String.format("%02d", channel.channelNum) else "${index + 1}"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) colorScheme.primaryContainer.copy(alpha = if (isDark) 0.45f else 0.7f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { fragment.clickLiveChannel(index) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 频道序号徽标
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) colorScheme.primary
                                else if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF0F0F2)
                            ) {
                                Text(
                                    channelNumStr,
                                    color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                channel.channelName,
                                color = if (selected) colorScheme.primary else colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (selected) {
                                Icon(
                                    painterResource(R.drawable.ic_m3_play_arrow),
                                    contentDescription = "正在播放",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

