package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.dialog.FontDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FontApplier
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.Utils
import com.google.android.material.color.MaterialColors
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * 外观设置(首个 Compose 页面): 主题模式/主题颜色(动态取色+预设色板)/界面材质/
 * 自定义字体/预见性返回/悬浮底栏/底栏显示名称/底栏随滚动隐藏。
 * 颜色经 themeColorScheme() 从现有 M3 主题桥接(动态取色/预设覆盖层全部生效);
 * 选择弹窗沿用既有 View 版 SelectDialog/FontDialog, 通过互操作调用。
 */
class AppearanceActivity : BaseActivity() {

    private val THEME_MODES = listOf("跟随系统", "浅色", "深色")
    private val MATERIAL_OPTIONS = listOf(
        ThemeManager.MATERIAL_SOLID to "实底",
        ThemeManager.MATERIAL_TRANSLUCENT to "半透明",
        ThemeManager.MATERIAL_BLUR to "高斯模糊",
        ThemeManager.MATERIAL_LIQUID to "液态玻璃",
    )

    private val fontPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            importFont(uri)
        }
    }

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        // Compose 页面直接接管内容视图(外观页为纯静态配置列表, 适合 Compose)
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { AppearanceScreen() }
    }

    override fun init() {
    }

    private fun themeColorLabel(): String {
        val scheme = ThemeManager.colorScheme()
        return if (scheme == ThemeManager.COLOR_DYNAMIC) "动态取色"
        else ThemeManager.presetOf(scheme)?.label ?: "动态取色"
    }

    private fun materialLabel(code: String): String =
        MATERIAL_OPTIONS.firstOrNull { it.first == code }?.second ?: MATERIAL_OPTIONS[0].second

    private fun showThemeModeDialog(current: Int, onPicked: (Int) -> Unit) {
        val dialog = SelectDialog<String>(this)
        dialog.setTip("主题模式")
        dialog.setAdapter(object : SelectDialogInterface<String?> {
            override fun click(value: String?, pos: Int) {
                Hawk.put(HawkConfig.THEME_TAG, pos)
                Utils.initTheme() // setDefaultNightMode 自动重建存活 Activity
                onPicked(pos)
            }

            override fun getDisplay(name: String?): String = name ?: ""
        }, SelectDialogAdapter.stringDiff, ArrayList(THEME_MODES), current)
        dialog.show()
    }

    private fun showMaterialDialog(current: String, onPicked: (Int) -> Unit) {
        val defaultPos = MATERIAL_OPTIONS.indexOfFirst { it.first == current }
        val dialog = SelectDialog<String>(this)
        dialog.setTip("界面材质")
        dialog.setAdapter(object : SelectDialogInterface<String?> {
            override fun click(value: String?, pos: Int) {
                Hawk.put(HawkConfig.UI_MATERIAL, MATERIAL_OPTIONS[pos].first)
                EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                onPicked(pos)
            }

            override fun getDisplay(name: String?): String = name ?: ""
        }, SelectDialogAdapter.stringDiff, ArrayList(MATERIAL_OPTIONS.map { it.second }), defaultPos)
        dialog.show()
    }

    private fun launchFontPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream")
            )
        }
        fontPicker.launch(Intent.createChooser(intent, "选择字体文件(.ttf/.otf)"))
    }

    private fun importFont(uri: Uri) {
        Thread {
            try {
                val resolver = contentResolver
                var name = "font.ttf"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(idx) ?: name
                    }
                }
                val lower = name.lowercase()
                val ext = when {
                    lower.endsWith(".ttf") -> ".ttf"
                    lower.endsWith(".otf") -> ".otf"
                    else -> {
                        ThreadUtils.runOnUiThread { ToastUtils.showShort("仅支持 .ttf / .otf 字体文件") }
                        return@Thread
                    }
                }
                val dir = File(filesDir, "fonts")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, "custom_font$ext")
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                } ?: throw IllegalStateException("无法读取所选文件")
                if (target.length() > 40L * 1024 * 1024) {
                    target.delete()
                    ThreadUtils.runOnUiThread { ToastUtils.showShort("字体文件过大(>40MB)") }
                    return@Thread
                }
                Hawk.put(HawkConfig.FONT_PATH, target.absolutePath)
                FontApplier.invalidate()
                ThreadUtils.runOnUiThread {
                    ToastUtils.showShort("字体已应用")
                    EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_RECREATE))
                    recreate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ThreadUtils.runOnUiThread { ToastUtils.showShort("导入失败: ${e.message}") }
            }
        }.start()
    }

    // ---------------- Compose 界面 ----------------

    /** 从当前 Activity 主题取色桥接 Compose 色板: 动态取色/预设覆盖层自动生效 */
    private fun themeInt(attr: Int): Int = MaterialColors.getColor(this, attr, 0)

    @Composable
    private fun AppearanceScreen() {
        val ctx = this
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // 桥接既有主题色(动态取色/8 预设覆盖层都在 View 主题上生效)
        val scheme = if (dark) {
            darkColorScheme(
                primary = Color(themeInt(androidx.appcompat.R.attr.colorPrimary)),
                onPrimary = Color(themeInt(R.attr.colorOnPrimary)),
                primaryContainer = Color(themeInt(R.attr.colorPrimaryContainer)),
                onPrimaryContainer = Color(themeInt(R.attr.colorOnPrimaryContainer)),
                secondaryContainer = Color(themeInt(R.attr.colorSecondaryContainer)),
                onSecondaryContainer = Color(themeInt(R.attr.colorOnSecondaryContainer)),
                background = Color(themeInt(android.R.attr.colorBackground)),
                onBackground = Color(themeInt(R.attr.colorOnBackground)),
                surface = Color(themeInt(R.attr.colorSurface)),
                onSurface = Color(themeInt(R.attr.colorOnSurface)),
                surfaceVariant = Color(themeInt(R.attr.colorSurfaceVariant)),
                onSurfaceVariant = Color(themeInt(R.attr.colorOnSurfaceVariant)),
                outline = Color(themeInt(R.attr.colorOutline)),
                outlineVariant = Color(themeInt(R.attr.colorOutlineVariant)),
            )
        } else {
            lightColorScheme(
                primary = Color(themeInt(androidx.appcompat.R.attr.colorPrimary)),
                onPrimary = Color(themeInt(R.attr.colorOnPrimary)),
                primaryContainer = Color(themeInt(R.attr.colorPrimaryContainer)),
                onPrimaryContainer = Color(themeInt(R.attr.colorOnPrimaryContainer)),
                secondaryContainer = Color(themeInt(R.attr.colorSecondaryContainer)),
                onSecondaryContainer = Color(themeInt(R.attr.colorOnSecondaryContainer)),
                background = Color(themeInt(android.R.attr.colorBackground)),
                onBackground = Color(themeInt(R.attr.colorOnBackground)),
                surface = Color(themeInt(R.attr.colorSurface)),
                onSurface = Color(themeInt(R.attr.colorOnSurface)),
                surfaceVariant = Color(themeInt(R.attr.colorSurfaceVariant)),
                onSurfaceVariant = Color(themeInt(R.attr.colorOnSurfaceVariant)),
                outline = Color(themeInt(R.attr.colorOutline)),
                outlineVariant = Color(themeInt(R.attr.colorOutlineVariant)),
            )
        }
        // 自定义字体桥接(导入字体同时作用于 Compose 文本)
        val fontFile = Hawk.get(HawkConfig.FONT_PATH, "")
        val typography = MaterialTheme.typography.let { base ->
            val family = if (fontFile.isNotEmpty() && File(fontFile).exists()) {
                try {
                    FontFamily(android.graphics.Typeface.createFromFile(File(fontFile)))
                } catch (e: Exception) {
                    FontFamily.Default
                }
            } else FontFamily.Default
            base.copy(
                titleLarge = base.titleLarge.copy(fontFamily = family),
                titleMedium = base.titleMedium.copy(fontFamily = family),
                bodyLarge = base.bodyLarge.copy(fontFamily = family),
                bodyMedium = base.bodyMedium.copy(fontFamily = family),
                labelLarge = base.labelLarge.copy(fontFamily = family),
                labelMedium = base.labelMedium.copy(fontFamily = family),
            )
        }

        MaterialTheme(colorScheme = scheme, typography = typography) {
            AppearanceScreenContent()
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun AppearanceScreenContent() {
        var themeMode by remember { mutableStateOf(Hawk.get(HawkConfig.THEME_TAG, 0)) }
        var themeColor by remember { mutableStateOf(ThemeManager.colorScheme()) }
        var material by remember { mutableStateOf(Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_LIQUID)) }
        var liquidBlurRadius by remember { mutableFloatStateOf(Hawk.get(HawkConfig.LIQUID_BLUR_RADIUS, 16f)) }
        var liquidRefractionHeight by remember { mutableFloatStateOf(Hawk.get(HawkConfig.LIQUID_REFRACTION_HEIGHT, 20f)) }
        var liquidRefractionAmount by remember { mutableFloatStateOf(Hawk.get(HawkConfig.LIQUID_REFRACTION_AMOUNT, 14f)) }
        var liquidDispersion by remember { mutableStateOf(Hawk.get(HawkConfig.LIQUID_DISPERSION, true)) }
        var fontPath by remember { mutableStateOf(Hawk.get(HawkConfig.FONT_PATH, "")) }
        var predictive by remember { mutableStateOf(Hawk.get(HawkConfig.PREDICTIVE_BACK, false)) }
        var barFloating by remember { mutableStateOf(Hawk.get(HawkConfig.BAR_FLOATING, false)) }
        var barLabels by remember { mutableStateOf(Hawk.get(HawkConfig.BAR_SHOW_LABELS, true)) }
        var barAutoHide by remember { mutableStateOf(Hawk.get(HawkConfig.BAR_AUTO_HIDE, false)) }

        val presets = ThemeManager.PRESETS

        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("外观设置", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SectionTitle("主题")
                GroupCard {
                    MenuRow(
                        icon = R.drawable.ic_theme_mode_48,
                        title = "主题模式",
                        value = THEME_MODES.getOrElse(themeMode) { "跟随系统" },
                        onClick = {
                            showThemeModeDialog(themeMode) { picked -> themeMode = picked }
                        }
                    )
                    MenuRow(
                        icon = R.drawable.ic_palette_48,
                        title = "主题颜色",
                        value = if (themeColor == ThemeManager.COLOR_DYNAMIC) "动态取色"
                        else ThemeManager.presetOf(themeColor)?.label ?: "动态取色"
                    )
                }
                // 色板
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        val rows = presets.chunked(5)
                        // 第一行: 动态取色 + 前 4 个预设; 第二行: 其余预设
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            Swatch(
                                selected = themeColor == ThemeManager.COLOR_DYNAMIC,
                                dynamic = true,
                                label = "动态取色",
                                modifier = Modifier.weight(1f),
                                onClick = { pickThemeColor(ThemeManager.COLOR_DYNAMIC) { themeColor = it } }
                            )
                            presets.take(4).forEach { preset ->
                                Swatch(
                                    selected = themeColor == preset.id,
                                    color = Color(ContextCompat.getColor(this@AppearanceActivity, preset.swatchRes)),
                                    label = preset.label,
                                    modifier = Modifier.weight(1f),
                                    onClick = { pickThemeColor(preset.id) { themeColor = it } }
                                )
                            }
                        }
                        if (rows.size > 1) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                presets.drop(4).forEach { preset ->
                                    Swatch(
                                        selected = themeColor == preset.id,
                                        color = Color(ContextCompat.getColor(this@AppearanceActivity, preset.swatchRes)),
                                        label = preset.label,
                                        modifier = Modifier.weight(1f),
                                        onClick = { pickThemeColor(preset.id) { themeColor = it } }
                                    )
                                }
                            }
                        }
                    }
                }

                SectionTitle("界面材质")
                GroupCard {
                    MenuRow(
                        icon = R.drawable.ic_material_48,
                        title = "材质效果",
                        value = materialLabel(material),
                        onClick = {
                            showMaterialDialog(material) { pos ->
                                material = MATERIAL_OPTIONS[pos].first
                            }
                        }
                    )
                }
                Text(
                    "高斯模糊/液态玻璃需 Android 12+, 低版本自动降级为半透明",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 6.dp, top = 6.dp)
                )

                if (material == ThemeManager.MATERIAL_LIQUID) {
                    SectionTitle("液态玻璃微调")
                    GroupCard {
                        SliderRow(
                            title = "模糊半径",
                            value = liquidBlurRadius,
                            valueRange = 8f..32f,
                            displayValue = "${liquidBlurRadius.toInt()} dp",
                            onValueChange = { newValue ->
                                liquidBlurRadius = newValue
                                Hawk.put(HawkConfig.LIQUID_BLUR_RADIUS, newValue)
                                EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                            }
                        )
                        SliderRow(
                            title = "折射高度",
                            value = liquidRefractionHeight,
                            valueRange = 0f..40f,
                            displayValue = "${liquidRefractionHeight.toInt()} dp",
                            onValueChange = { newValue ->
                                liquidRefractionHeight = newValue
                                Hawk.put(HawkConfig.LIQUID_REFRACTION_HEIGHT, newValue)
                                EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                            }
                        )
                        SliderRow(
                            title = "折射强度",
                            value = liquidRefractionAmount,
                            valueRange = 0f..30f,
                            displayValue = "${liquidRefractionAmount.toInt()} dp",
                            onValueChange = { newValue ->
                                liquidRefractionAmount = newValue
                                Hawk.put(HawkConfig.LIQUID_REFRACTION_AMOUNT, newValue)
                                EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                            }
                        )
                        SwitchRow(
                            icon = R.drawable.ic_material_48,
                            title = "色散彩虹效果",
                            checked = liquidDispersion,
                            onToggle = { newValue ->
                                liquidDispersion = newValue
                                Hawk.put(HawkConfig.LIQUID_DISPERSION, newValue)
                                EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                            }
                        )
                    }
                }

                SectionTitle("字体")
                GroupCard {
                    MenuRow(
                        icon = R.drawable.ic_font_48,
                        title = "自定义字体",
                        value = if (fontPath.isEmpty()) "系统默认" else File(fontPath).name,
                        onClick = {
                            com.lxj.xpopup.XPopup.Builder(this@AppearanceActivity)
                                .hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                                .asCustom(
                                    FontDialog(this@AppearanceActivity, object : FontDialog.Callback {
                                        override fun onImport() {
                                            launchFontPicker()
                                        }

                                        override fun onReset() {
                                            Hawk.put(HawkConfig.FONT_PATH, "")
                                            FontApplier.invalidate()
                                            fontPath = ""
                                            EventBus.getDefault()
                                                .post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_RECREATE))
                                            recreate()
                                        }
                                    })
                                )
                                .show()
                        }
                    )
                }

                SectionTitle("手势与底栏")
                GroupCard {
                    SwitchRow(
                        icon = R.drawable.ic_gesture_48,
                        title = "预见性返回手势",
                        checked = predictive,
                        onToggle = { newValue ->
                            predictive = newValue
                            Hawk.put(HawkConfig.PREDICTIVE_BACK, newValue)
                        }
                    )
                    SwitchRow(
                        icon = R.drawable.ic_dock_48,
                        title = "悬浮底栏",
                        checked = barFloating,
                        onToggle = { newValue ->
                            barFloating = newValue
                            Hawk.put(HawkConfig.BAR_FLOATING, newValue)
                            EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                        }
                    )
                    SwitchRow(
                        icon = R.drawable.ic_label_48,
                        title = "底栏显示名称",
                        checked = barLabels,
                        onToggle = { newValue ->
                            barLabels = newValue
                            Hawk.put(HawkConfig.BAR_SHOW_LABELS, newValue)
                            EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                        }
                    )
                    SwitchRow(
                        icon = R.drawable.ic_auto_hide_48,
                        title = "底栏随滚动隐藏",
                        checked = barAutoHide,
                        onToggle = { newValue ->
                            barAutoHide = newValue
                            Hawk.put(HawkConfig.BAR_AUTO_HIDE, newValue)
                            EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                        }
                    )
                }
                Text(
                    "底栏随滚动隐藏: 浏览内容上滑时收起底栏, 反向下滑时呼出",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 6.dp, top = 6.dp, bottom = 24.dp)
                )
            }
        }
    }

    private fun pickThemeColor(id: String, update: (String) -> Unit) {
        Hawk.put(HawkConfig.THEME_COLOR, id)
        update(id)
        // 预设覆盖层在 Activity 创建时叠加, applyStyle 不可撤销, 必须 recreate
        EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_RECREATE))
        recreate()
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text,
            color = colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
        )
    }

    @Composable
    private fun GroupCard(content: @Composable () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column { content() }
        }
    }

    @Composable
    private fun MenuRow(icon: Int, title: String, value: String? = null, onClick: (() -> Unit)? = null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(start = 20.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                title,
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(start = 20.dp)
                    .weight(1f)
            )
            if (value != null) {
                Text(
                    value,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Icon(
                    painterResource(R.drawable.icon_pre),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    @Composable
    private fun SwitchRow(icon: Int, title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!checked) }
                .padding(start = 20.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                title,
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(start = 20.dp)
                    .weight(1f)
            )
            Switch(checked = checked, onCheckedChange = { onToggle(it) })
        }
    }

    @Composable
    private fun SliderRow(
        title: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        displayValue: String,
        onValueChange: (Float) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = colorScheme.onSurface, fontSize = 15.sp)
                Text(displayValue, color = colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.surfaceVariant
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }

    @Composable
    private fun Swatch(
        selected: Boolean,
        label: String,
        modifier: Modifier = Modifier,
        color: Color? = null,
        dynamic: Boolean = false,
        onClick: () -> Unit
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (dynamic) {
                                Modifier.background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFF4C5C92),
                                            Color(0xFFD81B60),
                                            Color(0xFF388E3C),
                                            Color(0xFF4C5C92)
                                        )
                                    ),
                                    CircleShape
                                )
                            } else {
                                Modifier.background(color ?: Color.Gray, CircleShape)
                            }
                        )
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(2.dp, colorScheme.primary, CircleShape)
                    )
                }
            }
            Text(
                label,
                fontSize = 11.sp,
                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1
            )
        }
    }
}
