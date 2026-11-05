package com.github.tvbox.osc.view

import android.app.Activity
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.util.HawkConfig
import com.google.android.material.color.MaterialColors
import com.orhanobut.hawk.Hawk

/**
 * 界面材质系统(View 体系余量): 现仅服务于 BaseDialog 的跨窗背景模糊;
 * 底栏/悬浮件的玻璃已由 Compose 侧(ui.compose.LiquidGlass/LiquidBottomBar)接管。
 *
 * 材质三选(ThemeManager.MATERIAL_*):
 *  - you      Material You 实底(跟随 M3 动态色, 本类不做任何事)
 *  - liquid   液态玻璃 = 折射+色散+高光(Compose), 弹窗走背景模糊
 *  - frosted  轻透磨砂 = 重模糊高透(Compose), 弹窗走更重背景模糊
 */
object MaterialBarHelper {

    private const val TAG_BLUR_LAYER = "material_blur_layer"

    @JvmStatic
    fun material(): String = ThemeManager.material()

    /** 原生跨窗口/视图层级真模糊需要 Android 12+ */
    @JvmStatic
    fun blurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 实际生效的材质: 低版本把 liquid/frosted 降级为 you(实底) */
    @JvmStatic
    fun effectiveMaterial(): String = normalizeMaterial(material(), Build.VERSION.SDK_INT)

    /** 纯逻辑(供单测): 材质降级规则 */
    @JvmStatic
    fun normalizeMaterial(m: String, sdkInt: Int): String {
        return if ((m == ThemeManager.MATERIAL_LIQUID || m == ThemeManager.MATERIAL_FROSTED)
            && sdkInt < Build.VERSION_CODES.S
        ) {
            ThemeManager.MATERIAL_YOU
        } else {
            m
        }
    }

    /** XPopup 弹窗背景模糊开关: XPopup 自带 RenderScript 模糊全版本可用, 非 Material You 时开启 */
    @JvmStatic
    fun popupBlurEnabled(): Boolean = material() != ThemeManager.MATERIAL_YOU

