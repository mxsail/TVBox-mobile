package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.ParseBean
import com.github.tvbox.osc.bean.VideoInfo
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.CacheManager
import com.github.tvbox.osc.cache.DownloadEpisode
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.constant.CacheConst
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.MyVideoView
import com.github.tvbox.osc.player.controller.LocalVideoController
import com.github.tvbox.osc.receiver.BatteryReceiver
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.PlayerHelper
import com.google.common.reflect.TypeToken
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import xyz.doikki.videoplayer.player.ProgressManager
import java.io.File

/**
 * 本地视频播放页(Compose):
 * - 竖屏 16:9 播放窗 + 下方选集网格 (与 DetailContent 统一 M3 卡片规范)
 * - 横屏沉浸全屏 (通过控制层全屏键触发), 状态栏/导航栏全沉浸
 * - 横屏选集抽屉 (AnimatedVisibility 右侧滑入面板替代旧版 XPopup)
 * - 离线播放进度双向同步与观看历史无缝回写
 */
class LocalPlayActivity : BaseActivity() {

    private var mVideoView: MyVideoView? = null
    private var mController: LocalVideoController? = null
    private var mVodPlayerCfg = JSONObject()
    private var mVideoList: List<VideoInfo> = emptyList()
    private val mBatteryReceiver = BatteryReceiver()
    private var mEpisode: DownloadEpisode? = null

    // UI 状态
    private var isFullScreen by mutableStateOf(false)
    private var currentPosition by mutableIntStateOf(0)
    private var showSeriesDrawer by mutableStateOf(false)
    private var videoTitle by mutableStateOf("")
    private var episodeList by mutableStateOf<List<VodInfo.VodSeries>>(emptyList())

    override fun getLayoutResID(): Int = -1

    override fun autoNavigationBarInset(): Boolean = false

    override fun keepBackIntercept(): Boolean = true

