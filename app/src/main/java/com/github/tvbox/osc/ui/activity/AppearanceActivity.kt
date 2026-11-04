package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.databinding.ActivityAppearanceBinding
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.ui.adapter.AppearanceSwatchAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.dialog.FontDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FontApplier
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.Utils
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

/**
 * 外观设置: 主题模式/主题颜色(动态取色+预设色板)/界面材质/自定义字体/
 * 预见性返回手势/悬浮底栏/底栏随滚动隐藏。
 */
class AppearanceActivity : BaseVbActivity<ActivityAppearanceBinding>() {

    companion object {
        private val THEME_MODES = listOf("跟随系统", "浅色", "深色")
        private val MATERIAL_OPTIONS = listOf(
            ThemeManager.MATERIAL_SOLID to "实底",
            ThemeManager.MATERIAL_TRANSLUCENT to "半透明",
            ThemeManager.MATERIAL_BLUR to "高斯模糊",
            ThemeManager.MATERIAL_LIQUID to "液态玻璃",
        )
        private const val FONT_MAX_SIZE = 40L * 1024 * 1024
    }

    private lateinit var swatchAdapter: AppearanceSwatchAdapter

    private val fontPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            importFont(uri)
        }
    }

    override fun init() {
        mBinding.titleBar.setNavigationOnClickListener { finish() }

        initThemeMode()
        initThemeColor()
        initMaterial()
        initFont()
        initSwitches()
    }

    private fun initThemeMode() {
        val mode = Hawk.get(HawkConfig.THEME_TAG, 0)
        mBinding.tvThemeMode.text = THEME_MODES[mode]
        mBinding.llThemeMode.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val oldMode = Hawk.get(HawkConfig.THEME_TAG, 0)
            val names = ArrayList(THEME_MODES)
            val dialog = SelectDialog<String>(this)
            dialog.setTip("主题模式")
            dialog.setAdapter(object : SelectDialogInterface<String?> {
                override fun click(value: String?, pos: Int) {
                    Hawk.put(HawkConfig.THEME_TAG, pos)
                    // setDefaultNightMode 会自动重建所有存活 Activity, 无需手动 recreate
                    Utils.initTheme()
                }

                override fun getDisplay(name: String?): String {
                    return name ?: ""
                }
            }, SelectDialogAdapter.stringDiff, names, oldMode)
            dialog.show()
        }
    }

    private fun initThemeColor() {
        updateThemeColorLabel()
        swatchAdapter = AppearanceSwatchAdapter(ThemeManager.colorScheme()) { id ->
            Hawk.put(HawkConfig.THEME_COLOR, id)
            updateThemeColorLabel()
            // 预设色覆盖层在 Activity 创建时叠加, applyStyle 不可撤销, 必须 recreate
            EventBus.getDefault().post(AppearanceChangedEvent())
            recreate()
        }
        mBinding.rvSwatches.layoutManager = GridLayoutManager(this, 5)
        mBinding.rvSwatches.adapter = swatchAdapter
        mBinding.rvSwatches.isNestedScrollingEnabled = false
        mBinding.llThemeColor.setOnClickListener {
            ToastUtils.showShort("点击下方色块切换主题颜色")
        }
    }

    private fun updateThemeColorLabel() {
        val scheme = ThemeManager.colorScheme()
        mBinding.tvThemeColor.text = if (scheme == ThemeManager.COLOR_DYNAMIC) {
            "动态取色"
        } else {
            ThemeManager.presetOf(scheme)?.label ?: "动态取色"
        }
    }

    private fun initMaterial() {
        val current = Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_SOLID)
        mBinding.tvMaterial.text = materialLabel(current)
        mBinding.llMaterial.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val names = ArrayList(MATERIAL_OPTIONS.map { it.second })
            val defaultPos = MATERIAL_OPTIONS.indexOfFirst { it.first == Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_SOLID) }
            val dialog = SelectDialog<String>(this)
            dialog.setTip("界面材质")
            dialog.setAdapter(object : SelectDialogInterface<String?> {
                override fun click(value: String?, pos: Int) {
                    Hawk.put(HawkConfig.UI_MATERIAL, MATERIAL_OPTIONS[pos].first)
                    mBinding.tvMaterial.text = MATERIAL_OPTIONS[pos].second
                    EventBus.getDefault().post(AppearanceChangedEvent())
                }

                override fun getDisplay(name: String?): String {
                    return name ?: ""
                }
            }, SelectDialogAdapter.stringDiff, names, defaultPos)
            dialog.show()
        }
    }

    private fun materialLabel(code: String): String {
        return MATERIAL_OPTIONS.firstOrNull { it.first == code }?.second ?: MATERIAL_OPTIONS[0].second
    }

    private fun initFont() {
        updateFontLabel()
        mBinding.llFont.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            showFontDialog()
        }
    }

    private fun showFontDialog() {
        com.lxj.xpopup.XPopup.Builder(mContext)
            .asCustom(FontDialog(this, object : FontDialog.Callback {
                override fun onImport() {
                    launchFontPicker()
                }

                override fun onReset() {
                    Hawk.put(HawkConfig.FONT_PATH, "")
                    FontApplier.invalidate()
                    updateFontLabel()
                    EventBus.getDefault().post(AppearanceChangedEvent())
                    recreate()
                }
            }))
            .show()
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
                    target.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("无法读取所选文件")
                if (target.length() > FONT_MAX_SIZE) {
                    target.delete()
                    ThreadUtils.runOnUiThread { ToastUtils.showShort("字体文件过大(>40MB)") }
                    return@Thread
                }
                Hawk.put(HawkConfig.FONT_PATH, target.absolutePath)
                FontApplier.invalidate()
                ThreadUtils.runOnUiThread {
                    ToastUtils.showShort("字体已应用")
                    updateFontLabel()
                    EventBus.getDefault().post(AppearanceChangedEvent())
                    recreate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ThreadUtils.runOnUiThread { ToastUtils.showShort("导入失败: ${e.message}") }
            }
        }.start()
    }

    private fun updateFontLabel() {
        val path = Hawk.get(HawkConfig.FONT_PATH, "")
        mBinding.tvFont.text = if (path.isEmpty()) "系统默认" else File(path).name
    }

    private fun initSwitches() {
        mBinding.swPredictiveBack.setChecked(Hawk.get(HawkConfig.PREDICTIVE_BACK, false))
        mBinding.llPredictiveBack.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val newValue = !Hawk.get(HawkConfig.PREDICTIVE_BACK, false)
            mBinding.swPredictiveBack.setChecked(newValue)
            Hawk.put(HawkConfig.PREDICTIVE_BACK, newValue)
        }

        mBinding.swBarFloating.setChecked(Hawk.get(HawkConfig.BAR_FLOATING, false))
        mBinding.llBarFloating.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val newValue = !Hawk.get(HawkConfig.BAR_FLOATING, false)
            mBinding.swBarFloating.setChecked(newValue)
            Hawk.put(HawkConfig.BAR_FLOATING, newValue)
            EventBus.getDefault().post(AppearanceChangedEvent())
        }

        mBinding.swBarAutoHide.setChecked(Hawk.get(HawkConfig.BAR_AUTO_HIDE, false))
        mBinding.llBarAutoHide.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val newValue = !Hawk.get(HawkConfig.BAR_AUTO_HIDE, false)
            mBinding.swBarAutoHide.setChecked(newValue)
            Hawk.put(HawkConfig.BAR_AUTO_HIDE, newValue)
            EventBus.getDefault().post(AppearanceChangedEvent())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAppearanceChanged(event: AppearanceChangedEvent) {
        // 其他页面改了外观时本页刷新值显示(当前主要入口是本页自身)
        updateThemeColorLabel()
        mBinding.tvMaterial.text = materialLabel(Hawk.get(HawkConfig.UI_MATERIAL, ThemeManager.MATERIAL_SOLID))
        updateFontLabel()
    }
}
