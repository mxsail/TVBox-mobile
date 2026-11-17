package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseComposeLazyFragment
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.compose.BarAutoHideHost
import com.github.tvbox.osc.ui.compose.ObserveBarHideDeltas
import com.github.tvbox.osc.ui.compose.LoadStateBox
import com.github.tvbox.osc.ui.compose.VodCard
import com.github.tvbox.osc.ui.compose.VodCardItem
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.UA
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import java.util.ArrayList
import java.util.Calendar

/**
 * 主页 tab(Compose 渲染层): 站点推荐/豆瓣热门 3 列海报网格。
 * 数据链路(HOME_REC 设置/豆瓣接口 + Hawk 当日缓存)原样保留, 仅视图换 Compose。
 */
class UserFragment : BaseComposeLazyFragment() {

    private var homeSourceRec: List<Movie.Video>? = null
    private val hotItems = mutableStateOf<List<Movie.Video>>(emptyList())

    companion object {
        @JvmStatic
        fun newInstance(recVod: List<Movie.Video>?): UserFragment {
            return UserFragment().apply { homeSourceRec = recVod }
        }
    }

    override fun getLayoutResID(): Int = 0

    override fun init() {
        initHomeHotVod()
    }

    private fun initHomeHotVod() {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null && homeSourceRec!!.size > 0) {
                showSuccess()
                hotItems.value = homeSourceRec!!
            } else {
                showEmpty()
            }
            return
        }
        try {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DATE)
            val today = String.format("%d%d%d", year, month, day)
            val requestDay = Hawk.get("home_hot_day", "")
            if (requestDay == today) {
                val json = Hawk.get("home_hot", "")
                if (!json.isEmpty()) {
                    val hotMovies = loadHots(json)
                    if (hotMovies.size > 0) {
                        showSuccess()
                        hotItems.value = hotMovies
                        return
                    }
                }
            }
            val doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year
            OkGo.get<String>(doubanUrl)
                .headers("User-Agent", UA.randomOne())
                .execute(object : AbsCallback<String>() {
                    override fun onSuccess(response: Response<String>) {
                        val netJson = response.body()
                        Hawk.put("home_hot_day", today)
                        Hawk.put("home_hot", netJson)
                        mActivity.runOnUiThread {
                            val videos = loadHots(netJson)
                            if (videos.size > 0) {
                                showSuccess()
                                hotItems.value = videos
                            } else {
                                showEmpty()
                            }
                        }
                    }

                    override fun convertResponse(response: okhttp3.Response): String {
                        return response.body!!.string()
                    }
                })
        } catch (th: Throwable) {
            th.printStackTrace()
            if (hotItems.value.isEmpty()) {
                showEmpty()
            }
        }
    }

    private fun loadHots(json: String?): ArrayList<Movie.Video> {
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
        } catch (th: Throwable) {
        }
        return result
    }

    private fun onItemClick(vod: Movie.Video) {
        if (ApiConfig.get().sourceBeanList.isEmpty()) {
            ToastUtils.showShort("暂无订阅")
            return
        }
        val bundle = Bundle()
        if (!TextUtils.isEmpty(vod.id)) {
            bundle.putString("id", vod.id)
            bundle.putString("sourceKey", vod.sourceKey)
            jumpActivity(DetailActivity::class.java, bundle)
        } else {
            bundle.putString("title", vod.name)
            jumpActivity(FastSearchActivity::class.java, bundle)
        }
    }

    private fun onItemLongClick(vod: Movie.Video) {
        if (ApiConfig.get().sourceBeanList.isEmpty()) return
        val bundle = Bundle()
        bundle.putString("title", vod.name)
        jumpActivity(FastSearchActivity::class.java, bundle)
    }

    @Composable
    override fun Content() {
        val gridState = rememberLazyGridState()
        gridState.ObserveBarHideDeltas { dy ->
            BarAutoHideHost.onMainScrollDelta(mActivity, dy)
        }
        LoadStateBox(loadState.value, Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
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
                        onClick = { onItemClick(vod) },
                        onLongClick = { onItemLongClick(vod) }
                    )
                }
            }
        }
    }
}
