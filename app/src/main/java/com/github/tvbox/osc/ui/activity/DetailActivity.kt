package com.github.tvbox.osc.ui.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.NotificationUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.CastVideo
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.DownloadEpisode
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodSnapshotStore
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.download.DownloadTaskManager
import com.github.tvbox.osc.event.DownloadEvent
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.VodPlayerManager
import com.github.tvbox.osc.receiver.BatteryReceiver
import com.github.tvbox.osc.service.PlayService
import com.github.tvbox.osc.ui.adapter.ParseAdapter
import com.github.tvbox.osc.ui.adapter.SeriesAdapter
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter
import com.github.tvbox.osc.ui.compose.DetailScreen
import com.github.tvbox.osc.ui.compose.DetailScreenCallbacks
import com.github.tvbox.osc.ui.compose.DetailUiState
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.AllVodSeriesRightDialog
import com.github.tvbox.osc.ui.dialog.CacheSelectDialog
import com.github.tvbox.osc.ui.dialog.CastListDialog
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog
import com.github.tvbox.osc.ui.dialog.VideoDetailDialog
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ScreenShotListenManager
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.util.Utils
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.enums.PopupPosition
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 播放详情页(纯 Compose 宿主):
 * - 废除旧版 activity_detail.xml 与 PlayFragment
 * - 播放内核由 VodPlayerManager 承载, 竖屏 16:9 / 横屏全屏内嵌渲染
 * - 下半部全量由 DetailContent (Compose) 驱动, 支持选集、换源、下载、收藏及半屏全集
 */
class DetailActivity : BaseActivity() {

    private var vodPlayerManager: VodPlayerManager? = null
    private lateinit var sourceViewModel: SourceViewModel
    private var mVideo: Movie.Video? = null
    var vodInfo: VodInfo? = null
    private var previewVodInfo: VodInfo? = null
    @JvmField
    var vodId: String = ""
    @JvmField
    var sourceKey: String = ""
    private var isReverse = false
    private var preFlag = ""
    private var mCheckSources: HashMap<String, String>? = null
    private val mBatteryReceiver = BatteryReceiver()

    @JvmField
    var seriesFlagAdapter: SeriesFlagAdapter = SeriesFlagAdapter()
    @JvmField
    var seriesAdapter: SeriesAdapter = SeriesAdapter(false)

    private var mAllSeriesRightDialog: BasePopupView? = null
    private var mRemoteActionReceiver: BroadcastReceiver? = null
    private var screenShotListenManager: ScreenShotListenManager? = null

    // Compose 状态
    private var uiState by mutableStateOf(DetailUiState())
    private var fullWindows by mutableStateOf(false)
    private var showPreview = true
    private var playbackStarted = false
    private var autoJumpDone = false
    private var jumpPending = false
    private var autoPlayFromCache = false
    private var downloadEpisodeKey: String? = null
    private var hydratedFromSnapshot = false
    private var isCurrentlyInPip by mutableStateOf(false)
    private var wasFullscreenBeforePip = false
    private val pendingDownloadTasks = ArrayList<DownloadEpisode>()

    private var searchTitle = ""
    private var hadQuickStart = false
    private val quickSearchData = ArrayList<Movie.Video>()
    private val quickSearchWord = ArrayList<String>()
    private var searchExecutorService: ExecutorService? = null
    private var pauseRunnable: List<Runnable>? = null

    override fun autoNavigationBarInset(): Boolean = false
    override fun keepBackIntercept(): Boolean = true
    override fun getLayoutResID(): Int = -1

