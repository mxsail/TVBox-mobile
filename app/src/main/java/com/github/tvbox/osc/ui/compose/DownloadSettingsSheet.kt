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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) colorScheme.surfaceContainerHigh else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 1. 同时下载任务数
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_speed_48),
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "最大同时下载任务数",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "设置允许同时并行下载的影片/集数",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val concurrentOptions = listOf(1, 2, 3, 5)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            concurrentOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = concurrentCount == count,
                                    onClick = {
                                        concurrentCount = count
                                        Hawk.put(HawkConfig.DOWNLOAD_MAX_CONCURRENT, count)
                                        DownloadTaskManager.get().onConfigChanged()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = concurrentOptions.size
                                    )
                                ) {
                                    Text(text = "$count 个", fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorScheme.primaryContainer.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_18),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "仅在 Wi-Fi 下下载",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "脱离 Wi-Fi 时自动保护暂停，防止消耗移动流量",
                                fontSize = 12.sp,
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

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    // 3. HLS 单集切片下载并发数
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_memory_48),
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "HLS 单任务切片并发数",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "单集 M3U8 切片并发线程数，提速下载",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val hlsOptions = listOf(2, 3, 5)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            hlsOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = hlsThreads == count,
                                    onClick = {
                                        hlsThreads = count
                                        Hawk.put(HawkConfig.DOWNLOAD_HLS_THREADS, count)
                                        DownloadTaskManager.get().onConfigChanged()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = hlsOptions.size
                                    )
                                ) {
                                    Text(text = "$count 线程", fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    // 4. 存储空间保护阈值
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_material_48),
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "低可用存储空间保护",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "设备可用容量低于设定阈值时自动暂停",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val storageLimits = listOf(
                            0L to "关闭",
                            500L * 1024 * 1024L to "500MB",
                            1024L * 1024 * 1024L to "1GB",
                            2048L * 1024 * 1024L to "2GB"
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            storageLimits.forEachIndexed { index, (limit, label) ->
                                SegmentedButton(
                                    selected = lowStorageLimit == limit,
                                    onClick = {
                                        lowStorageLimit = limit
                                        Hawk.put(HawkConfig.DOWNLOAD_LOW_STORAGE_LIMIT, limit)
                                        DownloadTaskManager.get().onConfigChanged()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = storageLimits.size
                                    )
                                ) {
                                    Text(text = label, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
