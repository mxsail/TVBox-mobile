package com.github.tvbox.osc.ui.compose

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.HawkConfig
import com.google.android.material.color.MaterialColors
import com.orhanobut.hawk.Hawk
import java.io.File

/**
 * Compose 页面共用主题与组件库: 把现有 M3 View 主题(动态取色/预设覆盖层/深浅模式)
 * 桥接进 Compose, 保证 Compose 页面与 View 页面观感一致。
 */

/** 从当前 Activity 主题取色(attr 已被动态取色/预设覆盖层改写) */
fun android.content.Context.themeIntAttr(attr: Int): Int =
    MaterialColors.getColor(this, attr, 0)

@Composable
fun TvBoxTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(
            primary = Color(ctx.themeIntAttr(androidx.appcompat.R.attr.colorPrimary)),
            onPrimary = Color(ctx.themeIntAttr(R.attr.colorOnPrimary)),
            primaryContainer = Color(ctx.themeIntAttr(R.attr.colorPrimaryContainer)),
            onPrimaryContainer = Color(ctx.themeIntAttr(R.attr.colorOnPrimaryContainer)),
            secondaryContainer = Color(ctx.themeIntAttr(R.attr.colorSecondaryContainer)),
            onSecondaryContainer = Color(ctx.themeIntAttr(R.attr.colorOnSecondaryContainer)),
            background = Color(ctx.themeIntAttr(android.R.attr.colorBackground)),
            onBackground = Color(ctx.themeIntAttr(R.attr.colorOnBackground)),
            surface = Color(ctx.themeIntAttr(R.attr.colorSurface)),
            onSurface = Color(ctx.themeIntAttr(R.attr.colorOnSurface)),
            surfaceVariant = Color(ctx.themeIntAttr(R.attr.colorSurfaceVariant)),
            onSurfaceVariant = Color(ctx.themeIntAttr(R.attr.colorOnSurfaceVariant)),
            outline = Color(ctx.themeIntAttr(R.attr.colorOutline)),
            outlineVariant = Color(ctx.themeIntAttr(R.attr.colorOutlineVariant)),
            surfaceContainerLowest = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainerLowest)),
            surfaceContainerLow = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainerLow)),
            surfaceContainer = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainer)),
            surfaceContainerHigh = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainerHigh)),
            surfaceContainerHighest = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainerHighest)),
        )
    } else {
        lightColorScheme(
            primary = Color(ctx.themeIntAttr(androidx.appcompat.R.attr.colorPrimary)),
            onPrimary = Color(ctx.themeIntAttr(R.attr.colorOnPrimary)),
            primaryContainer = Color(ctx.themeIntAttr(R.attr.colorPrimaryContainer)),
            onPrimaryContainer = Color(ctx.themeIntAttr(R.attr.colorOnPrimaryContainer)),
            secondaryContainer = Color(ctx.themeIntAttr(R.attr.colorSecondaryContainer)),
            onSecondaryContainer = Color(ctx.themeIntAttr(R.attr.colorOnSecondaryContainer)),
            background = Color(0xFFF3F3F4),
            onBackground = Color(ctx.themeIntAttr(R.attr.colorOnBackground)),
            surface = Color(0xFFF3F3F4),
            onSurface = Color(ctx.themeIntAttr(R.attr.colorOnSurface)),
            surfaceVariant = Color(ctx.themeIntAttr(R.attr.colorSurfaceVariant)),
            onSurfaceVariant = Color(ctx.themeIntAttr(R.attr.colorOnSurfaceVariant)),
            outline = Color(ctx.themeIntAttr(R.attr.colorOutline)),
            outlineVariant = Color(ctx.themeIntAttr(R.attr.colorOutlineVariant)),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainerHigh)),
            surfaceContainerHighest = Color(ctx.themeIntAttr(R.attr.colorSurfaceContainerHighest)),
        )
    }
    // 自定义字体桥接: 用户导入的 .ttf/.otf 同时作用于 Compose 文本
    val fontPath = Hawk.get(HawkConfig.FONT_PATH, "")
    val family = if (fontPath.isNotEmpty() && File(fontPath).exists()) {
        try {
            FontFamily(android.graphics.Typeface.createFromFile(File(fontPath)))
        } catch (e: Exception) {
            FontFamily.Default
        }
    } else FontFamily.Default
    val base = MaterialTheme.typography
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** 深色判断(跟随应用主题, 非系统) */
@Composable
fun isAppDarkTheme(): Boolean {
    val ctx = LocalContext.current
    return (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}

/**
 * M3 Expressive 统一二级页面顶栏:
 * - 居中标题 (CenterAlignedTopAppBar)
 * - 标题规范: 18sp, FontWeight.SemiBold, colorScheme.onSurface, 单行自动截断
 * - 左侧返回键: 标准 48dp 触摸区域与水波纹, 带有无障碍描述
 * - 右侧 actions: 动作按钮槽位 (RowScope)
 * - 背景: 平铺一体化 (Flat Seamless) 与页面 background 融合
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvBoxTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = modifier
    )
}

/**
 * 标准居中管理顶栏 (收藏/下载/详情等统一顶栏组件):
 * - 居中标题与已选择计数胶囊
 * - 左侧返回箭头 (管理模式变为关闭叉号)
 * - 右侧管理按钮 (管理模式变为完成按钮)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvBoxManageTopBar(
    title: String,
    isEditMode: Boolean,
    selectedCount: Int = 0,
    canManage: Boolean = true,
    editTitle: String = "选择项目",
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(
                        if (isEditMode) R.drawable.ic_close_24 else R.drawable.ic_arrow_back
                    ),
                    contentDescription = if (isEditMode) "退出管理" else "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            if (canManage) {
                if (isEditMode) {
                    Button(
                        onClick = onToggleEditMode,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            "完成",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onToggleEditMode,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            "管理",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = modifier
    )
}

/**
 * M3 Expressive 悬浮多选管理胶囊岛 (全选/取消全选 + 删除(数量))
 */
@Composable
fun FloatingActionIsland(
    visible: Boolean,
    isAllSelected: Boolean,
    selectedCount: Int,
    onToggleSelectAll: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn() + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDark) colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            else Color.White.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .wrapContentWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // 全选 / 取消全选胶囊
                FilledTonalButton(
                    onClick = onToggleSelectAll,
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isAllSelected) colorScheme.secondaryContainer
                        else colorScheme.surfaceContainerHighest,
                        contentColor = if (isAllSelected) colorScheme.onSecondaryContainer
                        else colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = if (isAllSelected) "取消全选" else "全选",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.width(8.dp))

                // 胶囊内部精致分隔线
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                Spacer(Modifier.width(8.dp))

                // 删除按钮 (危险色胶囊，带动态计数)
                Button(
                    onClick = onDeleteClick,
                    enabled = selectedCount > 0,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                        disabledContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_outline_24),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (selectedCount == 0) "删除" else "删除 ($selectedCount)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** 卡片分组小节标题 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
}

