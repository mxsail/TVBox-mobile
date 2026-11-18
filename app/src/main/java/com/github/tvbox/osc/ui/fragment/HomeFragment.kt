package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.github.tvbox.osc.ui.compose.HomeFilterPanel
import com.github.tvbox.osc.ui.compose.HomeSearchController
import com.github.tvbox.osc.ui.compose.HomeSearchView
import com.github.tvbox.osc.ui.compose.HomeSearchSourceFilterPanel
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager.widget.ViewPager
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.api.ApiConfig.LoadConfigCallback
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort.SortData
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.compose.HomeCategoryBar
import com.github.tvbox.osc.ui.compose.smoothScrollToTop
import com.github.tvbox.osc.ui.compose.LoadState
import com.github.tvbox.osc.ui.compose.LoadStateBox
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.TipDialog
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HeavyTaskUtil
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import me.jessyan.autosize.AutoSize

/**
 * 首页(Compose 渲染层): 顶栏(数据源名/搜索) + 分类栏(HomeCategoryBar) + 内容区
 * (ViewPager1 经 AndroidView 承载 UserFragment/GridFragment 分类子页)。
 * 源加载链路(loadConfig/loadJar/getSort/重试)与子页装配逻辑原样保留, 仅渲染层替换。
 */
class HomeFragment : Fragment() {

    // ---------------- Compose 响应状态 ----------------
    private val selectedTabState = mutableIntStateOf(0)
    private val categoriesState = mutableStateOf<List<String>>(listOf("主页"))
    private val materialState = mutableStateOf(ThemeManager.material())
    private val isTabsExpandedState = mutableStateOf(false)
    private val isFilterExpandedState = mutableStateOf(false)
    val isAnyDropdownExpanded = derivedStateOf { isTabsExpandedState.value || isFilterExpandedState.value }
    private val sourceNameState = mutableStateOf("")
    private val pageLoadState = mutableStateOf(LoadState.None)

    var mDisplaySortList: List<SortData> = ArrayList()
    var homeRecVod: List<Movie.Video>? = null
    val gridControllers = java.util.concurrent.ConcurrentHashMap<String, com.github.tvbox.osc.ui.compose.GridCategoryController>()

    fun collapseTabsIfExpanded(): Boolean {
        if (isTabsExpandedState.value || isFilterExpandedState.value) {
            setTabsExpanded(false)
            setFilterExpanded(false)
            return true
        }
        return false
    }

    private fun setTabsExpanded(expanded: Boolean) {
        isTabsExpandedState.value = expanded
        if (expanded) {
            isFilterExpandedState.value = false
            topBarVisibleState.value = true // 展开分类面板时先呼出顶栏
        }
    }

    private fun setFilterExpanded(expanded: Boolean) {
        isFilterExpandedState.value = expanded
        if (expanded) {
            isTabsExpandedState.value = false
            topBarVisibleState.value = true // 展开筛选面板时先呼出顶栏
        }
    }

    // 顶栏(数据源/搜索)显隐: 内容区上滑收起、下滑/到顶呼出, 与底栏联动
    private val topBarVisibleState = mutableStateOf(true)

    /** 由内容列表的滚动增量驱动(MainActivity 转发, 仅主页 tab) */
    fun onHomeContentScroll(dy: Int) {
        if (searchController.isSearchActive.value) {
            topBarVisibleState.value = true
            return
        }
        if (dy > 8 && topBarVisibleState.value) topBarVisibleState.value = false
        else if (dy < -8 && !topBarVisibleState.value) topBarVisibleState.value = true
    }

    /** 强制呼出顶栏(切回主页 tab/展开分类面板) */
    fun showTopBar() {
        topBarVisibleState.value = true
    }

    val searchController by lazy { HomeSearchController { this } }

    fun handleSearchBack(): Boolean = searchController.handleBack()

