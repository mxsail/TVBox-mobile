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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
                    // 底部阴影留位 + clipToBounds: 面板与顶栏齐平, shadow 四周扩散会把
                    // 顶边阴影画进顶部内容区形成"上边框"——裁掉上/侧, 只留底部柔和阴影。
                    // 背景必须用 background 与页面同色: surface 与页面背景有色差,
                    // 面板顶边与搜索框之间会出现一条全宽分割线
                    Modifier
                        .clipToBounds()
                        .padding(bottom = 12.dp)
                        .shadow(elevation = shadowElevation, shape = drawerShape)
                        .background(
                            color = MaterialTheme.colorScheme.background,
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
            // 常态收起状态: 单行滚动分类条 + 右侧展开向下箭头 (紧凑高度, 纯净无上下边框)。
            // 不用 M3 ScrollableTabRow: 它对每个 tab 施加 ~88dp 最小宽度, 2 字分类会占 4 字宽;
            // 改自绘 Row + 选中项底部胶囊指示条, 宽度严格自适应文字。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val density = LocalDensity.current
                val rowScrollState = rememberScrollState()
                var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val tabOffsets = remember { mutableStateMapOf<Int, Float>() }
                val tabWidths = remember { mutableStateMapOf<Int, Float>() }

                val textPaddingPx = with(density) { 6.dp.toPx() }
                val targetOffsetPx = tabOffsets[validIndex]?.let { it + textPaddingPx } ?: 0f
                val targetWidthPx = tabWidths[validIndex]?.let { (it - textPaddingPx * 2).coerceAtLeast(0f) } ?: 0f

                val animOffsetPx by animateFloatAsState(
                    targetValue = targetOffsetPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tabIndicatorOffset"
                )
                val animWidthPx by animateFloatAsState(
                    targetValue = targetWidthPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tabIndicatorWidth"
                )

                // 切换分类(含展开面板点选远处分类)时把选中项滚到可见
                LaunchedEffect(validIndex, tabOffsets[validIndex]) {
                    val target = tabOffsets[validIndex]?.toInt() ?: 0
                    if (target > 0) {
                        rowScrollState.animateScrollTo((target - 40).coerceAtLeast(0))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rowScrollState)
                ) {
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .onGloballyPositioned { rowCoordinates = it },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(4.dp)) // 4dp + 分类内边距 6dp = 10dp, 与网格卡片左缘对齐
                        displayCategories.forEachIndexed { index, name ->
                            val isSelected = index == validIndex
                            Text(
                                text = name,
                                fontSize = if (isSelected) 18.sp else 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onTabSelected(index)
                                    }
                                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 4.dp)
                                    .onGloballyPositioned { coords ->
                                        rowCoordinates?.let { rowCoords ->
                                            if (rowCoords.isAttached && coords.isAttached) {
                                                val relPos = rowCoords.localPositionOf(coords, Offset.Zero)
                                                tabOffsets[index] = relPos.x
                                                tabWidths[index] = coords.size.width.toFloat()
                                            }
                                        }
                                    }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // 独立平滑滑动胶囊指示条: 独立于文字下方, 随切换平滑平移与伸缩
                    if (targetWidthPx > 0f || animWidthPx > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset { IntOffset(animOffsetPx.roundToInt(), 0) }
                                .width(with(density) { animWidthPx.toDp() })
                                .height(3.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(percent = 50)
                                )
                        )
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
