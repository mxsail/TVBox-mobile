package com.github.tvbox.osc.ui.fragment

import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.GsonUtils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseComposeLazyFragment
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.compose.BarAutoHideHost
import com.github.tvbox.osc.ui.compose.LoadStateBox
import com.github.tvbox.osc.ui.compose.ObserveBarHideDeltas
import com.github.tvbox.osc.ui.compose.VodCard
import com.github.tvbox.osc.ui.compose.VodCardItem
import com.github.tvbox.osc.ui.dialog.GridFilterDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.launch

/**
 * 分类内容网格(Compose 渲染层): 多级文件夹栈——每层独立的列表/分页/滚动状态。
 * 业务链路(SourceViewModel 分页加载/AList 等文件夹下钻/筛选弹窗/聚合搜索判断)
 * 原样保留, 原 Stack<GridInfo> 视图栈改为快照栈(LazyGridState 保留滚动位置)。
 */
class GridFragment : BaseComposeLazyFragment() {

    private var sortData: MovieSort.SortData? = null
    private var sourceViewModel: SourceViewModel? = null
    private var gridFilterDialog: GridFilterDialog? = null

    // 当前层状态(原 gridAdapter 数据 + page/maxPage/isLoad 字段)
    private val gridItems = mutableStateOf<List<Movie.Video>>(emptyList())
    private val canLoadMore = mutableStateOf(true)
    private val loadingMore = mutableStateOf(false)
    private var page = 1
    private var maxPage = 1
    private var isLoad = false
    private var isTop = true
    private var gridScrollState = LazyGridState()

    /** 层级内容变更触发重组(切层/还原时递增, 用于让组合订阅新的滚动状态) */
    private val gridScrollVersion = mutableIntStateOf(0)

    /**
     * 层栈快照(原 Stack<GridInfo>): 下钻时保存当前层数据与滚动状态,
     * 返回时还原, 滚动位置经 LazyGridState 保持。
     */
    private class LevelSnapshot(
        val sortId: String,
        val items: List<Movie.Video>,
        val page: Int,
        val maxPage: Int,
        val isLoad: Boolean,
        val scrollState: LazyGridState
    )

    private val levels = ArrayList<LevelSnapshot>()

    companion object {
        @JvmStatic
        fun newInstance(sortData: MovieSort.SortData): GridFragment {
            return GridFragment().apply { this.sortData = sortData }
        }
    }

