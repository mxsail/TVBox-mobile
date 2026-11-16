package com.github.tvbox.osc.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.util.HawkConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.orhanobut.hawk.Hawk

/**
 * 界面材质 (三类): 按 UI_MATERIAL 设置给玻璃容器套配方
 * - MATERIAL_YOU      Material You: 实底跟随 M3 动态色, 无玻璃效果
 * - MATERIAL_LIQUID   液态玻璃: 折射(Android 14+)+色散+高光+模糊, 背景透出后方内容
 * - MATERIAL_FROSTED  轻透磨砂: 类 iOS 26 毛玻璃, 重模糊高透无折射
 */

/** 跨组合共享的全屏背板: 底栏组合创建, 各 Compose 页面根节点 layerBackdrop 注册,
 *  底栏玻璃即可采样真实页面内容(背景透出后方内容)。Live 等 View 页不注册, 玻璃自动退化为表面色。 */
object AppUiBackdrop {
    val layer = mutableStateOf<LayerBackdrop?>(null)
}

/** 当前设备是否支持真 RenderEffect 模糊 */
val isLiquidGlassSupported: Boolean get() = isRenderEffectSupported()

/** 当前配置是否需要玻璃背板(液态玻璃/轻透磨砂) */
fun isGlassMaterialActive(): Boolean {
    val material = ThemeManager.material()
    return material != ThemeManager.MATERIAL_YOU && isLiquidGlassSupported
}

/**
 * 给 Compose 容器按材质套表面配方
 */
@Composable
fun Modifier.materialGlass(
    material: String = ThemeManager.material(),
    backdrop: Backdrop = emptyBackdrop(),
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = Hawk.get(HawkConfig.LIQUID_BLUR_RADIUS, 16f).dp,
    refractionHeight: Dp = Hawk.get(HawkConfig.LIQUID_REFRACTION_HEIGHT, 20f).dp,
    refractionAmount: Dp = Hawk.get(HawkConfig.LIQUID_REFRACTION_AMOUNT, 14f).dp,
    chromaticAberration: Boolean = Hawk.get(HawkConfig.LIQUID_DISPERSION, true),
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
): Modifier {
    // Material Design: 实底 M3 Expressive 表面容器, 辅以微描边与柔和阴影
    if (material == ThemeManager.MATERIAL_YOU || !isLiquidGlassSupported) {
        val colorScheme = MaterialTheme.colorScheme
        return this
            .shadow(elevation = 6.dp, shape = shape, spotColor = colorScheme.outline.copy(alpha = 0.2f))
            .background(colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
    }

    // 液态玻璃: 物理折射(Android 14+)+色散+高光+模糊
    val isRuntimeShaderOk = isRuntimeShaderSupported()
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.value)
            if (isRuntimeShaderOk) {
                lens(
                    refractionHeight = refractionHeight.value,
                    refractionAmount = refractionAmount.value,
                    depthEffect = true,
                    chromaticAberration = chromaticAberration
                )
            }
        },
        highlight = { Highlight.Default.copy(alpha = 0.7f) },
        onDrawSurface = { drawRect(containerColor) }
    )
}

/** 底栏玻璃配方(三类): 供 LiquidBottomTabs/筛选圆钮等深色玻璃件使用 */
@Composable
fun Modifier.barGlass(
    material: String,
    backdrop: Backdrop,
    shape: Shape,
    isLightTheme: Boolean
): Modifier {
    // Material Design / 低版本: 实底 M3 Expressive 胶囊
    if (material == ThemeManager.MATERIAL_YOU || !isLiquidGlassSupported) {
        val colorScheme = MaterialTheme.colorScheme
        return this
            .shadow(elevation = 6.dp, shape = shape, spotColor = colorScheme.outline.copy(alpha = 0.2f))
            .background(colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
    }
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8f.dp.toPx())
            if (material == ThemeManager.MATERIAL_LIQUID && isRuntimeShaderSupported()) {
                lens(24f.dp.toPx(), 24f.dp.toPx())
            }
        },
        onDrawSurface = { drawRect(containerColor) }
    )
}

/** 悬浮底栏浅色判断(玻璃内容色) */
@Composable
fun isBarLightTheme(): Boolean = !isSystemInDarkTheme()
