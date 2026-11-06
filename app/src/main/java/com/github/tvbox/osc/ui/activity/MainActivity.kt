package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.ActivityMainBinding
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.ui.compose.LastViewedTip
import com.github.tvbox.osc.ui.compose.LiquidBarSpec
import com.github.tvbox.osc.ui.compose.LiquidBottomBar
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.fragment.CacheFragment
import com.github.tvbox.osc.ui.fragment.HomeFragment
import com.github.tvbox.osc.ui.fragment.LiveFragment
import com.github.tvbox.osc.ui.fragment.MyFragment
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.view.BarAutoHide
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>(), BarAutoHide.ScrollBarHost {

    companion object {
        /** 通知/页面跳转直达指定底部 tab(0=首页 1=直播 2=我的缓存 3=我的) */
        const val EXTRA_TAB = "extra_tab"
    }

    val homeFragment = HomeFragment()
    val liveFragment = LiveFragment()
    val cacheFragment = CacheFragment()
    val myFragment = MyFragment()

    var useCacheConfig = false
    private var exitTime = 0L

    // 底栏显隐状态(滚动隐藏/呼出)
    private val barShownState = mutableStateOf(true)
    private var navInset = 0

    // Compose 底栏响应状态
    private val currentTabState = mutableIntStateOf(0)
    private val floatingState = mutableStateOf(false)
    private val showLabelsState = mutableStateOf(true)
    private val materialState = mutableStateOf(Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID))
    private val navInsetState = mutableIntStateOf(0)

    /** 「上次观看」悬浮条内容(主页内容就绪后由 HomeFragment 置入) */
    private val lastViewedTipState = mutableStateOf<VodInfo?>(null)

    /** 悬浮条在窗口中的 bounds(用于"点击他处消失"时排除本条的点击) */
    private var lastViewedTipBounds: Rect? = null

    /** 主页内容就绪后请求显示「上次观看」悬浮条 */
    fun showLastViewedTip(info: VodInfo) {
        lastViewedTipState.value = info
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 悬浮条不自动消失: 点击其以外任意处即消失(条内点击留给进详情)
        if (lastViewedTipState.value != null && ev.action == MotionEvent.ACTION_DOWN) {
            val bounds = lastViewedTipBounds
            if (bounds == null || !bounds.contains(ev.x.toInt(), ev.y.toInt())) {
                lastViewedTipState.value = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawableResource(R.color.launch_bg)
        super.onCreate(savedInstanceState)
    }

    override fun init() {
        App.getInstance().isNormalStart = true
        useCacheConfig = intent.extras?.getBoolean(IntentKey.CACHE_CONFIG_CHANGED, false) ?: false

        initBarInsets()
        applyBarStyle()

        val initialTab = intent.getIntExtra(EXTRA_TAB, 0)
        currentTabState.intValue = initialTab
        liveFragment.userVisibleHint = (initialTab == 1)

        if (supportFragmentManager.findFragmentByTag("live") == null) {
            supportFragmentManager.beginTransaction()
                .add(homeFragment, "home")
                .add(liveFragment, "live")
                .add(cacheFragment, "cache")
                .add(myFragment, "my")
                .commitNowAllowingStateLoss()
        }

        mBinding.composeRoot.setContent {
            TvBoxTheme {
                MainScreen(initialTab)
            }
        }

        mBinding.composeRoot.post { window.setBackgroundDrawableResource(R.color.md_background) }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MainScreen(initialTab: Int) {
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(initialPage = initialTab) { 4 }

        // 仅在 pager 未处于滚动状态且与目标 tab 不一致时(如外部入口调用 goToTab/onNewIntent)触发动画滚动
        LaunchedEffect(currentTabState.intValue) {
            val target = currentTabState.intValue
            if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(target)
            }
        }

        // 仅在 Pager 完全停稳(settled)后同步状态，绝不打断跨页连续滚动动画
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collectLatest { settled ->
                    currentTabState.intValue = settled
                    liveFragment.setUserVisibleHint(settled == 1)
                    if (settled == 0) {
                        homeFragment.showTopBar()
                    } else {
                        lastViewedTipState.value = null
                    }
                    showMainBar()
                }
        }

        val backdrop = rememberLayerBackdrop()
        val navInsetDp = with(LocalDensity.current) { navInsetState.intValue.toDp() }
        val floating = floatingState.value
        val bottomPadding = if (floating) (72.dp + navInsetDp) else (LiquidBarSpec.OccupiedHeightDp.dp + navInsetDp)

        val barOffsetY by animateDpAsState(
            targetValue = if (barShownState.value) 0.dp else 120.dp,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "barOffset"
        )

        Box(Modifier.fillMaxSize()) {
            // 1. 真实全屏内容容器 (layerBackdrop 注册给 backdrop, 供 LiquidBottomBar 采样真页面内容)
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 3,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    when (pageIndex) {
                        0 -> homeFragment.HomeScreen(
                            bottomPadding = bottomPadding,
                            onScrollDelta = { onMainScrollDelta(it) }
                        )
                        1 -> {
                            AndroidView(
                                factory = { ctx ->
                                    val v = liveFragment.onCreateView(android.view.LayoutInflater.from(ctx), null, null)
                                        ?: android.widget.FrameLayout(ctx)
                                    liveFragment.onViewCreated(v, null)
                                    v
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        2 -> cacheFragment.CacheScreen(
                            bottomPadding = bottomPadding,
                            onScrollDelta = { onMainScrollDelta(it) }
                        )
                        3 -> myFragment.MyScreen(
                            bottomPadding = bottomPadding,
                            onScrollDelta = { onMainScrollDelta(it) },
                            onGoToTab = { goToTab(it) }
                        )
                    }
                }
            }

            // 2. 底栏
            val barFilterVisible = currentTabState.intValue == 0 &&
                homeFragment.categoryCount > 0 &&
                homeFragment.hasCurrentCategoryFilters()

            val scrollFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction

            LiquidBottomBar(
                selectedIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    currentTabState.intValue = index
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                floating = floating,
                showLabels = showLabelsState.value,
                material = materialState.value,
                navInsetBottom = navInsetDp,
                pageScrollFraction = scrollFraction,
                backdrop = backdrop,
                filterVisible = barFilterVisible,
                onFilterClick = {
                    homeFragment.showCurrentCategoryFilter(this@MainActivity)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = barOffsetY)
            )

            // 3. 「上次观看」悬浮条
            Box(Modifier.matchParentSize()) {
                LastViewedTip(
                    tip = lastViewedTipState.value.takeIf { currentTabState.intValue == 0 },
                    material = materialState.value,
                    backdrop = backdrop,
                    onDismiss = { lastViewedTipState.value = null },
                    onBounds = { lastViewedTipBounds = it },
                    onOpen = { vod ->
                        lastViewedTipState.value = null
                        val bundle = Bundle().apply {
                            putString("id", vod.id)
                            putString("sourceKey", vod.sourceKey)
                        }
                        startActivity(
                            Intent(this@MainActivity, DetailActivity::class.java).putExtras(bundle)
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -(navInsetDp + 68.dp))
                )
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(IntentKey.CACHE_CONFIG_CHANGED, false)) {
            useCacheConfig = true
            recreate()
            return
        }
        goToTab(intent.getIntExtra(EXTRA_TAB, 0))
    }

    /** 切换底部 tab(直播标题栏返回/我的页入口/通知点击等统一入口) */
    fun goToTab(index: Int) {
        if (index < 0 || index >= 4) return
        currentTabState.intValue = index
        if (index == 0) {
            homeFragment.showTopBar()
        } else if (lastViewedTipState.value != null) {
            lastViewedTipState.value = null
        }
        showMainBar()
    }

    override fun keepBackIntercept(): Boolean = true

    override fun onBackHandled() {
        if (currentTabState.intValue != 0) {
            if (currentTabState.intValue == 1 && liveFragment.isUiReady() && liveFragment.handleBack()) {
                return
            }
            goToTab(0)
            return
        }
        if (homeFragment.collapseTabsIfExpanded()) {
            return
        }
        if (homeFragment.restoreCurrentCategoryView()) {
            return
        }
        if (homeFragment.scrollToFirstTab()) {
            return
        }
        confirmExit()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (currentTabState.intValue == 1 && liveFragment.isUiReady()) {
            liveFragment.handleKeyEvent(event)
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------- 底栏: 悬浮形态 / 界面材质 / 滚动隐藏 ----------------

    override fun autoNavigationBarInset(): Boolean = false

    private fun initBarInsets() {
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            navInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navInsetState.intValue = navInset
            applyBarStyle()
            insets
        }
        content.post {
            ViewCompat.getRootWindowInsets(content)?.let {
                navInset = it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                navInsetState.intValue = navInset
                applyBarStyle()
            }
        }
    }

    fun applyBarStyle() {
        val floating = Hawk.get(HawkConfig.BAR_FLOATING, false)
        val showLabels = Hawk.get(HawkConfig.BAR_SHOW_LABELS, true)
        val material = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID)
        floatingState.value = floating
        showLabelsState.value = showLabels
        materialState.value = material
        navInsetState.intValue = navInset
    }

    override fun onMainScrollDelta(dy: Int) {
        if (lastViewedTipState.value != null && (dy > 8 || dy < -8)) {
            lastViewedTipState.value = null
        }
        if (currentTabState.intValue == 0) {
            homeFragment.onHomeContentScroll(dy)
        }
        if (!Hawk.get(HawkConfig.BAR_AUTO_HIDE, false)) return
        if (dy > 8 && barShownState.value) {
            barShownState.value = false
        } else if (dy < -8 && !barShownState.value) {
            barShownState.value = true
        }
    }

    fun showMainBar() {
        barShownState.value = true
    }

    // ---------------- 外观变更广播 ----------------

    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    fun onAppearanceChanged(event: AppearanceChangedEvent) {
        when (event.type) {
            AppearanceChangedEvent.TYPE_STYLE -> applyBarStyle()
            AppearanceChangedEvent.TYPE_RECREATE -> recreate()
        }
    }

    private fun confirmExit() {
        if (System.currentTimeMillis() - exitTime > 2000) {
            ToastUtils.showShort("再按一次退出程序")
            exitTime = System.currentTimeMillis()
        } else {
            ActivityUtils.finishAllActivities(true)
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }
}
