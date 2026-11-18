package com.github.tvbox.osc.ui.compose

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
    const val HeightDp: Int = 56          // Layer 1 胶囊外壳 (56dp 黄金精致比例)
    const val InsetDp: Int = 4            // 外壳内边距 / 采样层与滑块的水平 padding (4dp)
    const val TopInsetDp: Int = 6         // 顶部预留 6dp, 供水滴膨胀突破边界
    const val ContentHeightDp: Int = HeightDp - InsetDp * 2 // Layer 2 / Layer 3 实际内容高度 (48dp)
    const val PressedHeightDp: Int = 61   // 按下微凸目标高 61dp (约 1.27x, 略微超出 56dp 底栏外边距约 2.5dp)
    const val HorizontalMarginDp: Int = 24 // 悬浮底栏左右外边距
    const val OccupiedHeightDp: Int = HeightDp + TopInsetDp // View 侧底部避让
    const val IconTextGapDp: Int = 2     // 图标与文字间距 2dp
}

data class BottomBarTabItem(
    val iconRes: Int,
    val title: String
)

val MainBottomTabs = listOf(
    BottomBarTabItem(R.drawable.ic_home, "首页"),
    BottomBarTabItem(R.drawable.ic_live_tv_48, "直播"),
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
    var onDragStarted: (DampedDragAnimation.(position: Offset) -> Unit)? = null,
    var onDragStopped: (DampedDragAnimation.() -> Unit)? = null,
    var onDrag: (DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit)? = null,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 0.001f)
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
                onDragStarted?.invoke(this@DampedDragAnimation, down.position)
                press()
            },
            onDragEnd = {
                onDragStopped?.invoke(this@DampedDragAnimation)
                release()
            },
            onDragCancel = {
                onDragStopped?.invoke(this@DampedDragAnimation)
                release()
            }
        ) { _, dragAmount ->
            onDrag?.invoke(this@DampedDragAnimation, size, dragAmount)
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
            if (velocity != 0f) {
                launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
            }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch {
                valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
                if (velocity != 0f) {
                    velocityAnimation.animateTo(0f, velocityAnimationSpec)
                }
            }
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
    showLabels: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val isLiquid = material == ThemeManager.MATERIAL_LIQUID && isLiquidGlassSupported
    val containerColor = if (isLightTheme) Color(0x66FAFAFA) else Color(0x66121212)
    val colorScheme = MaterialTheme.colorScheme
    val solidBarColor = if (floating) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer
    val barShape: Shape = if (floating) Capsule else RoundedCornerShape(0.dp)
    val barHeight = if (floating) LiquidBarSpec.HeightDp.dp else (LiquidBarSpec.HeightDp.dp + navInsetBottom)

    val blurRadiusDp = Hawk.get(HawkConfig.LIQUID_BLUR_RADIUS, 16f)
    val refractionHeightDp = Hawk.get(HawkConfig.LIQUID_REFRACTION_HEIGHT, 20f)
    val refractionAmountDp = Hawk.get(HawkConfig.LIQUID_REFRACTION_AMOUNT, 14f)
    val dispersion = Hawk.get(HawkConfig.LIQUID_DISPERSION, true)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val blurRadiusPx = with(density) { blurRadiusDp.dp.toPx() }
        val refractionHeightPx = with(density) { refractionHeightDp.dp.toPx() }
        val refractionAmountPx = with(density) { refractionAmountDp.dp.toPx() }

        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        val currentTabWidth by rememberUpdatedState(tabWidth)

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        var isDraggingSlider by remember { mutableStateOf(false) }
        var pendingDragTargetTab by remember { mutableIntStateOf(-1) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = LiquidBarSpec.PressedHeightDp.toFloat() / LiquidBarSpec.ContentHeightDp.toFloat()
            )
        }

        dampedDragAnimation.onDragStarted = { position ->
            isDraggingSlider = true
            pendingDragTargetTab = -1
            // 全域手势: 按下位置所在 Tab 即为目标, 每次均依据当前实时宽度和边界计算
            val insetPx = with(density) { 4f.dp.toPx() }
            val touchX = (position.x - insetPx).coerceIn(0f, constraints.maxWidth.toFloat() - insetPx * 2)
            val rawTab = if (tabWidth > 0f) {
                (touchX / tabWidth).toInt().coerceIn(0, tabsCount - 1)
            } else {
                0
            }
            val touchedTab = if (isLtr) rawTab else (tabsCount - 1 - rawTab)
            if (abs(targetValue - touchedTab) > 0.001f) {
                updateValue(touchedTab.toFloat())
            }
        }

        dampedDragAnimation.onDragStopped = {
            val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
            isDraggingSlider = false
            pendingDragTargetTab = targetIndex
            updateValue(targetIndex.toFloat())
            currentOnTabSelected(targetIndex)
            animationScope.launch {
                offsetAnimation.animateTo(
                    0f,
                    spring(1f, 300f, 0.5f)
                )
            }
        }

        dampedDragAnimation.onDrag = { _, dragAmount ->
            if (dragAmount.x != 0f && tabWidth > 0f) {
                updateValue(
                    (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                )
                animationScope.launch {
                    offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                }
            }
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { currentSelectedTabIndex() }
                .collectLatest { index ->
                    if (pendingDragTargetTab != -1 && index != pendingDragTargetTab) {
                        pendingDragTargetTab = -1
                    }
                }
        }
        LaunchedEffect(pageScrollFraction) {
            if (pageScrollFraction != null && pageScrollFraction >= 0f) {
                if (pendingDragTargetTab != -1 && abs(pageScrollFraction - pendingDragTargetTab) < 0.01f) {
                    pendingDragTargetTab = -1
                }
                if (!isDraggingSlider && pendingDragTargetTab == -1 && dampedDragAnimation.pressProgress == 0f) {
                    dampedDragAnimation.updateValue(pageScrollFraction)
                }
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * currentTabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * currentTabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        if (isLiquid) {
            // 液态玻璃: 100% 官方三层光学折射透镜架构 (严格复刻 AndroidLiquidGlass)
            // Layer 1: 底栏外壳毛玻璃 Row (展示常态图文)
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
                                blur(blurRadiusPx)
                                if (floating) {
                                    lens(
                                        refractionHeight = refractionHeightPx,
                                        refractionAmount = refractionAmountPx,
                                        depthEffect = true,
                                        chromaticAberration = dispersion
                                    )
                                }
                            },
                            layerBlock = {
                                val progress = dampedDragAnimation.pressProgress
                                val scale = lerp(1f, 1f + 6f.dp.toPx() / size.width, progress)
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(barHeight)
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            // Layer 2: 采样层 tabsBackdrop (与 Layer 1 几何坐标 100% 对齐，承载底板背景与主题强调色染色)
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.08f, dampedDragAnimation.pressProgress)
                },
                androidx.compose.material3.LocalContentColor provides accentColor
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { barShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(blurRadiusPx)
                                if (floating) {
                                    lens(
                                        refractionHeight = refractionHeightPx * progress,
                                        refractionAmount = refractionAmountPx * progress,
                                        depthEffect = true,
                                        chromaticAberration = dispersion
                                    )
                                }
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            // 官方: 采样层胶囊本身不缩放, 使液滴内可见等比缩小的内凹边缘
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        // 仅缩放图标内容层 (位于 drawBackdrop 之后), 与 Layer 1 外壳的缩放保持一致, 防止高亮图标偏移
                        .graphicsLayer {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 6f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                        .height(LiquidBarSpec.ContentHeightDp.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            // Layer 3: 顶层液态色散透镜滑块 (全透明 + 物理透镜扭曲 + 色散动态增强)
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                refractionHeight = (refractionHeightPx * 10f / 24f) * progress,
                                refractionAmount = (refractionAmountPx * 14f / 24f) * progress,
                                chromaticAberration = dispersion
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
                                radius = 8.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.08f, 0.08f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.08f, 0.08f)
                        },
                        onDrawBackdrop = { drawBackdrop ->
                            val _pos = dampedDragAnimation.value + panelOffset
                            drawBackdrop()
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

            // 全局手势交互层: 覆盖底栏全域 (任意 Tab 按下即刻水滴膨胀与弹射飞渡)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
            )
        } else {
            // 兼容性回退
            Row(
                Modifier
                    .height(barHeight)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

// =========================================================================
// 5.1 Material Design 3 Expressive 底栏组件 (悬浮与贴底原生规范)
// =========================================================================
@Composable
fun MaterialExpressiveBottomTabs(
    tabs: List<BottomBarTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
    navInsetBottom: Dp = 0.dp,
    pageScrollFraction: Float? = null,
    showLabels: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val solidBarColor = if (floating) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer
    val barShape: Shape = if (floating) Capsule else RoundedCornerShape(0.dp)
    val barHeight = if (floating) LiquidBarSpec.HeightDp.dp else (LiquidBarSpec.HeightDp.dp + navInsetBottom)

    val animatedPosition = remember { Animatable(selectedIndex.toFloat()) }
    var isProgrammaticClick by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        if (abs(animatedPosition.value - selectedIndex.toFloat()) > 0.001f) {
            isProgrammaticClick = true
            animatedPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 380f
                )
            )
            isProgrammaticClick = false
        }
    }

    LaunchedEffect(pageScrollFraction) {
        if (!isProgrammaticClick && pageScrollFraction != null && pageScrollFraction >= 0f) {
            animatedPosition.snapTo(pageScrollFraction)
        }
    }

    BoxWithConstraints(
        modifier = modifier.height(barHeight),
        contentAlignment = Alignment.TopStart
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidth = constraints.maxWidth.toFloat() / tabs.size
        val pillWidthDp = 56.dp
        val pillWidthPx = with(density) { pillWidthDp.toPx() }

        // 1. 实底容器背景 (非悬浮贴底平直, 悬浮为居中圆角胶囊)
        if (floating) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(LiquidBarSpec.HeightDp.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = barShape,
                        spotColor = colorScheme.outline.copy(alpha = 0.2f),
                        ambientColor = colorScheme.outline.copy(alpha = 0.1f)
                    )
                    .background(solidBarColor, barShape)
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(solidBarColor, barShape)
            )
        }

        // 2. M3 Expressive 指示胶囊 (在内容下方, 仅包裹 32dp 图标区域, 绝不遮挡文字)
        val distToTarget = abs(animatedPosition.targetValue - animatedPosition.value)
        val stretchScaleX = 1f + distToTarget.coerceIn(0f, 1f) * 0.18f
        val centerXPx = if (isLtr) {
            (animatedPosition.value + 0.5f) * tabWidth
        } else {
            constraints.maxWidth.toFloat() - (animatedPosition.value + 0.5f) * tabWidth
        }
        val pillTopDp = if (showLabels) 4.dp else 12.dp

        Box(
            Modifier
                .graphicsLayer {
                    translationX = centerXPx - (pillWidthPx / 2f)
                    translationY = with(density) { pillTopDp.toPx() }
                    scaleX = stretchScaleX
                }
                .size(pillWidthDp, 32.dp)
                .clip(Capsule)
                .background(colorScheme.secondaryContainer)
        )

        // 3. Tab 项列表 (位于指示器上方, 各 Tab 独立 M3 Ripple 水波纹点击反馈)
        Row(
            Modifier
                .fillMaxWidth()
                .height(LiquidBarSpec.HeightDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val iconTint = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant
                val labelColor = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "iconScale_$index"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (showLabels) Arrangement.Top else Arrangement.Center
                ) {
                    if (showLabels) {
                        Spacer(Modifier.height(4.dp))
                    }
                    // 32dp 图标容器: 尺寸与激活指示胶囊 100% 严丝合缝同心对齐, 水波纹仅在此胶囊内展现, 避免全项大椭圆
                    Box(
                        modifier = Modifier
                            .size(56.dp, 32.dp)
                            .clip(Capsule)
                            .indication(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.title,
                            tint = iconTint,
                            modifier = Modifier
                                .size(if (showLabels) 20.dp else 24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }
                    if (showLabels) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                lineHeight = 13.sp,
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                )
                            )
                        )
                    }
                }
            }
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
    pageScrollFraction: Float? = null
) {
    val tabs = MainBottomTabs
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveFloating = floating || isLandscape
    val isLiquid = material == ThemeManager.MATERIAL_LIQUID && isLiquidGlassSupported

    Box(
        modifier = modifier
            .then(
                if (effectiveFloating) {
                    Modifier
                        .padding(horizontal = LiquidBarSpec.HorizontalMarginDp.dp)
                        .widthIn(max = 280.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .padding(
                top = if (effectiveFloating) LiquidBarSpec.TopInsetDp.dp else 0.dp,
                bottom = if (effectiveFloating) (navInsetBottom + 10.dp) else 0.dp
            )
            .graphicsLayer { clip = false },
        contentAlignment = Alignment.Center
    ) {
        if (isLiquid) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex },
                onTabSelected = onTabSelected,
                backdrop = backdrop,
                tabsCount = tabs.size,
                modifier = Modifier.fillMaxWidth(),
                floating = effectiveFloating,
                navInsetBottom = if (effectiveFloating) 0.dp else navInsetBottom,
                pageScrollFraction = pageScrollFraction,
                material = material,
                showLabels = showLabels
            ) {
                tabs.forEachIndexed { index, item ->
                    LiquidBottomTab(onClick = { onTabSelected(index) }) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.title,
                            tint = androidx.compose.material3.LocalContentColor.current,
                            modifier = Modifier.size(if (showLabels) 20.dp else 24.dp)
                        )
                        if (showLabels) {
                            Text(
                                text = item.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = androidx.compose.material3.LocalContentColor.current,
                                maxLines = 1,
                                style = androidx.compose.ui.text.TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false
                                    ),
                                    lineHeight = 13.sp,
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } else {
            MaterialExpressiveBottomTabs(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onTabSelected = onTabSelected,
                modifier = Modifier.fillMaxWidth(),
                floating = effectiveFloating,
                navInsetBottom = if (effectiveFloating) 0.dp else navInsetBottom,
                pageScrollFraction = pageScrollFraction,
                showLabels = showLabels
            )
        }
    }
}
