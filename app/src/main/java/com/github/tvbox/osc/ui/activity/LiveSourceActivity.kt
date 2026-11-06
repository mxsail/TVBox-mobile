package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.LiveSource
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog
import com.github.tvbox.osc.util.live.LiveSourceManager
import com.github.tvbox.osc.view.MaterialBarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup
import com.obsez.android.lib.filechooser.ChooserDialog
import java.io.File

/**
 * 直播源管理(Compose): 
 * 1. 顶部常驻「跟随当前订阅源」单选项；
 * 2. 多直播源列表(选中/删除/置顶/重命名/复制链接)；
 * 3. 添加直播源(支持网络链接输入与本地文件导入)。
 */
class LiveSourceActivity : BaseActivity() {

    private data class LiveRow(
        val name: String,
        val url: String,
        val isChecked: Boolean,
        val isTop: Boolean
    )

    private val selectedUrlState = mutableStateOf(LiveSource.FOLLOW_SUBSCRIPTION)
    private val rowsState = mutableStateOf<List<LiveRow>>(emptyList())
    private val subLiveSummaryState = mutableStateOf("")

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { LiveSourceScreen() } }
    }

    override fun init() {
        refresh()
    }

    private fun refresh() {
        val currentSelected = LiveSourceManager.getSelectedSourceUrl()
        selectedUrlState.value = currentSelected
        subLiveSummaryState.value = LiveSourceManager.getSubscriptionLiveSummary()

        val list = LiveSourceManager.getLiveSources()
        val sorted = list.sortedByDescending { it.isTop }
        rowsState.value = sorted.map {
            LiveRow(it.name, it.url, it.url == currentSelected, it.isTop)
        }
    }

    private fun selectSource(url: String) {
        LiveSourceManager.setSelectedSourceUrl(url) {
            ToastUtils.showShort("已切换直播源")
        }
        refresh()
    }

    // ---------------- 添加直播源 ----------------

    private fun showAddDialog() {
        val count = LiveSourceManager.getLiveSources().size + 1
        XPopup.Builder(this)
            .hasBlurBg(MaterialBarHelper.popupBlurEnabled())
            .autoFocusEditText(false)
            .asCustom(
                SubsciptionDialog(
                    this,
                    "直播源 $count",
                    object : SubsciptionDialog.OnSubsciptionListener {
                        override fun onConfirm(name: String, url: String, check: Boolean) {
                            val success = LiveSourceManager.addLiveSource(name, url, check)
                            if (!success) {
                                ToastUtils.showLong("该直播源地址已存在")
                            } else {
                                ToastUtils.showShort("添加成功")
                                refresh()
                            }
                        }

                        override fun chooseLocal(check: Boolean) {
                            if (!XXPermissions.isGranted(this@LiveSourceActivity, Permission.MANAGE_EXTERNAL_STORAGE)) {
                                showPermissionTipPopup(check)
                            } else {
                                pickFile(check)
                            }
                        }
                    }
                )
            ).show()
    }

    private fun showPermissionTipPopup(check: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle("提示")
            .setMessage("这将访问您设备文件的读取权限")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                XXPermissions.with(this)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) pickFile(check)
                            else ToastUtils.showLong("部分权限未正常授予,请授权")
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("读写文件权限被永久拒绝，请手动授权")
                                XXPermissions.startPermissionActivity(this@LiveSourceActivity, permissions)
                            } else {
                                ToastUtils.showShort("获取权限失败")
                                showPermissionTipPopup(check)
                            }
                        }
                    })
            }
            .show()
    }

    private fun pickFile(check: Boolean) {
        ChooserDialog(this)
            .withFilter(false, "txt", "m3u", "m3u8", "json")
            .withStartFile("/storage/emulated/0")
            .withChosenListener { path, _ ->
                val f = File(path)
                val name = f.name
                val success = LiveSourceManager.addLiveSource(name, path, check)
                if (!success) {
                    ToastUtils.showLong("该直播源已存在")
                } else {
                    ToastUtils.showShort("添加本地直播源成功")
                    refresh()
                }
            }
            .build()
            .show()
    }

    // ---------------- 条目菜单操作 ----------------

    private fun showItemOptions(row: LiveRow) {
        val options = arrayOf(
            if (row.isTop) "取消置顶" else "置顶",
            "重命名",
            "复制链接",
            "删除"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(row.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        LiveSourceManager.toggleTop(row.url)
                        refresh()
                    }
                    1 -> showRenameDialog(row)
                    2 -> {
                        ClipboardUtils.copyText(row.url)
                        ToastUtils.showShort("已复制")
                    }
                    3 -> showDeleteConfirm(row)
                }
            }
            .show()
    }

    private fun showRenameDialog(row: LiveRow) {
        XPopup.Builder(this)
            .hasBlurBg(MaterialBarHelper.popupBlurEnabled())
            .asInputConfirm("重命名", null, row.name, "请输入名称") { text ->
                if (!TextUtils.isEmpty(text)) {
                    val trimmed = text.trim()
                    if (trimmed.length > 20) {
                        ToastUtils.showShort("名称过长")
                    } else {
                        LiveSourceManager.updateLiveSourceName(row.url, trimmed)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun showDeleteConfirm(row: LiveRow) {
        if (row.isChecked) {
            ToastUtils.showShort("不能删除当前生效的直播源")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("删除直播源")
            .setMessage("确定删除「${row.name}」吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                LiveSourceManager.deleteLiveSource(row.url)
                refresh()
                ToastUtils.showShort("已删除")
            }
            .show()
    }

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun LiveSourceScreen() {
        val isFollowSelected = selectedUrlState.value == LiveSource.FOLLOW_SUBSCRIPTION || selectedUrlState.value.isEmpty()
        val rows = rowsState.value

        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("直播源", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog() }) {
                            Icon(
                                painterResource(R.drawable.ic_add_24),
                                contentDescription = "添加直播源",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // 1. 顶部常驻单选项：跟随当前订阅源
                item {
                    Text(
                        "默认源",
                        color = colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp, start = 4.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (isFollowSelected) Modifier.border(
                                    1.5.dp, colorScheme.primary, RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFollowSelected) colorScheme.secondaryContainer
                            else colorScheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { selectSource(LiveSource.FOLLOW_SUBSCRIPTION) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "跟随当前订阅源",
                                    color = colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = if (isFollowSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    subLiveSummaryState.value,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            RadioButton(
                                selected = isFollowSelected,
                                onClick = { selectSource(LiveSource.FOLLOW_SUBSCRIPTION) },
                                colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "自定义直播源",
                        color = colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )
                }

                // 2. 自定义直播源列表
                if (rows.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "暂无自定义直播源，可点击右上角添加",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    itemsIndexed(rows) { _, row ->
                        val selected = row.isChecked
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .then(
                                    if (selected) Modifier.border(
                                        1.5.dp, colorScheme.primary, RoundedCornerShape(16.dp)
                                    ) else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) colorScheme.secondaryContainer
                                else colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { selectSource(row.url) }
                                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (row.isTop) {
                                            Text(
                                                "置顶",
                                                color = colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                        }
                                        Text(
                                            row.name,
                                            color = colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        row.url,
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                RadioButton(
                                    selected = selected,
                                    onClick = { selectSource(row.url) },
                                    colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                                )
                                IconButton(onClick = { showItemOptions(row) }) {
                                    Icon(
                                        painterResource(R.drawable.ic_more_vert),
                                        contentDescription = "操作",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
