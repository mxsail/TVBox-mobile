package com.github.tvbox.osc.ui.compose

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.VodInfo
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * 「上次观看」悬浮提示 (Material Design 3 Expressive 浮动 Snackbar):
 * 悬浮在底栏上方，采用无边框轻量微胶囊容器 (2dp 浅浮阴影)；
 * 左侧展示 [ 历史图标 片名与进度 ]，右侧带 [ 继续播放 ] 高亮操作胶囊；
 * 保持长显，当用户滑动页面或点击其他页面元素时自动轻柔淡出；
 * 点击整条或行动按钮均直接进入详情/播放页。
 */
@Composable
fun LastViewedTip(
    tip: VodInfo?,
    material: String,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onOpen: (VodInfo) -> Unit,
    onBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    // 展示期间冻结文案, 避免退场动画期间 tip 先被清空导致内容闪没
    var shown by remember { mutableStateOf<VodInfo?>(null) }
    if (tip != null) shown = tip

    LaunchedEffect(tip) {
        if (tip == null) onBounds(null)
    }

    val colorScheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = tip != null && shown != null,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            initialOffsetY = { it / 2 }
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(180)
        ) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        val vod = shown ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .height(36.dp)
                    .materialGlass(
                        material = material,
                        backdrop = backdrop,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        onBounds(
                            Rect(
                                b.left.roundToInt(),
                                b.top.roundToInt(),
                                b.right.roundToInt(),
                                b.bottom.roundToInt()
                            )
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpen(vod) }
                    .padding(start = 12.dp, end = 5.dp)
            ) {
                // 左侧历史图标
                Icon(
                    painter = painterResource(R.drawable.ic_history_18),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )

                // 片名与进度文案
                Text(
                    text = vod.name + (vod.note?.let { " $it" } ?: ""),
                    color = colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 6.dp, end = 8.dp)
                )

                // 右侧「继续播放」高亮行动按钮
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    modifier = Modifier
                        .height(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpen(vod) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "继续播放",
                            color = colorScheme.onPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
