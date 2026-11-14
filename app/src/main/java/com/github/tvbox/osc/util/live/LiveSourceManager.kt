package com.github.tvbox.osc.util.live

import android.net.Uri
import android.util.Base64
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.LiveSource
import com.github.tvbox.osc.download.PlayUrlResolver
import com.github.tvbox.osc.event.LiveSourceChangedEvent
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.MD5
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.util.ArrayList
import java.util.LinkedHashMap

object LiveSourceManager {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** 本地直播内容文本缓存目录 */
    private fun getCacheDir(): File {
        val dir = File(Utils.getApp().filesDir, "live_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(url: String): File {
        return File(getCacheDir(), MD5.string2MD5(url) + ".txt")
    }

    /** 获取所有直播源列表，首次为空时自动从历史 LIVE_URL / LIVE_HISTORY 迁移 */
    fun getLiveSources(): MutableList<LiveSource> {
        var list: MutableList<LiveSource>? = Hawk.get(HawkConfig.LIVE_SOURCES, null)
        if (list == null) {
            list = ArrayList()
            val oldLiveUrl: String = Hawk.get(HawkConfig.LIVE_URL, "")
            val history: ArrayList<String> = Hawk.get(HawkConfig.LIVE_HISTORY, ArrayList())

            val urls = ArrayList<String>()
            if (oldLiveUrl.isNotBlank() && !urls.contains(oldLiveUrl)) {
                urls.add(oldLiveUrl)
            }
            for (h in history) {
                if (h.isNotBlank() && !urls.contains(h)) {
                    urls.add(h)
                }
            }

            var selected = Hawk.get(HawkConfig.LIVE_SOURCE_SELECTED, null as String?)
            if (selected == null) {
                selected = if (oldLiveUrl.isNotBlank()) oldLiveUrl else LiveSource.FOLLOW_SUBSCRIPTION
                Hawk.put(HawkConfig.LIVE_SOURCE_SELECTED, selected)
            }

            urls.forEachIndexed { index, url ->
                val source = LiveSource("直播源 " + (index + 1), url)
                source.isChecked = (url == selected)
                list.add(source)
            }
            Hawk.put(HawkConfig.LIVE_SOURCES, list)
        }
        return list
    }

    /** 获取当前生效的直播源 URL（空字符串代表跟随当前订阅源） */
    fun getSelectedSourceUrl(): String {
        return Hawk.get(HawkConfig.LIVE_SOURCE_SELECTED, LiveSource.FOLLOW_SUBSCRIPTION) ?: LiveSource.FOLLOW_SUBSCRIPTION
    }

    var isReloading: Boolean = false
        private set

    /** 设置当前选中的直播源 */
    fun setSelectedSourceUrl(url: String, callback: ((Boolean) -> Unit)? = null) {
        Hawk.put(HawkConfig.LIVE_SOURCE_SELECTED, url)
        val sources = getLiveSources()
        sources.forEach { it.isChecked = (it.url == url) }
        saveLiveSources(sources)
        // 立即通知 UI 正在切换中，及时展示加载态并停止旧流播放
        EventBus.getDefault().post(LiveSourceChangedEvent(url, true))
        reloadLiveChannels(force = true, callback = callback)
    }

    fun saveLiveSources(list: List<LiveSource>) {
        Hawk.put(HawkConfig.LIVE_SOURCES, ArrayList(list))
    }

    fun addLiveSource(name: String, url: String, checked: Boolean): Boolean {
        val list = getLiveSources()
        for (item in list) {
            if (item.url == url) {
                return false
            }
        }
        if (checked) {
            for (item in list) item.isChecked = false
            Hawk.put(HawkConfig.LIVE_SOURCE_SELECTED, url)
        }
        val newSource = LiveSource(name, url).setChecked(checked)
        list.add(newSource)
        saveLiveSources(list)
        if (checked) {
            reloadLiveChannels(force = true)
        }
        return true
    }

    fun deleteLiveSource(url: String) {
        val list = getLiveSources()
        list.removeAll { it.url == url }
        saveLiveSources(list)
        if (getSelectedSourceUrl() == url) {
            setSelectedSourceUrl(LiveSource.FOLLOW_SUBSCRIPTION)
        }
    }

    fun updateLiveSourceName(url: String, newName: String) {
        val list = getLiveSources()
        list.find { it.url == url }?.name = newName
        saveLiveSources(list)
    }

    fun toggleTop(url: String) {
        val list = getLiveSources()
        val item = list.find { it.url == url } ?: return
        item.isTop = !item.isTop
        saveLiveSources(list)
    }

    /** 获取当前订阅源自带直播状态摘要 */
    fun getSubscriptionLiveSummary(): String {
        return if (ApiConfig.get().hasSubscriptionLives()) {
            "当前订阅已包含内置直播"
        } else {
            "当前订阅未包含直播"
        }
    }

    /** 获取当前生效直播源的展示名称 */
    fun getSelectedSourceName(): String {
        val selUrl = getSelectedSourceUrl()
        if (selUrl == LiveSource.FOLLOW_SUBSCRIPTION || selUrl.isEmpty()) {
            return "跟随当前订阅源"
        }
        val match = getLiveSources().find { it.url == selUrl }
        return match?.name ?: "自定义直播源"
    }

    /** 核心重载逻辑：网络/本地拉取 -> 容灾缓存 -> TxtSubscribe解析 -> 载入 ApiConfig -> 派发事件 */
    fun reloadLiveChannels(force: Boolean = false, callback: ((Boolean) -> Unit)? = null) {
        if (isReloading) {
            callback?.invoke(false)
            return
        }
        isReloading = true
        scope.launch {
            try {
                val selectedUrl = getSelectedSourceUrl()
                val success: Boolean

                if (selectedUrl == LiveSource.FOLLOW_SUBSCRIPTION || selectedUrl.isEmpty()) {
                    success = loadFromCurrentSubscription()
                } else {
                    success = loadFromCustomSource(selectedUrl)
                }

                withContext(Dispatchers.Main) {
                    EventBus.getDefault().post(LiveSourceChangedEvent(selectedUrl, false))
                    callback?.invoke(success)
                }
            } finally {
                isReloading = false
            }
        }
    }

    private fun loadFromCurrentSubscription(): Boolean {
        val livesJson = ApiConfig.get().subscriptionLivesJsonArray
        if (livesJson == null || livesJson.size() == 0) {
            ApiConfig.get().loadLives(JsonArray())
            return false
        }

        try {
            val firstElem = livesJson.get(0)
            if (!firstElem.isJsonObject) {
                ApiConfig.get().loadLives(livesJson)
                return true
            }

            val livesObj = firstElem.asJsonObject
            // 提取 EPG
            if (livesObj.has("epg")) {
                val epg = livesObj.get("epg").asString
                if (!epg.isNullOrEmpty()) {
                    Hawk.put(HawkConfig.EPG_URL, epg)
                }
            }

            // 1. 若已经是自带频道列表格式 (group + channels)
            if (livesObj.has("channels") || livesObj.has("group")) {
                ApiConfig.get().loadLives(livesJson)
                return true
            }

            // 2. 若是远程直播 URL (FongMi type 0 或 proxy://)
            var remoteUrl: String? = null
            if (livesObj.has("url")) {
                val rawUrl = livesObj.get("url").asString
                if (rawUrl.startsWith("proxy://")) {
                    val replaced = DefaultConfig.checkReplaceProxy(rawUrl)
                    val ext = Uri.parse(replaced).getQueryParameter("ext")
                    if (!ext.isNullOrEmpty()) {
                        remoteUrl = if (ext.startsWith("http") || ext.startsWith("clan://")) {
                            ext
                        } else {
                            String(Base64.decode(ext, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                        }
                    }
                } else if (rawUrl.startsWith("http") || rawUrl.startsWith("clan://") || rawUrl.startsWith("file://")) {
                    remoteUrl = rawUrl
                }
            }

            if (!remoteUrl.isNullOrEmpty()) {
                val text = fetchSourceText(remoteUrl)
                if (!text.isNullOrEmpty()) {
                    val map = LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>>()
                    TxtSubscribe.parse(map, text)
                    val jsonArray = TxtSubscribe.live2JsonArray(map)
                    ApiConfig.get().loadLives(jsonArray)
                    return true
                }
            }

            // 3. 回退为直接作为 lives 数组加载
            ApiConfig.get().loadLives(livesJson)
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
            return false
        }
    }

    private fun loadFromCustomSource(url: String): Boolean {
        val text = fetchSourceText(url)
        if (text.isNullOrEmpty()) {
            ApiConfig.get().loadLives(JsonArray())
            return false
        }

        try {
            val map = LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>>()
            TxtSubscribe.parse(map, text)
            val jsonArray = TxtSubscribe.live2JsonArray(map)
            ApiConfig.get().loadLives(jsonArray)
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
            return false
        }
    }

    /** 读取文本内容：网络抓取并写缓存；失败时读缓存；本地文件直接读取 */
    private fun fetchSourceText(url: String): String? {
        val cacheFile = getCacheFile(url)

        if (url.startsWith("file://") || url.startsWith("/")) {
            val filePath = if (url.startsWith("file://")) url.substring(7) else url
            val file = File(filePath)
            return if (file.exists() && file.isFile) {
                try {
                    file.readText(Charsets.UTF_8)
                } catch (t: Throwable) {
                    null
                }
            } else null
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            val text = PlayUrlResolver.fetchText(url)
            if (!text.isNullOrEmpty()) {
                try {
                    cacheFile.writeText(text, Charsets.UTF_8)
                } catch (_: Throwable) {
                }
                return text
            }
        }

        // 弱网或拉取失败时尝试读取旧缓存
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return try {
                cacheFile.readText(Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }
}
