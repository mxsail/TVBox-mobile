package com.github.tvbox.osc.view

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView

/**
 * 列表滚动回弹(iOS 式 overscroll bounce): 列表已滚到边缘后继续拖动,
 * 内容带阻尼跟手位移, 松手回弹。原 overscroll-decorator 库上游已删库
 * (GitHub 404/jitpack 401), 故自实现, 零依赖、全版本观感一致
 * (Android 12+ 由本效果接管, 不再出现系统 stretch)。
 *
 * 限制: 手指按住正常滚动途中抵达边缘不触发(事件已被列表接管),
 * 需松手后在边缘再次拖动——与常见回弹实现一致。
 */
object BounceOverScroll {

    private const val MAX_FRACTION = 0.35f // 最大位移 = 容器高的 35%
    private const val DAMPING = 0.45f      // 边缘拖动阻尼系数

    @JvmStatic
    fun attach(view: View?) {
        when (view) {
            is RecyclerView -> view.addOnItemTouchListener(BounceListener(view))
            is ScrollView -> view.setOnTouchListener(BounceTouchListener(view))
        }
    }

    private fun springBack(target: View, from: Float, onEnd: () -> Unit = {}): ValueAnimator {
        val anim = ValueAnimator.ofFloat(from, 0f)
        anim.duration = 260
        anim.interpolator = DecelerateInterpolator(1.6f)
        anim.addUpdateListener { animation ->
            target.translationY = animation.animatedValue as Float
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                target.translationY = 0f
                onEnd()
            }
        })
        anim.start()
        return anim
    }

    /** RecyclerView 版: 列表无法滚动时拦截边缘拖拽做回弹 */
    private class BounceListener(private val rv: RecyclerView) : RecyclerView.OnItemTouchListener {

        private var lastY = 0f
        private var active = false
        private var offset = 0f
        private var maxOffset = 0f
        private var anim: ValueAnimator? = null

        private fun cancelAnim() {
            anim?.cancel()
            anim = null
        }

        private fun applyDrag(dy: Float) {
            offset = (offset + dy * DAMPING).coerceIn(-maxOffset, maxOffset)
            rv.translationY = offset
        }

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAnim()
                    lastY = e.y
                    active = false
                    maxOffset = rv.height * MAX_FRACTION
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.y - lastY
                    lastY = e.y
                    // dy<0=手指上滑看下方内容 → 检查能否向下滚 canScroll(1); 反之检查 canScroll(-1)
                    if (!active && dy != 0f && !rv.canScrollVertically(if (dy < 0) 1 else -1)) {
                        active = true
                    }
                    if (active) {
                        applyDrag(dy)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (active) {
                        active = false
                        anim = springBack(rv, offset)
                        offset = 0f
                    }
                }
            }
            return active
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> applyDrag(e.y - lastY).also { lastY = e.y }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    active = false
                    anim = springBack(rv, offset)
                    offset = 0f
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    /** ScrollView 版 */
    private class BounceTouchListener(private val view: View) : View.OnTouchListener {

        private var lastY = 0f
        private var active = false
        private var offset = 0f
        private var maxOffset = 0f
        private var anim: ValueAnimator? = null

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    anim?.cancel()
                    anim = null
                    lastY = e.y
                    active = false
                    maxOffset = view.height * MAX_FRACTION
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.y - lastY
                    lastY = e.y
                    // 同上: 方向语义与 RecyclerView 版一致
                    if (!active && dy != 0f && !view.canScrollVertically(if (dy < 0) 1 else -1)) {
                        active = true
                    }
                    if (active) {
                        offset = (offset + dy * DAMPING).coerceIn(-maxOffset, maxOffset)
                        view.translationY = offset
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (active) {
                        active = false
                        anim = springBack(view, offset)
                        offset = 0f
                        return true
                    }
                }
            }
            return false
        }
    }
}