    override fun init() {
        PlayService.stop()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(android.graphics.Color.BLACK)
        initView()
        initViewModel()
        initData()
        registerReceiver(mBatteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        applySystemBarStyle()
        toggleScreenShotListen(true)
    }

    private fun applySystemBarStyle() {
        ImmersionBar.with(this)
            .transparentStatusBar()
            .statusBarDarkFont(!Utils.isDarkTheme())
            .navigationBarColor(android.R.color.transparent)
            .fitsSystemWindows(true)
            .init()
    }

    override fun onResume() {
        super.onResume()
        vodPlayerManager?.onResume()
        if (vodPlayerManager?.controller != null &&
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
        ) {
            vodPlayerManager?.controller?.setInPipMode(false)
        }
        updatePipParams()
    }

    override fun onPause() {
        super.onPause()
        vodPlayerManager?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        vodPlayerManager?.onDestroy()
        vodPlayerManager = null
        try {
            unregisterReceiver(mBatteryReceiver)
        } catch (_: Throwable) {
        }
        registerActionReceiver(false)
        toggleScreenShotListen(false)
        try {
            searchExecutorService?.shutdownNow()
            searchExecutorService = null
        } catch (_: Throwable) {
        }
    }

    private fun initView() {
        val bundle = intent.extras
        if (bundle != null) {
            vodId = bundle.getString("id", "") ?: ""
            sourceKey = bundle.getString("sourceKey", "") ?: ""
            autoPlayFromCache = bundle.getBoolean("autoPlayFromCache", false)
            downloadEpisodeKey = bundle.getString("downloadEpisodeKey", null)
            val title = bundle.getString("title", "") ?: ""
            uiState = uiState.copy(title = title)
        }

        seriesAdapter = SeriesAdapter(false)
        seriesFlagAdapter = SeriesFlagAdapter()
        isReverse = false
        preFlag = ""

        seriesFlagAdapter.setOnItemClickListener { _, _, position ->
            chooseFlag(position)
        }
        seriesAdapter.setOnItemClickListener { _, _, position ->
            chooseSeries(position, false)
        }

        showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true)

        if (showPreview) {
            vodPlayerManager = VodPlayerManager().apply {
                init(this@DetailActivity, object : VodPlayerManager.Callback {
                    override fun onPlaybackStarted() {
                        this@DetailActivity.onPlaybackStarted()
                    }

                    override fun updatePipParams() {
                        this@DetailActivity.updatePipParams()
                    }

                    override fun chooseSeriesDialog() {
                        showAllSeriesDialog()
                    }

                    override fun toggleFullPreview() {
                        this@DetailActivity.toggleFullPreview()
                    }

                    override fun showCastDialog() {
                        this@DetailActivity.showCastDialog()
                    }

                    override fun enterPip() {
                        this@DetailActivity.enterPip()
                    }

                    override fun showParseRoot(show: Boolean, adapter: ParseAdapter?) {
                    }
                })
            }
        }

        setContent {
            TvBoxTheme {
                DetailActivityScreen()
            }
        }
    }

    @Composable
    private fun DetailActivityScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            if (isCurrentlyInPip) {
                // 画中画模式：仅保留纯播放器视口且填满全屏，彻底剔除任何状态栏留白与下部详情布局
                if (vodPlayerManager != null) {
                    AndroidView(
                        factory = {
                            val p = vodPlayerManager?.playerView?.parent as? ViewGroup
                            p?.removeView(vodPlayerManager?.playerView)
                            vodPlayerManager!!.playerView.apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (fullWindows) androidx.compose.ui.graphics.Color.Black
                            else MaterialTheme.colorScheme.background
                        )
                ) {
                    // 顶部播放器：竖屏 16:9，全屏铺满
                    if (showPreview && vodPlayerManager != null) {
                        Box(
                            modifier = if (fullWindows) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            }
                        ) {
                            AndroidView(
                                factory = {
                                    val p = vodPlayerManager?.playerView?.parent as? ViewGroup
                                    p?.removeView(vodPlayerManager?.playerView)
                                    vodPlayerManager!!.playerView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 竖屏下半部详情选集
                    if (!fullWindows) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            DetailScreen(
                            state = uiState,
                            callbacks = object : DetailScreenCallbacks {
                                override fun onDetailClick() {
                                    vodInfo?.let {
                                        XPopup.Builder(this@DetailActivity)
                                            .hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                                            .isViewMode(true)
                                            .asCustom(VideoDetailDialog(this@DetailActivity, it))
                                            .show()
                                    }
                                }

                                override fun onChangeSourceClick() {
                                    startQuickSearch()
                                    val quickSearchDialog = QuickSearchDialog(this@DetailActivity)
                                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData))
                                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord))
                                    quickSearchDialog.show()
                                    if (!pauseRunnable.isNullOrEmpty()) {
                                        searchExecutorService = Executors.newFixedThreadPool(5)
                                        pauseRunnable?.forEach { searchExecutorService?.execute(it) }
                                        pauseRunnable = null
                                    }
                                    quickSearchDialog.setOnDismissListener {
                                        try {
                                            searchExecutorService?.shutdownNow()
                                            searchExecutorService = null
                                        } catch (th: Throwable) {
                                            th.printStackTrace()
                                        }
                                    }
                                }

                                override fun onDownloadClick() {
                                    showCacheSelectDialog()
                                }

                                override fun onCollectClick() {
                                    val info = vodInfo ?: return
                                    val isCollected = RoomDataManger.isVodCollect(sourceKey, vodId)
                                    if (isCollected) {
                                        RoomDataManger.deleteVodCollect(sourceKey, info)
                                        Toast.makeText(this@DetailActivity, "已移除收藏夹", Toast.LENGTH_SHORT).show()
                                        uiState = uiState.copy(isCollected = false)
                                    } else {
                                        RoomDataManger.insertVodCollect(sourceKey, info)
                                        Toast.makeText(this@DetailActivity, "已加入收藏夹", Toast.LENGTH_SHORT).show()
                                        uiState = uiState.copy(isCollected = true)
                                    }
                                }

                                override fun onPrivateBrowsingClick() {
                                    ToastUtils.showShort("当前为无痕浏览")
                                }

                                override fun onLineSelected(position: Int) {
                                    chooseFlag(position)
                                }

                                override fun onEpisodeClick(position: Int) {
                                    chooseSeries(position, false)
                                }

                                override fun onToggleSort() {
                                    sortSeries()
                                }

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
        }
    }
}

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel.detailResult.observe(this) { absXml ->
            if (absXml?.movie?.videoList != null && absXml.movie.videoList.isNotEmpty()) {
                mVideo = absXml.movie.videoList[0]
                vodInfo = VodInfo().apply {
                    setVideo(mVideo)
                    this.sourceKey = mVideo!!.sourceKey
                }
                VodSnapshotStore.save(vodInfo)
                applySeriesData()
            } else {
                if (!hydratedFromSnapshot) {
                    uiState = uiState.copy(hasEmptyPlaylist = true)
                }
            }
        }
    }

