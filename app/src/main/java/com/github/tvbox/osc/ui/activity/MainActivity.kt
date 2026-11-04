package com.github.tvbox.osc.ui.activity

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.ActivityMainBinding
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.ui.fragment.CacheFragment
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.HomeFragment
import com.github.tvbox.osc.ui.fragment.LiveFragment
import com.github.tvbox.osc.ui.fragment.MyFragment
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.view.BarAutoHide
import com.github.tvbox.osc.view.MaterialBarHelper
import com.google.android.material.navigation.NavigationBarView
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

        // NavigationBarView(Material)会在 insets 分发时自动给自己加导航栏避让 padding,
        // 与 BaseActivity 统一的 content padding 叠成双重避让(三键导航下 tab 与导航栏间出现大段空白),
        // 替换为透传监听抵消掉,避让只由 content padding 承担
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.bottomNav) { _, insets -> insets }

        mBinding.vp.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return fragments[position]
            }

            override fun getCount(): Int {
                return fragments.size
            }
        }
        // 4 个 tab 视图常驻:默认 offscreenPageLimit=1 时跨页跳转(首页↔我的等)
        // 每次销毁重建 2 个 tab 视图,BaseVbFragment.init 重跑导致切换掉帧;直播页已有
        // BaseLazyFragment 可见性守卫(切走 pause),常驻安全。
        // 但首帧只用首页(+相邻直播页),4 tab 全量膨胀会拖慢冷启动白屏,
        // 故首帧绘制完再延迟放开到全常驻(立刻放开会在首帧后的关键渲染期
        // 同时膨胀缓存页+我的页两张全屏视图,冷启动后头几秒掉帧)——冷启动快与切 tab 不重建两头兼顾
        mBinding.vp.offscreenPageLimit = 1
        mBinding.vp.postDelayed({ mBinding.vp.offscreenPageLimit = fragments.size - 1 }, 600)

        mBinding.bottomNav.setOnNavigationItemSelectedListener { menuItem: MenuItem ->
            mBinding.vp.setCurrentItem(menuItem.order, false)
            true
        }
        mBinding.vp.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                mBinding.bottomNav.menu.getItem(position).setChecked(true)
                showMainBar() // 切 tab 始终呼出底栏, 避免隐藏状态下迷失
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
        // singleTop 复用实例(通知点击/缓存页转跳)时切到指定 tab
        goToTab(intent.getIntExtra(EXTRA_TAB, 0))
    }

    /** 切换底部 tab(直播标题栏返回/我的页入口/通知点击等统一入口) */
    fun goToTab(index: Int) {
        if (index < 0 || index >= fragments.size) return
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
            applyBarStyle()
            insets
        }
        // insets 沿视图树可能被中间层级错过, 首帧布局后从 window 根兜底再取一次
        content.post {
            ViewCompat.getRootWindowInsets(content)?.let {
                navInset = it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                applyBarStyle()
            }
        }
    }

    /**
     * 按当前设置(悬浮/停靠/材质/是否显示名称)装配底栏外观, 外观变更时经 EventBus 触发重刷。
     * 布局关系: barContainer 贴屏幕底(FrameLayout gravity=bottom, content 无 bottom padding,
     * 若用 content padding 留位会把底栏一起顶离屏幕底, 出现空白带)——
     * 停靠: 导航栏避让由 bottomNav 自身 padding 承担, 底栏背景延伸到屏幕底;
     *      页面内容经 ViewPager 的 bottom padding 避让底栏内容区。
     * 悬浮: 胶囊悬在导航栏上方(bottomMargin=navInset+10dp), 内容从玻璃底栏后滚过。
     */
    fun applyBarStyle() {
        val floating = Hawk.get(HawkConfig.BAR_FLOATING, false)
        val material = MaterialBarHelper.effectiveMaterial()
        val showLabels = Hawk.get(HawkConfig.BAR_SHOW_LABELS, true)
        val lp = mBinding.barContainer.layoutParams as FrameLayout.LayoutParams
        val radius = if (floating) dp(28) else 0
        if (floating) {
            lp.leftMargin = dp(12)
            lp.rightMargin = dp(12)
            lp.bottomMargin = navInset + dp(10)
            mBinding.bottomNav.updatePadding(bottom = 0)
        } else {
            lp.leftMargin = 0
            lp.rightMargin = 0
            lp.bottomMargin = 0
            mBinding.bottomNav.updatePadding(bottom = navInset)
        }
        mBinding.barContainer.layoutParams = lp
        mBinding.barContainer.elevation = if (floating) dp(6).toFloat() else 0f
        if (radius > 0) {
            mBinding.barContainer.clipToOutline = true
            mBinding.barContainer.outlineProvider = MaterialBarHelper.roundedOutline(radius)
        } else {
            mBinding.barContainer.clipToOutline = false
        }
        mBinding.bottomNav.labelVisibilityMode =
            if (showLabels) NavigationBarView.LABEL_VISIBILITY_LABELED
            else NavigationBarView.LABEL_VISIBILITY_UNLABELED
        if (!floating && material == ThemeManager.MATERIAL_SOLID) {
            // 停靠+实底: 即原始形态, 底栏用组件自带 M3 surface 背景(延伸到屏底)
            mBinding.barContainer.background = null
            mBinding.barContainer.foreground = null
        } else {
            // 其余形态: 底栏本体透明, 由容器承载材质
            mBinding.bottomNav.background = null
            if (material == ThemeManager.MATERIAL_SOLID) {
                // 悬浮+实底: 圆角 surface 胶囊
                mBinding.barContainer.background = MaterialBarHelper.solidBarBackground(this, radius)
            } else {
                mBinding.barContainer.background = null
                MaterialBarHelper.applyToBar(mBinding.barContainer, this, radius)
            }
        }
        mBinding.barContainer.post { updateVpPadding() }
    }

    /** 页面内容对停靠底栏的避让: vp bottom padding = 底栏内容区高度(height 已含导航栏 padding, 减掉) */
    private fun updateVpPadding() {
        val floating = Hawk.get(HawkConfig.BAR_FLOATING, false)
        val extra = if (barShown && !floating) {
            (mBinding.bottomNav.height - navInset).coerceAtLeast(0)
        } else {
            0
        }
        if (mBinding.vp.paddingBottom != extra) mBinding.vp.updatePadding(bottom = extra)
    }

    override fun onMainScrollDelta(dy: Int) {
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
        val lp = container.layoutParams as FrameLayout.LayoutParams
        val hideY = container.height + dp(4)
        container.animate()
            .translationY(if (shown) 0f else hideY.toFloat())
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (Hawk.get(HawkConfig.BAR_FLOATING, false)) {
            // 悬浮模式不占内容位, 只动底栏本身
        } else {
            // 停靠模式收起时把内容区占位还给内容(动画同步); 导航栏区被底栏背景
            // 常驻填充, 不参与动画
            val extra = (mBinding.bottomNav.height - navInset).coerceAtLeast(0)
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
