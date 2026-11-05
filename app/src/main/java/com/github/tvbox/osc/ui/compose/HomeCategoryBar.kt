package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.util.HawkConfig
import com.orhanobut.hawk.Hawk

/**
 * 首页分类栏 (无缝同体延展形态):
 * 1. 常态下完全无上下边框/无阴影/无多余边框，纯净平铺与顶栏融合
 * 2. 展开时背景与顶栏保持一致，全宽贴边平滑长高，底部带 20dp 优雅圆角与柔和阴影
 * 3. 采用 Spring.DampingRatioNoBouncy 无过冲物理收敛，严格规避负圆角崩溃
 * 4. 展开后原首行单行滚动条隐藏，原位全量展示流式分类 Chips，右上角保留收起按钮
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeCategoryBar(
    categories: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    material: String = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID),
    isExpanded: Boolean = false,
    onExpandChanged: (Boolean) -> Unit = {}
) {
    val displayCategories = if (categories.isEmpty()) listOf("主页") else categories
    val validIndex = selectedIndex.coerceIn(0, displayCategories.size - 1)

    // 右侧箭头 0° ↔ 180° 翻转动画 (NoBouncy 阻尼平滑，不产生负角度震荡)
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expandRotation"
    )

    // 面板底部圆角形变: 常态 0dp -> 展开 20dp (使用 NoBouncy 阻尼并强制 coerceAtLeast(0.dp)，彻底根除负圆角崩溃)
    val bottomCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomCornerRadius"
    )

    val safeBottomCornerRadius = bottomCornerRadius.coerceAtLeast(0.dp)

    val drawerShape = RoundedCornerShape(
        bottomStart = safeBottomCornerRadius,
        bottomEnd = safeBottomCornerRadius
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 240),
        label = "shadowElevation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isExpanded) {
                    Modifier
                        .shadow(elevation = shadowElevation, shape = drawerShape)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = drawerShape
                        )
                } else {
                    // 常态收起状态: 保持纯净无上下边框、无阴影、无描边，与顶栏完全融为一体
                    Modifier
                }
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        if (!isExpanded) {
            // 常态收起状态: 单行滚动分类条 + 右侧展开向下箭头 (高 44dp，纯净无上下边框)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrollableTabRow(
                    selectedTabIndex = validIndex,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    edgePadding = 6.dp,
                    indicator = { tabPositions: List<TabPosition> ->
                        if (validIndex < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[validIndex])
                                    .height(3.dp)
                                    .padding(horizontal = 14.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                            )
                        }
                    },
                    divider = {}
                ) {
                    displayCategories.forEachIndexed { index, name ->
                        val isSelected = index == validIndex
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onTabSelected(index)
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                fontSize = if (isSelected) 16.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 右侧展开按钮 (分类数大于1时展示)
                if (displayCategories.size > 1) {
                    IconButton(
                        onClick = { onExpandChanged(true) },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_down),
                            contentDescription = "展开分类",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }
            }
        } else {
            // 展开状态: 原单行分类栏不重复展示！直接呈现全部分类流式 Chips，右上角保留收起按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
            ) {
                // 流式排布全部分类 (右侧预留 36dp 空间避免被右上角收起按钮遮挡)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 38.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayCategories.forEachIndexed { index, name ->
                        val isSelected = index == validIndex
                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            label = "chipBg"
                        )
                        val chipText by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            label = "chipText"
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(chipBg)
                                .clickable {
                                    onTabSelected(index)
                                    onExpandChanged(false)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = chipText
                            )
                        }
                    }
                }

                // 右上角收起按钮 (翻转向上箭头，点击收起)
                IconButton(
                    onClick = { onExpandChanged(false) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = "收起分类",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationAngle)
                    )
                }
            }
        }
    }
}
