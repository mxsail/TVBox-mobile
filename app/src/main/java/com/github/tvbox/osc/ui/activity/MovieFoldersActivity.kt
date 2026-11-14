package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VideoInfo
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.util.Utils

/**
 * 本地视频文件夹(Compose): 按文件夹分组的入口列表, 点击进该文件夹视频网格。
 */
class MovieFoldersActivity : BaseActivity() {

    private data class FolderRow(val name: String, val count: Int, val coverPath: String)
    private val folders = mutableStateOf<List<FolderRow>>(emptyList())

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { FoldersScreen() } }
    }

    override fun init() {
    }

    override fun onResume() {
        super.onResume()
        groupVideos()
    }

    /** 按文件夹名字分组视频 */
    private fun groupVideos() {
        val videoList = Utils.getVideoList()
        val rows = videoList.stream()
            .collect(java.util.stream.Collectors.groupingBy { obj: VideoInfo -> obj.bucketDisplayName })
            .map { (key, value) ->
                FolderRow(
                    name = key ?: "未知文件夹",
                    count = value?.size ?: 0,
                    coverPath = value?.firstOrNull()?.path ?: ""
                )
            }
            .toList()
        folders.value = rows
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun FoldersScreen() {
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("本地视频", fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
                )
            }
        ) { padding ->
            if (folders.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Text("未扫描到本地视频", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    items(folders.value) { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {
                                    val bundle = Bundle()
                                    bundle.putString("bucketDisplayName", folder.name)
                                    jumpActivity(VideoListActivity::class.java, bundle)
                                },
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                            setImageResource(R.drawable.iv_video)
                                        }
                                    },
                                    update = { iv ->
                                        Glide.with(iv)
                                            .load(folder.coverPath)
                                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                            .placeholder(R.drawable.iv_video)
                                            .centerCrop()
                                            .into(iv)
                                    },
                                    modifier = Modifier
                                        .width(88.dp)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        folder.name,
                                        color = colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        folder.count.toString() + " 个视频",
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
