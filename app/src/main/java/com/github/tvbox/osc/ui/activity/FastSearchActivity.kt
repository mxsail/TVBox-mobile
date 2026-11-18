package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.github.tvbox.osc.ui.compose.M3ConfirmDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.DoubanSuggestBean
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter
import com.github.tvbox.osc.ui.compose.HomeCategoryBar
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.DoubanSuggestDialog
import com.github.tvbox.osc.ui.dialog.SearchSuggestionsDialog
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.interfaces.SimpleCallback
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.callback.StringCallback
import com.orhanobut.hawk.Hawk
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch
import okhttp3.Response
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 聚合搜索(Compose): 搜索框(联想弹窗锚定)/历史+热词/多源并发搜索/源过滤 chips/结果列表。
 * 搜索数据流(10 线程池/暂停恢复/分组合并)原样保留, 渲染层换 Compose。
 */
class FastSearchActivity : BaseActivity() {

    companion object {
        private var mCheckSources: HashMap<String, String>? = null
        @JvmStatic
        fun setCheckedSourcesForSearch(checkedSources: HashMap<String, String>?) {
            mCheckSources = checkedSources
        }
    }

    private lateinit var sourceViewModel: SourceViewModel

    // ---- 数据流状态(原样保留的逻辑) ----
    private var searchTitle: String? = ""
    private val spNames = HashMap<String, String>()
    private var searchFilterKey = "" // 当前过滤的源 key, ""=全部
    private val resultVods = HashMap<String, MutableList<Movie.Video>>()
    private var pauseRunnable: MutableList<Runnable>? = null
    private var searchExecutorService: ExecutorService? = null
    private val allRunCount = AtomicInteger(0)

