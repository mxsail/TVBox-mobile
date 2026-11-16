package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.AppUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.CacheManager
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.activity.AboutActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.github.tvbox.osc.ui.activity.BackupRestoreActivity
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.LiveSourceActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.ObserveBarHideDeltas
import com.github.tvbox.osc.ui.compose.PosterFileImage
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.UA
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jessyan.autosize.AutoSize
import java.io.File

/**
 * 我的页(Material Design 3 Expressive):
 * 1. 沉浸式 Hero 头部卡片: 应用 Logo + 名称版本 + 关于快捷入口
 * 2. 观看历史卡片: 14dp 平滑圆角横向海报墙 + 渐变高对比度已播胶囊 +「全部 >」胶囊与 Expressive 空态
 * 3. 常用功能 2 列大卡片磁贴 (Feature Tiles): 18dp 圆角、色彩强调底座、主副标题说明
 */
class MyFragment : Fragment() {

    /** 观看历史条目: 记录 + 已持久缓存的海报文件(未下载完为 null) + 当前集已播放时间 */
    private data class HistoryEntry(val vod: VodInfo, val posterFile: File?, val progressText: String?)

    /** null=加载中, empty=无记录, 非空=海报墙数据(fragment 字段跨视图重建保留, 避免首访空白) */
    private val historyItems = mutableStateOf<List<HistoryEntry>?>(null)

    /** 海报持久缓存目录(filesDir, 不受"清空缓存"影响): 一次下载, 长期复用 */
    private fun getPosterDir(context: android.content.Context): File {
        return File(context.filesDir, "history_posters").apply { mkdirs() }
    }

    private val posterHttpClient by lazy { okhttp3.OkHttpClient() }

    /** 海报持久缓存: 已缓存直接返回文件; 未缓存走本地代理下载并落盘 */
    private fun downloadPoster(context: android.content.Context, url: String): File? {
        return try {
            val target = getPosterDir(context).resolve(MD5.string2MD5(url) + ".jpg")
            if (target.exists() && target.length() > 0) return target
            val request = okhttp3.Request.Builder()
                .url(DefaultConfig.checkReplaceProxy(url))
                .header("User-Agent", UA.random())
                .build()
            posterHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes()
                if (bytes == null || bytes.isEmpty()) return null
                target.outputStream().use { it.write(bytes) }
                target
            }
        } catch (t: Throwable) {
            null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity?.let { AutoSize.autoConvertDensity(it, 360f, true) }
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        composeView.setContent { TvBoxTheme { MyScreen() } }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.let { loadHistory(it) }
    }

    override fun onResume() {
        super.onResume()
        context?.let { loadHistory(it) } // 看完剧回来刷新进度
    }

