package com.github.tvbox.osc.base

import android.app.Activity
import android.content.res.Resources
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.HawkConfig
import com.google.android.material.color.DynamicColors
import com.orhanobut.hawk.Hawk

/**
 * 外观中枢: 主题颜色(动态取色/预设色板)的唯一裁决处。
 * 深浅模式仍走 Utils.initTheme()+THEME_TAG, 这里只管「颜色从哪来」。
 *
 * 预设覆盖层(values/styles_presets.xml)只叠彩色 token(primary/secondary/tertiary 全组),
 * 不动中性表面色和 android:windowBackground——页面底色在切色时保持稳定,
 * 叠加到 Dialog 显式主题上也安全(CustomDialogStyle 的透明窗口背景不会被覆盖)。
 */
object ThemeManager {

    /** 主题颜色: 动态取色(Android 12+ 跟随壁纸; 低版本无此能力, 保持品牌蓝) */
    const val COLOR_DYNAMIC = "dynamic"

    // 界面材质取值(HawkConfig.UI_MATERIAL), 二选
    const val MATERIAL_YOU = "you"                 // Material Design: 实底跟随 M3 Expressive 规范
    const val MATERIAL_DESIGN = MATERIAL_YOU       // 语义化别名
    const val MATERIAL_LIQUID = "liquid"           // 液态玻璃(默认): 折射+色散+高光

    /**
     * 读取材质(二选: Material Design / 液态玻璃)。液态玻璃为默认材质且强制使用悬浮底栏,
     * 不再因底栏形态把材质降级为实底, 避免底栏渲染与设置页/弹窗得出不同结论。
     */
    @JvmStatic
    fun material(): String {
        val stored = Hawk.get(HawkConfig.UI_MATERIAL, MATERIAL_LIQUID)
        return if (stored == MATERIAL_YOU) MATERIAL_YOU else MATERIAL_LIQUID
    }

    /** 底栏形态: 液态玻璃强制悬浮(设置页已锁定开关, 这里是读取层兜底) */
    @JvmStatic
    fun barFloating(): Boolean =
        material() == MATERIAL_LIQUID || Hawk.get(HawkConfig.BAR_FLOATING, true)

    /**
     * 校正「液态玻璃 + 非悬浮」的非法组合: 旧版首次启动默认值、本地备份还原
     * 都可能留下该组合, 统一写回悬浮, 保证各读取链路结论一致。
     * App 启动与 MainActivity 应用底栏样式时调用。
     */
    @JvmStatic
    fun normalizeBarFloating() {
        if (material() == MATERIAL_LIQUID && !Hawk.get(HawkConfig.BAR_FLOATING, true)) {
            Hawk.put(HawkConfig.BAR_FLOATING, true)
        }
    }

    class Preset(val id: String, val label: String, val overlayRes: Int, val swatchRes: Int)

    @JvmStatic
    val PRESETS = listOf(
        Preset("blue", "经典蓝", R.style.ThemeOverlay_Preset_Blue, R.color.md_primary),
        Preset("teal", "青绿", R.style.ThemeOverlay_Preset_Teal, R.color.preset_teal_primary),
        Preset("green", "翠绿", R.style.ThemeOverlay_Preset_Green, R.color.preset_green_primary),
        Preset("amber", "琥珀", R.style.ThemeOverlay_Preset_Amber, R.color.preset_amber_primary),
        Preset("orange", "暖橙", R.style.ThemeOverlay_Preset_Orange, R.color.preset_orange_primary),
        Preset("red", "绯红", R.style.ThemeOverlay_Preset_Red, R.color.preset_red_primary),
        Preset("pink", "粉紫", R.style.ThemeOverlay_Preset_Pink, R.color.preset_pink_primary),
        Preset("purple", "雅紫", R.style.ThemeOverlay_Preset_Purple, R.color.preset_purple_primary),
    )

    @JvmStatic
    fun colorScheme(): String = Hawk.get(HawkConfig.THEME_COLOR, COLOR_DYNAMIC)

    @JvmStatic
    fun presetOf(id: String): Preset? = PRESETS.firstOrNull { it.id == id }

    /** 当前是否应使用动态取色(App 注册的 DynamicColors precondition 逐 Activity 裁决) */
    @JvmStatic
    fun useDynamicColor(): Boolean = colorScheme() == COLOR_DYNAMIC && DynamicColors.isDynamicColorAvailable()

    /**
     * Activity 在 super.onCreate 之前调用。
     * 动态取色走 App 里 DynamicColors 的 precondition, 不在这里叠;
     * 预设色在这里 applyStyle。注意 applyStyle 无法撤销, 同一 Activity 主题上
     * 禁止连续叠加两套覆盖层(属性残留互相污染)——切换预设色必须 recreate。
     */
    @JvmStatic
    fun applyPresetOverlay(activity: Activity) {
        if (useDynamicColor()) return
        presetOf(colorScheme())?.let { activity.theme.applyStyle(it.overlayRes, true) }
    }

    /**
     * 显式主题的 Dialog 窗口在 setContentView 前调用:
     * activity 上的覆盖层(动态或预设)不会传导进 CustomDialogStyle 这类显式主题,
     * 需要对 window theme 再叠一次, 弹窗颜色才能跟随全局。
     */
    @JvmStatic
    fun applyOverlayToDialogTheme(theme: Resources.Theme) {
        if (DynamicColors.isDynamicColorAvailable() && colorScheme() == COLOR_DYNAMIC) {
            theme.applyStyle(R.style.ThemeOverlay_Material3_DynamicColors_DayNight, true)
            return
        }
        presetOf(colorScheme())?.let { theme.applyStyle(it.overlayRes, true) }
    }
}
