package com.github.tvbox.osc.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.adapter.CategoryChipAdapter

/**
 * 全部分类下拉菜单: 自分类栏向下展开的沉浸式面板(无遮罩, 覆盖在内容上),
 * 宽度自适应屏幕(两侧 12dp 边距), 高度按分类行数自适应(封顶 60% 屏, 多则内部滚动)。
 */
class CategoryDropdownPopup(
    context: Context,
    names: List<String>,
    current: Int,
    private val onPick: (Int) -> Unit
) {

    private val window = PopupWindow(context)
    private val widthPx: Int
    private val contentHeightPx: Int

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.popup_category_dropdown, null)
        val rv = view.findViewById<RecyclerView>(R.id.rv_categories)
        rv.layoutManager = GridLayoutManager(context, 4)
        rv.adapter = CategoryChipAdapter { position ->
            dismiss()
            onPick(position)
        }
        (rv.adapter as CategoryChipAdapter).setData(names, current)

        val metrics = context.resources.displayMetrics
        val margin = dp(context, 12)
        widthPx = metrics.widthPixels - margin * 2
        // 行高(chip 40dp+margin 8dp≈48dp/行), 封顶 60% 屏高
        val rows = Math.ceil(names.size / 4.0).toInt().coerceIn(1, 4)
        contentHeightPx = Math.min(
            dp(context, rows * 48 + 12),
            (metrics.heightPixels * 0.6f).toInt()
        )
        // 宽高直接定死在 contentView 上(PopupWindow 的 width 字段在此场景不生效)
        view.layoutParams = android.widget.FrameLayout.LayoutParams(widthPx, contentHeightPx)
        rv.layoutParams = rv.layoutParams.apply { height = contentHeightPx - dp(context, 16) }

        window.contentView = view
        window.width = widthPx
        window.height = contentHeightPx
        window.isOutsideTouchable = true
        window.isFocusable = true // BACK 可关闭
        window.animationStyle = R.style.CategoryDropdownAnimation
        // 透明背景+elevation: 卡片观感由 contentView 自带 M3 背景承担
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.elevation = dp(context, 8).toFloat()
    }

    /** 自分类栏按钮下方展开, 横跨屏幕(左右 12dp 边距) */
    fun show(anchor: View) {
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val context = anchor.context
        window.showAtLocation(
            anchor.rootView,
            Gravity.TOP or Gravity.START,
            dp(context, 12),
            location[1] + anchor.height + dp(context, 2)
        )
    }

    fun dismiss() {
        if (window.isShowing) window.dismiss()
    }

    private fun dp(context: Context, v: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
