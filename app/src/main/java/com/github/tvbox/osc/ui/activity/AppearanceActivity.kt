package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.M3ActionsCenterDialog
import com.github.tvbox.osc.ui.compose.M3ItemAction
import com.github.tvbox.osc.ui.compose.M3SlidingSegmentedPill
import com.github.tvbox.osc.ui.compose.MenuRow
import com.github.tvbox.osc.ui.compose.SectionTitle
import com.github.tvbox.osc.ui.compose.SwitchRow
import com.github.tvbox.osc.ui.compose.SliderRow
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.TvBoxTopBar
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
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
import androidx.compose.ui.draw.clip
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
        Triple(ThemeManager.MATERIAL_YOU, "Material Design", "实底 · M3 Expressive 规范"),
        Triple(ThemeManager.MATERIAL_LIQUID, "液态玻璃", "折射 · 色散 · 动态高光"),
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
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            TvBoxTheme {
                AppearanceScreen()
            }
        }
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

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun AppearanceScreen() {
        var themeMode by remember { mutableStateOf(Hawk.get(HawkConfig.THEME_TAG, 0)) }
        var themeColor by remember { mutableStateOf(ThemeManager.colorScheme()) }
        var material by remember { mutableStateOf(ThemeManager.material()) }
        var liquidBlurRadius by remember { mutableFloatStateOf(Hawk.get(HawkConfig.LIQUID_BLUR_RADIUS, 16f)) }
        var liquidRefractionHeight by remember { mutableFloatStateOf(Hawk.get(HawkConfig.LIQUID_REFRACTION_HEIGHT, 20f)) }
        var liquidRefractionAmount by remember { mutableFloatStateOf(Hawk.get(HawkConfig.LIQUID_REFRACTION_AMOUNT, 14f)) }
        var liquidDispersion by remember { mutableStateOf(Hawk.get(HawkConfig.LIQUID_DISPERSION, true)) }
        var fontPath by remember { mutableStateOf(Hawk.get(HawkConfig.FONT_PATH, "")) }
        var showFontDialog by remember { mutableStateOf(false) }
        var predictive by remember { mutableStateOf(Hawk.get(HawkConfig.PREDICTIVE_BACK, false)) }
        var barFloating by remember { mutableStateOf(ThemeManager.barFloating()) }
        var barLabels by remember { mutableStateOf(Hawk.get(HawkConfig.BAR_SHOW_LABELS, true)) }
        var barAutoHide by remember { mutableStateOf(Hawk.get(HawkConfig.BAR_AUTO_HIDE, false)) }

        val presets = ThemeManager.PRESETS
        val isDark = isAppDarkTheme()
        val pageBg = if (isDark) colorScheme.background else colorScheme.surfaceContainerLow

        Scaffold(
            containerColor = pageBg,
            topBar = {
                TvBoxTopBar(
                    title = "外观设置",
                    onBack = { finish() }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                SectionTitle("预览")
                PreviewCard(
                    themeMode = themeMode,
                    themeColor = themeColor,
                    material = material,
                    blurRadius = liquidBlurRadius,
                    refractionAmount = liquidRefractionAmount,
                    dispersion = liquidDispersion,
                    barFloating = if (material == ThemeManager.MATERIAL_LIQUID) true else barFloating,
                    barLabels = barLabels,
                    fontPath = fontPath
                )

                SectionTitle("主题")
                GroupCard {
                    Text(
                        "主题模式",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp)
                    )
                    M3SlidingSegmentedPill(
                        items = THEME_MODES.toList(),
                        selectedIndex = themeMode,
                        onItemSelected = { index ->
                            Hawk.put(HawkConfig.THEME_TAG, index)
                            Utils.initTheme()
                            themeMode = index
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    MenuRow(
                        icon = R.drawable.ic_palette_48,
                        title = "主题颜色",
                        value = if (themeColor == ThemeManager.COLOR_DYNAMIC) "动态取色"
                        else ThemeManager.presetOf(themeColor)?.label ?: "动态取色",
                        containerColor = Color.Transparent
                    )
                }
                // 色板
                GroupCard(
                    modifier = Modifier.padding(top = 8.dp)
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

                SectionTitle("底栏与材质")
                GroupCard {
                    Text(
                        "材质效果",
                        color = colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MATERIAL_OPTIONS.forEach { (code, label, desc) ->
                            val isSelected = material == code
                            val tileBg = if (isSelected) colorScheme.primaryContainer else if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFF3F3F4)
                            val tileBorder = if (isSelected) colorScheme.primary else if (isDark) colorScheme.outlineVariant.copy(alpha = 0.3f) else Color(0xFFE5E5EA)
                            val contentColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                            val descColor = if (isSelected) colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant
                            val iconRes = if (code == ThemeManager.MATERIAL_LIQUID) R.drawable.ic_material_48 else R.drawable.ic_palette_48

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        if (material != code) {
                                            Hawk.put(HawkConfig.UI_MATERIAL, code)
                                            material = code
                                            if (code == ThemeManager.MATERIAL_LIQUID) {
                                                barFloating = true
                                                Hawk.put(HawkConfig.BAR_FLOATING, true)
                                            }
                                            EventBus.getDefault()
                                                .post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                                        }
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = tileBg),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, tileBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) colorScheme.primary.copy(alpha = 0.18f)
                                                    else colorScheme.surfaceContainerHighest
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painterResource(iconRes),
                                                contentDescription = null,
                                                tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
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
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        label,
                                        color = contentColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        desc,
                                        color = descColor,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        minLines = 2,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    val isLiquidMode = material == ThemeManager.MATERIAL_LIQUID
                    SwitchRow(
                        icon = R.drawable.ic_dock_48,
                        title = "悬浮底栏",
                        checked = if (isLiquidMode) true else barFloating,
                        subtitle = if (isLiquidMode) "液态玻璃仅支持悬浮形态" else "开启时为 M3 悬浮胶囊，关闭时为平直贴底栏",
                        enabled = !isLiquidMode,
                        containerColor = Color.Transparent,
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
                        containerColor = Color.Transparent,
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
                        containerColor = Color.Transparent,
                        onToggle = { newValue ->
                            barAutoHide = newValue
                            Hawk.put(HawkConfig.BAR_AUTO_HIDE, newValue)
                            EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_STYLE))
                        }
                    )
                }

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

                SectionTitle("字体与手势")
                GroupCard {
                    MenuRow(
                        icon = R.drawable.ic_font_48,
                        title = "自定义字体",
                        value = if (fontPath.isEmpty()) "系统默认" else File(fontPath).name,
                        containerColor = Color.Transparent,
                        onClick = {
                            showFontDialog = true
                        }
                    )
                    SwitchRow(
                        icon = R.drawable.ic_gesture_48,
                        title = "预见性返回手势",
                        checked = predictive,
                        containerColor = Color.Transparent,
                        onToggle = { newValue ->
                            predictive = newValue
                            Hawk.put(HawkConfig.PREDICTIVE_BACK, newValue)
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

        if (showFontDialog) {
            M3ActionsCenterDialog(
                visible = showFontDialog,
                title = "自定义字体设置",
                subtitle = if (fontPath.isEmpty()) "当前使用系统默认字体" else "当前应用: ${File(fontPath).name}",
                actions = listOf(
                    M3ItemAction(
                        title = "导入新字体文件 (.ttf / .otf)",
                        iconRes = R.drawable.ic_add_24,
                        onClick = {
                            showFontDialog = false
                            launchFontPicker()
                        }
                    ),
                    M3ItemAction(
                        title = "恢复系统默认字体",
                        iconRes = R.drawable.ic_clear,
                        isDestructive = true,
                        onClick = {
                            showFontDialog = false
                            Hawk.put(HawkConfig.FONT_PATH, "")
                            FontApplier.invalidate()
                            fontPath = ""
                            EventBus.getDefault().post(AppearanceChangedEvent(AppearanceChangedEvent.TYPE_RECREATE))
                            recreate()
                        }
                    )
                ),
                onDismissRequest = { showFontDialog = false }
            )
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
    private fun PreviewCard(
        themeMode: Int,
        themeColor: String,
        material: String,
        blurRadius: Float,
        refractionAmount: Float,
        dispersion: Boolean,
        barFloating: Boolean,
        barLabels: Boolean,
        fontPath: String
    ) {
        val previewDark = when (themeMode) {
            0 -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            1 -> false
            else -> true
        }
        val preset = ThemeManager.presetOf(themeColor)
        val primary = when {
            themeColor == ThemeManager.COLOR_DYNAMIC ->
                Color(themeInt(androidx.appcompat.R.attr.colorPrimary))
            preset != null -> Color(ContextCompat.getColor(this, preset.swatchRes))
            else -> Color(themeInt(androidx.appcompat.R.attr.colorPrimary))
        }
        val bg = if (previewDark) Color(0xFF141418) else Color(0xFFFAF8FF)
        val surfaceHigh = if (previewDark) Color(0xFF2A2B31) else Color(0xFFECECF1)
        val onSurface = if (previewDark) Color(0xFFE4E2E8) else Color(0xFF1B1B1F)

        // 自定义字体联动
        val previewFontFamily = remember(fontPath) {
            if (fontPath.isNotEmpty() && File(fontPath).exists()) {
                try {
                    FontFamily(android.graphics.Typeface.createFromFile(File(fontPath)))
                } catch (_: Exception) {
                    FontFamily.Default
                }
            } else {
                FontFamily.Default
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(126.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(bg)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(224.dp)
                ) {
                    // 状态栏区
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(if (previewDark) Color.Black else Color(0xFFECECEE))
                    )
                    // 顶栏: TVBox 标题 + 右侧主题主色圆点
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TVBox",
                            color = onSurface,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = previewFontFamily
                        )
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(6.dp).background(primary, CircleShape))
                    }
                    // 内容区: 搜索条 + 海报墙 + Banner 卡片
                    Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(surfaceHigh, RoundedCornerShape(5.dp))
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(3) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(0.72f)
                                        .background(surfaceHigh, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(14.dp)
                                .background(surfaceHigh, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    // mini 底栏: 联动 悬浮底栏(barFloating) + 显示名称(barLabels) + 材质(material) + 液态微调
                    val barHeight = if (barLabels) 22.dp else 17.dp
                    val barShape = if (barFloating) RoundedCornerShape(9.dp) else androidx.compose.ui.graphics.RectangleShape

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (barFloating) Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .then(
                                    miniBarModifier(
                                        material = material,
                                        dark = previewDark,
                                        shape = barShape,
                                        blurRadius = blurRadius,
                                        refractionAmount = refractionAmount,
                                        dispersion = dispersion,
                                        floating = barFloating
                                    )
                                )
                                .padding(horizontal = if (barFloating) 8.dp else 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf("首页", "直播", "下载", "我的")
                            tabs.forEachIndexed { i, label ->
                                val selected = i == 0
                                Box(contentAlignment = Alignment.Center) {
                                    if (material == ThemeManager.MATERIAL_YOU && selected) {
                                        Box(
                                            Modifier
                                                .size(if (barLabels) 20.dp else 14.dp, if (barLabels) 12.dp else 8.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(primary.copy(alpha = 0.22f))
                                        )
                                    }
                                    if (barLabels) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(if (selected) 4.5.dp else 3.5.dp)
                                                    .background(
                                                        if (selected) primary else onSurface.copy(alpha = 0.5f),
                                                        CircleShape
                                                    )
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 5.sp,
                                                lineHeight = 6.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) primary else onSurface.copy(alpha = 0.6f),
                                                fontFamily = previewFontFamily
                                            )
                                        }
                                    } else {
                                        Box(
                                            Modifier
                                                .size(if (selected) 7.dp else 4.5.dp)
                                                .background(
                                                    if (selected) primary else onSurface.copy(alpha = 0.55f),
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 预览用迷你底栏配方: 联动材质、微调模糊与折射参数、色散彩虹边框 */
    @Composable
    private fun miniBarModifier(
        material: String,
        dark: Boolean,
        shape: androidx.compose.ui.graphics.Shape,
        blurRadius: Float,
        refractionAmount: Float,
        dispersion: Boolean = false,
        floating: Boolean = true
    ): Modifier {
        return when (material) {
            ThemeManager.MATERIAL_YOU -> {
                val solid = if (floating) {
                    if (dark) Color(0xFF2C2D35) else Color(0xFFECECF2)
                } else {
                    if (dark) Color(0xFF202024) else Color(0xFFF7F7F9)
                }
                Modifier
                    .background(solid, shape)
                    .then(
                        if (floating) Modifier.border(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
                        else Modifier
                    )
            }
            else -> { // MATERIAL_LIQUID
                val liquidBg = if (dark) Color(0x55323444) else Color(0x88FFFFFF)
                val strokeBrush = if (dispersion) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xCCFFFFFF),
                            Color(0x8876A0FF),
                            Color(0x88FF8A80),
                            Color(0xCCFFFFFF)
                        )
                    )
                } else {
                    // 折射强度联动: 高光边界更亮
                    val highAlpha = (0.50f + (refractionAmount / 30f) * 0.40f).coerceIn(0.4f, 0.95f)
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = highAlpha),
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
                Modifier
                    .background(liquidBg, shape)
                    .border(1.dp, strokeBrush, shape)
            }
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