    /**
     * 给悬浮容器(底栏/首页顶栏)套材质。
     * you: 清空自定义背景交给组件默认; frosted: BlurView 重模糊;
     * liquid: BlurView 轻模糊 + 高光描边。
     *
     * @return 插入的模糊背板 View, 无模糊时为 null
     */
    @JvmStatic
    fun applyToBar(container: FrameLayout, activity: Activity, cornerRadiusPx: Int): View? {
        container.findViewWithTag<View>(TAG_BLUR_LAYER)?.let { container.removeView(it) }
        val ctx = container.context
        when (effectiveMaterial()) {
            ThemeManager.MATERIAL_YOU -> {
                container.background = null
                container.foreground = null
                container.clipToOutline = false
                return null
            }
            else -> { // liquid / frosted
                val liquid = effectiveMaterial() == ThemeManager.MATERIAL_LIQUID
                container.background = null
                container.clipToOutline = cornerRadiusPx > 0
                container.outlineProvider = roundedOutline(cornerRadiusPx)
                val blurView = eightbitlab.com.blurview.BlurView(ctx)
                blurView.tag = TAG_BLUR_LAYER
                val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                val algorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    eightbitlab.com.blurview.RenderEffectBlur()
                } else {
                    eightbitlab.com.blurview.RenderScriptBlur(ctx)
                }
                blurView.setupWith(root, algorithm)
                    .setFrameClearDrawable(activity.window.decorView.background)
                    // RenderScript 模糊半径上限 25(RenderEffectBlur 回退路径也走它), 越界直接崩溃
                    .setBlurRadius(if (liquid) 25f else 16f)
                    .setBlurAutoUpdate(true)
                val surface = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurface)
                if (liquid) {
                    // 液态玻璃(iOS 26 LiquidGlass 风格的 View 体系近似): 清透白罩保持通透,
                    // 质感主要靠前景高光层(顶部高光渐变+内高光描边)而非表面色罩
                    val lightSurface = ColorUtils.calculateLuminance(surface) > 0.5
                    blurView.setOverlayColor(if (lightSurface) 0x24FFFFFF else 0x1AFFFFFF)
                } else {
                    // 轻透磨砂: surface 着色保证内容可读性
                    blurView.setOverlayColor(ColorUtils.setAlphaComponent(surface, 0x59))
                }
                container.addView(
                    blurView, 0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                // 液态玻璃: 顶部高光渐变 + 内高光描边
                container.foreground = if (liquid) liquidGlassForeground(ctx, cornerRadiusPx) else null
                return blurView
            }
        }
    }

    /**
     * 液态玻璃前景(参考 Kyant0/AndroidLiquidGlass 的观感做 View 体系近似):
     * 顶部白色高光渐变(玻璃受光面) + 1.5dp 内高光描边(玻璃边缘反光)。
     * 注: 完整的边缘折射/色散需 Compose/自绘渲染, 此处为静态高光近似。
     */
    fun liquidGlassForeground(ctx: android.content.Context, radiusPx: Int): android.graphics.drawable.Drawable {
        val highlight = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x36FFFFFF, 0x0DFFFFFF, 0x00FFFFFF)
        )
        highlight.shape = GradientDrawable.RECTANGLE
        if (radiusPx > 0) highlight.cornerRadius = radiusPx.toFloat()
        val stroke = GradientDrawable()
        stroke.shape = GradientDrawable.RECTANGLE
        stroke.setColor(Color.TRANSPARENT)
        stroke.setStroke(dp(ctx, 1.5f).coerceAtLeast(1), 0x59FFFFFF)
        if (radiusPx > 0) stroke.cornerRadius = radiusPx.toFloat()
        return android.graphics.drawable.LayerDrawable(arrayOf(highlight, stroke))
    }

    /** 普通弹窗(BaseDialog 系)跨窗背景模糊: API31+, 省电模式关闭跨窗模糊时系统自动跳过 */
    @JvmStatic
    fun applyDialogBlur(window: Window?) {
        if (window == null || effectiveMaterial() == ThemeManager.MATERIAL_YOU) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val lp = window.attributes
        // 轻透磨砂比液态玻璃模糊更重
        lp.blurBehindRadius = dp(window.context, if (effectiveMaterial() == ThemeManager.MATERIAL_FROSTED) 36 else 28)
        window.attributes = lp
    }

    /** 圆角裁剪 outline(材质圆角与悬浮胶囊底栏共用) */
    @JvmStatic
    fun roundedOutline(radiusPx: Int): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx.toFloat())
            }
        }
    }

    /** 实底材质的悬浮胶囊底(浮动+实底时容器用, 圆角 surface + 细描边) */
    @JvmStatic
    fun solidBarBackground(ctx: android.content.Context, radiusPx: Int): GradientDrawable {
        val surface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.setColor(surface)
        if (radiusPx > 0) gd.cornerRadius = radiusPx.toFloat()
        val outline = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutlineVariant, Color.TRANSPARENT)
        gd.setStroke(dp(ctx, 1), ColorUtils.setAlphaComponent(outline, 0x80))
        return gd
    }

    /** 半透明 surface 底 + 可选细描边 */
    private fun surfaceDrawable(ctx: android.content.Context, alphaPct: Float, radiusPx: Int, highlight: Boolean): GradientDrawable {
        val surface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.setColor(ColorUtils.setAlphaComponent(surface, (255 * alphaPct).toInt()))
        if (radiusPx > 0) gd.cornerRadius = radiusPx.toFloat()
        if (!highlight) {
            val outline = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutlineVariant, Color.TRANSPARENT)
            gd.setStroke(dp(ctx, 1), ColorUtils.setAlphaComponent(outline, 0x66))
        }
        return gd
    }

    private fun dp(ctx: android.content.Context, v: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
        ).toInt()
    }

    private fun dp(ctx: android.content.Context, v: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics
        ).toInt()
    }
}
