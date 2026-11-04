package com.github.tvbox.osc.view

import android.os.Build
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 底栏滚动隐藏的列表挂载工具: 各 tab 的主列表滚动方向驱动 MainActivity 底栏收起/呼出。
 * 各列表在视图创建处调用 attach(含 GridFragment 多级栈动态创建的列表)。
 */
object BarAutoHide {

    /** 主界面底栏宿主(MainActivity 实现) */
    interface ScrollBarHost {
        fun onMainScrollDelta(dy: Int)
    }

    @JvmStatic
    fun attach(rv: RecyclerView?, host: ScrollBarHost?) {
        if (rv == null || host == null) return
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                host.onMainScrollDelta(dy)
            }
        })
    }

    @JvmStatic
    fun attachScrollView(view: View?, host: ScrollBarHost?) {
        if (view == null || host == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        view.setOnScrollChangeListener { _, _, y, _, oldY ->
            host.onMainScrollDelta(y - oldY)
        }
    }
}