    private fun applySeriesData() {
        val info = vodInfo ?: return
        if (info.seriesMap == null) return
        renderHeader()

        if (info.seriesMap.isNotEmpty()) {
            uiState = uiState.copy(hasEmptyPlaylist = false)
            val vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId)
            if (vodInfoRecord != null) {
                info.playIndex = Math.max(vodInfoRecord.playIndex, 0)
                info.playFlag = vodInfoRecord.playFlag
                info.playerCfg = vodInfoRecord.playerCfg
                info.reverseSort = vodInfoRecord.reverseSort
            } else {
                info.playIndex = 0
                info.playFlag = null
                info.playerCfg = ""
                info.reverseSort = false
            }

            if (info.reverseSort) {
                info.reverse()
            }

            if (info.playFlag == null || !info.seriesMap.containsKey(info.playFlag)) {
                info.playFlag = info.seriesMap.keys.firstOrNull()
            }

            updateFlagSelection()
            refreshList()

            if (autoPlayFromCache) {
                handleAutoPlayFromCache()
            } else if (showPreview && !autoJumpDone) {
                jumpWhenPlayerReady()
            }
        } else {
            uiState = uiState.copy(hasEmptyPlaylist = true)
        }
    }

    private fun updateFlagSelection() {
        val flags = vodInfo?.seriesFlags ?: return
        var selectedIdx = 0
        for (j in flags.indices) {
            val flag = flags[j]
            flag.selected = (flag.name == vodInfo?.playFlag)
            if (flag.selected) selectedIdx = j
        }
        seriesFlagAdapter.setNewData(flags)
        uiState = uiState.copy(
            lines = ArrayList(flags),
            currentLineIndex = selectedIdx,
            version = uiState.version + 1
        )
    }

    private fun renderHeader() {
        val info = vodInfo ?: return
        val currentSource = ApiConfig.get().getSource(info.sourceKey)
        val sourceName = currentSource?.name ?: "默认源"

        val metadataParts = ArrayList<String>()
        if (info.year > 0) metadataParts.add("年份: ${info.year}")
        if (!info.area.isNullOrEmpty()) metadataParts.add("地区: ${info.area.trim()}")
        if (!info.type.isNullOrEmpty()) metadataParts.add("类型: ${info.type.trim()}")
        if (!info.lang.isNullOrEmpty()) metadataParts.add("语言: ${info.lang.trim()}")
        val metaSummary = metadataParts.joinToString("   ")

        val isCollected = RoomDataManger.isVodCollect(sourceKey, vodId)

        uiState = uiState.copy(
            title = if (info.name.isNullOrEmpty()) "暂无信息" else info.name,
            sourceName = sourceName,
            subInfo = metaSummary,
            isCollected = isCollected,
            isPrivateBrowsing = Hawk.get(HawkConfig.PRIVATE_BROWSING, false)
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshList() {
        val info = vodInfo ?: return
        val series = info.seriesMap?.get(info.playFlag) ?: emptyList()
        val seriesSize = series.size
        if (seriesSize > 0 && seriesSize <= info.playIndex) {
            info.playIndex = seriesSize - 1
        }
        val currentIdx = info.playIndex

        for (j in series.indices) {
            series[j].selected = (j == currentIdx)
        }

        seriesAdapter.setNewData(series)
        seriesAdapter.setCachedUrls(getCachedUrls())
        seriesAdapter.notifyDataSetChanged()

        uiState = uiState.copy(
            episodes = ArrayList(series),
            currentEpisodeIndex = currentIdx,
            isReverseSort = info.reverseSort,
            cachedUrls = getCachedUrls(),
            hasEmptyPlaylist = series.isEmpty(),
            version = uiState.version + 1
        )
    }

    private fun handleAutoPlayFromCache() {
        val info = vodInfo ?: return
        val targetEpisodeKey = downloadEpisodeKey ?: return
        autoPlayFromCache = false

        val episodes = info.seriesMap?.get(info.playFlag)
        if (episodes != null) {
            for (i in episodes.indices) {
                val ep = episodes[i]
                val key = ep.url ?: ep.name
                if (targetEpisodeKey == key) {
                    chooseSeries(i, false)
                    return
                }
            }
        }
        jumpWhenPlayerReady()
    }

    private fun jumpWhenPlayerReady() {
        if (autoJumpDone || jumpPending) return
        if (isFinishing || isDestroyed) return
        autoJumpDone = true
        jumpToPlay()
    }

    private fun jumpToPlay() {
        val info = vodInfo ?: return
        val seriesList = info.seriesMap?.get(info.playFlag)
        if (seriesList.isNullOrEmpty() || info.playIndex !in seriesList.indices) return

        preFlag = info.playFlag ?: ""
        val bundle = Bundle().apply {
            putString("sourceKey", sourceKey)
        }
        insertVod(sourceKey, info)
        App.getInstance().setVodInfo(info)

        if (previewVodInfo == null) {
            try {
                val bos = ByteArrayOutputStream()
                val oos = ObjectOutputStream(bos)
                oos.writeObject(info)
                oos.flush()
                oos.close()
                val ois = ObjectInputStream(ByteArrayInputStream(bos.toByteArray()))
                previewVodInfo = ois.readObject() as? VodInfo
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (previewVodInfo != null) {
            previewVodInfo?.playerCfg = info.playerCfg
            previewVodInfo?.playFlag = info.playFlag
            previewVodInfo?.playIndex = info.playIndex
            previewVodInfo?.seriesMap = info.seriesMap
            App.getInstance().setVodInfo(previewVodInfo)
        }

        vodPlayerManager?.setData(bundle)
    }

    private fun chooseFlag(position: Int) {
        val info = vodInfo ?: return
        val flags = info.seriesFlags ?: return
        if (position !in flags.indices) return

        val newFlag = flags[position].name
        if (newFlag == info.playFlag) return

        for (i in flags.indices) {
            val f = flags[i]
            if (f.name == info.playFlag) {
                f.selected = false
                seriesFlagAdapter.notifyItemChanged(i)
                break
            }
        }

        info.playFlag = newFlag
        seriesFlagAdapter.notifyItemChanged(position)
        refreshList()

        updateFlagSelection()
        chooseSeries(info.playIndex, true)
    }

    private fun chooseSeries(position: Int, reloadWithChangeLine: Boolean) {
        val info = vodInfo ?: return
        val seriesList = info.seriesMap?.get(info.playFlag) ?: return
        if (position !in seriesList.indices) return

        var reload = false
        for (j in seriesList.indices) {
            seriesList[j].selected = false
            seriesAdapter.notifyItemChanged(j)
        }

        if (info.playIndex != position) {
            seriesList[position].selected = true
            seriesAdapter.notifyItemChanged(position)
            info.playIndex = position
            reload = true
        }

        if (preFlag.isNotEmpty() && info.playFlag != preFlag) {
            reload = true
        }

        seriesList[info.playIndex].selected = true
        seriesAdapter.notifyItemChanged(info.playIndex)
        refreshList()

        if (!showPreview || reload || reloadWithChangeLine) {
            jumpToPlay()
        }
    }

    fun sortSeries() {
        val info = vodInfo ?: return
        val seriesList = info.seriesMap?.get(info.playFlag)
        if (!seriesList.isNullOrEmpty()) {
            info.reverseSort = !info.reverseSort
            info.reverse()
            info.playIndex = (seriesList.size - 1) - info.playIndex
            refreshList()
        }
    }

    fun toggleFullPreview() {
        fullWindows = !fullWindows
        vodPlayerManager?.changedLandscape(fullWindows)
        if (fullWindows) {
            ImmersionBar.with(this)
                .hideBar(BarHide.FLAG_HIDE_BAR)
                .init()
        } else {
            applySystemBarStyle()
        }
    }

    fun showAllSeriesDialog() {
        val info = vodInfo ?: return
        val seriesList = info.seriesMap?.get(info.playFlag) ?: return
        if (fullWindows) {
            mAllSeriesRightDialog = XPopup.Builder(this)
                .hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                .isViewMode(true)
                .popupHeight(ScreenUtils.getScreenHeight())
                .popupPosition(PopupPosition.Right)
                .enableDrag(false)
                .asCustom(AllVodSeriesRightDialog(this))
            mAllSeriesRightDialog?.show()
        } else {
            uiState = uiState.copy(showAllEpisodesSheet = true)
        }
    }

    fun showCastDialog() {
        val info = vodInfo ?: return
        val seriesList = info.seriesMap?.get(info.playFlag) ?: return
        if (info.playIndex !in seriesList.indices) return
        val currentSeries = seriesList[info.playIndex]

        val url = if (vodPlayerManager?.finalUrl.isNullOrEmpty()) currentSeries.url else vodPlayerManager?.finalUrl
        val castVideo = CastVideo(currentSeries.name, url ?: "")
        XPopup.Builder(this)
            .hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
            .maxWidth(ConvertUtils.dp2px(360f))
            .asCustom(CastListDialog(this, castVideo))
            .show()
    }

    fun showCacheSelectDialog() {
        val info = vodInfo ?: return
        val seriesList = info.seriesMap?.get(info.playFlag)
        if (seriesList.isNullOrEmpty()) {
            ToastUtils.showShort("资源异常,请稍后重试")
            return
        }
        val cachedUrls = getCachedUrls()
        XPopup.Builder(this)
            .hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
            .isViewMode(true)
            .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
            .asCustom(CacheSelectDialog(this, info, cachedUrls))
            .show()
    }

    private fun getCachedUrls(): HashSet<String> {
        val result = HashSet<String>()
        val info = vodInfo ?: return result
        try {
            val episodes = RoomDataManger.getDownloadEpisodes(sourceKey, info.id, info.playFlag)
            if (episodes != null) {
                for (ep in episodes) {
                    if (ep.status != DownloadEpisode.STATUS_FAILED && ep.rawUrl != null) {
                        result.add(ep.rawUrl)
                    }
                }
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        return result
    }

    fun onPlaybackStarted() {
        playbackStarted = true
        updatePipParams()
        playServerSwitch(true)
        if (pendingDownloadTasks.isNotEmpty()) {
            val tasks = ArrayList(pendingDownloadTasks)
            pendingDownloadTasks.clear()
            DownloadTaskManager.get().enqueue(tasks)
        }
    }

    fun updatePipParams() {
        if (!Utils.supportsPiPMode()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams()
                if (params != null) {
                    setPictureInPictureParams(params)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateVideoContentRect(): Rect? {
        val renderView = vodPlayerManager?.player?.renderView
        if (renderView != null && renderView.isShown) {
            val renderRect = Rect()
            if (renderView.getGlobalVisibleRect(renderRect) && !renderRect.isEmpty) {
                return renderRect
            }
        }
        val playerView = vodPlayerManager?.playerView
        if (playerView != null && playerView.isShown) {
            val viewRect = Rect()
            if (playerView.getGlobalVisibleRect(viewRect) && !viewRect.isEmpty) {
                return viewRect
            }
        }
        return null
    }

    private fun buildPipParams(): PictureInPictureParams? {
        if (!Utils.supportsPiPMode()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
            val size = vodPlayerManager?.player?.videoSize
            if (size != null && size.size >= 2 && size[0] > 0 && size[1] > 0) {
                val rational = Rational(size[0], size[1])
                val safeRational = when {
                    rational.toFloat() > 2.39f -> Rational(239, 100)
                    rational.toFloat() < 1f / 2.39f -> Rational(100, 239)
                    else -> rational
                }
                builder.setAspectRatio(safeRational)
            } else {
                builder.setAspectRatio(Rational(16, 9))
            }

            val videoRect = calculateVideoContentRect()
            if (videoRect != null && !videoRect.isEmpty) {
                builder.setSourceRectHint(videoRect)
            }

            val isPlaying = vodPlayerManager?.player?.isPlaying ?: false
            val actions = ArrayList<RemoteAction>()
            actions.add(generateRemoteAction(R.drawable.ic_notify_pre, IntentKey.BROADCAST_ACTION_PREV, "上一集", "Play Previous"))
            val playIcon = if (isPlaying) R.drawable.ic_notify_pause else R.drawable.ic_notify_play
            actions.add(generateRemoteAction(playIcon, IntentKey.BROADCAST_ACTION_PLAYPAUSE, if (isPlaying) "暂停" else "播放", "Play/Pause"))
            actions.add(generateRemoteAction(R.drawable.ic_notify_next, IntentKey.BROADCAST_ACTION_NEXT, "下一集", "Play Next"))
            builder.setActions(actions)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bgType = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0)
                builder.setAutoEnterEnabled(bgType == 2 && isPlaying)
            }
            return builder.build()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun generateRemoteAction(iconResId: Int, actionCode: Int, title: String, desc: String): RemoteAction {
        val actionIntent = Intent(IntentKey.BROADCAST_ACTION).apply {
            putExtra("action", actionCode)
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            actionCode,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(this, iconResId)
        return RemoteAction(icon, title, desc, pendingIntent)
    }

    fun enterPip() {
        if (!Utils.supportsPiPMode()) return
        prepareForPip()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams()
                if (params != null) {
                    enterPictureInPictureMode(params)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun prepareForPip() {
        isCurrentlyInPip = true
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(android.graphics.Color.BLACK)
        mAllSeriesRightDialog?.dismiss()
        uiState = uiState.copy(showAllEpisodesSheet = false)
        vodPlayerManager?.hideAllDialogSuccess()
        vodPlayerManager?.controller?.setInPipMode(true)
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (pipState.isTransitioningToPip) {
                prepareForPip()
            }
        }
    }

    private fun registerActionReceiver(isRegister: Boolean) {
        if (isRegister) {
            if (mRemoteActionReceiver != null) return
            mRemoteActionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || IntentKey.BROADCAST_ACTION != intent.action || vodPlayerManager == null || vodPlayerManager?.controller == null) {
                        return
                    }
                    val currentStatus = intent.getIntExtra("action", 1)
                    when (currentStatus) {
                        IntentKey.BROADCAST_ACTION_PREV -> vodPlayerManager?.playPrevious()
                        IntentKey.BROADCAST_ACTION_PLAYPAUSE -> {
                            vodPlayerManager?.controller?.togglePlay()
                            updatePipParams()
                        }
                        IntentKey.BROADCAST_ACTION_NEXT -> vodPlayerManager?.playNext(false)
                        IntentKey.BROADCAST_ACTION_SEEK -> {
                            vodPlayerManager?.player?.seekTo(intent.getLongExtra(IntentKey.BROADCAST_ACTION_POSITION, 0))
                        }
                        IntentKey.BROADCAST_ACTION_CLOSE -> {
                            playServerSwitch(false)
                            finish()
                            NotificationUtils.cancelAll()
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                this, mRemoteActionReceiver!!,
                IntentFilter(IntentKey.BROADCAST_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            if (mRemoteActionReceiver != null) {
                try {
                    unregisterReceiver(mRemoteActionReceiver)
                } catch (_: Exception) {
                }
                mRemoteActionReceiver = null
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        handlePipModeChanged(isInPictureInPictureMode)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        handlePipModeChanged(isInPictureInPictureMode)
    }

    private fun handlePipModeChanged(isInPip: Boolean) {
        this.isCurrentlyInPip = isInPip
        if (isInPip) {
            wasFullscreenBeforePip = fullWindows
            prepareForPip()
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
            ImmersionBar.with(this)
                .hideBar(BarHide.FLAG_HIDE_BAR)
                .fitsSystemWindows(false)
                .init()
            val content = findViewById<android.view.View>(android.R.id.content)
            content?.setBackgroundColor(android.graphics.Color.BLACK)
            content?.setPadding(0, 0, 0, 0)
            registerActionReceiver(true)
        } else {
            vodPlayerManager?.controller?.setInPipMode(false)
            registerActionReceiver(false)
            if (wasFullscreenBeforePip) {
                fullWindows = true
                vodPlayerManager?.changedLandscape(true)
                ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_BAR).init()
            } else {
                fullWindows = false
                vodPlayerManager?.changedLandscape(false)
                applySystemBarStyle()
            }
            updatePipParams()
        }
    }

    private fun playServerSwitch(open: Boolean) {
        if (open) {
            val info = vodInfo ?: return
            val vodList = info.seriesMap?.get(info.playFlag) ?: return
            if (info.playIndex !in vodList.indices) return
            val vod = vodList[info.playIndex]
            val player = vodPlayerManager?.player ?: return
            PlayService.start(player, "${info.name}&&${vod.name}&&${info.pic ?: ""}")
            registerActionReceiver(true)
        } else {
            registerActionReceiver(false)
            PlayService.stop()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = vodPlayerManager?.player ?: return
        val isPlaying = player.isPlaying
        val bgType = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0)
        if (bgType == 2 && isPlaying) {
            prepareForPip()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
                enterPip()
            }
        } else if (bgType == 1 && isPlaying) {
            playServerSwitch(true)
        }
    }

    private fun insertVod(sourceKey: String, vodInfo: VodInfo) {
        if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) return
        try {
            vodInfo.playNote = vodInfo.seriesMap?.get(vodInfo.playFlag)?.get(vodInfo.playIndex)?.name ?: ""
        } catch (_: Throwable) {
            vodInfo.playNote = ""
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo)
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
    }

    private fun initData() {
        if (!tryHydrateFromSnapshot()) {
            loadDetail(vodId, sourceKey)
        } else {
            loadDetail(vodId, sourceKey)
        }
    }

    private fun loadDetail(vid: String, key: String) {
        sourceViewModel.getDetail(key, vid)
    }

    private fun tryHydrateFromSnapshot(): Boolean {
        val snapshot = VodSnapshotStore.load(sourceKey, vodId) ?: return false
        vodInfo = snapshot
        hydratedFromSnapshot = true
        applySeriesData()
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        if (event.type == DownloadEvent.TYPE_PROGRESS) {
            return
        }
        refreshList()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun refresh(event: RefreshEvent) {
        super.refresh(event)
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                val info = vodInfo ?: return
                if (event.obj is Int) {
                    val index = event.obj as Int
                    val list = info.seriesMap?.get(info.playFlag) ?: return
                    for (j in list.indices) {
                        seriesAdapter.data[j].selected = false
                        seriesAdapter.notifyItemChanged(j)
                    }
                    seriesAdapter.data[index].selected = true
                    seriesAdapter.notifyItemChanged(index)
                    info.playIndex = index
                    insertVod(sourceKey, info)
                    refreshList()
                } else if (event.obj is JSONObject) {
                    info.playerCfg = (event.obj as JSONObject).toString()
                    insertVod(sourceKey, info)
                }
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                val video = event.obj as Movie.Video
                loadDetail(video.id, video.sourceKey)
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            if (event.obj != null) {
                val word = event.obj as String
                switchSearchWord(word)
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(if (event.obj == null) null else event.obj as AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        }
    }

    private fun switchSearchWord(word: String) {
        OkGo.getInstance().cancelTag("quick_search")
        quickSearchData.clear()
        searchTitle = word
        searchResult()
    }

    private fun initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch()
    }

    private fun startQuickSearch() {
        initCheckedSourcesForSearch()
        if (hadQuickStart) return
        hadQuickStart = true
        OkGo.getInstance().cancelTag("quick_search")
        quickSearchWord.clear()
        searchTitle = if (mVideo != null && mVideo?.name != null) mVideo!!.name
        else (if (vodInfo != null && vodInfo?.name != null) vodInfo!!.name else "")
        quickSearchData.clear()
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle))

        OkGo.get<String>("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
            .tag("fenci")
            .execute(object : AbsCallback<String>() {
                override fun onSuccess(response: Response<String>?) {
                    val json = response?.body() ?: return
                    try {
                        for (je in Gson().fromJson(json, JsonArray::class.java)) {
                            quickSearchWord.add(je.asJsonObject.get("t").asString)
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                    val words = ArrayList(HashSet(quickSearchWord))
                    EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words))
                }

                override fun onError(response: Response<String>?) {
                    super.onError(response)
                }

                override fun convertResponse(response: okhttp3.Response?): String {
                    return response?.body?.string() ?: ""
                }
            })

        searchResult()
    }

    private fun searchResult() {
        try {
            searchExecutorService?.shutdownNow()
            searchExecutorService = null
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        searchExecutorService = Executors.newFixedThreadPool(5)
        val searchRequestList = ArrayList<SourceBean>()
        searchRequestList.addAll(ApiConfig.get().sourceBeanList)
        val home = ApiConfig.get().homeSourceBean
        searchRequestList.remove(home)
        searchRequestList.add(0, home)

        val siteKey = ArrayList<String>()
        for (bean in searchRequestList) {
            if (!bean.isSearchable || !bean.isQuickSearch) continue
            if (mCheckSources != null && !mCheckSources!!.containsKey(bean.key)) continue
            siteKey.add(bean.key)
        }
        for (key in siteKey) {
            searchExecutorService?.execute {
                sourceViewModel.getQuickSearch(key, searchTitle)
            }
        }
    }

    private fun searchData(absXml: AbsXml?) {
        if (absXml?.movie?.videoList != null && absXml.movie.videoList.isNotEmpty()) {
            val data = ArrayList<Movie.Video>()
            for (video in absXml.movie.videoList) {
                if (video.sourceKey == sourceKey && video.id == vodId) continue
                data.add(video)
            }
            quickSearchData.addAll(data)
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data))
        }
    }

    private fun toggleScreenShotListen(open: Boolean) {
        if (open) {
            if (screenShotListenManager == null) {
                screenShotListenManager = ScreenShotListenManager.newInstance(this).apply {
                    setListener { _ -> }
                    startListen()
                }
            }
        } else {
            screenShotListenManager?.stopListen()
            screenShotListenManager = null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vodPlayerManager?.dispatchKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackHandled() {
        if (mAllSeriesRightDialog?.isShow == true) {
            mAllSeriesRightDialog?.dismiss()
            return
        }
        if (uiState.showAllEpisodesSheet) {
            uiState = uiState.copy(showAllEpisodesSheet = false)
            return
        }
        if (vodPlayerManager?.hideAllDialogSuccess() == true) {
            return
        }
        if (fullWindows) {
            toggleFullPreview()
            return
        }
        if (vodPlayerManager?.player?.onBackPressed() != true) {
            finish()
        }
    }

    fun queueDownloadAfterPlay(tasks: List<DownloadEpisode>) {
        if (playbackStarted || vodPlayerManager == null) {
            DownloadTaskManager.get().enqueue(tasks)
            refreshList()
            ToastUtils.showShort("已加入下载队列，可在通知栏查看进度")
            return
        }
        pendingDownloadTasks.addAll(tasks)
        ToastUtils.showShort(String.format("开始播放后将自动开始下载(%d集)", tasks.size))
        if (vodPlayerManager?.isPlayerIdleOrError == true) {
            jumpToPlay()
        }
    }

    fun use1DMDownload() {
        val info = vodInfo ?: return
        val series = info.seriesMap?.get(info.playFlag) ?: return
        if (info.playIndex !in series.indices) return
        val vod = series[info.playIndex]
        val url = if (vodPlayerManager?.finalUrl.isNullOrEmpty()) vod.url else vodPlayerManager?.finalUrl
        if (url.isNullOrEmpty()) return

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setDataAndType(Uri.parse(url), "video/mp4")
            putExtra("title", "${info.name} ${vod.name}")
            setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.Downloader")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pm = packageManager
        val activities = pm.queryIntentActivities(intent, 0)
        if (activities.isNotEmpty()) {
            startActivity(intent)
        } else {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("请先安装1DM+下载管理器")
                .setMessage("检测到未安装1DM+下载器，是否前往下载？")
                .setPositiveButton("前往下载") { _, _ ->
                    try {
                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=idm.internet.download.manager.plus"))
                        startActivity(marketIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    fun getCurrentVodUrl(): String {
        return vodPlayerManager?.finalUrl ?: ""
    }

    fun quickLineChange() {
        val flags = seriesFlagAdapter.data
        if (flags.size > 1) {
            var currentIndex = 0
            for (i in flags.indices) {
                if (flags[i].selected) {
                    currentIndex = i
                }
            }
            currentIndex += 1
            if (currentIndex >= flags.size) {
                currentIndex = 0
            }
            chooseFlag(currentIndex)
        }
    }

    override fun finish() {
        super.finish()
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_REFRESH, ""))
    }
}
