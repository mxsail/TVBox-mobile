package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.os.Handler
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.SPUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VideoInfo
import com.github.tvbox.osc.constant.CacheConst
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.util.Utils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.stream.Collectors

/**
 * 本地视频列表(Compose): 缩略图网格 + 选择模式(长按进入/全选/取消/批量删除)。
 */
class VideoListActivity : BaseActivity() {

    private var mBucketDisplayName = ""
    private val mVideos = mutableListOf<VideoInfo>()

    /** UI 强刷版本号(VideoInfo 为可变 bean) */
    private var uiVersion = mutableIntStateOf(0)
    private var selectMode = mutableStateOf(false)
    private var mSelectedCount = 0

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { VideoListScreen() } }
    }

    override fun init() {
        mBucketDisplayName = intent.extras?.getString("bucketDisplayName") ?: ""
        groupVideos()
    }

    override fun refresh(event: RefreshEvent) {
        Handler().postDelayed({ groupVideos() }, 1000)
    }

    override fun onResume() {
        super.onResume()
        groupVideos()
    }

    /** 根据文件夹名字筛选视频 */
    private fun groupVideos() {
        val videoList = Utils.getVideoList()
        val collect = videoList.stream()
            .filter { videoInfo: VideoInfo -> videoInfo.bucketDisplayName == mBucketDisplayName }
            .collect(Collectors.toList())
        mVideos.clear()
        mVideos.addAll(collect)
        uiVersion.value++
    }

    private fun checkedCount(): Int = mVideos.count { it.isChecked }

    private fun toggleSelectMode(open: Boolean) {
        selectMode.value = open
        if (!open) {
            mVideos.forEach { it.isChecked = false }
        }
        mSelectedCount = checkedCount()
        uiVersion.value++
    }

    private fun cancelAll() {
        mVideos.forEach { it.isChecked = false }
        mSelectedCount = 0
        uiVersion.value++
    }

    private fun deleteChecked() {
        showLoadingDialog()
        lifecycleScope.launch(Dispatchers.IO) {
            val deleteList: MutableList<VideoInfo> = ArrayList()
            for (item in mVideos) {
                if (item.isChecked) {
                    deleteList.add(item)
                    if (FileUtils.delete(item.path)) {
                        // 删除缓存的影片时长、进度
                        SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP).remove(item.path)
                        SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).remove(item.path)
                        // 文件增删需要通知系统扫描,否则删除文件后还能查出来
                        FileUtils.notifySystemToScan(FileUtils.getDirName(item.path))
                    }
                }
            }
            mVideos.removeAll(deleteList.toSet())
            withContext(Dispatchers.Main) {
                dismissLoadingDialog()
                toggleSelectMode(false)
            }
        }
    }

    // 选择模式需要先消化返回
    override fun keepBackIntercept(): Boolean = true

    override fun onBackHandled() {
        if (selectMode.value) {
            if (mSelectedCount > 0) {
                cancelAll()
            } else {
                toggleSelectMode(false)
            }
        } else {
            finish()
        }
    }

    // ---------------- Compose 界面 ----------------

    @OptIn(
        androidx.compose.material3.ExperimentalMaterial3Api::class,
        androidx.compose.foundation.ExperimentalFoundationApi::class
    )
    @Composable
    private fun VideoListScreen() {
        // 读取强刷版本(仅建立订阅, 不直接渲染数值)
        val version = uiVersion.value
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(mBucketDisplayName, fontSize = 16.sp, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
                )
            },
            bottomBar = {
                if (selectMode.value) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "全选",
                            color = colorScheme.primary,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable {
                                mVideos.forEach { it.isChecked = true }
                                mSelectedCount = checkedCount()
                                uiVersion.value++
                            }
                        )
                        Text(
                            "取消",
                            color = colorScheme.onSurface,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { cancelAll() }
                        )
                        Text(
                            "删除(${mSelectedCount})",
                            color = if (mSelectedCount > 0) colorScheme.primary else colorScheme.outline,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable(enabled = mSelectedCount > 0) {
                                MaterialAlertDialogBuilder(this@VideoListActivity)
                                    .setTitle("提示")
                                    .setMessage("确定删除所选视频吗？")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("确定") { _, _ -> deleteChecked() }
                                    .show()
                            }
                        )
                    }
                }
            }
        ) { padding ->
            if (mVideos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("文件夹为空", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    itemsIndexed(mVideos, key = { _, item -> item.path }) { _, item ->
                        LocalVideoItem(
                            item = item,
                            selectMode = selectMode.value,
                            version = version,
                            onClick = {
                                if (selectMode.value) {
                                    item.isChecked = !item.isChecked
                                    mSelectedCount = checkedCount()
                                    uiVersion.value++
                                } else {
                                    val bundle = Bundle()
                                    bundle.putString("videoList", GsonUtils.toJson(mVideos))
                                    bundle.putInt("position", mVideos.indexOf(item))
                                    jumpActivity(LocalPlayActivity::class.java, bundle)
                                }
                            },
                            onLongClick = {
                                if (!selectMode.value) {
                                    toggleSelectMode(true)
                                    item.isChecked = true
                                    mSelectedCount = checkedCount()
                                    uiVersion.value++
                                }
                            },
                            onCheckedChange = { checked ->
                                item.isChecked = checked
                                mSelectedCount = checkedCount()
                                uiVersion.value++
                            }
                        )
                    }
                }
            }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun LocalVideoItem(
        item: VideoInfo,
        selectMode: Boolean,
        version: Int,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
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
                            .load(item.path)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .placeholder(R.drawable.iv_video)
                            .centerCrop()
                            .into(iv)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // 时长(缺失时从缓存取, 逻辑同旧适配器)
                val cacheDuration = SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP)
                    .getLong(item.path, -1)
                val duration = if (item.duration > 0) item.duration else cacheDuration
                if (duration > 0) {
                    Text(
                        Utils.stringForTime(duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (selectMode) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { onCheckedChange(it) },
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
            // 观看进度条(有缓存进度时显示)
            val progress = SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).getLong(item.path, -1)
            if (progress >= 0) {
                val dur = SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP).getLong(item.path, 0)
                if (dur > 0 && progress in 1 until dur) {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() / dur },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .height(3.dp)
                    )
                }
            }
            Text(
                item.displayName,
                color = colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                ConvertUtils.byte2FitMemorySize(item.size) + " · " + item.resolution,
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
