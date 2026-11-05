package com.github.tvbox.osc.ui.compose

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R

/**
 * LoadSir 三态的 Compose 等价物: 复刻 loadsir_loading(M3 LoadingIndicator 居中)/
 * loadsir_empty(圆形底图标 + "暂无数据") 的观感, 业务侧 show*() 调用不变。
 */
enum class LoadState { None, Loading, Empty, Success }

/** Compose 侧滚动→底栏隐藏联动分发(等价 View 侧 BarAutoHide.attach 的宿主判断) */
object BarAutoHideHost {
    fun onMainScrollDelta(activity: Activity?, dy: Int) {
        (activity as? com.github.tvbox.osc.view.BarAutoHide.ScrollBarHost)?.onMainScrollDelta(dy)
    }
}

@Composable
fun LoadStateBox(
    state: LoadState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (state) {
        LoadState.None, LoadState.Success -> Box(modifier) { content() }
        LoadState.Loading -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        LoadState.Empty -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_inbox_24),
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    "暂无数据",
                    fontSize = 16.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
