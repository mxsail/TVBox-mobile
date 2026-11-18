package com.github.tvbox.osc.ui.compose

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.orhanobut.hawk.Hawk
import okhttp3.Response
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 首页 M3 原地展开式搜索控制器
 * 负责搜索状态、网络请求、历史搜索、全网热门、联想词与多源聚合检索
 */
class HomeSearchController(private val fragmentProvider: () -> Fragment?) {
    private val mainHandler = Handler(Looper.getMainLooper())
    val isSearchActive = mutableStateOf(false)
    val showSuggestPage = mutableStateOf(true)
    val inputText = mutableStateOf("")

    val historyWords = mutableStateOf<List<String>>(emptyList())
    val hotWords = mutableStateOf<List<String>>(emptyList())
    val suggestWords = mutableStateOf<List<String>>(emptyList())

    val resultItems = mutableStateOf<List<Movie.Video>>(emptyList())
    val resultVods = HashMap<String, MutableList<Movie.Video>>()
    val sourceTabs = mutableStateOf(listOf("全部显示" to ""))
    val selectedTabKey = mutableStateOf("")
    val loadState = mutableIntStateOf(0) // 0: idle, 1: loading, 2: success, 3: empty

    val isFilterExpandedState = mutableStateOf(false)
    val isSourceTabsExpanded = mutableStateOf(false)

    val checkedKeysState = mutableStateOf<Set<String>>(emptySet())
    val allSearchableSources: List<SourceBean>
        get() = try {
            ApiConfig.get().sourceBeanList.filter { it.isSearchable }
        } catch (_: Throwable) {
            emptyList()
        }

    private var searchExecutorService: ExecutorService? = null
    private val allRunCount = AtomicInteger(0)
    private val spNames = HashMap<String, String>()
    var searchTitle = ""
    var searchFilterKey = ""
    private var mCheckSources: Map<String, String>? = null

    private val sourceViewModel: SourceViewModel?
        get() {
            val frag = fragmentProvider() ?: return null
            return try {
                ViewModelProvider(frag).get(SourceViewModel::class.java)
            } catch (_: Throwable) {
                null
            }
        }

    fun init() {
        mCheckSources = SearchHelper.getSourcesForSearch()
        val allSearchable = allSearchableSources
        checkedKeysState.value = mCheckSources?.keys?.toSet() ?: allSearchable.map { it.key }.toSet()
        initHistorySearch()
    }

    fun openSearch() {
        isSearchActive.value = true
        showSuggestPage.value = true
        init()
        loadHotWords()
    }

