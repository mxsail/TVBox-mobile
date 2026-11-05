package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.DoubanSuggestDialog
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog
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
    private val showSuggestPage = mutableStateOf(true) // true=历史/热词页 false=结果页
    private val loadState = mutableIntStateOf(1) // 1=初始 2=内容 3=空
    private val historyWords = mutableStateOf<List<String>>(emptyList())
    private val hotWords = mutableStateOf<List<String>>(emptyList())
    private val inputText = mutableStateOf("")

    private var mSearchSuggestionsDialog: SearchSuggestionsDialog? = null
    private var searchEditText: EditText? = null

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { FastSearchScreen() } }
    }

    override fun init() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        initData()
        initHistorySearch()
        loadHotWords()
    }

    override fun onResume() {
        super.onResume()
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

    // ---------------- 数据流(与旧版一致) ----------------

    private fun filterSearchSource() {
        val allSourceBean = ApiConfig.get().sourceBeanList
        if (allSourceBean.isNotEmpty()) {
            val searchAbleSource: MutableList<SourceBean> = ArrayList()
            for (sourceBean in allSourceBean) {
                if (sourceBean.isSearchable) searchAbleSource.add(sourceBean)
            }
            SearchCheckboxDialog(this, searchAbleSource, mCheckSources).show()
        }
    }

    private fun initData() {
        mCheckSources = SearchHelper.getSourcesForSearch()
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title")
            if (!TextUtils.isEmpty(title)) {
                search(title)
            }
        }
    }

    private fun initHistorySearch() {
        val mSearchHistory: List<String> = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList())
        historyWords.value = mSearchHistory
    }

    private fun loadHotWords() {
        OkGo.get<String>("https://node.video.qq.com/x/api/hot_search")
            .params("channdlId", "0")
            .params("_", System.currentTimeMillis())
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    try {
                        val hots = ArrayList<String>()
                        val itemList = JsonParser.parseString(response.body()).asJsonObject["data"]
                            .asJsonObject["mapResult"].asJsonObject["0"].asJsonObject["listInfo"].asJsonArray
                        for (ele in itemList) {
                            val obj = ele as JsonObject
                            hots.add(
                                obj["title"].asString.trim { it <= ' ' }
                                    .replace("<|>|《|》|-".toRegex(), "")
                                    .split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                            )
                        }
                        runOnUiThread { hotWords.value = hots }
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
                    if (titles.isNotEmpty()) {
                        runOnUiThread { showSuggestDialog(titles) }
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String = response.body!!.string()
            })
    }

    private fun showSuggestDialog(list: List<String>) {
        val anchor = searchEditText ?: return
        if (mSearchSuggestionsDialog == null) {
            mSearchSuggestionsDialog = SearchSuggestionsDialog(this, list) { _, text ->
                LogUtils.d("搜索:$text")
                mSearchSuggestionsDialog!!.dismissWith { search(text) }
            }
            XPopup.Builder(this).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                .atView(anchor)
                .notDismissWhenTouchInView(anchor)
                .isViewMode(true)
                .isRequestFocus(false)
                .setPopupCallback(object : SimpleCallback() {
                    override fun onDismiss(popupView: BasePopupView) {
                        super.onDismiss(popupView)
                        mSearchSuggestionsDialog = null
                    }
                })
                .asCustom(mSearchSuggestionsDialog!!)
                .show()
        } else {
            mSearchSuggestionsDialog!!.updateSuggestions(list)
        }
    }

    private fun saveSearchHistory(searchWord: String?) {
        if (!searchWord.isNullOrEmpty()) {
            val history = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList<String?>())
            if (!history.contains(searchWord)) {
                history.add(0, searchWord)
            } else {
                history.remove(searchWord)
                history.add(0, searchWord)
            }
            if (history.size > 30) {
                history.removeAt(30)
            }
            Hawk.put(HawkConfig.HISTORY_SEARCH, history)
        }
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

    private fun search(title: String?) {
        if (title.isNullOrEmpty()) {
            ToastUtils.showShort("请输入搜索内容")
            return
        }
        searchEditText?.removeTextChangedListener(textWatcher)
        searchEditText?.setText(title)
        searchEditText?.setSelection(title.length)
        searchEditText?.addTextChangedListener(textWatcher)
        inputText.value = title
        if (mSearchSuggestionsDialog != null && mSearchSuggestionsDialog!!.isShow) {
            mSearchSuggestionsDialog!!.dismiss()
        }
        if (!Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {
            saveSearchHistory(title)
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

    /** 输入框 TextWatcher(联想触发), 挂在 AndroidView EditText 上 */
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(editable: Editable?) {
            val text = editable?.toString() ?: ""
            inputText.value = text
            if (TextUtils.isEmpty(text)) {
                mSearchSuggestionsDialog?.dismiss()
                showSuggestPage.value = true
            } else {
                getSuggest(text)
            }
        }
    }

    // ---------------- Compose 界面 ----------------

    @Composable
    private fun FastSearchScreen() {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding() // 顶栏避开状态栏(原 View 版由 ImmersionBar fitsSystemWindows 承担)
                .background(colorScheme.background)
        ) {
            // 顶栏: 返回 + 搜索框 + 过滤 + 搜索按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回",
                        tint = colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(19.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                setBackgroundResource(android.R.color.transparent)
                                setPadding(28, 12, 28, 12)
                                textSize = 14f
                                setSingleLine(true)
                                hint = "搜索"
                                setTextColor(resolveColorOnSurface())
                                setHintTextColor(resolveColorOnSurfaceVariant())
                                imeOptions = EditorInfo.IME_ACTION_SEARCH
                                setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
                                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                        search(text.toString())
                                        true
                                    } else false
                                }
                                addTextChangedListener(textWatcher)
                            }.also { searchEditText = it }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(onClick = { filterSearchSource() }) {
                    Icon(
                        painterResource(R.drawable.ic_filter_list),
                        contentDescription = "搜索源过滤",
                        tint = colorScheme.onSurface
                    )
                }
                IconButton(onClick = { search(searchEditText?.text?.toString()) }) {
                    Icon(
                        painterResource(R.drawable.ic_search_18),
                        contentDescription = "搜索",
                        tint = colorScheme.onSurface
                    )
                }
            }

            if (showSuggestPage.value) {
                SuggestPage()
            } else {
                ResultPage()
            }
        }
    }

    private fun resolveColorOnSurface(): Int =
        com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0)

    private fun resolveColorOnSurfaceVariant(): Int =
        com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

    /** 历史+热词页 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SuggestPage() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (historyWords.value.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "搜索历史",
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painterResource(R.drawable.ic_clear),
                        contentDescription = "清除历史",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                Hawk.put(HawkConfig.HISTORY_SEARCH, ArrayList<Any>())
                                initHistorySearch()
                            }
                    )
                }
                ChipFlow(words = historyWords.value) { search(it) }
            }
            if (hotWords.value.isNotEmpty()) {
                Text(
                    "热门搜索",
                    color = colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp)
                )
                ChipFlow(words = hotWords.value) { search(it) }
            }
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun ChipFlow(words: List<String>, onPick: (String) -> Unit) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            words.forEach { word ->
                Text(
                    word,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .clickable { onPick(word) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    /** 结果页: 源过滤 chips + 列表 */
    @Composable
    private fun ResultPage() {
        Column(Modifier.fillMaxSize()) {
            // 源过滤 chips(横向滚动)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sourceTabs.value.forEach { (name, key) ->
                    val selected = selectedTabKey.value == key
                    Text(
                        name,
                        color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable {
                                selectedTabKey.value = key
                                searchFilterKey = key
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            when {
                loadState.value == 1 -> SearchHint("正在搜索…")
                loadState.value == 3 && displayed().isEmpty() -> SearchHint("暂无搜索结果")
                else -> {
                    val list = displayed()
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(list) { _, video ->
                            ResultRow(video) { openVideo(video) }
                        }
                    }
                }
            }
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
    private fun SearchHint(text: String) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(text, color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
