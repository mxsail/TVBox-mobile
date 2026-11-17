package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.MovieSort
import java.util.HashMap

/**
 * 首页分类高级筛选下拉面板 (Material 3 Expressive):
 * 1. 顶部向下弹性展开，与顶部分类栏无缝连接，底部采用 24dp 表现力大圆角与柔和阴影
 * 2. 摒弃旧版从底部弹出的 XML 弹窗与深黑全屏遮罩，操作源头与视觉展开完全一致
 * 3. 内部采用多行横向滚动的 M3 FilterChip，即选即生效并即时联动网格刷新
 * 4. 底部提供“重置全部”与“收起”操作
 */
@Composable
fun HomeFilterPanel(
    sortData: MovieSort.SortData,
    onFilterChanged: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filterVersion = remember { mutableIntStateOf(0) }
    val drawerShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(bottom = 12.dp)
            .shadow(elevation = 12.dp, shape = drawerShape)
            .background(color = colorScheme.surface, shape = drawerShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 筛选项目滚动区 (最多占据屏幕适中高度)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 读取版本触发重组
                filterVersion.intValue

                val filters = sortData.filters ?: emptyList()
                filters.forEach { filter ->
                    val filterKey = filter.key ?: ""
                    val filterValues = filter.values ?: linkedMapOf()
                    if (filterValues.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = filter.name ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .width(38.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentVal = sortData.filterSelect?.get(filterKey)
                                val entries = filterValues.entries.toList()

                                entries.forEachIndexed { index, (displayName, valueKey) ->
                                    val isSelected = when {
                                        currentVal != null -> currentVal == valueKey
                                        else -> valueKey.isEmpty() || displayName == "全部" || (index == 0 && !filterValues.values.any { it.isEmpty() })
                                    }

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (sortData.filterSelect == null) {
                                                sortData.filterSelect = HashMap()
                                            }
                                            if (isSelected) {
                                                // 点击已选中项: 若不是"全部"，取消选择
                                                if (valueKey.isNotEmpty() && displayName != "全部") {
                                                    sortData.filterSelect.remove(filterKey)
                                                }
                                            } else {
                                                if (valueKey.isEmpty() || displayName == "全部") {
                                                    sortData.filterSelect.remove(filterKey)
                                                } else {
                                                    sortData.filterSelect[filterKey] = valueKey
                                                }
                                            }
                                            filterVersion.intValue++
                                            onFilterChanged()
                                        },
                                        label = {
                                            Text(
                                                text = displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colorScheme.surfaceContainerHigh,
                                            labelColor = colorScheme.onSurfaceVariant,
                                            selectedContainerColor = colorScheme.primaryContainer,
                                            selectedLabelColor = colorScheme.onPrimaryContainer
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            // 底部操作与状态栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectCount = sortData.filterSelectCount()
                Text(
                    text = if (selectCount > 0) "已选 $selectCount 项过滤" else "全部筛选默认",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(Modifier.weight(1f))

                if (selectCount > 0) {
                    TextButton(
                        onClick = {
                            sortData.filterSelect?.clear()
                            filterVersion.intValue++
                            onFilterChanged()
                        }
                    ) {
                        Text("重置", fontSize = 12.sp, color = colorScheme.error)
                    }
                    Spacer(Modifier.width(4.dp))
                }

                TextButton(
                    onClick = onCollapse
                ) {
                    Text("收起", fontSize = 12.sp, color = colorScheme.primary)
                }
            }
        }
    }
}
