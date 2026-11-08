package com.github.tvbox.osc.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.tvbox.osc.bean.LiveChannelGroup
import com.github.tvbox.osc.bean.LiveChannelItem
import com.github.tvbox.osc.bean.LiveSettingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 直播页 Compose 渲染层状态: LiveFragment(Java, 业务逻辑)写入, LiveScreen(Compose)读取。
 * Fragment 视图重建时整体换新实例, 业务数据在 Fragment 字段里不丢。
 */
class LiveUiState {

    var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private set

    /** 三态(等价旧 LoadSir) */
    var loadState by mutableStateOf(LoadState.None)

    /** 频道分组列表 */
    var groups by mutableStateOf<List<LiveChannelGroup>>(emptyList())
    var selectedGroupIdx by mutableStateOf(-1)

    /** 当前分组的频道列表 */
    var channels by mutableStateOf<List<LiveChannelItem>>(emptyList())
    var selectedChannelIdx by mutableStateOf(-1)

    /** 右侧滑入设置面板 */
    var settingVisible by mutableStateOf(false)
    var settingGroupIdx by mutableStateOf(-1)
    var settingItems by mutableStateOf<List<LiveSettingItem>>(emptyList())
    var settingItemSelectedIdx by mutableStateOf(-1)
    var settingGroups by mutableStateOf<List<com.github.tvbox.osc.bean.LiveSettingGroup>>(emptyList())

    /** 频道号浮层 */
    var channelInfoVisible by mutableStateOf(false)
    var channelInfoText by mutableStateOf("")

    /** 视频区高度(px), 初始按屏宽 16:9, 起播后按实际比例微调 */
    var videoHeightPx by mutableStateOf(0)

    /** 底部 EPG 卡 */
    var epgName by mutableStateOf("")
    var epgNum by mutableStateOf("")
    var epgSource by mutableStateOf("源 1/1")

    var groupListState = LazyListState()
    var channelListState = LazyListState()

    /** 列表滚动到选中项(Java 侧 showChannelList 调用) */
    fun scrollToSelected(groupIdx: Int, channelIdx: Int, groupTop: Boolean) {
        scope.launch {
            if (groupTop) groupListState.scrollToItem(groupIdx)
            else groupListState.animateScrollToItem(groupIdx)
            if (channelIdx > -1) channelListState.animateScrollToItem(channelIdx)
        }
    }

    fun cancelScope() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