/** M3 卡片分组容器(行竖排) - HyperOS 风格 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = isAppDarkTheme()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else Color.White
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

/** 列表行: 图标+标题+(右侧当前值+chevron), 与 View 版 M3MenuRow 同构 (HyperOS 风格) */
@Composable
fun MenuRow(
    icon: Int,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    val isDark = isAppDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        )
        if (value != null) {
            Text(
                value,
                color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF8E8E93),
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        if (onClick != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color(0xFFC7C7CC),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 开关列表行 (HyperOS 风格) */
@Composable
fun SwitchRow(
    icon: Int,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val isDark = isAppDarkTheme()
    val alpha = if (enabled) 1f else 0.42f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(start = 16.dp, end = 16.dp, top = if (subtitle != null) 12.dp else 10.dp, bottom = if (subtitle != null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = (if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF8E8E93)).copy(alpha = alpha),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = if (enabled) onToggle else null)
    }
}

/** 滑块列表行 */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    val isDark = isAppDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                displayValue,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE5E5EA)
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** 说明小字 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = modifier.padding(start = 6.dp, top = 6.dp)
    )
}

/** swatch 背景色桥接便利 */
fun colorFromRes(res: Int, ctx: android.content.Context): Color =
    Color(ContextCompat.getColor(ctx, res))

/**
 * LazyGrid 滚动增量 → 底栏滚动隐藏联动(替代 View 版 BarAutoHide.attach):
 * 用可见首项 index/offset 的差分近似 dy; 回到顶部时强制呼出。
 */
@Composable
fun androidx.compose.foundation.lazy.grid.LazyGridState.ObserveBarHideDeltas(
    onDelta: (Int) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(this) {
        var lastIndex = 0
        var lastOffset = 0
        androidx.compose.runtime.snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset == 0) {
                    lastIndex = 0
                    lastOffset = 0
                    onDelta(-100) // 到顶, 强制呼出
                    return@collect
                }
                val dy = (index - lastIndex) * 500 + (offset - lastOffset)
                lastIndex = index
                lastOffset = offset
                if (dy > 8 || dy < -8) onDelta(dy)
            }
    }
}

/**
 * LazyList 滚动增量 → 底栏滚动隐藏联动(逻辑同 LazyGridState 版)
 */
@Composable
fun androidx.compose.foundation.lazy.LazyListState.ObserveBarHideDeltas(
    onDelta: (Int) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(this) {
        var lastIndex = 0
        var lastOffset = 0
        androidx.compose.runtime.snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset == 0) {
                    lastIndex = 0
                    lastOffset = 0
                    onDelta(-100) // 到顶, 强制呼出
                    return@collect
                }
                val dy = (index - lastIndex) * 500 + (offset - lastOffset)
                lastIndex = index
                lastOffset = offset
                if (dy > 8 || dy < -8) onDelta(dy)
            }
    }
}

