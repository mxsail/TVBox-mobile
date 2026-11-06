package com.github.tvbox.osc.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.util.HawkConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

val Capsule: Shape = RoundedCornerShape(percent = 50)

/**
 * 底栏尺寸单一来源: Compose 三层与 View 侧内容避让都引用这里。
 * 不变量 ContentHeightDp = HeightDp - InsetDp * 2, 否则采样层/滑块会与底板垂直错位。
 */
object LiquidBarSpec {
    const val HeightDp: Int = 56          // Layer 1 胶囊外壳
    const val InsetDp: Int = 4            // 外壳内边距 / 采样层与滑块的水平 padding
    const val TopInsetDp: Int = 8         // 顶部预留, 供水滴膨胀突破边界
    const val ContentHeightDp: Int = HeightDp - InsetDp * 2 // Layer 2 / Layer 3
    const val PressedHeightDp: Int = 67   // 按下膨胀目标高, 约 1.4x
    const val OccupiedHeightDp: Int = HeightDp + TopInsetDp // View 侧底部避让
    const val IconTextGapDp: Int = 0     // 图标与文字间距, 0=紧贴; Column 唯一间距, 勿再叠加 Spacer
}

data class BottomBarTabItem(
    val iconRes: Int,
    val title: String
)

val MainBottomTabs = listOf(
    BottomBarTabItem(R.drawable.ic_home, "首页"),
    BottomBarTabItem(R.drawable.ic_live_tv_48, "直播"),
    BottomBarTabItem(R.drawable.ic_download_nav, "缓存"),
    BottomBarTabItem(R.drawable.ic_my, "我的")
)

// =========================================================================
// 1. 手势检测器: 非消费式手势监听，点击与拖拽零冲突
// =========================================================================
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(
            pointerId = drag.id,
            onDrag = { onDrag(it, it.positionChange()) }
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) return dragEvent
        }
    }
}

