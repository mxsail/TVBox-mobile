package com.github.tvbox.osc.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.tvbox.osc.ui.compose.LoadState
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import me.jessyan.autosize.AutoSize

/**
 * 懒加载 Fragment 的 Compose 渲染层基类: 保留 BaseLazyFragment 的可见性分发
 * (setUserVisibleHint/嵌套可见性)与 AutoSize 机制, 视图根换成 ComposeView;
 * LoadSir 三态改为状态驱动(展示用 ui.compose.LoadStateBox, 业务侧 show*() 调用不变)。
 */
abstract class BaseComposeLazyFragment : BaseLazyFragment() {

    protected val loadState = mutableStateOf(LoadState.None)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AutoSize.autoConvertDensity(activity, sizeInDp, isBaseOnWidth)
        if (rootView == null) {
            rootView = ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { TvBoxTheme { Content() } }
            }
        }
        isViewCreated = true
        return rootView!!
    }

    // 原基类走 LoadSir 视图切换, Compose 版改为状态驱动; XML 布局不再使用
    protected override fun showLoading() {
        loadState.value = LoadState.Loading
    }

    protected override fun showEmpty() {
        loadState.value = LoadState.Empty
    }

    protected override fun showSuccess() {
        loadState.value = LoadState.Success
    }

    protected override fun getLayoutResID(): Int = 0

    @Composable
    protected abstract fun Content()
}