    override fun init() {
        registerReceiver(mBatteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val bundle = intent.extras
        val videoListJson = bundle?.getString("videoList")
        if (!videoListJson.isNullOrEmpty()) {
            mVideoList = GsonUtils.fromJson(
                videoListJson,
                object : TypeToken<List<VideoInfo>>() {}.type
            ) ?: emptyList()
        }
        currentPosition = bundle?.getInt("position", 0) ?: 0

        mVideoView = MyVideoView(this)
        initController()
        initPlayerCfg()
        mVideoView?.setVideoController(mController)

        setContent {
            TvBoxTheme {
                LocalPlayScreen()
            }
        }

        if (mVideoList.isNotEmpty() && currentPosition in mVideoList.indices) {
            play(false)
        } else {
            ToastUtils.showShort("没有可播放的本地视频")
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun refresh(event: RefreshEvent) {
        super.refresh(event)
        if (event.type == RefreshEvent.TYPE_BATTERY_CHANGE && mController?.mMyBatteryView != null) {
            mController?.mMyBatteryView?.updateBattery(event.obj as Int)
        }
    }

    private fun initController() {
        mController = LocalVideoController(this).apply {
            setListener(object : LocalVideoController.VodControlListener {
                override fun chooseSeries() {
                    // 全屏状态下唤起右侧选集抽屉
                    if (isFullScreen) {
                        showSeriesDrawer = true
                    }
                }

                override fun playNext(rmProgress: Boolean) {
                    if (currentPosition >= mVideoList.size - 1) {
                        ToastUtils.showShort("当前已经是最后一集了")
                    } else {
                        currentPosition++
                        play(true)
                    }
                }

                override fun playPre() {
                    if (currentPosition <= 0) {
                        ToastUtils.showShort("当前已经是第一集了")
                    } else {
                        currentPosition--
                        play(true)
                    }
                }

                override fun changeParse(pb: ParseBean?) {
                    // 本地文件无解析链路
                }

                override fun updatePlayerCfg() {
                    mVideoView?.let {
                        PlayerHelper.updateCfg(it, mVodPlayerCfg)
                        it.replay(true)
                    }
                }

                override fun replay(replay: Boolean) {
                    mVideoView?.replay(true)
                }

                override fun errReplay() {
                    mVideoView?.replay(true)
                }

                override fun selectSubtitle() {}

                override fun selectAudioTrack() {}

                override fun prepared() {}

                override fun toggleFullScreen() {
                    applyFullScreen(!isFullScreen)
                }

                override fun exit() {
                    if (isFullScreen) {
                        applyFullScreen(false)
                    } else {
                        finish()
                    }
                }
            })
        }
    }

    private fun initPlayerCfg() {
        mVodPlayerCfg = JSONObject()
        try {
            mVodPlayerCfg.put("pl", Hawk.get(HawkConfig.PLAY_TYPE, 1))
            mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0))
            mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"))
            mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0))
            mVodPlayerCfg.put("sp", 1.0f)
            mVodPlayerCfg.put("st", 0)
            mVodPlayerCfg.put("et", 0)
        } catch (_: Throwable) {
        }
        mController?.setPlayerConfig(mVodPlayerCfg)
    }

    private fun play(fromSkip: Boolean) {
        if (mVideoList.isEmpty() || currentPosition !in mVideoList.indices) return
        val videoInfo = mVideoList[currentPosition]
        val path = videoInfo.path

        val file = File(path)
        if (!file.exists()) {
            ToastUtils.showShort("视频文件不存在或已被移动: ${file.name}")
            finish()
            return
        }

        val uri = Uri.parse("file://${file.absolutePath}").toString()
        videoTitle = videoInfo.displayName ?: file.name
        mController?.setTitle(videoTitle)
        mVideoView?.setUrl(uri)

        // 与在线播放(PlayFragment)统一的进度键
        mEpisode = findDoneEpisodeByPath(path)
        val progressKey = mEpisode?.let {
            "${it.sourceKey}${it.vodId}${it.flag}${it.episodeIndex}${it.episodeName}"
        }

        mVideoView?.setProgressManager(object : ProgressManager() {
            override fun saveProgress(url: String?, progress: Long) {
                val duration = mVideoView?.duration ?: 0L
                SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP).put(path, duration)
                SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).put(path, progress)
                if (progressKey != null) {
                    CacheManager.save(MD5.string2MD5(progressKey), progress)
                }
            }

            override fun getSavedProgress(url: String?): Long {
                if (progressKey != null) {
                    val cache = CacheManager.getCache(MD5.string2MD5(progressKey))
                    if (cache is Number) {
                        return cache.toLong()
                    }
                }
                return SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).getLong(path)
            }
        })

        PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg)

        if (fromSkip) {
            mVideoView?.replay(true)
        } else {
            mVideoView?.start()
        }

        saveVodHistory(mEpisode)
        refreshSeriesList()
    }

    private fun saveVodHistory(episode: DownloadEpisode?) {
        if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false) || episode == null) return
        try {
            val vodInfo = VodInfo().apply {
                id = episode.vodId
                name = episode.vodName
                pic = episode.vodPic
                sourceKey = episode.sourceKey
                playFlag = episode.flag
                playIndex = episode.episodeIndex
                playNote = episode.episodeName ?: ""
            }
            RoomDataManger.insertVodRecord(episode.sourceKey, vodInfo)
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun findDoneEpisodeByPath(path: String?): DownloadEpisode? {
        if (path.isNullOrEmpty()) return null
        return try {
            RoomDataManger.getAllDownloadEpisodes()?.firstOrNull {
                it.status == DownloadEpisode.STATUS_DONE && path == it.localFilePath
            }
        } catch (th: Throwable) {
            th.printStackTrace()
            null
        }
    }

    private fun refreshSeriesList() {
        val currentPath = if (currentPosition in mVideoList.indices) mVideoList[currentPosition].path else null
        episodeList = mVideoList.map { local ->
            VodInfo.VodSeries(local.displayName, local.path).apply {
                selected = (local.path == currentPath)
            }
        }
    }

    private fun applyFullScreen(full: Boolean) {
        if (isFullScreen == full) return
        isFullScreen = full

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (full) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            showSeriesDrawer = false
        }
    }

    override fun onBackHandled() {
        if (showSeriesDrawer) {
            showSeriesDrawer = false
            return
        }
        if (isFullScreen) {
            applyFullScreen(false)
            return
        }
        if (mVideoView?.onBackPressed() != true) {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_REFRESH, ""))
    }

    override fun onPause() {
        super.onPause()
        mVideoView?.pause()
    }

    override fun onResume() {
        super.onResume()
        mVideoView?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(mBatteryReceiver)
        } catch (_: Throwable) {
        }
        mVideoView?.release()
        mVideoView = null
    }

    // ---------------- Compose 界面 ----------------

    @Composable
    private fun LocalPlayScreen() {
        val gridState = rememberLazyGridState()

        // 选集变化时平滑滚动定位
        LaunchedEffect(currentPosition) {
            if (currentPosition in episodeList.indices) {
                gridState.animateScrollToItem(currentPosition)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isFullScreen) Color.Black else MaterialTheme.colorScheme.background)
            ) {
                // 播放器区域：竖屏 16:9，全屏铺满整个界面
                Box(
                    modifier = if (isFullScreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }
                ) {
                    AndroidView(
                        factory = {
                            val parent = mVideoView?.parent as? ViewGroup
                            parent?.removeView(mVideoView)
                            mVideoView!!
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 竖屏下方选集区
                if (!isFullScreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 标题栏与集数
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = videoTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (episodeList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "共 ${episodeList.size} 集",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 选集网格 (4 列)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                bottom = WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding() + 16.dp
                            )
                        ) {
                            itemsIndexed(
                                items = episodeList,
                                key = { index, item -> "${item.url ?: item.name}_$index" }
                            ) { index, item ->
                                val isSelected = index == currentPosition
                                LocalEpisodeCard(
                                    name = item.name ?: "",
                                    isSelected = isSelected,
                                    onClick = {
                                        if (index != currentPosition) {
                                            currentPosition = index
                                            play(true)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 横屏全屏模式下的右侧抽屉
            AnimatedVisibility(
                visible = isFullScreen && showSeriesDrawer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左侧空白半透明遮罩，点击收起
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showSeriesDrawer = false
                            }
                    )

                    // 右侧选集面板
                    Surface(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "选集 (${episodeList.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { showSeriesDrawer = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close_24),
                                        contentDescription = "关闭",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val drawerGridState = rememberLazyGridState()
                            LaunchedEffect(showSeriesDrawer) {
                                if (showSeriesDrawer && currentPosition in episodeList.indices) {
                                    drawerGridState.scrollToItem(currentPosition)
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                state = drawerGridState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                itemsIndexed(
                                    items = episodeList,
                                    key = { index, item -> "drawer_${item.url ?: item.name}_$index" }
                                ) { index, item ->
                                    val isSelected = index == currentPosition
                                    LocalEpisodeCard(
                                        name = item.name ?: "",
                                        isSelected = isSelected,
                                        onClick = {
                                            if (index != currentPosition) {
                                                currentPosition = index
                                                play(true)
                                            }
                                            showSeriesDrawer = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LocalEpisodeCard(
        name: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
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
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
