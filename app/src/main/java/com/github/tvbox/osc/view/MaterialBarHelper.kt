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
 * 界面材质系统: 按 UI_MATERIAL 设置给底栏/顶栏等悬浮容器套材质, 并给弹窗加背景模糊。
 *
 * 材质取值(ThemeManager.MATERIAL_*):
 *  - solid       实底(默认, 组件自带背景, 本类不做任何事)
 *  - translucent 半透明 surface + 细描边
 *  - blur        高斯模糊背板(Android 12+ RenderEffect, 低版本自动降级半透明)
 *  - liquid      液态玻璃 = 模糊 + 更亮着色 + 高光描边(低版本同样降级)
 *
 * 性能: 模糊层每帧采样根视图, 仅在材质≠实底时创建; 采样源为 android.R.id.content。
 */
object MaterialBarHelper {

    private const val TAG_BLUR_LAYER = "material_blur_layer"

    @JvmStatic
    fun material(): String = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_SOLID)

    /** 原生跨窗口/视图层级真模糊需要 Android 12+ */
    @JvmStatic
    fun blurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 实际生效的材质: 低版本把 blur/liquid 降级为 translucent */
    @JvmStatic
    fun effectiveMaterial(): String = normalizeMaterial(material(), Build.VERSION.SDK_INT)

    /** 纯逻辑(供单测): 材质降级规则 */
    @JvmStatic
    fun normalizeMaterial(m: String, sdkInt: Int): String {
        return if ((m == ThemeManager.MATERIAL_BLUR || m == ThemeManager.MATERIAL_LIQUID)
            && sdkInt < Build.VERSION_CODES.S
        ) {
            ThemeManager.MATERIAL_TRANSLUCENT
        } else {
            m
        }
    }

    /** XPopup 弹窗背景模糊开关: XPopup 自带 RenderScript 模糊全版本可用, blur/liquid 时开启 */
    @JvmStatic
    fun popupBlurEnabled(): Boolean =
        material() == ThemeManager.MATERIAL_BLUR || material() == ThemeManager.MATERIAL_LIQUID

    /**
     * 给悬浮容器(底栏/首页顶栏)套材质。
     * solid: 清空自定义背景交给组件默认; translucent: 半透明着色+描边;
     * blur/liquid: 插入一层 BlurView 背板(index 0, 带 tag 防重复), 容器按 cornerRadiusPx 裁圆角。
     *
     * @return 插入的模糊背板 View, 无模糊时为 null
     */
    @JvmStatic
    fun applyToBar(container: FrameLayout, activity: Activity, cornerRadiusPx: Int): View? {
        container.findViewWithTag<View>(TAG_BLUR_LAYER)?.let { container.removeView(it) }
        val ctx = container.context
        when (effectiveMaterial()) {
            ThemeManager.MATERIAL_SOLID -> {
                container.background = null
                container.foreground = null
                container.clipToOutline = false
                return null
            }
            ThemeManager.MATERIAL_TRANSLUCENT -> {
                container.background = surfaceDrawable(ctx, 0.65f, cornerRadiusPx, highlight = false)
                container.foreground = null
                container.clipToOutline = cornerRadiusPx > 0
                container.outlineProvider = roundedOutline(cornerRadiusPx)
                return null
            }
            else -> { // blur / liquid
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
                    .setBlurRadius(if (liquid) 30f else 18f)
                    .setBlurAutoUpdate(true)
                // 模糊之上叠 surface 着色(液态玻璃更透亮), 保证内容可读性
                val surface = MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurface)
                val tint = ColorUtils.setAlphaComponent(surface, if (liquid) 0x59 else 0x73)
                blurView.setOverlayColor(tint)
                container.addView(
                    blurView, 0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                // 液态玻璃: 高光描边
                container.foreground = if (liquid) highlightStroke(ctx, cornerRadiusPx) else null
                return blurView
            }
        }
    }

    /** 普通弹窗(BaseDialog 系)跨窗背景模糊: API31+, 省电模式关闭跨窗模糊时系统自动跳过 */
    @JvmStatic
    fun applyDialogBlur(window: Window?) {
        if (window == null || material() == ThemeManager.MATERIAL_SOLID) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val lp = window.attributes
        lp.blurBehindRadius = dp(window.context, 28)
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

    /** 液态玻璃高光描边(顶部亮/底部暗的渐变边) */
    private fun highlightStroke(ctx: android.content.Context, radiusPx: Int): GradientDrawable {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.setColor(Color.TRANSPARENT)
        if (radiusPx > 0) gd.cornerRadius = radiusPx.toFloat()
        gd.setStroke(dp(ctx, 1), 0x40FFFFFF)
        return gd
    }

    private fun dp(ctx: android.content.Context, v: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
        ).toInt()
    }
}
