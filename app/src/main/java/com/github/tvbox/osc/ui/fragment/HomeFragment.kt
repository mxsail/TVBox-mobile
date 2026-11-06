package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.compose.HomeCategoryBar
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
    private val materialState = mutableStateOf(Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID))
    private val isTabsExpandedState = mutableStateOf(false)
    private val sourceNameState = mutableStateOf("")
    private val pageLoadState = mutableStateOf(LoadState.None)

    var mDisplaySortList: List<SortData> = ArrayList()
    var homeRecVod: List<Movie.Video>? = null
    val gridControllers = java.util.concurrent.ConcurrentHashMap<String, com.github.tvbox.osc.ui.compose.GridCategoryController>()

    fun collapseTabsIfExpanded(): Boolean {
        if (isTabsExpandedState.value) {
            setTabsExpanded(false)
            return true
        }
        return false
    }

    private fun setTabsExpanded(expanded: Boolean) {
        isTabsExpandedState.value = expanded
        if (expanded) topBarVisibleState.value = true // 展开分类面板时先呼出顶栏
    }

    // 顶栏(数据源/搜索)显隐: 内容区上滑收起、下滑/到顶呼出, 与底栏联动
    private val topBarVisibleState = mutableStateOf(true)

    /** 由内容列表的滚动增量驱动(MainActivity 转发, 仅主页 tab) */
    fun onHomeContentScroll(dy: Int) {
        if (dy > 8 && topBarVisibleState.value) topBarVisibleState.value = false
        else if (dy < -8 && !topBarVisibleState.value) topBarVisibleState.value = true
    }

    /** 强制呼出顶栏(切回主页 tab/展开分类面板) */
    fun showTopBar() {
        topBarVisibleState.value = true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAppearanceChanged(event: AppearanceChangedEvent) {
        if (event.type == AppearanceChangedEvent.TYPE_STYLE) {
            materialState.value = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID)
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
        return curSort?.let { gridControllers[it.id]?.hasFilters() } ?: false
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
        AutoSize.autoConvertDensity(activity, 360f, true)
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
        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(44.dp)) // 原 topLayout marginTop(状态栏区)
                androidx.compose.animation.AnimatedVisibility(
                    visible = topBarVisibleState.value,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(180))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // 数据源名称: 点击切源, 长按刷新订阅
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (dataInitOk && jarInitOk) {
                                        showSiteSwitch()
                                    } else {
                                        ToastUtils.showShort("数据源未加载，长按刷新或切换订阅")
                                    }
                                },
                                onLongClick = { refreshHomeSources() }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sourceNameState.value,
                            color = colorScheme.onSurface,
                            fontSize = 16.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .widthIn(max = 110.dp)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Icon(
                            painterResource(R.drawable.ic_arrow_down),
                            contentDescription = "切换数据源",
                            tint = colorScheme.onSurface,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(14.dp)
                        )
                    }
                    // 搜索入口
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp, end = 10.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(colorScheme.surfaceContainerHigh)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val act = activity ?: return@clickable
                                act.startActivity(Intent(act, FastSearchActivity::class.java))
                            }
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("搜索", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painterResource(R.drawable.ic_search_18),
                                contentDescription = "搜索",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    }
                }

                Box(Modifier.weight(1f)) {
                    val categoryPagerState = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = 0
                    ) { categoriesState.value.size }

                    // 仅在 Pager 完全停稳(settled)后同步状态到 selectedTabState, 绝不在翻页动画进行中用中间页打断跨页滚动
                    androidx.compose.runtime.LaunchedEffect(categoryPagerState) {
                        androidx.compose.runtime.snapshotFlow { categoryPagerState.settledPage }
                            .collectLatest { settled ->
                                selectedTabState.intValue = settled
                            }
                    }

                    // 仅在外部主动修改 selectedTabState(如按返回键 scrollToFirstTab)且 Pager 未处于滚动中时触发滚动
                    androidx.compose.runtime.LaunchedEffect(selectedTabState.intValue) {
                        val target = selectedTabState.intValue
                        if (categoryPagerState.currentPage != target && !categoryPagerState.isScrollInProgress) {
                            categoryPagerState.animateScrollToPage(target)
                        }
                    }

                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(40.dp)) // 分类栏让位(分类栏已收窄至 38dp, 同步收紧)
                        LoadStateBox(pageLoadState.value, Modifier.fillMaxSize()) {
                            if (categoriesState.value.isNotEmpty()) {
                                androidx.compose.foundation.pager.HorizontalPager(
                                    state = categoryPagerState,
                                    beyondViewportPageCount = 1,
                                    modifier = Modifier.fillMaxSize()
                                ) { catIndex ->
                                    val sortData = mDisplaySortList.getOrNull(catIndex)
                                    if (sortData != null) {
                                        if (sortData.id == "my0") {
                                            com.github.tvbox.osc.ui.compose.UserCategoryScreen(
                                                recVod = homeRecVod,
                                                bottomPadding = bottomPadding,
                                                onScrollDelta = onScrollDelta
                                            )
                                        } else {
                                            com.github.tvbox.osc.ui.compose.GridCategoryScreen(
                                                sortData = sortData,
                                                bottomPadding = bottomPadding,
                                                onScrollDelta = onScrollDelta,
                                                onControllerReady = { ctrl ->
                                                    gridControllers[sortData.id] = ctrl
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 分类展开时的内容区压暗遮罩(淡入, 点击收起)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isTabsExpandedState.value,
                        enter = fadeIn(tween(240)),
                        exit = fadeOut(tween(200))
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0x73000000))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { setTabsExpanded(false) }
                        )
                    }
                    // 顶部分类栏(常态单行滚动 / 展开全量 chips)。
                    // 不加顶部 padding: 展开面板背景必须从本 Box 顶开始, 否则让位缝里
                    // 会透出展开遮罩形成一条分割线(面板与搜索框之间的"多余线")
                    HomeCategoryBar(
                        categories = categoriesState.value,
                        selectedIndex = selectedTabState.intValue,
                        onTabSelected = { pos ->
                            selectedTabState.intValue = pos
                            coroutineScope.launch {
                                categoryPagerState.animateScrollToPage(pos)
                            }
                        },
                        material = materialState.value,
                        isExpanded = isTabsExpandedState.value,
                        onExpandChanged = { expanded ->
                            setTabsExpanded(expanded)
                        }
                    )
                }
            }
        }
    }
}
