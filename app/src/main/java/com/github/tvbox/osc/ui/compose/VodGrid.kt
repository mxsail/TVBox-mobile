package com.github.tvbox.osc.ui.compose

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.DefaultConfig
import com.squareup.picasso.Picasso

/**
 * 海报网格(历史/收藏/本地视频等 Compose 页共用): 复刻 item_grid 观感
 * (140dp 卡片/16dp 圆角/左上徽章/底部黑渐变 note+名称)。
 */

data class VodCard(
    val key: String,
    val name: String,
    val pic: String,
    val badge: String,
    val note: String? = null,
)

/** Picasso 海报(复用既有加载链: proxy 替换/占位图) */
@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.img_loading_placeholder)
            }
        },
        update = { iv ->
            if (url.isNotEmpty()) {
                Picasso.get()
                    .load(DefaultConfig.checkReplaceProxy(url))
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(iv)
            } else {
                iv.setImageResource(R.drawable.img_loading_placeholder)
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VodCardItem(
    card: VodCard,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick?.invoke() })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                // clip 必须在 background 之前: 海报是 AndroidView(ImageView), 不裁剪会
                // 以方角盖住圆角背景 → 卡片看起来没圆角、行间视觉上挤在一起
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surfaceContainer)
        ) {
            PosterImage(card.pic, Modifier.fillMaxSize())
            if (card.badge.isNotEmpty()) {
                Text(
                    card.badge,
                    color = colorScheme.onPrimaryContainer,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(4.dp)
                        .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                )
            }
            if (!card.note.isNullOrEmpty()) {
                // 评分: 海报右下角小胶囊
                Text(
                    card.note,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .widthIn(max = 96.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        // 名称移到卡片下方
        Text(
            card.name,
            color = colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/**
 * 海报网格页骨架: 顶栏(标题+右上清空) + 3 列网格 + 空态。
 * cards 由页面自行加载传入; 清空/点击/长按行为由页面注入。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VodGridScreen(
    title: String,
    cards: List<VodCard>,
    onBack: () -> Unit,
    onClear: (() -> Unit)? = null,
    onItemClick: (VodCard) -> Unit,
    onItemLongClick: ((VodCard) -> Unit)? = null
) {
    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                            tint = colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (onClear != null && cards.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                painterResource(R.drawable.ic_clear),
                                contentDescription = "清空",
                                tint = colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        }
    ) { padding ->
        if (cards.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painterResource(R.drawable.ic_inbox_24),
                    contentDescription = null,
                    tint = colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text("暂无内容", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(cards, key = { it.key }) { card ->
                    VodCardItem(
                        card,
                        onClick = { onItemClick(card) },
                        onLongClick = onItemLongClick?.let { cb -> { cb(card) } }
                    )
                }
            }
        }
    }
}