/**
 * ScrollState 滚动增量 → 底栏滚动隐藏联动
 */
@Composable
fun androidx.compose.foundation.ScrollState.ObserveBarHideDeltas(
    onDelta: (Int) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(this) {
        var lastValue = 0
        androidx.compose.runtime.snapshotFlow { value }
            .collect { cur ->
                if (cur == 0) {
                    lastValue = 0
                    onDelta(-100) // 到顶, 强制呼出
                    return@collect
                }
                val dy = cur - lastValue
                lastValue = cur
                if (dy > 8 || dy < -8) onDelta(dy)
            }
    }
}

/**
 * Material Design 3 Expressive 风格单选底部抽屉
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> M3SingleChoiceBottomSheet(
    visible: Boolean,
    title: String,
    options: List<T>,
    selectedPos: Int,
    getDisplayName: (T) -> String = { it.toString() },
    onDismissRequest: () -> Unit,
    onSelected: (pos: Int, item: T) -> Unit
) {
    if (!visible) return

    val isDark = isAppDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isDark) colorScheme.surfaceContainerLow else Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                items(options.size) { index ->
                    val item = options[index]
                    val isSelected = index == selectedPos
                    val itemBg = if (isSelected) colorScheme.primaryContainer else Color.Transparent
                    val itemTextColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(itemBg)
                            .clickable {
                                onSelected(index, item)
                                onDismissRequest()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = getDisplayName(item),
                            color = itemTextColor,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.onPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material Design 3 Expressive 风格确认对话框
 */
@Composable
fun M3ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    isDestructive: Boolean = false,
    iconRes: Int? = null,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return

    val isDark = isAppDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (isDark) colorScheme.surfaceContainerHigh else Color.White,
        icon = iconRes?.let {
            {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isDestructive) colorScheme.errorContainer
                            else colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        tint = if (isDestructive) colorScheme.onErrorContainer else colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colorScheme.error else colorScheme.primary,
                    contentColor = if (isDestructive) colorScheme.onError else colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(cancelText, color = colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * M3 Expressive 操作动作模型
 */
data class M3ItemAction(
    val title: String,
    val iconRes: Int,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Material Design 3 Expressive 风格操作列表居中弹窗 (条目更多操作)
 */
@Composable
fun M3ActionsCenterDialog(
    visible: Boolean,
    title: String,
    subtitle: String? = null,
    actions: List<M3ItemAction>,
    onDismissRequest: () -> Unit
) {
    if (!visible) return
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) colorScheme.surfaceContainerHigh else Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actions.forEach { action ->
                        val actionColor = if (action.isDestructive) colorScheme.error else colorScheme.onSurface
                        val iconBg = if (action.isDestructive) colorScheme.errorContainer.copy(alpha = 0.6f)
                                     else colorScheme.surfaceContainerHighest

                        Surface(
                            onClick = {
                                onDismissRequest()
                                action.onClick()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(action.iconRes),
                                        contentDescription = null,
                                        tint = if (action.isDestructive) colorScheme.error else colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = action.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = actionColor,
                                    modifier = Modifier
                                        .padding(start = 14.dp)
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("关闭", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Material Design 3 Expressive 风格输入弹窗 (重命名等)
 */
@Composable
fun M3InputDialog(
    visible: Boolean,
    title: String,
    initialValue: String = "",
    label: String = "请输入",
    maxLength: Int = 20,
    confirmText: String = "保存",
    cancelText: String = "取消",
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) colorScheme.surfaceContainerHigh else Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= maxLength) {
                            text = it
                        }
                    },
                    label = { Text(label) },
                    singleLine = true,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${text.length}/$maxLength",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24),
                                    contentDescription = "清空",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(cancelText, color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isEmpty()) {
                                ToastUtils.showShort("内容不能为空")
                                return@Button
                            }
                            onConfirm(trimmed)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text(confirmText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Material Design 3 Expressive 风格列表单选居中弹窗 (多仓线路选择等)
 */
@Composable
fun <T> M3SingleChoiceCenterDialog(
    visible: Boolean,
    title: String,
    subtitle: String? = null,
    options: List<T>,
    getDisplayName: (T) -> String = { it.toString() },
    getSecondaryText: ((T) -> String?)? = null,
    onDismissRequest: () -> Unit,
    onSelected: (pos: Int, item: T) -> Unit
) {
    if (!visible) return
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) colorScheme.surfaceContainerHigh else Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 4.dp, bottom = 12.dp)
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = false)
                        .padding(horizontal = 14.dp)
                ) {
                    items(options.size) { index ->
                        val item = options[index]
                        Surface(
                            onClick = {
                                onSelected(index, item)
                                onDismissRequest()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = getDisplayName(item),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val secondary = getSecondaryText?.invoke(item)
                                    if (!secondary.isNullOrEmpty()) {
                                        Text(
                                            text = secondary,
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("取消", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