    // ---- Compose UI 状态 ----
    private val resultItems = mutableStateOf<List<Movie.Video>>(emptyList())
    private val sourceTabs = mutableStateOf(listOf("全部显示" to ""))
    private val selectedTabKey = mutableStateOf("")
    private val isSourceTabsExpanded = mutableStateOf(false)
    private val isFilterExpandedState = mutableStateOf(false)
    private val checkedKeysState = mutableStateOf<Set<String>>(emptySet())
    private val showSuggestPage = mutableStateOf(true) // true=历史/热词页 false=结果页
    private val loadState = mutableIntStateOf(1) // 1=初始 2=内容 3=空
    private val historyWords = mutableStateOf<List<String>>(emptyList())
    private val hotWords = mutableStateOf<List<String>>(emptyList())
    private val suggestWords = mutableStateOf<List<String>>(emptyList())
    private val inputText = mutableStateOf("")

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        com.gyf.immersionbar.ImmersionBar.with(this)
            .transparentStatusBar()
            .statusBarDarkFont(!com.github.tvbox.osc.util.Utils.isDarkTheme())
            .navigationBarColor(android.R.color.transparent)
            .init()
        // 进页自动拉起输入法: STATE_VISIBLE + EditText requestFocus,
        // (仅 post showSoftInput 会在窗口拿到焦点前被吞掉)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { FastSearchScreen() } }
    }

    override fun onBackHandled() {
        if (isFilterExpandedState.value) {
            closeFilterAndApply()
            return
        }
        if (isSourceTabsExpanded.value) {
            isSourceTabsExpanded.value = false
            return
        }
        if (!showSuggestPage.value) {
            showSuggestPage.value = true
            initHistorySearch()
            return
        }
        super.onBackHandled()
    }

    override fun init() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        initData()
        initHistorySearch()
        loadHotWords()
    }

    override fun onResume() {
        super.onResume()
        initHistorySearch()
        if (pauseRunnable != null && pauseRunnable!!.size > 0) {
            searchExecutorService = Executors.newFixedThreadPool(10)
            allRunCount.set(pauseRunnable!!.size)
            for (runnable in pauseRunnable!!) {
                searchExecutorService!!.execute(runnable)
            }
            pauseRunnable!!.clear()
            pauseRunnable = null
        }
    }

    // ---------------- 数据流与搜索源过滤 ----------------

    private fun closeFilterAndApply() {
        val allSearchable = ApiConfig.get().sourceBeanList.filter { it.isSearchable }
        if (checkedKeysState.value.isEmpty()) {
            ToastUtils.showShort("未选择搜索源，已自动恢复全选")
            checkedKeysState.value = allSearchable.map { it.key }.toSet()
        }
        val oldKeys = mCheckSources?.keys ?: allSearchable.map { it.key }.toSet()
        val changed = oldKeys != checkedKeysState.value
        val isAll = checkedKeysState.value.size == allSearchable.size
        val map = HashMap<String, String>()
        checkedKeysState.value.forEach { map[it] = "1" }
        mCheckSources = map
        SearchHelper.putCheckedSources(map, isAll)
        isFilterExpandedState.value = false

        if (changed && !showSuggestPage.value && !searchTitle.isNullOrEmpty()) {
            search(searchTitle)
        }
    }

    private fun initData() {
        mCheckSources = SearchHelper.getSourcesForSearch()
        val allSearchable = ApiConfig.get().sourceBeanList.filter { it.isSearchable }
        checkedKeysState.value = mCheckSources?.keys?.toSet() ?: allSearchable.map { it.key }.toSet()
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title")
            if (!TextUtils.isEmpty(title)) {
                search(title)
            }
        }
    }

    private fun loadHistoryList(): List<String> {
        return try {
            val raw = Hawk.get<Any?>(HawkConfig.HISTORY_SEARCH)
            when (raw) {
                is String -> {
                    if (raw.isNotBlank()) {
                        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        com.google.gson.Gson().fromJson<List<String>>(raw, type) ?: emptyList()
                    } else emptyList()
                }
                is List<*> -> {
                    raw.mapNotNull { it?.toString() }
                }
                else -> {
                    val spJson = com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").getString("history", "")
                    if (spJson.isNotBlank()) {
                        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        com.google.gson.Gson().fromJson<List<String>>(spJson, type) ?: emptyList()
                    } else emptyList()
                }
            }
        } catch (_: Throwable) {
            try {
                val spJson = com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").getString("history", "")
                if (spJson.isNotBlank()) {
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    com.google.gson.Gson().fromJson<List<String>>(spJson, type) ?: emptyList()
                } else emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    private fun saveHistoryList(list: List<String>) {
        try {
            val json = com.google.gson.Gson().toJson(list)
            Hawk.put(HawkConfig.HISTORY_SEARCH, json)
            com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").put("history", json)
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun initHistorySearch() {
        historyWords.value = loadHistoryList()
    }

    private fun loadHotWords() {
        OkGo.get<String>("https://top.baidu.com/api/board?platform=wise&tab=movie")
            .headers("User-Agent", "Mozilla/5.0")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    try {
                        val hots = ArrayList<String>()
                        val root = JsonParser.parseString(response.body()).asJsonObject
                        val cards = root["data"].asJsonObject["cards"].asJsonArray
                        if (cards.size() > 0) {
                            val contentList = cards[0].asJsonObject["content"].asJsonArray
                            if (contentList.size() > 0) {
                                val items = contentList[0].asJsonObject["content"].asJsonArray
                                for (ele in items) {
                                    val title = (ele as JsonObject)["title"].asString.trim()
                                    if (title.isNotEmpty() && !hots.contains(title)) {
                                        hots.add(title)
                                    }
                                }
                            }
                        }
                        if (hots.isNotEmpty()) {
                            runOnUiThread { hotWords.value = hots }
                        }
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String = response.body!!.string()
            })
    }

    /** 联想搜索 */
    private fun getSuggest(text: String) {
        if (text.isEmpty()) {
            suggestWords.value = emptyList()
            return
        }
        OkGo.get<String>("https://suggest.video.iqiyi.com/?if=mobile&key=$text")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    val titles: MutableList<String> = ArrayList()
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        val datas = json["data"].asJsonArray
                        for (data in datas) {
                            val item = data as JsonObject
                            titles.add(item["name"].asString.trim { it <= ' ' })
                        }
                    } catch (th: Throwable) {
                        LogUtils.d(th.toString())
                    }
                    runOnUiThread { suggestWords.value = titles }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String = response.body!!.string()
            })
    }

    private fun removeHistoryWord(word: String) {
        val list = loadHistoryList().toMutableList()
        list.remove(word)
        saveHistoryList(list)
        historyWords.value = list.toList()
    }

    private fun clearAllHistory() {
        try {
            Hawk.delete(HawkConfig.HISTORY_SEARCH)
            com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").remove("history")
        } catch (_: Throwable) {}
        historyWords.value = emptyList()
    }

    private fun saveSearchHistory(searchWord: String?) {
        if (searchWord.isNullOrBlank()) return
        val word = searchWord.trim()
        val list = loadHistoryList().toMutableList()
        list.remove(word)
        list.add(0, word)
        while (list.size > 30) {
            list.removeAt(list.lastIndex)
        }
        saveHistoryList(list)
        historyWords.value = list.toList()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            search(event.obj as String)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRefreshEvent(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(if (event.obj == null) null else event.obj as AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        }
    }

    fun search(title: String?) {
        if (title.isNullOrEmpty()) {
            ToastUtils.showShort("请输入搜索内容")
            return
        }
        inputText.value = title
        suggestWords.value = emptyList()
        if (!Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {
            saveSearchHistory(title)
            initHistorySearch()
        }
        showSuggestPage.value = false
        KeyboardUtils.hideSoftInput(this)
        cancel()
        searchTitle = title
        resultItems.value = emptyList()
        resultVods.clear()
        searchFilterKey = ""
        selectedTabKey.value = ""
        sourceTabs.value = listOf("全部显示" to "")
        spNames.clear()
        loadState.value = 1
        searchResult()
    }

    private fun searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        } finally {
            allRunCount.set(0)
        }
        searchExecutorService = Executors.newFixedThreadPool(10)
        val searchRequestList: MutableList<SourceBean> = ArrayList()
        searchRequestList.addAll(ApiConfig.get().sourceBeanList)
        val home = ApiConfig.get().homeSourceBean
        searchRequestList.remove(home)
        searchRequestList.add(0, home)
        val siteKey = ArrayList<String>()
        for (bean in searchRequestList) {
            if (!bean.isSearchable) continue
            if (mCheckSources != null && !mCheckSources!!.containsKey(bean.key)) continue
            siteKey.add(bean.key)
            spNames[bean.name] = bean.key
            allRunCount.incrementAndGet()
        }
        for (key in siteKey) {
            searchExecutorService!!.execute {
                try {
                    sourceViewModel.getSearch(key, searchTitle)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun matchSearchResult(name: String, keyword: String?): Boolean {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(keyword)) return false
        val kw = keyword!!.trim { it <= ' ' }
        val arr = kw.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var matchNum = 0
        for (one in arr) {
            if (name.contains(one)) matchNum++
        }
        return matchNum == arr.size
    }

    private fun searchData(absXml: AbsXml?) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null
            && absXml.movie.videoList.size > 0
        ) {
            val data: MutableList<Movie.Video> = ArrayList()
            for (video in absXml.movie.videoList) {
                if (!matchSearchResult(video.name, searchTitle)) continue
                data.add(video)
                if (!resultVods.containsKey(video.sourceKey)) {
                    resultVods[video.sourceKey] = ArrayList()
                }
                resultVods[video.sourceKey]!!.add(video)
                // 源过滤 chips 动态补齐(已有则跳过)
                val name = spNames.entries.firstOrNull { it.value == video.sourceKey }?.key
                if (name != null && sourceTabs.value.none { it.first == name }) {
                    sourceTabs.value = sourceTabs.value + (name to video.sourceKey)
                }
            }
            val current = resultItems.value
            resultItems.value = if (selectedTabKey.value.isEmpty()) current + data else current
            if (loadState.value != 2 && resultItems.value.isNotEmpty()) loadState.value = 2
        }
        val count = allRunCount.decrementAndGet()
        if (count <= 0) {
            if (resultItems.value.isEmpty()) {
                loadState.value = 3
            }
            cancel()
        }
    }

    private fun cancel() {
        OkGo.getInstance().cancelTag("search")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.load()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun getDoubanSuggest(text: String) {
        OkGo.get<String>("https://movie.douban.com/j/subject_suggest?q=" + text.trim())
            .execute(object : StringCallback() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String>?) {
                    val list = GsonUtils.fromJson<List<DoubanSuggestBean>>(
                        response?.body(),
                        object : TypeToken<List<DoubanSuggestBean>>() {}.type
                    )
                    val filterList = list.filter { it.title == text }
                    if (filterList.isEmpty()) {
                        ToastUtils.showShort("暂无评分信息")
                        return
                    }
                    XPopup.Builder(this@FastSearchActivity)
                        .hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                        .asCustom(DoubanSuggestDialog(this@FastSearchActivity, filterList.subList(0, 1)))
                        .show()
                }
            })
    }

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun FastSearchScreen() {
        val isDark = isAppDarkTheme()
        val coroutineScope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }
        val allSearchableSources = remember {
            ApiConfig.get().sourceBeanList.filter { it.isSearchable }
        }

        LaunchedEffect(Unit) {
            if (inputText.value.isEmpty()) {
                kotlinx.coroutines.delay(200)
                try { focusRequester.requestFocus() } catch (_: Throwable) {}
            }
        }

        val backdrop = rememberLayerBackdrop()
        val resultGridState = rememberLazyGridState()
        val suggestListState = rememberLazyListState()
        val suggestScrollState = rememberScrollState()

        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val searchBarRowHeight = 48.dp
        val sourceBarHeight = if (!showSuggestPage.value && sourceTabs.value.isNotEmpty()) 34.dp else 0.dp
        val totalTopBarHeight = statusBarHeight + searchBarRowHeight + sourceBarHeight
        val filterTopOffset = statusBarHeight + searchBarRowHeight

        // 滚动感知：当建议页或结果页产生滑动偏移时，激活动态毛玻璃
        val isScrolled by remember {
            derivedStateOf {
                if (showSuggestPage.value) {
                    if (inputText.value.isNotBlank()) {
                        suggestListState.firstVisibleItemIndex > 0 || suggestListState.firstVisibleItemScrollOffset > 4
                    } else {
                        suggestScrollState.value > 4
                    }
                } else {
                    resultGridState.firstVisibleItemIndex > 0 || resultGridState.firstVisibleItemScrollOffset > 4
                }
            }
        }

        val blurAlpha by animateFloatAsState(
            targetValue = if (isScrolled) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "search_top_blur_alpha"
        )

        val isAnyDropdownOpen = isFilterExpandedState.value || isSourceTabsExpanded.value
        val topBarBgColor by androidx.compose.animation.animateColorAsState(
            targetValue = when {
                isAnyDropdownOpen -> colorScheme.surface
                isScrolled -> colorScheme.surface.copy(alpha = (if (isDark) 0.82f else 0.86f) * blurAlpha)
                else -> Color.Transparent
            },
            animationSpec = tween(durationMillis = 200),
            label = "top_bar_bg_color"
        )
        val isBackdropActive = !isAnyDropdownOpen && isScrolled && isRenderEffectSupported()

        var showClearConfirmDialog by remember { mutableStateOf(false) }
        var isHistoryDeleteMode by remember { mutableStateOf(false) }
        var isHistoryCollapsed by remember { mutableStateOf(false) }

        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            // 1. 底层：穿顶全屏内容层 (layerBackdrop 注册给 backdrop, 供顶栏采样真实模糊)
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                if (showSuggestPage.value) {
                    SuggestPage(
                        topBarHeight = totalTopBarHeight,
                        listState = suggestListState,
                        scrollState = suggestScrollState,
                        isHistoryCollapsed = isHistoryCollapsed,
                        onToggleHistoryCollapsed = { isHistoryCollapsed = !isHistoryCollapsed },
                        isHistoryDeleteMode = isHistoryDeleteMode,
                        onToggleHistoryDeleteMode = { isHistoryDeleteMode = it },
                        onShowClearConfirm = { showClearConfirmDialog = true }
                    )
                } else {
                    ResultPage(
                        topBarHeight = totalTopBarHeight,
                        gridState = resultGridState
                    )
                }
            }

            // 2. 遮罩层 1: 搜索源分类展开时的柔和半透明遮罩
            androidx.compose.animation.AnimatedVisibility(
                visible = isSourceTabsExpanded.value,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.scrim.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isSourceTabsExpanded.value = false
                        }
                )
            }

            // 3. 遮罩层 2: 搜索源配置面板展开时柔和渐变暗色遮罩
            androidx.compose.animation.AnimatedVisibility(
                visible = isFilterExpandedState.value,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.scrim.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            closeFilterAndApply()
                        }
                )
            }

            // 4. 顶层悬浮一体化动态毛玻璃顶栏 (状态栏 + 搜索栏 + 搜索源栏)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // 防穿透点击
                    .then(
                        if (isBackdropActive) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = {
                                    vibrancy()
                                    blur(20f.dp.toPx() * blurAlpha)
                                },
                                highlight = null,
                                shadow = null,
                                onDrawSurface = {
                                    drawRect(topBarBgColor)
                                }
                            )
                        } else {
                            Modifier.background(topBarBgColor)
                        }
                    )
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                    // 搜索输入栏行 (48dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 独立返回按钮 (若在结果页按返回则退回搜索建议/历史页，若已在建议页则退出 Activity)
                        IconButton(
                            onClick = {
                                if (!showSuggestPage.value) {
                                    showSuggestPage.value = true
                                    initHistorySearch()
                                } else {
                                    finish()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // 搜索胶囊主容器 (36dp 高度，18dp 圆角 / percent = 50，复用首页 surfaceContainerHigh)
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_search_18),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                BasicTextField(
                                    value = inputText.value,
                                    onValueChange = { newText ->
                                        inputText.value = newText
                                        if (newText.isEmpty()) {
                                            suggestWords.value = emptyList()
                                            showSuggestPage.value = true
                                        } else {
                                            getSuggest(newText)
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { search(inputText.value) }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (inputText.value.isEmpty()) {
                                                Text(
                                                    "搜索电影、电视剧、综艺…",
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                // 清空按钮
                                if (inputText.value.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            inputText.value = ""
                                            suggestWords.value = emptyList()
                                            showSuggestPage.value = true
                                            initHistorySearch()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_close_24),
                                            contentDescription = "清空输入",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // 搜索按钮
                                IconButton(
                                    onClick = { search(inputText.value) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_search_18),
                                        contentDescription = "搜索",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // 右侧搜索源筛选按钮 (36dp 圆形，与首页完全一致的变色与状态指示)
                        val isFilterOpen = isFilterExpandedState.value
                        val isFilterActive = checkedKeysState.value.size < allSearchableSources.size && checkedKeysState.value.isNotEmpty()

                        val btnBg = if (isFilterOpen) {
                            colorScheme.primary
                        } else if (isFilterActive) {
                            colorScheme.primaryContainer
                        } else {
                            colorScheme.surfaceContainerHigh
                        }

                        val iconTint = if (isFilterOpen) {
                            colorScheme.onPrimary
                        } else if (isFilterActive) {
                            colorScheme.onPrimaryContainer
                        } else {
                            colorScheme.onSurface
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(btnBg)
                                .clickable {
                                    if (isFilterOpen) {
                                        closeFilterAndApply()
                                    } else {
                                        isFilterExpandedState.value = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_filter_list),
                                contentDescription = "搜索源配置",
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                            if (isFilterActive && !isFilterOpen) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(6.dp)
                                        .background(colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }

                    // 搜索源栏 (HomeCategoryBar): 仅在结果页且有搜索源时显示
                    if (!showSuggestPage.value && sourceTabs.value.isNotEmpty()) {
                        val tabNames = sourceTabs.value.map { it.first }
                        val currentTabIdx = sourceTabs.value.indexOfFirst { it.second == selectedTabKey.value }.coerceAtLeast(0)
                        HomeCategoryBar(
                            categories = tabNames,
                            selectedIndex = currentTabIdx,
                            onTabSelected = { idx ->
                                if (idx in sourceTabs.value.indices) {
                                    val item = sourceTabs.value[idx]
                                    selectedTabKey.value = item.second
                                    searchFilterKey = item.second
                                    coroutineScope.launch {
                                        try { resultGridState.scrollToItem(0) } catch (_: Throwable) {}
                                    }
                                }
                            },
                            isExpanded = isSourceTabsExpanded.value,
                            onExpandChanged = { isSourceTabsExpanded.value = it }
                        )
                    }
                }
            }

            // 5. 顶部向下平滑展开的搜索源配置面板 (M3 Expressive 风格)
            androidx.compose.animation.AnimatedVisibility(
                visible = isFilterExpandedState.value,
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
                ) + fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = filterTopOffset)
            ) {
                SearchSourceFilterPanel(
                    allSources = allSearchableSources,
                    checkedKeys = checkedKeysState.value,
                    onToggleKey = { key ->
                        val cur = checkedKeysState.value
                        checkedKeysState.value = if (cur.contains(key)) cur - key else cur + key
                    },
                    onSelectAll = {
                        checkedKeysState.value = allSearchableSources.map { it.key }.toSet()
                    },
                    onClearAll = {
                        checkedKeysState.value = emptySet()
                    },
                    onClose = {
                        closeFilterAndApply()
                    }
                )
            }

            // 清空搜索历史二次确认弹窗 (Material 3 Expressive 危险弹窗)
            M3ConfirmDialog(
                visible = showClearConfirmDialog,
                title = "清空历史",
                message = "确定清空全部搜索历史吗？此操作不可撤销。",
                confirmText = "清空",
                cancelText = "取消",
                isDestructive = true,
                iconRes = R.drawable.ic_delete_outline_24,
                onDismissRequest = { showClearConfirmDialog = false },
                onConfirm = {
                    showClearConfirmDialog = false
                    clearAllHistory()
                    isHistoryDeleteMode = false
                }
            )
        }
    }

    /** 顶部平滑下拉展开的搜索源配置面板 (Material Design 3 Expressive) */
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun SearchSourceFilterPanel(
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
                // 标题栏: "搜索源配置" + 已选数量徽章
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

                // 搜索源 Chips 流式布局（高度限制，支持滑动）
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
                                        text = source.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    labelColor = colorScheme.onSurfaceVariant,
                                    selectedContainerColor = colorScheme.primaryContainer,
                                    selectedLabelColor = colorScheme.onPrimaryContainer
                                ),
                                border = null
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(4.dp))

                // 底部操作栏: "全选" / "清空" / "完成"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSelectAll) {
                        Text("全选", fontSize = 12.sp, color = colorScheme.primary)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onClearAll) {
                        Text("清空", fontSize = 12.sp, color = colorScheme.outline)
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onClose) {
                        Text("完成", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                    }
                }
            }
        }
    }

    /** 联想与历史+热词页 */
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun SuggestPage(
        topBarHeight: androidx.compose.ui.unit.Dp,
        listState: LazyListState,
        scrollState: ScrollState,
        isHistoryCollapsed: Boolean,
        onToggleHistoryCollapsed: () -> Unit,
        isHistoryDeleteMode: Boolean,
        onToggleHistoryDeleteMode: (Boolean) -> Unit,
        onShowClearConfirm: () -> Unit
    ) {
        val isDark = isAppDarkTheme()
        val suggestions = suggestWords.value
        val hasInput = inputText.value.isNotBlank()

        if (hasInput) {
            // 输入关键词时：隐藏搜索历史和热搜，仅展示实时联想词；若联想词为空则保持页面干净留白
            if (suggestions.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topBarHeight + 6.dp, bottom = 20.dp)
                ) {
                    itemsIndexed(suggestions) { _, suggest ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { search(suggest) }
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
                if (historyWords.value.isNotEmpty()) {
                    // 搜索历史标题行
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
                                label = "history_arrow_rotation"
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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_delete_outline_24),
                                        contentDescription = "管理历史",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 全部删除按钮 (红调高危)
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
                                // 完成按钮 (主色调)
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

                    // 历史搜索词流式布局 (带平滑折叠展开动效)
                    androidx.compose.animation.AnimatedVisibility(
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
                            historyWords.value.forEach { word ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!isHistoryDeleteMode) {
                                                search(word)
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
                                                   .clickable { removeHistoryWord(word) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_close_24),
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

                // 热门搜索
                if (hotWords.value.isNotEmpty()) {
                    Text(
                        "热门搜索",
                        color = colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (historyWords.value.isNotEmpty()) 22.dp else 6.dp)
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hotWords.value.forEachIndexed { index, word ->
                            val isTop3 = index < 3
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTop3) colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.6f)
                                else colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { search(word) }
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
            }
        }
    }

    /** 结果页: 穿顶全屏内容区 + M3 Expressive 自适应海报网格 (3列) */
    @Composable
    private fun ResultPage(
        topBarHeight: androidx.compose.ui.unit.Dp,
        gridState: LazyGridState
    ) {
        when {
            loadState.value == 1 && displayed().isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topBarHeight)
                ) {
                    SearchLoadingHint()
                }
            }
            loadState.value == 3 && displayed().isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topBarHeight)
                ) {
                    SearchEmptyHint()
                }
            }
            else -> {
                val list = displayed()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topBarHeight + 8.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list) { video ->
                        SearchResultGridItem(
                            video = video,
                            onClick = { openVideo(video) }
                        )
                    }
                }
            }
        }
    }

    /** M3 Expressive 自适应海报网格卡片: 3:4海报 + 左上角搜索源徽章 + 右下角备注 + 下方单行片名 */
    @Composable
    private fun SearchResultGridItem(
        video: Movie.Video,
        onClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "fast_search_grid_scale"
        )

        val sourceName = remember(video.sourceKey) {
            try {
                ApiConfig.get().getSource(video.sourceKey)?.name ?: ""
            } catch (_: Exception) { "" }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceContainer)
            ) {
                PosterImage(
                    url = video.pic ?: "",
                    modifier = Modifier.fillMaxSize()
                )

                // 左上角徽章: 搜索源名称 (若无则显示年份)
                val badgeText = sourceName.ifEmpty {
                    if (video.year > 0) video.year.toString() else ""
                }
                if (badgeText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.secondaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }
                }

                // 右下角徽章: 备注/集数/清晰度 (磨砂半透黑渐变胶囊)
                if (!video.note.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .widthIn(max = 100.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x99000000), Color(0xCC000000))
                                ),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
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

            // 下方标题
            Text(
                text = video.name ?: "",
                color = colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }

    /** 当前展示列表: 全部 or 单源过滤(快照) */
    private fun displayed(): List<Movie.Video> {
        val key = selectedTabKey.value
        return if (key.isEmpty()) resultItems.value
        else resultVods[key]?.toList() ?: emptyList()
    }

    private fun openVideo(video: Movie.Video) {
        try {
            if (searchExecutorService != null) {
                pauseRunnable = searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        val bundle = Bundle()
        bundle.putString("id", video.id)
        bundle.putString("sourceKey", video.sourceKey)
        jumpActivity(DetailActivity::class.java, bundle)
    }

    @Composable
    private fun SearchLoadingHint() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                "正在聚合搜索各大影视源…",
                color = colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }

    @Composable
    private fun SearchEmptyHint() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_search_18),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                "未找到相关影视结果",
                color = colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "可尝试更换关键字，或点击右上角筛选搜索源",
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    private fun ResultRow(video: Movie.Video, onClick: () -> Unit) {
        val sourceName = try {
            ApiConfig.get().getSource(video.sourceKey)?.name ?: ""
        } catch (e: Exception) {
            ""
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(120.dp)
        ) {
            PosterImage(
                video.pic,
                modifier = Modifier
                    .width(90.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    video.name,
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    sourceName,
                    color = colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (!video.note.isNullOrEmpty()) {
                    Text(
                        video.note,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
