package com.github.tvbox.osc.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.download.DownloadTaskManager
import com.github.tvbox.osc.util.HawkConfig
import com.orhanobut.hawk.Hawk

/**
 * Material Design 3 Expressive 下载配置底部抽屉
 * - 同时下载任务数配置 (1, 2, 3, 5)
 * - 仅 Wi-Fi 下下载开关
 * - HLS 单集切片下载并发数 (2, 3, 5)
 * - 磁盘存储保护阈值 (关闭 / 500MB / 1GB / 2GB)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit
) {
    if (!visible) return

    val isDark = isAppDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var concurrentCount by remember {
        mutableIntStateOf(Hawk.get(HawkConfig.DOWNLOAD_MAX_CONCURRENT, 1))
    }
    var wifiOnly by remember {
        mutableStateOf(Hawk.get(HawkConfig.DOWNLOAD_WIFI_ONLY, false))
    }
    var hlsThreads by remember {
        mutableIntStateOf(Hawk.get(HawkConfig.DOWNLOAD_HLS_THREADS, 3))
    }
    var lowStorageLimit by remember {
        mutableLongStateOf(Hawk.get(HawkConfig.DOWNLOAD_LOW_STORAGE_LIMIT, 500L * 1024 * 1024L))
    }

    // 子选项弹窗状态
    var subSheetTitle by remember { mutableStateOf("") }
    var subSheetOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var subSheetSelectedPos by remember { mutableIntStateOf(0) }
    var onSubSheetPick by remember { mutableStateOf<(Int) -> Unit>({}) }
    var isSubSheetVisible by remember { mutableStateOf(false) }

    fun openSubSheet(title: String, options: List<String>, selectedPos: Int, onPick: (Int) -> Unit) {
        subSheetTitle = title
        subSheetOptions = options
        subSheetSelectedPos = selectedPos
        onSubSheetPick = onPick
        isSubSheetVisible = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isDark) colorScheme.surfaceContainerLow else Color(0xFFF9F9FA),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download_nav),
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "下载设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "配置离线缓存并发数与网络防护策略",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 设置卡片
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) colorScheme.surfaceContainerHigh else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 1. 同时下载任务数
                    DownloadSettingItem(
                        iconRes = R.drawable.ic_speed_48,
                        title = "最大同时下载任务数",
                        subtitle = "设置允许同时并行下载的影片/集数",
                        trailingText = "${concurrentCount} 个任务",
                        onClick = {
                            val options = listOf("1 个任务 (推荐)", "2 个任务", "3 个任务", "5 个任务 (高性能)")
                            val counts = listOf(1, 2, 3, 5)
                            val curPos = counts.indexOf(concurrentCount).let { if (it < 0) 0 else it }
                            openSubSheet("最大同时下载任务数", options, curPos) { pos ->
                                val count = counts[pos]
                                concurrentCount = count
                                Hawk.put(HawkConfig.DOWNLOAD_MAX_CONCURRENT, count)
                                DownloadTaskManager.get().onConfigChanged()
                            }
                        }
                    )

                    // 2. 仅 Wi-Fi 下下载
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !wifiOnly
                                wifiOnly = next
                                Hawk.put(HawkConfig.DOWNLOAD_WIFI_ONLY, next)
                                DownloadTaskManager.get().onConfigChanged()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_18),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "仅在 Wi-Fi 下下载",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "脱离 Wi-Fi 时自动保护暂停，防止消耗移动流量",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { next ->
                                wifiOnly = next
                                Hawk.put(HawkConfig.DOWNLOAD_WIFI_ONLY, next)
                                DownloadTaskManager.get().onConfigChanged()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colorScheme.primary
                            )
                        )
                    }

                    // 3. HLS 单集切片下载并发数
                    DownloadSettingItem(
                        iconRes = R.drawable.ic_memory_48,
                        title = "HLS 单任务切片并发数",
                        subtitle = "单集 M3U8 切片并发线程数，提速下载",
                        trailingText = "${hlsThreads} 个线程",
                        onClick = {
                            val options = listOf("2 线程 (省流稳定)", "3 线程 (默认均衡)", "5 线程 (疾速)")
                            val counts = listOf(2, 3, 5)
                            val curPos = counts.indexOf(hlsThreads).let { if (it < 0) 1 else it }
                            openSubSheet("HLS 单任务切片并发数", options, curPos) { pos ->
                                val count = counts[pos]
                                hlsThreads = count
                                Hawk.put(HawkConfig.DOWNLOAD_HLS_THREADS, count)
                                DownloadTaskManager.get().onConfigChanged()
                            }
                        }
                    )

                    // 4. 存储空间保护阈值
                    val storageDesc = when (lowStorageLimit) {
                        0L -> "关闭"
                        500L * 1024 * 1024L -> "500 MB (推荐)"
                        1024L * 1024 * 1024L -> "1 GB"
                        2048L * 1024 * 1024L -> "2 GB"
                        else -> "500 MB"
                    }
                    DownloadSettingItem(
                        iconRes = R.drawable.ic_material_48,
                        title = "低可用存储空间保护",
                        subtitle = "设备可用容量低于设定阈值时自动暂停",
                        trailingText = storageDesc,
                        onClick = {
                            val options = listOf("关闭 (不保护)", "500 MB (推荐)", "1 GB", "2 GB")
                            val limits = listOf(0L, 500L * 1024 * 1024L, 1024L * 1024 * 1024L, 2048L * 1024 * 1024L)
                            val curPos = limits.indexOf(lowStorageLimit).let { if (it < 0) 1 else it }
                            openSubSheet("低可用存储空间保护", options, curPos) { pos ->
                                val limit = limits[pos]
                                lowStorageLimit = limit
                                Hawk.put(HawkConfig.DOWNLOAD_LOW_STORAGE_LIMIT, limit)
                                DownloadTaskManager.get().onConfigChanged()
                            }
                        }
                    )
                }
            }
        }
    }

    // 二级选择抽屉
    M3SingleChoiceBottomSheet(
        visible = isSubSheetVisible,
        title = subSheetTitle,
        options = subSheetOptions,
        selectedPos = subSheetSelectedPos,
        onDismissRequest = { isSubSheetVisible = false },
        onSelected = { pos, _ ->
            onSubSheetPick(pos)
        }
    )
}

@Composable
private fun DownloadSettingItem(
    iconRes: Int,
    title: String,
    subtitle: String,
    trailingText: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = trailingText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.primary
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
