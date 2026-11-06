package com.github.tvbox.osc.ui.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.MovieSort
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.dialog.GridFilterDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.UA
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.Calendar

/**
 * 首页分类 1: 主页推荐/豆瓣热门 3 列网格 (纯 Compose 渲染)
 */
@Composable
fun UserCategoryScreen(
    recVod: List<Movie.Video>?,
    bottomPadding: Dp,
    onScrollDelta: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val hotItems = remember { mutableStateOf<List<Movie.Video>>(emptyList()) }
    val loadState = remember { mutableStateOf(LoadState.Loading) }
    val gridState = rememberLazyGridState()

    gridState.ObserveBarHideDeltas { dy ->
        onScrollDelta?.invoke(dy)
    }

    LaunchedEffect(recVod) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (!recVod.isNullOrEmpty()) {
                loadState.value = LoadState.Success
                hotItems.value = recVod
            } else {
                loadState.value = LoadState.Empty
            }
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance()
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DATE)
                val today = String.format("%d%d%d", year, month, day)
                val requestDay = Hawk.get("home_hot_day", "")
                if (requestDay == today) {
                    val json = Hawk.get("home_hot", "")
                    if (!json.isNullOrEmpty()) {
                        val hotMovies = parseHots(json)
                        if (hotMovies.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                loadState.value = LoadState.Success
                                hotItems.value = hotMovies
                            }
                            return@withContext
                        }
                    }
                }
                val doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=$year,$year"
                OkGo.get<String>(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(object : AbsCallback<String>() {
                        override fun onSuccess(response: Response<String>) {
                            val netJson = response.body()
                            Hawk.put("home_hot_day", today)
                            Hawk.put("home_hot", netJson)
                            (context as? Activity)?.runOnUiThread {
                                val videos = parseHots(netJson)
                                if (videos.isNotEmpty()) {
                                    loadState.value = LoadState.Success
                                    hotItems.value = videos
                                } else {
                                    loadState.value = LoadState.Empty
                                }
                            }
                        }

                        override fun convertResponse(response: okhttp3.Response): String {
                            return response.body!!.string()
                        }
                    })
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    if (hotItems.value.isEmpty()) loadState.value = LoadState.Empty
                }
            }
        }
    }

    LoadStateBox(loadState.value, Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(hotItems.value) { vod ->
                VodCardItem(
                    card = VodCard(
                        key = "${vod.sourceKey}:${vod.id}:${vod.name}",
                        name = vod.name,
                        pic = vod.pic.trim(),
                        badge = if (vod.year > 0) vod.year.toString() else "",
                        note = vod.note
                    ),
                    onClick = {
                        if (ApiConfig.get().sourceBeanList.isEmpty()) {
                            ToastUtils.showShort("暂无订阅")
                            return@VodCardItem
                        }
                        val bundle = Bundle()
                        if (!TextUtils.isEmpty(vod.id)) {
                            bundle.putString("id", vod.id)
                            bundle.putString("sourceKey", vod.sourceKey)
                            val intent = Intent(context, DetailActivity::class.java).putExtras(bundle)
                            context.startActivity(intent)
                        } else {
                            bundle.putString("title", vod.name)
                            val intent = Intent(context, FastSearchActivity::class.java).putExtras(bundle)
                            context.startActivity(intent)
                        }
                    },
                    onLongClick = {
                        if (ApiConfig.get().sourceBeanList.isEmpty()) return@VodCardItem
                        val bundle = Bundle().apply { putString("title", vod.name) }
                        val intent = Intent(context, FastSearchActivity::class.java).putExtras(bundle)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

private fun parseHots(json: String?): ArrayList<Movie.Video> {
    val result = ArrayList<Movie.Video>()
    try {
        val infoJson = Gson().fromJson(json, JsonObject::class.java)
        val array: JsonArray = infoJson.getAsJsonArray("data")
        for (ele in array) {
            val obj = ele as JsonObject
            val vod = Movie.Video()
            vod.name = obj.get("title").asString
            vod.note = obj.get("rate").asString
            if (!vod.note.isEmpty()) vod.note += " 分"
            vod.pic = obj.get("cover").asString + "@Referer=https://movie.douban.com/@User-Agent=" + UA.random()
            result.add(vod)
        }
    } catch (_: Throwable) {}
    return result
}

/**
 * 分类控制器: 状态保持、分页加载、AList 等多级文件夹下钻/返回
 */
class GridCategoryController(
    val sortData: MovieSort.SortData,
    val activity: Activity
) {
    val gridItems = mutableStateOf<List<Movie.Video>>(emptyList())
    val loadState = mutableStateOf(LoadState.Loading)
    val canLoadMore = mutableStateOf(true)
    val loadingMore = mutableStateOf(false)
    var page = 1
    var maxPage = 1
    var isLoad = false
    val gridScrollState = LazyGridState()
    val scrollVersion = mutableIntStateOf(0)

    private class LevelSnapshot(
        val sortId: String,
        val items: List<Movie.Video>,
        val page: Int,
        val maxPage: Int,
        val isLoad: Boolean,
        val scrollState: LazyGridState
    )
    private val levels = ArrayList<LevelSnapshot>()
    private var sourceViewModel: SourceViewModel? = null
    private var gridFilterDialog: GridFilterDialog? = null

    fun init() {
        if (sourceViewModel == null) {
            val owner = activity as? ViewModelStoreOwner ?: return
            sourceViewModel = ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())
                .get("grid_${sortData.id}", SourceViewModel::class.java)
            sourceViewModel?.listResult?.observe(activity as LifecycleOwner) { absXml: AbsXml? ->
                loadingMore.value = false
                if (absXml?.movie?.videoList != null && absXml.movie.videoList.isNotEmpty()) {
                    if (page == 1) {
                        loadState.value = LoadState.Success
                        isLoad = true
                        gridItems.value = absXml.movie.videoList
                    } else {
                        gridItems.value = gridItems.value + absXml.movie.videoList
                    }
                    page++
                    maxPage = absXml.movie.pagecount
                    canLoadMore.value = page <= maxPage
                } else {
                    if (page == 1) {
                        loadState.value = LoadState.Empty
                    }
                    canLoadMore.value = false
                }
            }
        }
        loadData()
    }

    fun loadData() {
        if (ApiConfig.get().homeSourceBean?.api == null) {
            loadState.value = LoadState.Empty
            return
        }
        loadState.value = LoadState.Loading
        isLoad = false
        sourceViewModel?.getList(sortData, page)
    }

    fun changeView(id: String, isFolder: Boolean) {
        sortData.flag = if (isFolder) "1" else null
        levels.add(LevelSnapshot(sortData.id, gridItems.value, page, maxPage, isLoad, gridScrollState))
        gridItems.value = emptyList()
        page = 1
        maxPage = 1
        isLoad = false
        canLoadMore.value = true
        loadingMore.value = false
        sortData.id = id
        scrollVersion.intValue++
        loadData()
    }

    fun restoreView(): Boolean {
        if (levels.isEmpty()) return false
        loadState.value = LoadState.Success
        val info = levels.removeAt(levels.size - 1)
        sortData.id = info.sortId
        gridItems.value = info.items
        page = info.page
        maxPage = info.maxPage
        isLoad = info.isLoad
        canLoadMore.value = true
        loadingMore.value = false
        scrollVersion.intValue++
        return true
    }

    fun hasFilters(): Boolean = !sortData.filters.isNullOrEmpty()

    fun showFilter(context: Context) {
        if (!sortData.filters.isNullOrEmpty() && gridFilterDialog == null) {
            gridFilterDialog = GridFilterDialog(context).apply {
                setData(sortData)
                setOnDismiss(GridFilterDialog.Callback {
                    page = 1
                    loadData()
                })
            }
        }
        gridFilterDialog?.show()
    }

    fun getUITag(): Char {
        val flag = sortData.flag
        return if (flag.isNullOrEmpty()) '0' else flag[0]
    }

    fun enableFastSearch(): Boolean {
        val flag = sortData.flag
        return flag == null || flag.length < 2 || flag[1] == '1'
    }

    fun loadMore() {
        if (gridItems.value.isNotEmpty() && canLoadMore.value && !loadingMore.value && page <= maxPage) {
            loadingMore.value = true
            sourceViewModel?.getList(sortData, page)
        }
    }
}

/**
 * 首页分类 2..N: 动态分类数据源 3 列网格 (纯 Compose 渲染)
 */
@Composable
fun GridCategoryScreen(
    sortData: MovieSort.SortData,
    bottomPadding: Dp,
    onScrollDelta: ((Int) -> Unit)? = null,
    onControllerReady: ((GridCategoryController) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val controller = remember(sortData.id) {
        GridCategoryController(sortData, activity ?: Activity()).also {
            onControllerReady?.invoke(it)
        }
    }

    LaunchedEffect(sortData.id) {
        controller.init()
    }

    controller.scrollVersion.intValue
    val scrollState = controller.gridScrollState

    scrollState.ObserveBarHideDeltas { dy ->
        onScrollDelta?.invoke(dy)
    }

    Box(Modifier.fillMaxSize()) {
        LoadStateBox(controller.loadState.value, Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = scrollState,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(controller.gridItems.value) { video ->
                    VodCardItem(
                        card = VodCard(
                            key = "${video.sourceKey}:${video.id}:${video.name}",
                            name = video.name,
                            pic = video.pic.trim(),
                            badge = if (video.year > 0) video.year.toString() else "",
                            note = video.note
                        ),
                        onClick = {
                            val bundle = Bundle().apply {
                                putString("id", video.id)
                                putString("sourceKey", video.sourceKey)
                                putString("title", video.name)
                            }
                            val homeSource = ApiConfig.get().homeSourceBean
                            if (("12".indexOf(controller.getUITag()) != -1) && (video.tag == "folder" || video.tag == "cover")) {
                                controller.changeView(video.id, video.tag == "folder")
                            } else if (homeSource.isQuickSearch() && Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) && controller.enableFastSearch()) {
                                val intent = Intent(context, FastSearchActivity::class.java).putExtras(bundle)
                                context.startActivity(intent)
                            } else {
                                if (TextUtils.isEmpty(video.id) || video.id.startsWith("msearch:")) {
                                    val intent = Intent(context, FastSearchActivity::class.java).putExtras(bundle)
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(context, DetailActivity::class.java).putExtras(bundle)
                                    context.startActivity(intent)
                                }
                            }
                        },
                        onLongClick = {
                            val bundle = Bundle().apply {
                                putString("id", video.id)
                                putString("sourceKey", video.sourceKey)
                                putString("title", video.name)
                            }
                            val intent = Intent(context, FastSearchActivity::class.java).putExtras(bundle)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(scrollState, controller.gridItems.value.size) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= controller.gridItems.value.size - 8) {
                    controller.loadMore()
                }
            }
    }
}