// =========================================================================
// 2. 动态触控光泽高光 (InteractiveHighlight)
// =========================================================================
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(
                    Color.White.copy(0.08f * progress),
                    blendMode = BlendMode.Plus
                )
                shader.apply {
                    val pos = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        pos.x.fastCoerceIn(0f, size.width),
                        pos.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(
                    ShaderBrush(shader.asComposeShader()),
                    blendMode = BlendMode.Plus
                )
            } else {
                drawRect(
                    Color.White.copy(0.20f * progress),
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

// =========================================================================
// 3. 物理阻尼与速度张量拉伸引擎 (DampedDragAnimation)
// =========================================================================
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

// =========================================================================
// 4. Tab 项组件: 100% 官方标准 LiquidBottomTab (com.kyant.backdrop.catalog.components)
// =========================================================================
internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(LiquidBarSpec.IconTextGapDp.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

// =========================================================================
// 5. 100% 官方原版 LiquidBottomTabs 组件 (复刻 BottomTabsContent 第二个示例 4 Tabs)
// =========================================================================
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
    navInsetBottom: Dp = 0.dp,
    pageScrollFraction: Float? = null,
    material: String = ThemeManager.MATERIAL_LIQUID,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)
    val isLiquid = material == ThemeManager.MATERIAL_LIQUID
    val solidBarColor = if (isLightTheme) Color(0xFFF7F7F9) else Color(0xFF202024)
    val barShape: Shape = if (floating) Capsule else RoundedCornerShape(0.dp)
    val barHeight = if (floating) LiquidBarSpec.HeightDp.dp else (LiquidBarSpec.HeightDp.dp + navInsetBottom)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = LiquidBarSpec.PressedHeightDp.toFloat() / LiquidBarSpec.ContentHeightDp,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    onTabSelected(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(pageScrollFraction) {
            if (pageScrollFraction != null && pageScrollFraction >= 0f) {
                dampedDragAnimation.updateValue(pageScrollFraction)
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        if (isLiquid) {
            // 液态玻璃: 3 层光学透镜与色散折射结构
            // Layer 1: 外层玻璃底板 Row (展示常态图文)
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides (if (isLightTheme) Color.Black else Color.White)
            ) {
                Row(
                    Modifier
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { barShape },
                            effects = {
                                vibrancy()
                                blur(8f.dp.toPx())
                                if (floating) {
                                    lens(24f.dp.toPx(), 24f.dp.toPx())
                                }
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(barHeight)
                        .fillMaxWidth()
                        .padding(horizontal = LiquidBarSpec.InsetDp.dp)
                        .padding(
                            top = LiquidBarSpec.InsetDp.dp,
                            bottom = LiquidBarSpec.InsetDp.dp + if (floating) 0.dp else navInsetBottom
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            // Layer 2: 采样层 tabsBackdrop (高亮图文纯色染色，透明背景，与 Layer 1 几何坐标 100% 精确对齐)
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides { 1f },
                androidx.compose.material3.LocalContentColor provides accentColor
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .layerBackdrop(tabsBackdrop)
                        .height(barHeight)
                        .fillMaxWidth()
                        .padding(horizontal = LiquidBarSpec.InsetDp.dp)
                        .padding(
                            top = LiquidBarSpec.InsetDp.dp,
                            bottom = LiquidBarSpec.InsetDp.dp + if (floating) 0.dp else navInsetBottom
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            // Layer 3: 顶层液态色散透镜滑块
            Box(
                Modifier
                    .padding(horizontal = LiquidBarSpec.InsetDp.dp)
                    .padding(bottom = if (floating) 0.dp else navInsetBottom)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress)
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8f.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.1f)
                                else Color.White.copy(0.1f),
                                alpha = 1f - progress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    )
                    .height(LiquidBarSpec.ContentHeightDp.dp)
                    .fillMaxWidth(1f / tabsCount)
            )
        } else {
            // Material You 风格: 实底表面 + 位于图文下方的 M3 胶囊指示器 (绝不挡住图文)
            // 1. 实底容器背景 (非悬浮时平直贴底无圆角, 悬浮时为胶囊)
            Box(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .height(barHeight)
                    .fillMaxWidth()
                    .background(solidBarColor, barShape)
            )

            // 2. M3 胶囊指示器 (位于内容下方，跟随滚动滑动)
            val indicatorWidth = 56.dp
            val indicatorHeight = 32.dp
            Box(
                Modifier
                    .padding(horizontal = LiquidBarSpec.InsetDp.dp)
                    .padding(bottom = if (floating) 0.dp else navInsetBottom)
                    .graphicsLayer {
                        val tabCenterOffset = (tabWidth - with(density) { indicatorWidth.toPx() }) / 2f
                        val startX = if (isLtr) {
                            dampedDragAnimation.value * tabWidth + tabCenterOffset
                        } else {
                            constraints.maxWidth.toFloat() - (dampedDragAnimation.value + 1f) * tabWidth + tabCenterOffset
                        }
                        translationX = startX + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .size(indicatorWidth, indicatorHeight)
                    .clip(Capsule)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            // 3. Tab 项内容层 (位于指示器上方，图文完全清晰)
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .then(interactiveHighlight.modifier)
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(horizontal = LiquidBarSpec.InsetDp.dp)
                    .padding(bottom = if (floating) 0.dp else navInsetBottom),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

// =========================================================================
// 6. 业务接入层: LiquidBottomBar (TVBox 适配)
// =========================================================================
@Composable
fun LiquidBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
    showLabels: Boolean = true,
    material: String = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID),
    navInsetBottom: Dp = 0.dp,
    backdrop: Backdrop = emptyBackdrop(),
    pageScrollFraction: Float? = null,
    filterVisible: Boolean = false,
    onFilterClick: () -> Unit = {}
) {
    val tabs = MainBottomTabs

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (floating) 16.dp else 0.dp,
                end = if (floating) 16.dp else 0.dp,
                top = if (floating) LiquidBarSpec.TopInsetDp.dp else 0.dp,
                bottom = if (floating) (navInsetBottom + 10.dp) else 0.dp
            )
            .graphicsLayer { clip = false },
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = tabs.size,
            modifier = Modifier.weight(1f), // 筛选钮可见时底栏收窄让位
            floating = floating,
            navInsetBottom = if (floating) 0.dp else navInsetBottom,
            pageScrollFraction = pageScrollFraction,
            material = material
        ) {
            tabs.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                LiquidBottomTab(onClick = { onTabSelected(index) }) {
                    val iconTint = if (material == ThemeManager.MATERIAL_YOU) {
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        androidx.compose.material3.LocalContentColor.current
                    }
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.title,
                        tint = iconTint,
                        modifier = Modifier.size(if (showLabels) 18.dp else 21.dp)
                    )
                    if (showLabels) {
                        val labelColor = if (material == ThemeManager.MATERIAL_YOU) {
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            androidx.compose.material3.LocalContentColor.current
                        }
                        Text(
                            text = item.title,
                            fontSize = 12.sp,
                            fontWeight = if (selected && material == ThemeManager.MATERIAL_YOU) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 分类筛选玻璃圆钮: 与底栏并排
        AnimatedVisibility(
            visible = filterVisible,
            enter = expandHorizontally(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                expandFrom = Alignment.End
            ) + fadeIn(tween(180)),
            exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) +
                fadeOut(tween(140))
        ) {
            val isLightTheme = !isSystemInDarkTheme()
            val isLiquid = material == ThemeManager.MATERIAL_LIQUID
            val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)
            val solidCircle = if (isLightTheme) Color(0xFFF7F7F9) else Color(0xFF202024)
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(LiquidBarSpec.HeightDp.dp)
                    .then(
                        if (material == ThemeManager.MATERIAL_YOU || !isLiquidGlassSupported) {
                            Modifier.background(solidCircle, CircleShape)
                        } else {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(8f.dp.toPx())
                                    lens(24f.dp.toPx(), 24f.dp.toPx())
                                },
                                onDrawSurface = { drawRect(containerColor) }
                            )
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onFilterClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_filter_list),
                    contentDescription = "筛选",
                    tint = if (isLightTheme) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
