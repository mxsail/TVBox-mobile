package com.github.tvbox.osc.ui.compose

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * 液态玻璃 (Liquid Glass) Compose 工具层:
 * - 基于 Kyant0/AndroidLiquidGlass
 * - 结合项目设置 UI_MATERIAL:
 *    MATERIAL_LIQUID: 开启物理折射与色散 (API 33+) + 模糊 (API 31+) + 高光
 *    MATERIAL_BLUR: 仅高斯模糊
 *    MATERIAL_TRANSLUCENT / 低版本降级: 半透明 surface + 细描边
 *    MATERIAL_SOLID: 正常实底
 */

/** 当前设备是否支持真 RenderEffect 模糊 */
val isLiquidGlassSupported: Boolean get() = isRenderEffectSupported()

/** 当前配置是否选中了液态玻璃材质 */
fun isLiquidMaterialActive(): Boolean {
    val material = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID)
    return material == ThemeManager.MATERIAL_LIQUID && isLiquidGlassSupported
}

/** 当前配置是否选中了模糊或液态玻璃材质 */
fun isBlurOrLiquidActive(): Boolean {
    val material = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID)
    return (material == ThemeManager.MATERIAL_LIQUID || material == ThemeManager.MATERIAL_BLUR) && isLiquidGlassSupported
}

/**
 * 给 Compose 容器应用液态玻璃 / 半透明降级修饰符
 */
@Composable
fun Modifier.liquidGlass(
    material: String = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID),
    backdrop: Backdrop = emptyBackdrop(),
    cornerRadius: Dp = 28.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    blurRadius: Dp = Hawk.get(HawkConfig.LIQUID_BLUR_RADIUS, 16f).dp,
    refractionHeight: Dp = Hawk.get(HawkConfig.LIQUID_REFRACTION_HEIGHT, 20f).dp,
    refractionAmount: Dp = Hawk.get(HawkConfig.LIQUID_REFRACTION_AMOUNT, 14f).dp,
    chromaticAberration: Boolean = Hawk.get(HawkConfig.LIQUID_DISPERSION, true),
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
): Modifier {
    // 实底材质
    if (material == ThemeManager.MATERIAL_SOLID) {
        return this.background(MaterialTheme.colorScheme.surface, shape)
    }

    // 不支持 RenderEffect 或普通半透明材质时：走优雅降级
    if (!isLiquidGlassSupported || material == ThemeManager.MATERIAL_TRANSLUCENT) {
        return this
            .background(containerColor, shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
    }

    // Android 12+ 真模糊与液态玻璃材质
    val isLiquid = material == ThemeManager.MATERIAL_LIQUID
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.value)
            if (isLiquid && isRuntimeShaderSupported()) {
                lens(
                    refractionHeight = refractionHeight.value,
                    refractionAmount = refractionAmount.value,
                    depthEffect = true,
                    chromaticAberration = chromaticAberration
                )
            }
        },
        highlight = if (isLiquid) {
            { Highlight.Default.copy(alpha = 0.7f) }
        } else null,
        onDrawSurface = {
            drawRect(containerColor)
        }
    )
}
