package com.github.tvbox.osc.util

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.orhanobut.hawk.Hawk
import java.io.File

/**
 * 自定义字体应用器: 用户导入 .ttf/.otf 后全局生效。
 *
 * 视图创建走 BaseActivity 里挂的 FontFactory2(LayoutInflater.Factory2, 委托 AppCompat 原工厂),
 * BottomNavigationView 菜单等非 inflater 创建的文本由 applyToViewTree 兜底遍历;
 * 弹窗 inflater 克隆自 activity, 工厂自动生效。
 */
object FontApplier {

    private var cachePath: String? = null
    private var cacheTypeface: Typeface? = null

    @JvmStatic
    fun fontPath(): String = Hawk.get(HawkConfig.FONT_PATH, "")

    @JvmStatic
    fun hasCustomFont(): Boolean = fontPath().isNotEmpty()

    /** 字体文件更新(导入/重置)后清缓存 */
    @JvmStatic
    fun invalidate() {
        cachePath = null
        cacheTypeface = null
    }

    /** 解析当前字体; 文件被清理或备份还原到新机后不存在时, 自动清 key 回退系统默认 */
    @JvmStatic
    fun typeface(): Typeface? {
        val path = fontPath()
        if (path.isEmpty()) return null
        if (path == cachePath) return cacheTypeface
        val file = File(path)
        cachePath = path
        if (!file.exists()) {
            Hawk.put(HawkConfig.FONT_PATH, "")
            cacheTypeface = null
            return null
        }
        cacheTypeface = try {
            Typeface.createFromFile(file)
        } catch (e: Exception) {
            // 损坏/不支持的字体文件: 清 key 永久回退系统默认, 避免每个视图反复解析
            Hawk.put(HawkConfig.FONT_PATH, "")
            null
        }
        return cacheTypeface
    }

    /** 单视图应用(仅 TextView 系, 工厂回调用) */
    @JvmStatic
    fun applyToView(view: View) {
        if (view is TextView && cacheTypeface != null) {
            view.typeface = cacheTypeface
        }
    }

    /** 视图树兜底遍历(setContentView 后对底栏菜单等非 inflater 视图补一次) */
    @JvmStatic
    fun applyToViewTree(root: View?) {
        if (root == null || cacheTypeface == null) return
        walk(root)
    }

    private fun walk(view: View) {
        if (view is TextView && cacheTypeface != null) {
            view.typeface = cacheTypeface
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
    }
}

/**
 * LayoutInflater.Factory2: 先委托 AppCompat 原工厂完成视图创建(Material 控件转换依赖它),
 * 创建出的 TextView 立即套用自定义字体。必须挂在 AppCompatActivity super.onCreate 之前——
 * appcompat 检测到已有 Factory2 就不再安装自己的, 由这里转发, 否则字体不生效或控件失效。
 */
class FontFactory2(private val delegate: LayoutInflater.Factory2) : LayoutInflater.Factory2 {

    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        val view = delegate.onCreateView(parent, name, context, attrs)
        if (view != null) FontApplier.applyToView(view)
        return view
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }
}
