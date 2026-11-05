package com.github.tvbox.osc.ui.compose

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * 「上次观看」悬浮条 (Compose 版, 替代 XPopup LastViewedDialog):
 * 悬浮在底栏上方, 背景按外观设置的材质套液态玻璃(采样底栏同一 backdrop);
 * 进场自底滑入 + 淡入; 不自动消失, 由宿主在点击他处/滚动/切页时调 onDismiss;
 * 点击本条进详情页。
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

    AnimatedVisibility(
        visible = tip != null && shown != null,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
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
                    .materialGlass(
                        material = material,
                        backdrop = backdrop,
                        shape = CircleShape
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
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "上次看到: " + vod.name + (vod.note?.let { " " + it } ?: ""),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