    fun closeSearch() {
        cancel()
        isSearchActive.value = false
        showSuggestPage.value = true
        inputText.value = ""
        suggestWords.value = emptyList()
        isFilterExpandedState.value = false
        isSourceTabsExpanded.value = false
        fragmentProvider()?.activity?.let {
            KeyboardUtils.hideSoftInput(it)
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
        fragmentProvider()?.activity?.let {
            KeyboardUtils.hideSoftInput(it)
        }
        cancel()
        searchTitle = title
        resultItems.value = emptyList()
        resultVods.clear()
        searchFilterKey = ""
        selectedTabKey.value = ""
        sourceTabs.value = listOf("全部显示" to "")
        spNames.clear()
        loadState.intValue = 1
        searchResult()
    }

    private fun searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
            }
        } catch (_: Throwable) {
        } finally {
            allRunCount.set(0)
        }
        searchExecutorService = Executors.newFixedThreadPool(10)
        val searchRequestList = ArrayList<SourceBean>()
        try {
            searchRequestList.addAll(ApiConfig.get().sourceBeanList)
            val home = ApiConfig.get().homeSourceBean
            if (home != null) {
                searchRequestList.remove(home)
                searchRequestList.add(0, home)
            }
        } catch (_: Throwable) {}

        val siteKey = ArrayList<String>()
        for (bean in searchRequestList) {
            if (!bean.isSearchable) continue
            if (mCheckSources != null && !mCheckSources!!.containsKey(bean.key)) continue
            siteKey.add(bean.key)
            spNames[bean.name] = bean.key
            allRunCount.incrementAndGet()
        }
        val vm = sourceViewModel ?: return
        for (key in siteKey) {
            searchExecutorService?.execute {
                try {
                    vm.getSearch(key, searchTitle)
                } catch (_: Exception) {}
            }
        }
    }

    fun onSearchResult(absXml: AbsXml?) {
        if (!isSearchActive.value || showSuggestPage.value) return
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size > 0) {
            val data = ArrayList<Movie.Video>()
            for (video in absXml.movie.videoList) {
                if (!matchSearchResult(video.name, searchTitle)) continue
                data.add(video)
                if (!resultVods.containsKey(video.sourceKey)) {
                    resultVods[video.sourceKey] = ArrayList()
                }
                resultVods[video.sourceKey]!!.add(video)
                val name = spNames.entries.firstOrNull { it.value == video.sourceKey }?.key
                if (name != null && sourceTabs.value.none { it.first == name }) {
                    sourceTabs.value = sourceTabs.value + (name to video.sourceKey)
                }
            }
            val current = resultItems.value
            resultItems.value = if (selectedTabKey.value.isEmpty()) current + data else current
            if (loadState.intValue != 2 && resultItems.value.isNotEmpty()) {
                loadState.intValue = 2
            }
        }
        val count = allRunCount.decrementAndGet()
        if (count <= 0) {
            if (resultItems.value.isEmpty()) {
                loadState.intValue = 3
            }
            cancel()
        }
    }

    private fun matchSearchResult(name: String?, keyword: String?): Boolean {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(keyword)) return false
        val kw = keyword!!.trim()
        val arr = kw.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var matchNum = 0
        for (one in arr) {
            if (name!!.contains(one)) matchNum++
        }
        return matchNum == arr.size
    }

    fun displayed(): List<Movie.Video> {
        val key = selectedTabKey.value
        return if (key.isEmpty()) resultItems.value
        else resultVods[key]?.toList() ?: emptyList()
    }

    fun closeFilterAndApply() {
        val allSearchable = allSearchableSources
        if (checkedKeysState.value.isEmpty()) {
            ToastUtils.showShort("未选择搜索源，已自动恢复全选")
            checkedKeysState.value = allSearchable.map { it.key }.toSet()
        }
        val oldKeys = mCheckSources?.keys ?: allSearchable.map { it.key }.toSet()
        val changed = oldKeys != checkedKeysState.value
        val isAll = checkedKeysState.value.size == allSearchable.size
        val map = HashMap<String, String>()
        checkedKeysState.value.forEach { map[it] = "1" }
        mCheckSources = map
        SearchHelper.putCheckedSources(map, isAll)
        isFilterExpandedState.value = false

        if (changed && !showSuggestPage.value && searchTitle.isNotEmpty()) {
            search(searchTitle)
        }
    }

    fun cancel() {
        try {
            OkGo.getInstance().cancelTag("search")
        } catch (_: Throwable) {}
    }

    fun destroy() {
        cancel()
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
            }
        } catch (_: Throwable) {}
    }

    fun handleBack(): Boolean {
        if (!isSearchActive.value) return false
        if (isFilterExpandedState.value) {
            closeFilterAndApply()
            return true
        }
        if (isSourceTabsExpanded.value) {
            isSourceTabsExpanded.value = false
            return true
        }
        if (!showSuggestPage.value) {
            showSuggestPage.value = true
            initHistorySearch()
            return true
        }
        closeSearch()
        return true
    }

    fun initHistorySearch() {
        historyWords.value = loadHistoryList()
    }

    fun loadHotWords() {
        OkGo.get<String>("https://top.baidu.com/api/board?platform=wise&tab=movie")
            .headers("User-Agent", "Mozilla/5.0")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    try {
                        val hots = ArrayList<String>()
                        val root = JsonParser.parseString(response.body()).asJsonObject
                        val cards = root["data"].asJsonObject["cards"].asJsonArray
                        if (cards.size() > 0) {
                            val contentList = cards[0].asJsonObject["content"].asJsonArray
                            if (contentList.size() > 0) {
                                val items = contentList[0].asJsonObject["content"].asJsonArray
                                for (ele in items) {
                                    val title = (ele as JsonObject)["title"].asString.trim()
                                    if (title.isNotEmpty() && !hots.contains(title)) {
                                        hots.add(title)
                                    }
                                }
                            }
                        }
                        if (hots.isNotEmpty()) {
                            mainHandler.post { hotWords.value = hots }
                        }
                    } catch (_: Throwable) {}
                }
                override fun convertResponse(response: Response): String = response.body!!.string()
            })
    }

    fun getSuggest(text: String) {
        if (text.isEmpty()) {
            suggestWords.value = emptyList()
            return
        }
        OkGo.get<String>("https://suggest.video.iqiyi.com/?if=mobile&key=$text")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    val titles = ArrayList<String>()
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        val datas = json["data"].asJsonArray
                        for (data in datas) {
                            val item = data as JsonObject
                            titles.add(item["name"].asString.trim())
                        }
                    } catch (_: Throwable) {}
                    mainHandler.post { suggestWords.value = titles }
                }
                override fun convertResponse(response: Response): String = response.body!!.string()
            })
    }

    fun removeHistoryWord(word: String) {
        val list = loadHistoryList().toMutableList()
        list.remove(word)
        saveHistoryList(list)
        historyWords.value = list.toList()
    }

    fun clearAllHistory() {
        try {
            Hawk.delete(HawkConfig.HISTORY_SEARCH)
            com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").remove("history")
        } catch (_: Throwable) {}
        historyWords.value = emptyList()
    }

    fun saveSearchHistory(searchWord: String?) {
        if (searchWord.isNullOrBlank()) return
        val word = searchWord.trim()
        val list = loadHistoryList().toMutableList()
        list.remove(word)
        list.add(0, word)
        while (list.size > 30) {
            list.removeAt(list.lastIndex)
        }
        saveHistoryList(list)
        historyWords.value = list.toList()
    }

    private fun loadHistoryList(): List<String> {
        return try {
            val raw = Hawk.get<Any?>(HawkConfig.HISTORY_SEARCH)
            when (raw) {
                is String -> {
                    if (raw.isNotBlank()) {
                        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        com.google.gson.Gson().fromJson<List<String>>(raw, type) ?: emptyList()
                    } else emptyList()
                }
                is List<*> -> {
                    raw.mapNotNull { it?.toString() }
                }
                else -> {
                    val spJson = com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").getString("history", "")
                    if (spJson.isNotBlank()) {
                        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        com.google.gson.Gson().fromJson<List<String>>(spJson, type) ?: emptyList()
                    } else emptyList()
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveHistoryList(list: List<String>) {
        try {
            val json = com.google.gson.Gson().toJson(list)
            Hawk.put(HawkConfig.HISTORY_SEARCH, json)
            com.blankj.utilcode.util.SPUtils.getInstance("search_prefs").put("history", json)
        } catch (_: Throwable) {}
    }
}