    fun loadHistory(context: android.content.Context) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            // 第一步: 立即装载记录(名称/集数/进度), 海报以占位图先行, 不阻塞任何元素
            val entries = withContext(Dispatchers.IO) {
                try {
                    // 首页墙最多展示最新 10 条: 列表与海报下载都只做 10 份,
                    // 更多记录点卡片头部进观看历史页查看(避免全量拉取拖慢"我的"页)
                    RoomDataManger.getAllVodRecord(10).mapNotNull { vod ->
                        vod ?: return@mapNotNull null
                        HistoryEntry(vod, null, currentProgressText(vod))
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("MyFragment", "loadHistory failed", t)
                    emptyList<HistoryEntry>()
                }
            }
            historyItems.value = entries

            // 第二步: 后台逐个下载/复用海报文件(filesDir 持久缓存), 完成一个刷新一个
            withContext(Dispatchers.IO) {
                entries.forEachIndexed { index, entry ->
                    val file = entry.vod.pic.takeIf { p -> !p.isNullOrEmpty() }
                        ?.let { downloadPoster(context, it) }
                    if (file != null) {
                        historyItems.value = historyItems.value?.mapIndexed { i, e ->
                            if (i == index) e.copy(posterFile = file) else e
                        }
                    }
                }
            }
        }
    }

    /** 当前集已播放时间文本(只显示已播放时间), 无进度返回 null */
    private fun currentProgressText(vod: VodInfo): String? {
        return try {
            val vs = vod.seriesMap[vod.playFlag]?.getOrNull(vod.playIndex)
            val key = vod.sourceKey + vod.id + vod.playFlag + vod.playIndex + (vs?.name ?: "")
            val pos = (CacheManager.getCache(MD5.string2MD5(key)) as? Long) ?: 0L
            if (pos > 0) formatTime(pos) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun jumpActivity(act: android.app.Activity?, clazz: Class<*>, bundle: Bundle? = null) {
        val a = act ?: activity ?: return
        val intent = Intent(a, clazz)
        if (bundle != null) intent.putExtras(bundle)
        a.startActivity(intent)
    }

    private fun goMainTab(act: android.app.Activity?, index: Int) {
        ((act ?: activity) as? MainActivity)?.goToTab(index)
    }

    @Composable
    fun MyScreen(
        bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
        onScrollDelta: ((Int) -> Unit)? = null,
        onGoToTab: ((Int) -> Unit)? = null
    ) {
        val currentContext = androidx.compose.ui.platform.LocalContext.current
        val currentActivity = (activity ?: currentContext as? android.app.Activity)
        val scrollState = rememberScrollState()
        scrollState.ObserveBarHideDeltas { dy ->
            onScrollDelta?.invoke(dy) ?: (currentActivity as? com.github.tvbox.osc.view.BarAutoHide.ScrollBarHost)?.onMainScrollDelta(dy)
        }

        LaunchedEffect(Unit) {
            loadHistory(currentContext)
        }

        DisposableEffect(Unit) {
            val subscriber = object {
                @Subscribe(threadMode = ThreadMode.MAIN)
                fun onRefresh(event: RefreshEvent) {
                    if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
                        loadHistory(currentContext)
                    }
                }
            }
            EventBus.getDefault().register(subscriber)
            onDispose {
                EventBus.getDefault().unregister(subscriber)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(bottom = bottomPadding)
        ) {
            // 1. M3 Expressive Hero 头部卡片
            Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                HeroCard(currentActivity)
            }

            // 2. 观看历史卡片
            Box(Modifier.padding(horizontal = 14.dp)) {
                HistoryCard(currentActivity, onGoToTab)
            }

            Spacer(Modifier.height(14.dp))

            // 3. 常用功能 2 列 Expressive 磁贴
            Box(Modifier.padding(horizontal = 14.dp)) {
                FunctionsTilesCard(currentActivity, onGoToTab)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ---------------- 卡片0: M3 Expressive Hero 头部 ----------------

    @Composable
    private fun HeroCard(currentActivity: android.app.Activity?) {
        val isDark = isAppDarkTheme()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White
            ),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo 底座
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_icon),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(42.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            color = colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                "v${AppUtils.getAppVersionName()}",
                                color = colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "随时随地畅享影音视界",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // 右上角: 进关于页
                IconButton(
                    onClick = { jumpActivity(currentActivity, AboutActivity::class.java) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_about_48),
                        contentDescription = "关于应用",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // ---------------- 卡片1: 观看历史 ----------------

    @Composable
    private fun HistoryCard(currentActivity: android.app.Activity?, onGoToTab: ((Int) -> Unit)?) {
        val isDark = isAppDarkTheme()
        val items = historyItems.value
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                // 头部: 标题 + 数量徽章 + 右侧「全部 >」胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "观看历史",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!items.isNullOrEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                "${items.size}",
                                color = colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 全部胶囊
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF3F3F4),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { jumpActivity(currentActivity, HistoryActivity::class.java) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "全部",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                painterResource(R.drawable.ic_chevron_right),
                                contentDescription = "查看全部",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                when {
                    // 加载中: 海报骨架占位
                    items == null -> Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(108.dp)
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colorScheme.surfaceContainerHigh)
                            )
                        }
                    }

                    // 空态: Expressive 空态提示卡 +「去逛逛」引导
                    items.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_history_48),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            "暂无观看记录",
                            color = colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            "在首页挑选心仪的影视剧集开启观看吧",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = { onGoToTab?.invoke(0) ?: goMainTab(currentActivity, 0) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("去发现好片", fontSize = 12.sp)
                        }
                    }

                    // 历史记录横向海报墙
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items) { entry ->
                            HistoryPoster(currentActivity, entry)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HistoryPoster(currentActivity: android.app.Activity?, entry: HistoryEntry) {
        Column(
            modifier = Modifier
                .width(108.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    val bundle = Bundle().apply {
                        putString("id", entry.vod.id)
                        putString("sourceKey", entry.vod.sourceKey)
                    }
                    jumpActivity(currentActivity, DetailActivity::class.java, bundle)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceContainerHigh)
            ) {
                PosterFileImage(entry.posterFile, entry.vod.pic, Modifier.fillMaxSize())
                entry.progressText?.let { progress ->
                    // 右下角: 播放进度精致胶囊
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x99000000), Color(0xCC000000))
                                ),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            progress,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
            // 名称 + 集数
            Text(
                entry.vod.name,
                color = colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (!entry.vod.playNote.isNullOrEmpty()) {
                Text(
                    entry.vod.playNote,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }

    // ---------------- 卡片2: 常用功能 2 列 Expressive 磁贴 ----------------

    private data class FeatureTile(
        val icon: Int,
        val title: String,
        val subtitle: String,
        val accentColor: Color,
        val action: () -> Unit
    )

    @Composable
    private fun FunctionsTilesCard(currentActivity: android.app.Activity?, onGoToTab: ((Int) -> Unit)?) {
        val tiles = listOf(
            FeatureTile(
                icon = R.drawable.ic_collect_48,
                title = "我的收藏",
                subtitle = "常看与心仪剧集",
                accentColor = Color(0xFFE91E63)
            ) { jumpActivity(currentActivity, CollectActivity::class.java) },
            FeatureTile(
                icon = R.drawable.ic_download_nav,
                title = "我的下载",
                subtitle = "离线离网本地缓存",
                accentColor = Color(0xFF1E88E5)
            ) { onGoToTab?.invoke(2) ?: goMainTab(currentActivity, 2) },
            FeatureTile(
                icon = R.drawable.ic_database_48,
                title = "订阅源",
                subtitle = "多源站点接口配置",
                accentColor = Color(0xFF43A047)
            ) { jumpActivity(currentActivity, SubscriptionActivity::class.java) },
            FeatureTile(
                icon = R.drawable.ic_live_tv_48,
                title = "直播源",
                subtitle = "电视网络直播列表",
                accentColor = Color(0xFFFB8C00)
            ) { jumpActivity(currentActivity, LiveSourceActivity::class.java) },
            FeatureTile(
                icon = R.drawable.ic_sync_48,
                title = "备份与同步",
                subtitle = "WebDAV/快照管理",
                accentColor = Color(0xFF8E24AA)
            ) { jumpActivity(currentActivity, BackupRestoreActivity::class.java) },
            FeatureTile(
                icon = R.drawable.ic_settings,
                title = "系统设置",
                subtitle = "播放内核与偏好",
                accentColor = Color(0xFF546E7A)
            ) { jumpActivity(currentActivity, SettingActivity::class.java) }
        )

        GroupCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Text(
                    "常用功能",
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)
                )

                // 2 列网格
                tiles.chunked(2).forEachIndexed { index, rowTiles ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            FeatureTileItem(tile, Modifier.weight(1f))
                        }
                        if (rowTiles.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FeatureTileItem(tile: FeatureTile, modifier: Modifier) {
        val isDark = isAppDarkTheme()
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) colorScheme.surfaceContainerHigh.copy(alpha = 0.6f) else Color(0xFFF7F7F8),
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { tile.action() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标底座 (带有柔和彩色高亮)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tile.accentColor.copy(alpha = if (isDark) 0.22f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(tile.icon),
                        contentDescription = tile.title,
                        tint = if (isDark) tile.accentColor.copy(alpha = 0.9f) else tile.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        tile.title,
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tile.subtitle,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

