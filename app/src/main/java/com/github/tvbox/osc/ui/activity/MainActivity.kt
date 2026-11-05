package com.github.tvbox.osc.ui.activity

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
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
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.HomeFragment
import com.github.tvbox.osc.ui.fragment.LiveFragment
import com.github.tvbox.osc.ui.fragment.MyFragment
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.view.BarAutoHide
import com.github.tvbox.osc.view.MaterialBarHelper
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.orhanobut.hawk.Hawk
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>(), BarAutoHide.ScrollBarHost {

    companion object {
        /** 通知/页面跳转直达指定底部 tab(0=首页 1=直播 2=我的缓存 3=我的) */
        const val EXTRA_TAB = "extra_tab"
    }

    var fragments = listOf<Fragment>(HomeFragment(), LiveFragment(), CacheFragment(), MyFragment())
    var useCacheConfig = false
    private var exitTime = 0L

    // 底栏显隐状态(滚动隐藏/呼出)
    private var barShown = true
    private var navInset = 0

    // Compose 底栏响应状态
    private val currentTabState = mutableIntStateOf(0)
    private val pageScrollOffsetState = mutableFloatStateOf(-1f)
    private var isVpUserDragging = false
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
        // 启动窗由系统按 manifest 的 LaunchTheme(图标居中背景)绘制, 冷启动期间可见;
        // 真实窗口在 decor 生成前把背景换成纯色, 图标层不会透到内容后面。
        // 注意不能用 setTheme 切主题: 会把 DynamicColors 在 Activity 创建前套上的
        // 动态取色覆盖层一并重置, 整个应用退回静态蓝色板(已踩坑)
        window.setBackgroundDrawableResource(R.color.launch_bg)
        super.onCreate(savedInstanceState)
    }

    override fun init() {

        // 本 Activity 即启动入口, BaseActivity 的 isNormalStart 守卫在 init() 之后执行
        App.getInstance().isNormalStart = true

        useCacheConfig = intent.extras?.getBoolean(IntentKey.CACHE_CONFIG_CHANGED, false)?:false

        mBinding.vp.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return fragments[position]
            }

            override fun getCount(): Int {
                return fragments.size
            }
        }
        // 4 个 tab 视图常驻:默认 offscreenPageLimit=1 时跨页跳转(首页↔我的等)
        mBinding.vp.offscreenPageLimit = 1
        mBinding.vp.postDelayed({ mBinding.vp.offscreenPageLimit = fragments.size - 1 }, 600)

        // 液态玻璃背板: 屏幕已不显示壁纸, 但 blur/lens 仍需采样源。
        // 采样层改为与主题背景同色系的柔和渐变(不再引用壁纸), alpha 0f 不参与屏幕显示,
        // 由 layerBackdrop 写进 LayerBackdrop 供底栏折射。
        mBinding.composeBottomBar.setContent {
            TvBoxTheme {
                val screenHeight = with(LocalDensity.current) {
                    resources.displayMetrics.heightPixels.toDp()
                }
                val baseColor = MaterialTheme.colorScheme.background
                val surfaceColor = MaterialTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop()

                Box {
                    // 仅作采样源: 与屏幕主题背景一致, 底栏后方不会再出现壁纸花纹。
                    // 全屏高度底对齐使记录层截到的是底栏对应区域。
                    Box(
                        Modifier
                            .matchParentSize()
                            .alpha(0f)
                            .layerBackdrop(backdrop)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(screenHeight)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(surfaceColor, baseColor, surfaceColor)
                                    )
                                )
                        )
                    }

                    LiquidBottomBar(
                        selectedIndex = currentTabState.intValue,
                        onTabSelected = { index ->
                            goToTab(index)
                        },
                        floating = floatingState.value,
                        showLabels = showLabelsState.value,
                        material = materialState.value,
                        navInsetBottom = (navInsetState.intValue / resources.displayMetrics.density).dp,
                        pageScrollFraction = if (pageScrollOffsetState.floatValue >= 0f) pageScrollOffsetState.floatValue else null,
                        backdrop = backdrop
                    )

                    // 「上次观看」悬浮条: matchParentSize 覆盖层 + offset 定位——
                    // 不参与 Box 测量, 否则会把底栏容器撑高、底栏被顶离屏幕底;
                    // 位置 = 底栏(OccupiedHeight 64dp / 悬浮 66dp)上方约 10-12dp
                    Box(Modifier.matchParentSize()) {
                        val navInsetDp = with(LocalDensity.current) { navInsetState.intValue.toDp() }
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
                                .offset(y = -(navInsetDp + 76.dp))
                        )
                    }
                }
            }
        }

        mBinding.vp.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (isVpUserDragging) {
                    pageScrollOffsetState.floatValue = position + positionOffset
                    // 横向滑动切页时收起「上次观看」悬浮条
                    if (lastViewedTipState.value != null && positionOffset > 0f) {
                        lastViewedTipState.value = null
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                currentTabState.intValue = position
                showMainBar() // 切 tab 始终呼出底栏, 避免隐藏状态下迷失
            }

            override fun onPageScrollStateChanged(state: Int) {
                isVpUserDragging = (state == ViewPager.SCROLL_STATE_DRAGGING || state == ViewPager.SCROLL_STATE_SETTLING)
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    isVpUserDragging = false
                    pageScrollOffsetState.floatValue = -1f
                }
            }
        })

        // 悬浮/材质/滚动隐藏装配(导航栏 inset 由本页统一接管, 见 autoNavigationBarInset)
        initBarInsets()
        applyBarStyle()

        // 冷启动窗口背景(launch_bg 浅色)只服务启动动画衔接; 首帧后换成主题背景,
        // 否则底栏让出的导航栏避让区域会透出浅色(深色模式下刺眼)
        mBinding.vp.post { window.setBackgroundDrawableResource(R.color.md_background) }

        goToTab(intent.getIntExtra(EXTRA_TAB, 0))
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(IntentKey.CACHE_CONFIG_CHANGED, false)) {
            useCacheConfig = true
            recreate()
            return
        }
        // singleTop 复用实例(通知点击/缓存页转跳)时切到指定 tab
        goToTab(intent.getIntExtra(EXTRA_TAB, 0))
    }

    /** 切换底部 tab(直播标题栏返回/我的页入口/通知点击等统一入口) */
    fun goToTab(index: Int) {
        if (index < 0 || index >= fragments.size) return
        if (index != 0 && lastViewedTipState.value != null) {
            lastViewedTipState.value = null // 离开主页 tab 时收起「上次观看」
        }
        currentTabState.intValue = index
        mBinding.vp.currentItem = index
    }

    // 主页返回层级复杂(直播层/首页网格栈/双击退出), 预见性模式下也保持自行拦截
    override fun keepBackIntercept(): Boolean = true

    override fun onBackHandled() {
        if (mBinding.vp.currentItem != 0) {
            // 直播 tab 内部有可回退层(侧边设置/全屏)时先消化,否则切回首页 tab
            val liveFragment = fragments[1] as LiveFragment
            if (liveFragment.isUiReady() && liveFragment.handleBack()) {
                return
            }
            mBinding.vp.currentItem = 0
            return
        }
        val homeFragment = fragments[0] as HomeFragment
        if (homeFragment.isAdded && homeFragment.collapseTabsIfExpanded()) {
            return
        }
        if (!homeFragment.isAdded) { // 资源不足销毁重建时未挂载到activity时getChildFragmentManager会崩溃
            confirmExit()
            return
        }
        val childFragments = homeFragment.allFragments
        if (childFragments.isEmpty()) { //加载中(没有tab)
            confirmExit()
            return
        }
        val fragment: Fragment = childFragments[homeFragment.tabIndex]
        if (fragment is GridFragment) { // 首页数据源动态加载的tab
            if (!fragment.restoreView()) { // 有回退的view,先回退(AList等文件夹列表),没有可回退的,返到主页tab
                if (!homeFragment.scrollToFirstTab()) {
                    confirmExit()
                }
            }
        } else {
            confirmExit()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 直播 tab 内遥控器按键(换台/呼出频道列表)交给直播页处理
        if (mBinding.vp.currentItem == 1) {
            val liveFragment = fragments[1] as LiveFragment
            if (liveFragment.isUiReady()) {
                liveFragment.handleKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------- 底栏: 悬浮形态 / 界面材质 / 滚动隐藏 ----------------

    /** BaseVbActivity/BaseActivity 的默认导航栏避让关闭, 由本页把「导航栏 inset + 停靠底栏占位」统一算给 content */
    override fun autoNavigationBarInset(): Boolean = false

    private fun initBarInsets() {
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            navInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navInsetState.intValue = navInset
            applyBarStyle()
            insets
        }
        // insets 沿视图树可能被中间层级错过, 首帧布局后从 window 根兜底再取一次
        content.post {
            ViewCompat.getRootWindowInsets(content)?.let {
                navInset = it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                navInsetState.intValue = navInset
                applyBarStyle()
            }
        }
    }

    /**
     * 按当前设置(悬浮/停靠/材质/是否显示名称)装配 Compose 底栏。
     * 外观材质、高光与透镜折射完全由 Compose 侧 LiquidBottomBar 自治渲染。
     */
    fun applyBarStyle() {
        val floating = Hawk.get(HawkConfig.BAR_FLOATING, false)
        val showLabels = Hawk.get(HawkConfig.BAR_SHOW_LABELS, true)
        val material = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID)
        floatingState.value = floating
        showLabelsState.value = showLabels
        materialState.value = material
        navInsetState.intValue = navInset

        val lp = mBinding.barContainer.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = 0
        lp.rightMargin = 0
        lp.bottomMargin = 0
        mBinding.barContainer.layoutParams = lp
        mBinding.barContainer.elevation = 0f
        mBinding.barContainer.background = null
        mBinding.barContainer.foreground = null
        mBinding.barContainer.clipToOutline = false
        mBinding.barContainer.clipChildren = false
        mBinding.composeBottomBar.clipChildren = false
        (mBinding.barContainer.parent as? ViewGroup)?.clipChildren = false

        mBinding.barContainer.post { updateVpPadding() }
    }

    /** 页面内容对停靠底栏的避让: vp bottom padding = 底栏占位高度 + 导航栏高度 */
    private fun updateVpPadding() {
        val floating = Hawk.get(HawkConfig.BAR_FLOATING, false)
        val extra = if (barShown && !floating) {
            dp(LiquidBarSpec.OccupiedHeightDp) + navInset
        } else {
            0
        }
        if (mBinding.vp.paddingBottom != extra) mBinding.vp.updatePadding(bottom = extra)
    }

    override fun onMainScrollDelta(dy: Int) {
        // 「上次观看」悬浮条: 内容区滚动即收起
        if (lastViewedTipState.value != null && (dy > 8 || dy < -8)) {
            lastViewedTipState.value = null
        }
        if (!Hawk.get(HawkConfig.BAR_AUTO_HIDE, false)) return
        if (dy > 8 && barShown) {
            setBarShown(false)
        } else if (dy < -8 && !barShown) {
            setBarShown(true)
        }
    }

    /** 强制呼出底栏(切 tab 时) */
    fun showMainBar() {
        if (!barShown) setBarShown(true)
    }

    private fun setBarShown(shown: Boolean) {
        if (barShown == shown) return
        barShown = shown
        val container = mBinding.barContainer
        val hideY = container.height + dp(12)
        container.animate()
            .translationY(if (shown) 0f else hideY.toFloat())
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (Hawk.get(HawkConfig.BAR_FLOATING, false)) {
            // 悬浮模式不占内容位, 只动底栏本身
        } else {
            val extra = dp(LiquidBarSpec.OccupiedHeightDp) + navInset
            val from = mBinding.vp.paddingBottom
            val to = if (shown) extra else navInset
            ValueAnimator.ofInt(from, to).apply {
                duration = 220
                addUpdateListener { anim ->
                    mBinding.vp.updatePadding(bottom = anim.animatedValue as Int)
                }
                start()
            }
        }
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    // ---------------- 外观变更广播 ----------------

    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    fun onAppearanceChanged(event: AppearanceChangedEvent) {
        when (event.type) {
            AppearanceChangedEvent.TYPE_STYLE -> applyBarStyle()
            AppearanceChangedEvent.TYPE_RECREATE -> recreate() // 主题色/字体变了, 重建套新覆盖层
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