    override fun getLayoutResID(): Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null && this.sortData == null) {
            //activity销毁再进入,会直接恢复fragment,从而直接getList,导致sortData为空闪退
            this.sortData = GsonUtils.fromJson(savedInstanceState.getString("sortDataJson"), MovieSort.SortData::class.java)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("sortDataJson", GsonUtils.toJson(sortData))
    }

    override fun init() {
        initViewModel()
        initData()
    }

    private fun changeView(id: String, isFolder: Boolean) {
        val sd = sortData!!
        sd.flag = if (isFolder) "1" else null // 修改sortData.flag
        pushCurrentLevel()
        gridScrollState = LazyGridState()
        gridScrollVersion.intValue++
        gridItems.value = emptyList()
        page = 1
        maxPage = 1
        isLoad = false
        canLoadMore.value = true
        loadingMore.value = false
        sd.id = id // 修改sortData.id为新的ID
        initData()
    }

    // 获取当前页面UI的显示模式 '0' 正常模式 '1' 文件夹模式 '2' 显示缩略图的文件夹模式
    fun getUITag(): Char {
        val sd = sortData ?: return '0'
        val flag = sd.flag
        return if (flag.isNullOrEmpty()) '0' else flag[0]
    }

    // 是否允许聚合搜索 sortData.flag 的第二个字符为'1'时允许聚搜
    fun enableFastSearch(): Boolean {
        val flag = sortData?.flag
        return flag == null || flag.length < 2 || flag[1] == '1'
    }

    fun isLoad(): Boolean {
        return isLoad || levels.isNotEmpty() //如果有缓存页的话也可以认为是加载了数据的
    }

    /** 当前分类是否有筛选条件(驱动底栏右侧筛选玻璃钮显隐) */
    fun hasFilters(): Boolean = !sortData?.filters.isNullOrEmpty()

    fun isTop(): Boolean {
        return isTop
    }

    // 保存当前层
    private fun pushCurrentLevel() {
        val sd = sortData!!
        levels.add(LevelSnapshot(sd.id, gridItems.value, page, maxPage, isLoad, gridScrollState))
    }

    // 丢弃当前页面，将页面还原成上一个保存的页面(MainActivity 返回键入口)
    fun restoreView(): Boolean {
        if (levels.isEmpty()) return false
        showSuccess()
        val info = levels.removeAt(levels.size - 1)
        sortData!!.id = info.sortId
        gridItems.value = info.items
        page = info.page
        maxPage = info.maxPage
        isLoad = info.isLoad
        canLoadMore.value = true
        loadingMore.value = false
        gridScrollState = info.scrollState
        gridScrollVersion.intValue++
        return true
    }

    private fun initViewModel() {
        if (sourceViewModel != null) return
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel!!.listResult.observe(this) { absXml: AbsXml? ->
            loadingMore.value = false
            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size > 0) {
                if (page == 1) {
                    showSuccess()
                    isLoad = true
                    gridItems.value = absXml.movie.videoList
                } else {
                    gridItems.value = gridItems.value + absXml.movie.videoList
                }
                page++
                maxPage = absXml.movie.pagecount

                if (page > maxPage) {
                    canLoadMore.value = false
                    if (page > 2) Toast.makeText(context, "没有更多了", Toast.LENGTH_SHORT).show()
                } else {
                    canLoadMore.value = true
                }
            } else {
                if (page == 1) {
                    showEmpty()
                } else {
                    Toast.makeText(context, "没有更多了", Toast.LENGTH_SHORT).show()
                }
                canLoadMore.value = false
            }
        }
    }

    private fun initData() {
        if (ApiConfig.get().homeSourceBean.api == null) { // 系统杀死app恢复缓存的fragment后会直接getList,此时首页api都未加载完
            showEmpty()
            return
        }
        showLoading()
        isLoad = false
        scrollTop()
        sourceViewModel!!.getList(sortData!!, page)
    }

    fun scrollTop() {
        isTop = true
        val state = gridScrollState
        lifecycleScope.launch { state.scrollToItem(0) }
    }

    fun showFilter() {
        val sd = sortData
        if (sd != null && !sd.filters.isEmpty() && gridFilterDialog == null) {
            gridFilterDialog = GridFilterDialog(mContext).apply {
                setData(sd)
                setOnDismiss(GridFilterDialog.Callback {
                    page = 1
                    initData()
                })
            }
        }
        gridFilterDialog?.show()
    }

    // ---------------- 点击行为(原 GridAdapter 监听器) ----------------

    private fun onGridItemClick(video: Movie.Video, clickView: android.view.View) {
        FastClickCheckUtil.check(clickView)
        if (video == null) return
        val bundle = Bundle()
        bundle.putString("id", video.id)
        bundle.putString("sourceKey", video.sourceKey)
        bundle.putString("title", video.name)
        val homeSourceBean = ApiConfig.get().homeSourceBean
        if (("12".indexOf(getUITag()) != -1) && (video.tag == "folder" || video.tag == "cover")) {
            changeView(video.id, video.tag == "folder")
        } else if (homeSourceBean.isQuickSearch() && Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) && enableFastSearch()) {
            jumpActivity(FastSearchActivity::class.java, bundle)
        } else {
            if (TextUtils.isEmpty(video.id) || video.id.startsWith("msearch:")) {
                jumpActivity(FastSearchActivity::class.java, bundle)
            } else {
                jumpActivity(DetailActivity::class.java, bundle)
            }
        }
    }

    private fun onGridItemLongClick(video: Movie.Video, clickView: android.view.View) {
        FastClickCheckUtil.check(clickView)
        if (video == null) return
        val bundle = Bundle()
        bundle.putString("id", video.id)
        bundle.putString("sourceKey", video.sourceKey)
        bundle.putString("title", video.name)
        jumpActivity(FastSearchActivity::class.java, bundle)
    }

    // ---------------- Compose 渲染层 ----------------

    @Composable
    override fun Content() {
        gridScrollVersion.intValue // 订阅层级变更(切层/还原换滚动状态)
        val scrollState = gridScrollState
        val clickView = LocalView.current

        scrollState.ObserveBarHideDeltas { dy ->
            BarAutoHideHost.onMainScrollDelta(mActivity, dy)
        }

        Box(Modifier.fillMaxSize()) {
            LoadStateBox(loadState.value, Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = scrollState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(gridItems.value) { video ->
                        VodCardItem(
                            card = VodCard(
                                key = "${video.sourceKey}:${video.id}:${video.name}",
                                name = video.name,
                                pic = video.pic.trim(),
                                badge = if (video.year > 0) video.year.toString() else "",
                                note = video.note
                            ),
                            onClick = { onGridItemClick(video, clickView) },
                            onLongClick = { onGridItemLongClick(video, clickView) }
                        )
                    }
                }
            }
        }

        // 分页加载: 接近底部触发(原 BaseQuickAdapter onLoadMore)
        LaunchedEffect(scrollState, gridItems.value.size) {
            snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (gridItems.value.isNotEmpty() && last >= gridItems.value.size - 8 &&
                        canLoadMore.value && !loadingMore.value && page <= maxPage
                    ) {
                        loadingMore.value = true
                        sourceViewModel?.getList(sortData!!, page)
                    }
                }
        }
    }
}