    val isSearchActive: androidx.compose.runtime.State<Boolean> get() = searchController.isSearchActive

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRefreshEvent(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            searchController.onSearchResult(event.obj as? AbsXml)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAppearanceChanged(event: AppearanceChangedEvent) {
        if (event.type == AppearanceChangedEvent.TYPE_STYLE) {
            materialState.value = ThemeManager.material()
        }
    }

    /**
     * 提供给主页返回操作
     */
    val tabIndex: Int
        get() = selectedTabState.intValue

    /** 已装载的分类数(供底栏筛选钮显隐订阅分类装载变化) */
    val categoryCount: Int
        get() = categoriesState.value.size

    fun hasCurrentCategoryFilters(): Boolean {
        val curSort = mDisplaySortList.getOrNull(selectedTabState.intValue)
        return curSort?.let {
            gridControllers[it.id]?.hasFilters() ?: (!it.filters.isNullOrEmpty())
        } ?: false
    }

    fun showCurrentCategoryFilter(context: android.content.Context) {
        val curSort = mDisplaySortList.getOrNull(selectedTabState.intValue)
        curSort?.let { gridControllers[it.id]?.showFilter(context) }
    }

    fun restoreCurrentCategoryView(): Boolean {
        val curSort = mDisplaySortList.getOrNull(selectedTabState.intValue)
        return curSort?.let { gridControllers[it.id]?.restoreView() } ?: false
    }

    private var sourceViewModel: SourceViewModel? = null
    private val fragments: MutableList<BaseLazyFragment> = ArrayList()
    private val mHandler = Handler(Looper.getMainLooper())

    /**
     * 顶部tabs分类集合,用于渲染tab页,每个tab对应fragment内的数据
     */
    private var mSortDataList: List<SortData> = ArrayList()

    private var dataInitOk = false
    private var jarInitOk = false

    /** 「上次观看」悬浮条待弹标记: jar 就绪后置位, 主页分类装载完成后弹出 */
    private var historyPending = false

    var errorTipDialog: TipDialog? = null

    /**
     * true: 配置变更重载
     * false: 全部重载(api变更、重启app等)
     */
    var onlyConfigChanged = false

    private var eventBusRegistered = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity?.let { AutoSize.autoConvertDensity(it, 360f, true) }
        sourceNameState.value = getString(R.string.app_name)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { TvBoxTheme { HomeScreen() } }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!eventBusRegistered) {
            eventBusRegistered = true
            EventBus.getDefault().register(this)
        }
        init()
    }

    private fun init() {
        ControlManager.get().startServer()
        initViewModel()
        initData()
    }

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel?.sortResult?.observe(this) { absXml: AbsSortXml? ->
            showSuccess()
            val homeKey = ApiConfig.get().homeSourceBean?.key
            mSortDataList =
                if (absXml?.classes != null && absXml.classes.sortList != null) {
                    DefaultConfig.adjustSort(
                        homeKey,
                        absXml.classes.sortList,
                        true
                    )
                } else {
                    DefaultConfig.adjustSort(homeKey, ArrayList(), true)
                }
            initViewPager(absXml)
            // 「上次观看」悬浮条等主页内容就绪后再弹(原来在 loadJar 成功即弹, 会盖在加载页上)
            if (historyPending) {
                historyPending = false
                queryHistory()
            }
        }
    }

    private fun initData() {
        val mainActivity = requireActivity() as MainActivity
        onlyConfigChanged = mainActivity.useCacheConfig

        val home = ApiConfig.get().homeSourceBean
        if (home != null && !home.name.isNullOrEmpty()) {
            sourceNameState.value = home.name
        }

        showLoading()
        // 首帧性能: 本方法在首页视图实例化(应用首帧布局)期间执行, 而 loadConfig/loadJar 首次
        // 触碰 OkGo/OkHttp 类栈并构建请求(实测数百 ms), jar 缓存命中时 DexClassLoader 加载
        // 也在此链路上——全部挪到后台线程执行。回调统一经 mHandler(主线程)回到本类,
        // 缓存命中/空订阅的同步回调也只做 mHandler.post, 线程安全。
        HeavyTaskUtil.executeNewTask {
            when {
                dataInitOk && jarInitOk -> {
                    //正常初始化会先加载,最终到这,此时数据有以下几种情况
                    // 1. api/jar/spider等均加载完,正常显示数据。2. 缺失spider(存疑?)/api配置有问题同样加载(最后空布局 或 只有豆瓣首页)
                    sourceViewModel?.getSort(ApiConfig.get().homeSourceBean.key)
                }
                dataInitOk && !jarInitOk -> {
                    loadJar()
                }
                else -> {
                    loadConfig()
                }
            }
        }
    }

    private fun loadConfig() {
        ApiConfig.get().loadConfig(onlyConfigChanged, object : LoadConfigCallback {

            override fun retry() {
                mHandler.post { initData() }
            }

            override fun success() {
                dataInitOk = true
                if (ApiConfig.get().spider.isEmpty()) {
                    jarInitOk = true
                }
                // 「上次观看」悬浮条在配置就绪时预约(分类装载完成后显示);
                // 不能挂在 loadJar 成功上——jar 加载可能长时间阻塞, 主页内容却早已就绪
                if (!onlyConfigChanged) {
                    historyPending = true
                }
                mHandler.postDelayed({ initData() }, 50)
            }

            override fun error(msg: String) {
                if (msg.equals("-1", ignoreCase = true)) {
                    mHandler.post {
                        dataInitOk = true
                        jarInitOk = true
                        if (!onlyConfigChanged) {
                            historyPending = true
                        }
                        initData()
                    }
                } else {
                    showTipDialog(msg)
                }
            }
        }, requireActivity())
    }

    private fun loadJar() {
        if (!ApiConfig.get().spider.isNullOrEmpty()) {
            ApiConfig.get().loadJar(
                onlyConfigChanged,
                ApiConfig.get().spider,
                object : LoadConfigCallback {
                    override fun success() {
                        jarInitOk = true
                        mHandler.postDelayed({
                            initData()
                        }, 50)
                    }

                    override fun retry() {}
                    override fun error(msg: String) {
                        jarInitOk = true
                        android.util.Log.e("TVBox", "loadJar failed: $msg")
                        mHandler.post {
                            if (!msg.isNullOrEmpty()) {
                                ToastUtils.showShort(msg)
                            } else {
                                ToastUtils.showShort("更新订阅失败")
                            }
                            initData()
                        }
                    }
                })
        }
    }

    private fun showTipDialog(msg: String) {
        if (errorTipDialog == null) {
            errorTipDialog =
                TipDialog(requireActivity(), msg, "重试", "取消", object : TipDialog.OnListener {
                    override fun left() {
                        mHandler.post {
                            initData()
                            errorTipDialog?.hide()
                        }
                    }

                    override fun right() {
                        dataInitOk = true
                        jarInitOk = true
                        mHandler.post {
                            initData()
                            errorTipDialog?.hide()
                        }
                    }

                    override fun cancel() {
                        dataInitOk = true
                        jarInitOk = true
                        mHandler.post {
                            initData()
                            errorTipDialog?.hide()
                        }
                    }

                    override fun onTitleClick() {
                        errorTipDialog?.hide()
                        jumpActivity(SubscriptionActivity::class.java)
                    }
                })
        }
        if (!errorTipDialog!!.isShowing) errorTipDialog!!.show()
    }

    private fun initViewPager(absXml: AbsSortXml?) {
        gridControllers.clear()
        if (mSortDataList.isNotEmpty()) {
            homeRecVod = if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml?.videoList != null) {
                absXml.videoList
            } else null

            mDisplaySortList = if (Hawk.get(HawkConfig.HOME_REC, 0) == 2 && mSortDataList.isNotEmpty()) {
                mSortDataList.drop(1)
            } else {
                mSortDataList
            }

            categoriesState.value = mDisplaySortList.map { it.name }
        }
    }

    /**
     * 提供给主页返回操作
     */
    fun scrollToFirstTab(): Boolean {
        return if (selectedTabState.intValue != 0) {
            selectedTabState.intValue = 0
            true
        } else {
            false
        }
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchController.destroy()
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this)
            eventBusRegistered = false
        }
        ControlManager.get().stopServer()
    }

    private fun jumpActivity(clazz: Class<*>) {
        val act = activity ?: return
        act.startActivity(Intent(act, clazz))
    }

    private fun showSiteSwitch() {
        val act = activity ?: return
        val sites = ApiConfig.get().sourceBeanList
        if (sites.size > 0) {
            val dialog = SelectDialog<SourceBean>(act)
            val tvRecyclerView = dialog.findViewById<TvRecyclerView>(R.id.list)
            tvRecyclerView.setLayoutManager(V7GridLayoutManager(dialog.context, 2))
            dialog.setTip("请选择首页数据源")
            dialog.setAdapter(object : SelectDialogInterface<SourceBean?> {
                override fun click(value: SourceBean?, pos: Int) {
                    ApiConfig.get().setSourceBean(value)
                    refreshHomeSources()
                }

                override fun getDisplay(source: SourceBean?): String {
                    return if (source == null) "" else source.name
                }
            }, object : DiffUtil.ItemCallback<SourceBean>() {
                override fun areItemsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                    return oldItem === newItem
                }

                override fun areContentsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                    return oldItem.key.contentEquals(newItem.key)
                }
            }, sites, sites.indexOf(ApiConfig.get().homeSourceBean))
            dialog.show()
        } else {
            ToastUtils.showLong("暂无可用数据源")
        }
    }

    private fun refreshHomeSources() {
        val act = activity ?: return
        val intent = Intent(act, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        val bundle = Bundle()
        bundle.putBoolean(IntentKey.CACHE_CONFIG_CHANGED, true)
        intent.putExtras(bundle)
        act.startActivity(intent)
    }

    private fun queryHistory() {
        lifecycleScope.launch {
            val vodInfoList = withContext(Dispatchers.IO) {
                val allVodRecord = RoomDataManger.getAllVodRecord(100)
                val vodInfoList: MutableList<VodInfo?> = ArrayList()
                for (vodInfo in allVodRecord) {
                    if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) vodInfo.note =
                        vodInfo.playNote
                    vodInfoList.add(vodInfo)
                }
                vodInfoList
            }

            // 有观看记录则交给 MainActivity 在底栏上方展示「上次观看」悬浮条
            val first = vodInfoList.firstOrNull() ?: return@launch
            (activity as? MainActivity)?.showLastViewedTip(first)
        }
    }

    private fun showLoading() {
        pageLoadState.value = LoadState.Loading
    }

    private fun showSuccess() {
        pageLoadState.value = LoadState.Success
    }

    // ---------------- Compose 渲染层 ----------------

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun HomeScreen(
        bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
        onScrollDelta: ((Int) -> Unit)? = null
    ) {
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        val isDark = isAppDarkTheme()

        val backdrop = rememberLayerBackdrop()
        val userCategoryGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

        val categoryPagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = 0
        ) { categoriesState.value.size }

        var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        // 实时同步分类高亮: 手指滑动页面跨过一半时立即同步
        LaunchedEffect(categoryPagerState) {
            snapshotFlow { categoryPagerState.currentPage }
                .collectLatest { page ->
                    if (!isProgrammaticScroll) {
                        selectedTabState.intValue = page
                        setTabsExpanded(false)
                        setFilterExpanded(false)
                    }
                }
        }

        // 外部触发跳转 (例如按返回键 scrollToFirstTab)
        LaunchedEffect(selectedTabState.intValue) {
            val target = selectedTabState.intValue
            if (categoryPagerState.currentPage != target && !categoryPagerState.isScrollInProgress) {
                isProgrammaticScroll = true
                try {
                    categoryPagerState.animateScrollToPage(target)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        val isSearchActive = searchController.isSearchActive.value
        val searchFocusRequester = remember { FocusRequester() }

        LaunchedEffect(isSearchActive) {
            if (isSearchActive) {
                showTopBar()
                if (searchController.inputText.value.isEmpty()) {
                    kotlinx.coroutines.delay(180)
                    try { searchFocusRequester.requestFocus() } catch (_: Throwable) {}
                }
            }
        }

        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // 初态顶部避让：状态栏 + 搜索行(44dp) + 分类栏(38dp)
        val initialTopPadding = statusBarHeight + 44.dp + 38.dp

        val hasSearchSourceTabs = isSearchActive && !searchController.showSuggestPage.value && searchController.sourceTabs.value.isNotEmpty()
        val searchSourceTabsHeight = if (hasSearchSourceTabs) 38.dp else 0.dp
        val totalSearchTopBarHeight = statusBarHeight + 44.dp + searchSourceTabsHeight

        // 滚动感知：搜索栏收起，或者主页网格产生滑动偏移，即激活动态毛玻璃
        val isScrolled by remember {
            derivedStateOf {
                isSearchActive ||
                !topBarVisibleState.value ||
                userCategoryGridState.firstVisibleItemIndex > 0 ||
                userCategoryGridState.firstVisibleItemScrollOffset > 4
            }
        }

        val blurAlpha by animateFloatAsState(
            targetValue = if (isScrolled) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "home_top_blur_alpha"
        )

        val isAnyDropdownOpen = isAnyDropdownExpanded.value || (isSearchActive && (searchController.isFilterExpandedState.value || searchController.isSourceTabsExpanded.value))
        val topBarBgColor by androidx.compose.animation.animateColorAsState(
            targetValue = when {
                isAnyDropdownOpen -> colorScheme.surface
                isSearchActive -> colorScheme.surface
                isScrolled -> colorScheme.surface.copy(alpha = (if (isDark) 0.82f else 0.86f) * blurAlpha)
                else -> Color.Transparent
            },
            animationSpec = tween(durationMillis = 200),
            label = "top_bar_bg_color"
        )
        val isBackdropActive = !isAnyDropdownOpen && isScrolled && isRenderEffectSupported()

        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            // 1. 底层：穿顶全屏网格内容层
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                LoadStateBox(pageLoadState.value, Modifier.fillMaxSize()) {
                    if (categoriesState.value.isNotEmpty()) {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = categoryPagerState,
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !isAnyDropdownOpen,
                            modifier = Modifier.fillMaxSize()
                        ) { catIndex ->
                            val sortData = mDisplaySortList.getOrNull(catIndex)
                            if (sortData != null) {
                                if (sortData.id == "my0") {
                                    com.github.tvbox.osc.ui.compose.UserCategoryScreen(
                                        recVod = homeRecVod,
                                        bottomPadding = bottomPadding,
                                        topPadding = initialTopPadding,
                                        onScrollDelta = onScrollDelta,
                                        gridState = userCategoryGridState
                                    )
                                } else {
                                    val ctrl = gridControllers.getOrPut(sortData.id) {
                                        com.github.tvbox.osc.ui.compose.GridCategoryController(
                                            sortData,
                                            requireActivity()
                                        )
                                    }
                                    com.github.tvbox.osc.ui.compose.GridCategoryScreen(
                                        sortData = sortData,
                                        controller = ctrl,
                                        bottomPadding = bottomPadding,
                                        topPadding = initialTopPadding,
                                        onScrollDelta = onScrollDelta
                                    )
                                }
                            }
                        }
                    }
                }

                // 搜索展开时的全屏 Search View 覆盖层
                if (isSearchActive) {
                    HomeSearchView(
                        controller = searchController,
                        topBarHeight = totalSearchTopBarHeight,
                        onOpenVideo = { video ->
                            val act = activity ?: return@HomeSearchView
                            val intent = Intent(act, DetailActivity::class.java).apply {
                                putExtra("id", video.id)
                                putExtra("sourceKey", video.sourceKey)
                            }
                            act.startActivity(intent)
                        }
                    )
                }
            }

            // 2. 遮罩层 (分类展开、筛选展开或搜索源下拉时的柔和遮罩)
            val isSearchScrimVisible = isSearchActive && (searchController.isFilterExpandedState.value || searchController.isSourceTabsExpanded.value)
            val isNormalScrimVisible = !isSearchActive && (isTabsExpandedState.value || isFilterExpandedState.value)
            androidx.compose.animation.AnimatedVisibility(
                visible = isNormalScrimVisible || isSearchScrimVisible,
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
                            if (isSearchActive) {
                                if (searchController.isFilterExpandedState.value) {
                                    searchController.closeFilterAndApply()
                                }
                                if (searchController.isSourceTabsExpanded.value) {
                                    searchController.isSourceTabsExpanded.value = false
                                }
                            } else {
                                setTabsExpanded(false)
                                setFilterExpanded(false)
                            }
                        }
                )
            }

            // 3. 顶层悬浮一体化动态毛玻璃顶栏 (状态栏 + 联动折叠搜索栏 + 分类栏 + 底部渐隐阴影)
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
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) // 动态贴合系统状态栏高度

                    // 联动收起的搜索栏
                    androidx.compose.animation.AnimatedVisibility(
                        visible = topBarVisibleState.value,
                        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(180))
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 数据源名称 (常态显示，搜索展开时平滑水平收起)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isSearchActive,
                                enter = expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                                ) + fadeIn(tween(150)),
                                exit = shrinkHorizontally(
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f)
                                ) + fadeOut(tween(150))
                            ) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.75f))
                                        .combinedClickable(
                                            onClick = {
                                                if (dataInitOk && jarInitOk) {
                                                    showSiteSwitch()
                                                } else {
                                                    ToastUtils.showShort("数据源未加载，长按刷新或切换订阅")
                                                }
                                            },
                                            onLongClick = { refreshHomeSources() }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        sourceNameState.value,
                                        color = colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier
                                            .widthIn(max = 76.dp)
                                            .basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                    Icon(
                                        painterResource(R.drawable.ic_arrow_down),
                                        contentDescription = "切换数据源",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .size(13.dp)
                                    )
                                }
                            }

                            // 搜索栏 (常态为胶囊，聚焦时向两侧变宽展开为全宽搜索栏)
                            val searchPaddingStart by animateDpAsState(
                                targetValue = if (isSearchActive) 0.dp else 8.dp,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                label = "home_search_padding_start"
                            )

                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = searchPaddingStart)
                                    .height(38.dp)
                            ) {
                                if (!isSearchActive) {
                                    Row(
                                        Modifier
                                            .fillMaxSize()
                                            .clickable { searchController.openSearch() }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_search_18),
                                            contentDescription = "搜索",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "搜索影视",
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Row(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(start = 2.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { searchController.handleBack() },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_arrow_back),
                                                contentDescription = "返回",
                                                tint = colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        BasicTextField(
                                            value = searchController.inputText.value,
                                            onValueChange = { newText ->
                                                searchController.inputText.value = newText
                                                if (newText.isEmpty()) {
                                                    searchController.suggestWords.value = emptyList()
                                                    searchController.showSuggestPage.value = true
                                                } else {
                                                    searchController.getSuggest(newText)
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = colorScheme.onSurface,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            cursorBrush = SolidColor(colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                searchController.search(searchController.inputText.value)
                                            }),
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(searchFocusRequester),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (searchController.inputText.value.isEmpty()) {
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

                                        if (searchController.inputText.value.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    searchController.inputText.value = ""
                                                    searchController.suggestWords.value = emptyList()
                                                    searchController.showSuggestPage.value = true
                                                    searchController.initHistorySearch()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_clear),
                                                    contentDescription = "清空输入",
                                                    tint = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                searchController.search(searchController.inputText.value)
                                            },
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
                            }

                            // 分类筛选入口
                            val hasFilters = remember(selectedTabState.intValue, categoriesState.value) {
                                hasCurrentCategoryFilters()
                            }
                            AnimatedVisibility(
                                visible = !isSearchActive && hasFilters,
                                enter = expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                    expandFrom = Alignment.End
                                ) + scaleIn(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                    initialScale = 0.8f
                                ) + fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)),
                                exit = shrinkHorizontally(
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
                                    shrinkTowards = Alignment.End
                                ) + scaleOut(
                                    animationSpec = tween(150),
                                    targetScale = 0.8f
                                ) + fadeOut(animationSpec = tween(150))
                            ) {
                                val isFilterOpen = isFilterExpandedState.value
                                val curSort = mDisplaySortList.getOrNull(selectedTabState.intValue)
                                val filterCount = curSort?.filterSelectCount() ?: 0
                                val isFilterActive = filterCount > 0

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
                                    Modifier
                                        .padding(start = 8.dp)
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(btnBg)
                                        .clickable {
                                            setFilterExpanded(!isFilterExpandedState.value)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_filter_list),
                                        contentDescription = "筛选",
                                        tint = iconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (filterCount > 0 && !isFilterOpen) {
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

                            // 观看历史入口
                            AnimatedVisibility(
                                visible = !isSearchActive,
                                enter = expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                    expandFrom = Alignment.End
                                ) + fadeIn(tween(150)),
                                exit = shrinkHorizontally(
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
                                    shrinkTowards = Alignment.End
                                ) + fadeOut(tween(150))
                            ) {
                                Box(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(colorScheme.surfaceContainerHigh)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            val act = activity ?: return@clickable
                                            act.startActivity(Intent(act, HistoryActivity::class.java))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_history_18),
                                        contentDescription = "观看历史",
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 搜索激活态下的【搜索源配置】入口
                            AnimatedVisibility(
                                visible = isSearchActive,
                                enter = expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                    expandFrom = Alignment.End
                                ) + scaleIn(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                                    initialScale = 0.8f
                                ) + fadeIn(tween(180)),
                                exit = shrinkHorizontally(
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
                                    shrinkTowards = Alignment.End
                                ) + scaleOut(
                                    animationSpec = tween(150),
                                    targetScale = 0.8f
                                ) + fadeOut(tween(150))
                            ) {
                                val isFilterOpen = searchController.isFilterExpandedState.value
                                val allSearchable = searchController.allSearchableSources
                                val isFilterActive = searchController.checkedKeysState.value.size < allSearchable.size && searchController.checkedKeysState.value.isNotEmpty()

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
                                        .padding(start = 8.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(btnBg)
                                        .clickable {
                                            if (isFilterOpen) {
                                                searchController.closeFilterAndApply()
                                            } else {
                                                searchController.isFilterExpandedState.value = true
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
                        }
                    }

                    // 常驻分类栏 (未激活搜索时显示首页分类，激活搜索且在结果页时显示搜索源分类)
                    if (!isSearchActive) {
                        HomeCategoryBar(
                            categories = categoriesState.value,
                            selectedIndex = selectedTabState.intValue,
                            onTabSelected = { pos ->
                                selectedTabState.intValue = pos
                                setTabsExpanded(false)
                                setFilterExpanded(false)
                                coroutineScope.launch {
                                    isProgrammaticScroll = true
                                    try {
                                        categoryPagerState.animateScrollToPage(pos)
                                    } finally {
                                        isProgrammaticScroll = false
                                    }
                                }
                            },
                            onTabDoubleClicked = { pos ->
                                scrollJob?.cancel()
                                scrollJob = coroutineScope.launch {
                                    val sortData = mDisplaySortList.getOrNull(pos) ?: return@launch
                                    if (sortData.id == "my0") {
                                        userCategoryGridState.smoothScrollToTop()
                                    } else {
                                        gridControllers[sortData.id]?.gridScrollState?.smoothScrollToTop()
                                    }
                                }
                            },
                            material = materialState.value,
                            isExpanded = isTabsExpandedState.value,
                            onExpandChanged = { expanded ->
                                setTabsExpanded(expanded)
                            },
                            pagerState = categoryPagerState,
                            isProgrammaticScroll = isProgrammaticScroll
                        )
                    } else if (!searchController.showSuggestPage.value && searchController.sourceTabs.value.isNotEmpty()) {
                        val tabNames = searchController.sourceTabs.value.map { it.first }
                        val currentTabIdx = searchController.sourceTabs.value.indexOfFirst { it.second == searchController.selectedTabKey.value }.coerceAtLeast(0)
                        HomeCategoryBar(
                            categories = tabNames,
                            selectedIndex = currentTabIdx,
                            onTabSelected = { idx ->
                                if (idx in searchController.sourceTabs.value.indices) {
                                    val item = searchController.sourceTabs.value[idx]
                                    searchController.selectedTabKey.value = item.second
                                    searchController.searchFilterKey = item.second
                                }
                            },
                            material = materialState.value,
                            isExpanded = searchController.isSourceTabsExpanded.value,
                            onExpandChanged = { expanded ->
                                searchController.isSourceTabsExpanded.value = expanded
                                if (expanded) searchController.isFilterExpandedState.value = false
                            }
                        )
                    }
                }

                // 底部柔和扩散淡阴影
                if (blurAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .align(Alignment.BottomCenter)
                            .offset(y = 8.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        (if (isDark) Color.Black else colorScheme.scrim).copy(alpha = (if (isDark) 0.16f else 0.08f) * blurAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // 4. 顶部分类高级筛选下拉面板 (悬浮在毛玻璃外壳与遮罩之上)
            val filterTopOffset = statusBarHeight + (if (topBarVisibleState.value) 44.dp else 0.dp) + 38.dp
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive && isFilterExpandedState.value,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ) + fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = filterTopOffset)
            ) {
                val curSort = mDisplaySortList.getOrNull(selectedTabState.intValue)
                if (curSort != null && !curSort.filters.isNullOrEmpty()) {
                    val ctrl = gridControllers[curSort.id]
                    HomeFilterPanel(
                        sortData = curSort,
                        onFilterChanged = {
                            ctrl?.refresh()
                        },
                        onCollapse = {
                            setFilterExpanded(false)
                        }
                    )
                }
            }

            // 5. 顶部搜索源配置下拉面板 (当搜索展开且点击搜索源配置按钮时显示)
            val searchFilterTopOffset = statusBarHeight + 44.dp
            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive && searchController.isFilterExpandedState.value,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                ) + fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = searchFilterTopOffset)
            ) {
                HomeSearchSourceFilterPanel(
                    allSources = searchController.allSearchableSources,
                    checkedKeys = searchController.checkedKeysState.value,
                    onToggleKey = { key ->
                        val set = searchController.checkedKeysState.value.toMutableSet()
                        if (set.contains(key)) set.remove(key) else set.add(key)
                        searchController.checkedKeysState.value = set
                    },
                    onSelectAll = {
                        searchController.checkedKeysState.value = searchController.allSearchableSources.map { it.key }.toSet()
                    },
                    onClearAll = {
                        searchController.checkedKeysState.value = emptySet()
                    },
                    onClose = {
                        searchController.closeFilterAndApply()
                    }
                )
            }
        }
    }
}
