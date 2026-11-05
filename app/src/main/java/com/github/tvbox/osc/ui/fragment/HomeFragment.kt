package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.widget.TextView
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.api.ApiConfig.LoadConfigCallback
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.base.BaseVbFragment
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.MovieSort.SortData
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.FragmentHomeBinding
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.compose.HomeCategoryBar
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.LastViewedDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.TipDialog
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HeavyTaskUtil
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.lxj.xpopup.XPopup
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : BaseVbFragment<FragmentHomeBinding>() {

    private val selectedTabState = mutableIntStateOf(0)
    private val categoriesState = mutableStateOf<List<String>>(listOf("主页"))
    private val materialState = mutableStateOf(Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID))
    private val isTabsExpandedState = mutableStateOf(false)

    fun collapseTabsIfExpanded(): Boolean {
        if (isTabsExpandedState.value) {
            setTabsExpanded(false)
            return true
        }
        return false
    }

    private fun setTabsExpanded(expanded: Boolean) {
        if (isTabsExpandedState.value == expanded) return
        isTabsExpandedState.value = expanded
        if (expanded) {
            mBinding.tabMaskView.visibility = android.view.View.VISIBLE
            mBinding.tabMaskView.animate().alpha(1f).setDuration(240).start()
        } else {
            mBinding.tabMaskView.animate().alpha(0f).setDuration(200).withEndAction {
                mBinding.tabMaskView.visibility = android.view.View.GONE
            }.start()
        }
    }

    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
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

    /**
     * 提供给主页返回操作
     */
    val allFragments: List<BaseLazyFragment>
        get() = fragments

    private var sourceViewModel: SourceViewModel? = null
    private val fragments: MutableList<BaseLazyFragment> = ArrayList()
    private val mHandler = Handler()

    /**
     * 顶部tabs分类集合,用于渲染tab页,每个tab对应fragment内的数据
     */
    private var mSortDataList: List<SortData> = ArrayList()

    /**
     * 「展开全部」弹窗用的分类名列表(与 fragments 一一对应, 含/不含主页 tab 与 HOME_REC 设置一致)
     */
    private var allCategoryNames: List<String> = ArrayList()
    private var categoryDropdown: com.github.tvbox.osc.view.CategoryDropdownPopup? = null
    private var dataInitOk = false
    private var jarInitOk = false

    var errorTipDialog: TipDialog? = null

    /**
     * true: 配置变更重载
     * false: 全部重载(api变更、重启app等)
     */
    var onlyConfigChanged = false

    override fun init() {
        ControlManager.get().startServer()
        mBinding.nameContainer.setOnClickListener {
            if (dataInitOk && jarInitOk) {
                showSiteSwitch()
            } else {
                ToastUtils.showShort("数据源未加载，长按刷新或切换订阅")
            }
        }
        mBinding.nameContainer.setOnLongClickListener {
            refreshHomeSources()
            true
        }
        mBinding.search.setOnClickListener {
            jumpActivity(FastSearchActivity::class.java)
        }
        // 右侧固定「展开全部分类」按钮 → 自分类栏向下展开的 M3 网格下拉
        mBinding.btnExpandCategories.setOnClickListener { v ->
            FastClickCheckUtil.check(v)
            if (allCategoryNames.isEmpty()) {
                ToastUtils.showShort("分类未加载")
                return@setOnClickListener
            }
            if (categoryDropdown == null) {
                categoryDropdown = com.github.tvbox.osc.view.CategoryDropdownPopup(
                    requireActivity(), allCategoryNames, mBinding.mViewPager.currentItem
                ) { position ->
                    mBinding.mViewPager.setCurrentItem(position, false)
                }
            }
            categoryDropdown?.show(v)
        }
        mBinding.tabMaskView.setOnClickListener {
            setTabsExpanded(false)
        }
        mBinding.mViewPager.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                selectedTabState.intValue = position
            }
        })
        mBinding.composeTabs.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        mBinding.composeTabs.setContent {
            TvBoxTheme {
                HomeCategoryBar(
                    categories = categoriesState.value,
                    selectedIndex = selectedTabState.intValue,
                    onTabSelected = { pos ->
                        selectedTabState.intValue = pos
                        mBinding.mViewPager.setCurrentItem(pos, false)
                    },
                    material = materialState.value,
                    isExpanded = isTabsExpandedState.value,
                    onExpandChanged = { expanded ->
                        setTabsExpanded(expanded)
                    }
                )
            }
        }
        setLoadSir(mBinding.contentLayout)
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
        }
    }

    private fun initData() {
        val mainActivity = mActivity as MainActivity
        onlyConfigChanged = mainActivity.useCacheConfig

        val home = ApiConfig.get().homeSourceBean
        if (home != null && !home.name.isNullOrEmpty()) {
            mBinding.tvName.text = home.name
            mBinding.tvName.postDelayed({ mBinding.tvName.isSelected = true }, 2000)
        }

        showLoading()
        // 首帧性能: 本方法在首页视图实例化(应用首帧布局)期间执行, 而 loadConfig/loadJar 首次
        // 触碰 OkGo/OkHttp 类栈并构建请求(实测数百 ms), jar 缓存命中时 DexClassLoader 加载
        // 也在此链路上——全部挪到后台线程执行。回调统一经 mHandler(主线程)回到本类,
        // 缓存命中/空订阅的同步回调也只做 mHandler.post, 线程安全。
        HeavyTaskUtil.executeNewTask {
            when{
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

    private fun loadConfig(){
        ApiConfig.get().loadConfig(onlyConfigChanged, object : LoadConfigCallback {

            override fun retry() {
                mHandler.post { initData() }
            }

            override fun success() {
                dataInitOk = true
                if (ApiConfig.get().spider.isEmpty()) {
                    jarInitOk = true
                }
                mHandler.postDelayed({ initData() }, 50)
            }

            override fun error(msg: String) {
                if (msg.equals("-1", ignoreCase = true)) {
                    mHandler.post {
                        dataInitOk = true
                        jarInitOk = true
                        initData()
                    }
                } else {
                    showTipDialog(msg)
                }
            }
        }, activity)
    }

    private fun loadJar(){
        if (!ApiConfig.get().spider.isNullOrEmpty()) {
            ApiConfig.get().loadJar(
                onlyConfigChanged,
                ApiConfig.get().spider,
                object : LoadConfigCallback {
                    override fun success() {
                        jarInitOk = true
                        mHandler.postDelayed({
                            if (!onlyConfigChanged) {
                                queryHistory()
                            }
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
            fragments.clear()
            for (data in mSortDataList) {
                if (data.id == "my0") { //tab是主页,添加主页fragment 根据设置项显示豆瓣热门/站点推荐(每个源不一样)/历史记录
                    if (Hawk.get(
                            HawkConfig.HOME_REC,
                            0
                        ) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size > 0
                    ) { //站点推荐
                        fragments.add(UserFragment.newInstance(absXml.videoList))
                    } else { //豆瓣热门/历史记录
                        fragments.add(UserFragment.newInstance(null))
                    }
                } else { //来自源的分类
                    fragments.add(GridFragment.newInstance(data))
                }
            }
            if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) { //关闭主页
                fragments.removeAt(0)
            }

            // 供「展开全部」使用的分类名(与 fragments 一一对应, HOME_REC=2 时同样去掉主页)
            val names = ArrayList<String>()
            for (data in mSortDataList) names.add(data.name)
            if (Hawk.get(HawkConfig.HOME_REC, 0) == 2 && names.isNotEmpty()) names.removeAt(0)
            allCategoryNames = names
            categoryDropdown = null

            // 驱动 Compose 响应式刷新分类栏 (彻底解决异步加载分类不展示的问题)
            categoriesState.value = names.toList()

            //重新渲染vp
            mBinding.mViewPager.adapter =
                object : FragmentStatePagerAdapter(getChildFragmentManager()) {
                    override fun getItem(position: Int): Fragment {
                        return fragments[position]
                    }

                    override fun getCount(): Int {
                        return fragments.size
                    }
                }
        }
    }

    /**
     * 提供给主页返回操作
     */
    fun scrollToFirstTab(): Boolean {
        return if (selectedTabState.intValue != 0) {
            selectedTabState.intValue = 0
            mBinding.mViewPager.setCurrentItem(0, false)
            true
        } else {
            false
        }
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun showSiteSwitch() {
        val sites = ApiConfig.get().sourceBeanList
        if (sites.size > 0) {
            val dialog = SelectDialog<SourceBean>(requireActivity())
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
        val intent = Intent(requireActivity(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        val bundle = Bundle()
        bundle.putBoolean(IntentKey.CACHE_CONFIG_CHANGED, true)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ControlManager.get().stopServer()
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

            // 查询完成后更新UI
            if (vodInfoList.isNotEmpty() && vodInfoList[0] != null) {
                XPopup.Builder(context).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                    .hasShadowBg(false)
                    .isDestroyOnDismiss(true)
                    .isCenterHorizontal(true)
                    .isTouchThrough(true)
                    .offsetY(ScreenUtils.getAppScreenHeight() - 360)
                    .asCustom(LastViewedDialog(requireContext(), vodInfoList[0]))
                    .show()
                    .delayDismiss(4000)
            }
        }
    }
}