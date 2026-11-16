package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
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
import com.github.tvbox.osc.ui.compose.HomeCategoryBar
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.VodCard
import com.github.tvbox.osc.ui.compose.VodCardItem
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
    private val isSourceTabsExpanded = mutableStateOf(false)
    private val showSuggestPage = mutableStateOf(true) // true=历史/热词页 false=结果页
    private val loadState = mutableIntStateOf(1) // 1=初始 2=内容 3=空
    private val historyWords = mutableStateOf<List<String>>(emptyList())
    private val hotWords = mutableStateOf<List<String>>(emptyList())
    private val suggestWords = mutableStateOf<List<String>>(emptyList())
    private val inputText = mutableStateOf("")

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        com.gyf.immersionbar.ImmersionBar.with(this)
            .transparentStatusBar()
            .statusBarDarkFont(!com.github.tvbox.osc.util.Utils.isDarkTheme())
            .navigationBarColor(android.R.color.transparent)
            .init()
        // 进页自动拉起输入法: STATE_VISIBLE + EditText requestFocus,
        // (仅 post showSoftInput 会在窗口拿到焦点前被吞掉)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { FastSearchScreen() } }
    }

    override fun onBackHandled() {
        if (isSourceTabsExpanded.value) {
            isSourceTabsExpanded.value = false
            return
        }
        super.onBackHandled()
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
        if (text.isEmpty()) {
            suggestWords.value = emptyList()
            return
        }
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
                    runOnUiThread { suggestWords.value = titles }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String = response.body!!.string()
            })
    }

    private fun removeHistoryWord(word: String) {
        val history = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList<String?>())
        history.remove(word)
        Hawk.put(HawkConfig.HISTORY_SEARCH, history)
        initHistorySearch()
    }

    private fun clearAllHistory() {
        Hawk.put(HawkConfig.HISTORY_SEARCH, ArrayList<Any>())
        initHistorySearch()
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

    fun search(title: String?) {
        if (title.isNullOrEmpty()) {
            ToastUtils.showShort("请输入搜索内容")
            return
        }
        inputText.value = title
        suggestWords.value = emptyList()
        if (!Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {
            saveSearchHistory(title)
            initHistorySearch()
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

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun FastSearchScreen() {
        val isDark = isAppDarkTheme()
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (inputText.value.isEmpty()) {
                kotlinx.coroutines.delay(200)
                try { focusRequester.requestFocus() } catch (_: Throwable) {}
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .statusBarsPadding()
        ) {
            // M3 Expressive SearchBar 胶囊顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 搜索胶囊主容器 (52dp 饱满 Expressive 高度，26dp 连续平滑方圆)
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF1F1F4),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        BasicTextField(
                            value = inputText.value,
                            onValueChange = { newText ->
                                inputText.value = newText
                                if (newText.isEmpty()) {
                                    suggestWords.value = emptyList()
                                    showSuggestPage.value = true
                                } else {
                                    getSuggest(newText)
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search(inputText.value) }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .padding(horizontal = 6.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (inputText.value.isEmpty()) {
                                        Text(
                                            "搜索电影、电视剧、综艺…",
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // 清空按钮
                        if (inputText.value.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    inputText.value = ""
                                    suggestWords.value = emptyList()
                                    showSuggestPage.value = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_clear),
                                    contentDescription = "清空输入",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 搜索按钮
                        IconButton(
                            onClick = { search(inputText.value) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_search_18),
                                contentDescription = "搜索",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // 右侧搜索源过滤按钮 (48dp 圆形)
                Surface(
                    shape = CircleShape,
                    color = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF1F1F4),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { filterSearchSource() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_filter_list),
                            contentDescription = "搜索源过滤",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (showSuggestPage.value) {
                SuggestPage()
            } else {
                ResultPage()
            }
        }
    }

    /** 联想与历史+热词页 */
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun SuggestPage() {
        val isDark = isAppDarkTheme()
        val suggestions = suggestWords.value
        val hasInput = inputText.value.isNotBlank()

        if (hasInput && suggestions.isNotEmpty()) {
            // 实时联想词列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                itemsIndexed(suggestions) { _, suggest ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { search(suggest) }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_search_18),
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            suggest,
                            color = colorScheme.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            // 搜索历史 + 全网热搜
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (historyWords.value.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "搜索历史",
                            color = colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF1F1F4),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { clearAllHistory() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_outline_24),
                                    contentDescription = "清空历史",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "清空",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        historyWords.value.forEach { word ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF4F4F6)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Text(
                                        word,
                                        color = colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        modifier = Modifier.clickable { search(word) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        painterResource(R.drawable.ic_clear),
                                        contentDescription = "删除记录",
                                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .clickable { removeHistoryWord(word) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (hotWords.value.isNotEmpty()) {
                    Text(
                        "全网热门搜索",
                        color = colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 22.dp)
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hotWords.value.forEachIndexed { index, word ->
                            val isTop3 = index < 3
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTop3) colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.6f)
                                else if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF4F4F6),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { search(word) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = if (isTop3) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        word,
                                        color = colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 结果页: 顶部筛选栏(样式与首页分类栏一致，带独立平滑指示器与悬浮下拉筛选，不挤压内容) + 自适应多列卡片网格 */
    @Composable
    private fun ResultPage() {
        Box(Modifier.fillMaxSize()) {
            // 1. 底层: 结果内容区 (顶部留出 38dp 给分类栏常态让位)
            Column(Modifier.fillMaxSize()) {
                if (sourceTabs.value.isNotEmpty()) {
                    Spacer(Modifier.height(38.dp))
                }
                when {
                    loadState.value == 1 && displayed().isEmpty() -> SearchLoadingHint()
                    loadState.value == 3 && displayed().isEmpty() -> SearchEmptyHint()
                    else -> {
                        val list = displayed()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 105.dp),
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list) { video ->
                                val sourceName = try {
                                    ApiConfig.get().getSource(video.sourceKey)?.name ?: ""
                                } catch (_: Exception) { "" }
                                VodCardItem(
                                    card = VodCard(
                                        key = video.id ?: "",
                                        name = video.name ?: "",
                                        pic = video.pic ?: "",
                                        badge = sourceName,
                                        note = video.note
                                    ),
                                    onClick = { openVideo(video) }
                                )
                            }
                        }
                    }
                }
            }

            // 2. 中层: 分类展开时的半透明遮罩 (点击可直接收起展开菜单)
            androidx.compose.animation.AnimatedVisibility(
                visible = isSourceTabsExpanded.value,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0x73000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isSourceTabsExpanded.value = false }
                )
            }

            // 3. 顶层: 悬浮的分类栏 (展开时自然向下悬浮延展，不挤压内容区)
            if (sourceTabs.value.isNotEmpty()) {
                val tabNames = sourceTabs.value.map { it.first }
                val currentTabIdx = sourceTabs.value.indexOfFirst { it.second == selectedTabKey.value }.coerceAtLeast(0)
                HomeCategoryBar(
                    categories = tabNames,
                    selectedIndex = currentTabIdx,
                    onTabSelected = { idx ->
                        if (idx in sourceTabs.value.indices) {
                            val item = sourceTabs.value[idx]
                            selectedTabKey.value = item.second
                            searchFilterKey = item.second
                        }
                    },
                    isExpanded = isSourceTabsExpanded.value,
                    onExpandChanged = { isSourceTabsExpanded.value = it }
                )
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
    private fun SearchLoadingHint() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                "正在聚合搜索各大影视源…",
                color = colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }

    @Composable
    private fun SearchEmptyHint() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_search_18),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                "未找到相关影视结果",
                color = colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "可尝试更换关键字，或点击右上角筛选搜索源",
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
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
