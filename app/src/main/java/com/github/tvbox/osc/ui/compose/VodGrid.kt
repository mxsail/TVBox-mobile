package com.github.tvbox.osc.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.DefaultConfig
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

/** 纯 Compose 原生海报(直接由 Compose DrawScope 渲染, 允许 LayerBackdrop 采样真实海报) */
@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val replaced = DefaultConfig.checkReplaceProxy(url)
                    val bmp = Picasso.get().load(replaced).get()
                    if (bmp != null) {
                        bitmap = bmp.asImageBitmap()
                    }
                } catch (_: Throwable) {
                    bitmap = null
                }
            }
        } else {
            bitmap = null
        }
    }
    val currentBmp = bitmap
    if (currentBmp != null) {
        Image(
            bitmap = currentBmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icon_img_placeholder),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 本地文件海报(观看历史墙): 海报已持久缓存到本地文件时直接解码显示(零网络等待),
 * 未缓存时走网络加载并显示占位图(占位图先行, 不阻塞其他元素)。纯 Compose 渲染。
 */
@Composable
fun PosterFileImage(file: File?, url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(file?.path, url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file?.path, url) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = when {
                    file != null && file.exists() -> Picasso.get().load(file).get()
                    url.isNotEmpty() -> Picasso.get().load(DefaultConfig.checkReplaceProxy(url)).get()
                    else -> null
                }
                if (bmp != null) {
                    bitmap = bmp.asImageBitmap()
                }
            } catch (_: Throwable) {
                bitmap = null
            }
        }
    }
    val currentBmp = bitmap
    if (currentBmp != null) {
        Image(
            bitmap = currentBmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icon_img_placeholder),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
    }
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
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surfaceContainer)
        ) {
            PosterImage(card.pic, Modifier.fillMaxSize())
            if (card.badge.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier.padding(6.dp)
                ) {
                    Text(
                        card.badge,
                        color = colorScheme.onSecondaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }
            }
            if (!card.note.isNullOrEmpty()) {
                // 评分/集数: 海报右下角微磨砂渐变胶囊
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .widthIn(max = 100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x99000000), Color(0xCC000000))
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        card.note,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // 名称移到卡片下方
        Text(
            card.name,
            color = colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
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
            TvBoxTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    if (onClear != null && cards.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                painterResource(R.drawable.ic_delete_outline_24),
                                contentDescription = "清空",
                                tint = colorScheme.onSurface
                            )
                        }
                    }
                }
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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
