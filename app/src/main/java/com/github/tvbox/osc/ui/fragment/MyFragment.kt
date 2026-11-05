package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.AppUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.CacheManager
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.ui.activity.AboutActivity
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.PosterImage
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.util.MD5
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jessyan.autosize.AutoSize

/**
 * 我的页(Compose): 观看历史卡片(横向海报墙, 右下角已播放时间, 下方名称+集数) +
 * 常用功能卡片(图标按钮网格)。头部点击进观看历史页。
 */
class MyFragment : Fragment() {

    /** 观看历史条目: 记录 + 当前集已播放时间(无进度为 null) */
    private data class HistoryEntry(val vod: VodInfo, val progressText: String?)

    /** null=加载中, empty=无记录, 非空=海报墙数据(fragment 字段跨视图重建保留, 避免首访空白) */
    private val historyItems = mutableStateOf<List<HistoryEntry>?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AutoSize.autoConvertDensity(activity, 360f, true)
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        composeView.setContent { TvBoxTheme { MyScreen() } }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory() // 看完剧回来刷新进度
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    RoomDataManger.getAllVodRecord(20).mapNotNull { vod ->
                        vod ?: return@mapNotNull null
                        // 预热海报缓存: 首次进入我的页即有图可看, 不等网络逐张加载
                        vod.pic.takeIf { p -> !p.isNullOrEmpty() }?.let {
                            runCatching { com.squareup.picasso.Picasso.get().load(it).fetch() }
                        }
                        HistoryEntry(vod, currentProgressText(vod))
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("MyFragment", "loadHistory failed", t)
                    emptyList<HistoryEntry>()
                }
            }
            historyItems.value = list
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

    private fun jumpActivity(clazz: Class<*>) {
        startActivity(Intent(requireActivity(), clazz))
    }

    private fun jumpActivity(clazz: Class<*>, bundle: Bundle) {
        startActivity(Intent(requireActivity(), clazz).putExtras(bundle))
    }

    private fun goMainTab(index: Int) {
        (requireActivity() as? MainActivity)?.goToTab(index)
    }

    @Composable
    private fun MyScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(
                    com.github.tvbox.osc.ui.compose.AppUiBackdrop.layer.value?.let {
                        Modifier.layerBackdrop(it)
                    } ?: Modifier
                )
        ) {
            Text(
                stringResource(R.string.app_name),
                color = colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 20.dp, top = 40.dp, bottom = 12.dp)
            )
            HistoryCard()
            Spacer(Modifier.height(12.dp))
            FunctionsCard()
            Text(
                "v" + AppUtils.getAppVersionName(),
                color = colorScheme.outline,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .fillMaxWidth()
            )
        }
    }

    // ---------------- 卡片1: 观看历史 ----------------

    @Composable
    private fun HistoryCard() {
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                // 头部: 标题 + 右箭头, 点击进观看历史页
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { jumpActivity(HistoryActivity::class.java) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "观看历史",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painterResource(R.drawable.ic_arrow_down),
                        contentDescription = "查看全部",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(-90f) // 向下箭头转为向右
                    )
                }
                val items = historyItems.value
                when {
                    // 加载中: 海报骨架占位, 避免首访出现整块空白
                    items == null -> Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(104.dp)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.surfaceContainerHigh)
                            )
                        }
                    }
                    items.isEmpty() -> Text(
                        "暂无观看记录",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items) { entry ->
                            HistoryPoster(entry)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HistoryPoster(entry: HistoryEntry) {
        Column(
            modifier = Modifier
                .width(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val bundle = Bundle().apply {
                        putString("id", entry.vod.id)
                        putString("sourceKey", entry.vod.sourceKey)
                    }
                    jumpActivity(DetailActivity::class.java, bundle)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainerHigh)
            ) {
                PosterImage(entry.vod.pic, Modifier.fillMaxSize())
                entry.progressText?.let { progress ->
                    // 右下角: 已播放时间
                    Text(
                        progress,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .background(Color(0x99000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            // 名称 + 集数(若有)
            Text(
                entry.vod.name,
                color = colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
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

    // ---------------- 卡片2: 常用功能 ----------------

    private data class FuncEntry(val icon: Int, val label: String, val action: () -> Unit)

    @Composable
    private fun FunctionsCard() {
        val funcs = listOf(
            FuncEntry(R.drawable.ic_collect_48, "我的收藏") { jumpActivity(CollectActivity::class.java) },
            FuncEntry(R.drawable.ic_cache_48, "我的缓存") { goMainTab(2) },
            FuncEntry(R.drawable.ic_live_tv_48, "直播") { goMainTab(1) },
            FuncEntry(R.drawable.ic_database_48, "订阅管理") { jumpActivity(SubscriptionActivity::class.java) },
            FuncEntry(R.drawable.ic_settings, "设置") { jumpActivity(SettingActivity::class.java) },
            FuncEntry(R.drawable.ic_about_48, "关于") { jumpActivity(AboutActivity::class.java) }
        )
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "常用功能",
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                funcs.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { func ->
                            FuncButton(func, Modifier.weight(1f))
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FuncButton(func: FuncEntry, modifier: Modifier) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { func.action() }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(func.icon),
                    contentDescription = func.label,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                func.label,
                color = colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
