package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.LiveSource
import com.github.tvbox.osc.ui.compose.M3ActionsCenterDialog
import com.github.tvbox.osc.ui.compose.M3ConfirmDialog
import com.github.tvbox.osc.ui.compose.M3InputDialog
import com.github.tvbox.osc.ui.compose.M3ItemAction
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.TvBoxTopBar
import com.github.tvbox.osc.util.live.LiveSourceManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
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
    private val showAddDialogState = mutableStateOf(false)

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
        showAddDialogState.value = true
    }

    @Composable
    private fun AddLiveSourceDialog(
        onDismiss: () -> Unit,
        onConfirm: (name: String, url: String, check: Boolean) -> Unit,
        onChooseLocal: (check: Boolean) -> Unit
    ) {
        val count = remember { LiveSourceManager.getLiveSources().size + 1 }
        var name by remember { mutableStateOf("直播源 $count") }
        var url by remember { mutableStateOf("") }
        var check by remember { mutableStateOf(true) }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // 顶栏：标题 + 本地导入入口
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "添加直播源",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Surface(
                            onClick = {
                                onDismiss()
                                onChooseLocal(check)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_file_24),
                                    contentDescription = "本地导入",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "本地导入",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // 输入框 1：直播源名称
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("直播源名称") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // 输入框 2：直播源地址
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("直播源地址") },
                        placeholder = { Text("https://... 或 http://...", color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            if (url.isNotEmpty()) {
                                IconButton(onClick = { url = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close_24),
                                        contentDescription = "清空",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))

                    // 设为当前直播源开关
                    Surface(
                        onClick = { check = !check },
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "设为当前直播源",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "添加完成后立即生效并加载频道",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = check,
                                onCheckedChange = { check = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colorScheme.onPrimary,
                                    checkedTrackColor = colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    // 底部操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("取消", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val trimmedName = name.trim()
                                if (trimmedName.isEmpty()) {
                                    ToastUtils.showShort("请输入名称")
                                    return@Button
                                }
                                val trimmedUrl = url.trim()
                                if (trimmedUrl.isEmpty()) {
                                    ToastUtils.showShort("请输入直播源地址")
                                    return@Button
                                }
                                onConfirm(trimmedName, trimmedUrl, check)
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text("添 加", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    private fun requestStoragePermission(check: Boolean, onDeniedRetry: () -> Unit) {
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
                        onDeniedRetry()
                    }
                }
            })
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

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun LiveSourceScreen() {
        val isFollowSelected = selectedUrlState.value == LiveSource.FOLLOW_SUBSCRIPTION || selectedUrlState.value.isEmpty()
        val rows = rowsState.value
        val isFabVisible = remember { mutableStateOf(true) }
        var itemForActions by remember { mutableStateOf<LiveRow?>(null) }
        var itemForRename by remember { mutableStateOf<LiveRow?>(null) }
        var itemForDelete by remember { mutableStateOf<LiveRow?>(null) }
        val showPermissionTipState = remember { mutableStateOf<Boolean?>(null) }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < -12f) {
                        isFabVisible.value = false
                    } else if (available.y > 12f) {
                        isFabVisible.value = true
                    }
                    return Offset.Zero
                }
            }
        }

        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TvBoxTopBar(
                    title = "直播源",
                    onBack = { finish() }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isFabVisible.value,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { showAddDialog() },
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_add_24),
                            contentDescription = "添加直播源",
                            tint = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .nestedScroll(nestedScrollConnection)
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 顶部常驻单选项：跟随当前订阅源
                    item {
                        Text(
                            "默认源",
                            color = colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp, start = 4.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isFollowSelected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow,
                            border = if (isFollowSelected) BorderStroke(1.5.dp, colorScheme.primary) else null,
                            onClick = { selectSource(LiveSource.FOLLOW_SUBSCRIPTION) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "跟随当前订阅源",
                                        color = colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = if (isFollowSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        subLiveSummaryState.value,
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = isFollowSelected,
                                        onClick = { selectSource(LiveSource.FOLLOW_SUBSCRIPTION) },
                                        colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
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
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "暂无自定义直播源",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { showAddDialog() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_add_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("添加直播源", fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(rows) { _, row ->
                            val selected = row.isChecked
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow,
                                border = if (selected) BorderStroke(1.5.dp, colorScheme.primary) else null,
                                onClick = { selectSource(row.url) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (row.isTop) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = colorScheme.primaryContainer,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                ) {
                                                    Text(
                                                        "置顶",
                                                        color = colorScheme.onPrimaryContainer,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                row.name,
                                                color = colorScheme.onSurface,
                                                fontSize = 15.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            row.url,
                                            color = colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = selected,
                                                onClick = { selectSource(row.url) },
                                                colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                                            )
                                            IconButton(onClick = { itemForActions = row }) {
                                                Icon(
                                                    painterResource(R.drawable.ic_more_vert),
                                                    contentDescription = "操作",
                                                    tint = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }

        // 更多操作卡片弹窗
        itemForActions?.let { row ->
            val actions = listOf(
                M3ItemAction(
                    title = if (row.isTop) "取消置顶" else "置顶",
                    iconRes = R.drawable.ic_m3_pin_24,
                    onClick = {
                        LiveSourceManager.toggleTop(row.url)
                        refresh()
                    }
                ),
                M3ItemAction(
                    title = "重命名",
                    iconRes = R.drawable.ic_m3_edit_24,
                    onClick = {
                        itemForRename = row
                    }
                ),
                M3ItemAction(
                    title = "复制链接",
                    iconRes = R.drawable.ic_m3_copy_24,
                    onClick = {
                        ClipboardUtils.copyText(row.url)
                        ToastUtils.showShort("已复制")
                    }
                ),
                M3ItemAction(
                    title = "删除",
                    iconRes = R.drawable.ic_delete_outline_24,
                    isDestructive = true,
                    onClick = {
                        if (row.isChecked) {
                            ToastUtils.showShort("不能删除当前生效的直播源")
                        } else {
                            itemForDelete = row
                        }
                    }
                )
            )

            M3ActionsCenterDialog(
                visible = true,
                title = row.name,
                subtitle = row.url,
                actions = actions,
                onDismissRequest = { itemForActions = null }
            )
        }

        // 重命名弹窗
        itemForRename?.let { row ->
            M3InputDialog(
                visible = true,
                title = "重命名",
                initialValue = row.name,
                label = "直播源名称",
                maxLength = 20,
                confirmText = "保存",
                cancelText = "取消",
                onDismissRequest = { itemForRename = null },
                onConfirm = { newName ->
                    LiveSourceManager.updateLiveSourceName(row.url, newName)
                    refresh()
                    ToastUtils.showShort("已修改")
                }
            )
        }

        // 删除确认弹窗
        itemForDelete?.let { row ->
            M3ConfirmDialog(
                visible = true,
                title = "删除直播源",
                message = "确定删除「${row.name}」吗？",
                confirmText = "删除",
                cancelText = "取消",
                isDestructive = true,
                iconRes = R.drawable.ic_delete_outline_24,
                onDismissRequest = { itemForDelete = null },
                onConfirm = {
                    LiveSourceManager.deleteLiveSource(row.url)
                    refresh()
                    ToastUtils.showShort("已删除")
                }
            )
        }

        // 存储权限提示弹窗
        showPermissionTipState.value?.let { check ->
            M3ConfirmDialog(
                visible = true,
                title = "存储权限提示",
                message = "导入本地直播源需要读取设备存储权限，是否前往授权？",
                confirmText = "去授权",
                cancelText = "取消",
                iconRes = R.drawable.ic_file_24,
                onDismissRequest = { showPermissionTipState.value = null },
                onConfirm = {
                    requestStoragePermission(check) {
                        showPermissionTipState.value = check
                    }
                }
            )
        }

        if (showAddDialogState.value) {
            AddLiveSourceDialog(
                onDismiss = { showAddDialogState.value = false },
                onConfirm = { name, url, check ->
                    val success = LiveSourceManager.addLiveSource(name, url, check)
                    if (!success) {
                        ToastUtils.showLong("该直播源地址已存在")
                    } else {
                        ToastUtils.showShort("添加成功")
                        showAddDialogState.value = false
                        refresh()
                    }
                },
                onChooseLocal = { check ->
                    showAddDialogState.value = false
                    if (!XXPermissions.isGranted(this@LiveSourceActivity, Permission.MANAGE_EXTERNAL_STORAGE)) {
                        showPermissionTipState.value = check
                    } else {
                        pickFile(check)
                    }
                }
            )
        }
    }
}
