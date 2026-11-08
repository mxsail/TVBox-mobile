package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 直播页 Compose 渲染层: 视频区(MyVideoView 内嵌, doikki 控制器仍为 View) +
 * 右侧滑入设置面板 + EPG 信息卡 + 常驻频道浏览区(分组/频道双列) + 频道号浮层。
 * 业务回调全部走 LiveFragment。
 */
@Composable
fun LiveScreen(fragment: LiveFragment, uiState: LiveUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        LoadStateBox(state = uiState.loadState, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 视频区: 高度由 uiState 驱动(初始 16:9, 起播后按实际比例)
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
            LiveChannelInfoOverlay(uiState, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** 右侧滑入设置面板: 线路/比例/解码/超时/偏好 五组, 双列结构同旧版 */
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
                .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .padding(vertical = 10.dp)
        ) {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(uiState.settingItems, key = { _, item -> item.itemIndex }) { _, item ->
                    val selected = item.itemIndex == uiState.settingItemSelectedIdx
                    Text(
                        item.itemName,
                        color = if (selected) colorScheme.primary else colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fragment.clickSettingItem(item.itemIndex) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colorScheme.outlineVariant)
            )
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(uiState.settingGroups, key = { _, group -> group.groupIndex }) { _, group ->
                    val selected = group.groupIndex == uiState.settingGroupIdx
                    Text(
                        group.groupName,
                        color = if (selected) colorScheme.primary else colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fragment.selectSettingGroup(group.groupIndex, false) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/** EPG 信息卡: 频道名/台号 + 线路切换 + 投屏/设置入口 */
@Composable
private fun LiveEpgBar(fragment: LiveFragment, uiState: LiveUiState) {
    Box(
        Modifier
            .padding(10.dp)
            .fillMaxWidth()
            .height(96.dp)
            .background(colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
    ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                uiState.epgName,
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .width(132.dp)
                    .basicMarquee()
                    .clickable { fragment.scrollChannelListToSelection() }
            )
            if (uiState.epgNum.isNotEmpty()) {
                Text(
                    uiState.epgNum,
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.icon_pre),
                contentDescription = "上一线路",
                tint = colorScheme.onSurface,
                modifier = Modifier
                    .size(30.dp)
                    .padding(4.dp)
                    .clickable { fragment.playPreSource() }
            )
            Text(
                uiState.epgSource,
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .clickable { fragment.playNextSource() }
            )
            Icon(
                painterResource(R.drawable.icon_pre),
                contentDescription = "下一线路",
                tint = colorScheme.onSurface,
                modifier = Modifier
                    .size(30.dp)
                    .padding(4.dp)
                    .clickable { fragment.playNextSource() }
            )
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 14.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_cast),
                contentDescription = "投屏",
                tint = colorScheme.onSurface,
                modifier = Modifier
                    .size(34.dp)
                    .padding(4.dp)
                    .clickable { fragment.showCastDialog() }
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                painterResource(R.drawable.ic_player_setting),
                contentDescription = "设置",
                tint = colorScheme.onSurface,
                modifier = Modifier
                    .size(34.dp)
                    .padding(4.dp)
                    .clickable { fragment.showSettingDialog(false) }
            )
        }
    }
}

/** 常驻频道浏览区: 左分组右频道双列 */
@Composable
private fun LiveChannelBrowser(fragment: LiveFragment, uiState: LiveUiState, modifier: Modifier) {
    Row(
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .background(colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
    ) {
        LazyColumn(
            state = uiState.groupListState,
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            itemsIndexed(uiState.groups, key = { _, group -> group.groupIndex }) { _, group ->
                val selected = group.groupIndex == uiState.selectedGroupIdx
                val locked = group.groupPassword.isNotEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { fragment.selectChannelGroup(group.groupIndex, false, -1) }
                        .background(
                            if (selected) colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.groupName,
                        color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (locked) {
                        Icon(
                            painterResource(R.drawable.ic_lock),
                            contentDescription = "已加密",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        LazyColumn(
            state = uiState.channelListState,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            itemsIndexed(uiState.channels, key = { _, channel -> channel.channelIndex }) { index, channel ->
                val selected = index == uiState.selectedChannelIdx
                Text(
                    channel.channelName,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fragment.clickLiveChannel(index) }
                        .background(
                            if (selected) colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                )
            }
        }
    }
}

/** 频道号浮层: 换台/换线路后显示 3 秒 */
@Composable
private fun LiveChannelInfoOverlay(uiState: LiveUiState, modifier: Modifier) {
    AnimatedVisibility(
        visible = uiState.channelInfoVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(top = 48.dp, end = 24.dp)
    ) {
        Text(
            uiState.channelInfoText,
            color = colorScheme.onSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
