package com.github.tvbox.osc.ui.activity

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.data.AppDataManager
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.TvBoxTopBar
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.SubscriptionManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.orhanobut.hawk.Hawk
import com.tvbox.sync.OkHttpSyncHttp
import com.tvbox.sync.SyncEngine
import com.tvbox.sync.SyncHttp
import com.tvbox.sync.SyncProtocol
import com.tvbox.sync.SyncTrigger
import com.tvbox.sync.WebDavStorage
import com.tvbox.sync.adapter.AppRoomSyncAdapter
import com.tvbox.sync.model.SyncSnapshot
import okhttp3.Credentials
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 备份与恢复设置页(M3 Expressive Compose 规范):
 * 整合「云端数据同步(WebDAV)」与「本地备份与恢复」，采用双表现力卡片分组与单页内联展开流转。
 */
class BackupRestoreActivity : BaseActivity() {

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class BackupSnapshot(
        val dirName: String,
        val formattedDate: String,
        val path: String
    )

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            TvBoxTheme {
                BackupRestoreScreen()
            }
        }
    }

    override fun init() {}

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BackupRestoreScreen() {
        // --- 云端同步状态 ---
        val syncEnabled = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_ENABLED, false)) }
        val syncUrl = remember { mutableStateOf(migrateBackupDir()) }
        val syncDir = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_DIR, "tvbox")) }
        val syncUser = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_USER, "")) }
        val syncPassword = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_PASSWORD, "")) }
        val syncSpaceId = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_SPACE_ID, SyncProtocol.DEFAULT_SPACE_ID)) }
        val passwordVisible = remember { mutableStateOf(false) }

        val wifiOnly = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_WIFI_ONLY, false)) }
        val scopeConfig = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_SCOPE_CONFIG, true)) }
        val scopeHistory = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_SCOPE_HISTORY, true)) }
        val scopeCollect = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_SCOPE_COLLECT, true)) }
        val scopeProgress = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_SCOPE_PROGRESS, true)) }
        val scopePlayer = remember { mutableStateOf(Hawk.get(HawkConfig.SYNC_SCOPE_PLAYER_SETTINGS, false)) }

        val syncStatusText = remember { mutableStateOf(getSyncStatusDisplay()) }
        val isSyncing = remember { mutableStateOf(false) }
        val isTesting = remember { mutableStateOf(false) }

        // --- 本地备份状态 ---
        val localBackups = remember { mutableStateOf<List<BackupSnapshot>>(emptyList()) }
        val confirmRestoreItem = remember { mutableStateOf<BackupSnapshot?>(null) }
        val confirmDeleteItem = remember { mutableStateOf<BackupSnapshot?>(null) }

        fun loadBackups() {
            localBackups.value = loadLocalBackups()
        }

        LaunchedEffect(Unit) {
            loadBackups()
        }

        // 保存同步参数
        fun saveSync() {
            Hawk.put(HawkConfig.SYNC_ENABLED, syncEnabled.value)
            Hawk.put(HawkConfig.SYNC_URL, syncUrl.value.trim())
            Hawk.put(HawkConfig.SYNC_DIR, syncDir.value.trim())
            Hawk.put(HawkConfig.SYNC_USER, syncUser.value.trim())
            Hawk.put(HawkConfig.SYNC_PASSWORD, syncPassword.value.trim())
            Hawk.put(HawkConfig.SYNC_SPACE_ID, syncSpaceId.value.trim().ifEmpty { SyncProtocol.DEFAULT_SPACE_ID })
            Hawk.put(HawkConfig.SYNC_WIFI_ONLY, wifiOnly.value)
            Hawk.put(HawkConfig.SYNC_SCOPE_CONFIG, scopeConfig.value)
            Hawk.put(HawkConfig.SYNC_SCOPE_HISTORY, scopeHistory.value)
            Hawk.put(HawkConfig.SYNC_SCOPE_COLLECT, scopeCollect.value)
            Hawk.put(HawkConfig.SYNC_SCOPE_PROGRESS, scopeProgress.value)
            Hawk.put(HawkConfig.SYNC_SCOPE_PLAYER_SETTINGS, scopePlayer.value)

            if (syncEnabled.value && syncUrl.value.isNotBlank()) {
                val space = syncSpaceId.value.trim().ifEmpty { SyncProtocol.DEFAULT_SPACE_ID }
                AppRoomSyncAdapter.exportDirty(SyncTrigger.get().opLog, space)
                SyncTrigger.get().triggerDebouncedSync()
            }
        }

        // 测试 WebDAV 连接
        fun testConnection() {
            val url = SyncProtocol.buildSyncUrl(syncUrl.value.trim(), syncDir.value.trim())
            val user = syncUser.value.trim()
            val pass = syncPassword.value.trim()
            val spaceId = syncSpaceId.value.trim().ifEmpty { SyncProtocol.DEFAULT_SPACE_ID }

            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                syncStatusText.value = "WebDAV 地址无效，须以 http:// 或 https:// 开头"
                return
            }

            isTesting.value = true
            syncStatusText.value = "正在连接 WebDAV 服务器..."

            bgExecutor.submit {
                try {
                    val auth = if (user.isNotEmpty() || pass.isNotEmpty()) Credentials.basic(user, pass) else ""
                    val http: SyncHttp = OkHttpSyncHttp(OkGoHelper.getDefaultClient())
                    val storage = WebDavStorage(http, url, spaceId, auth)
                    val ok = storage.ensure()
                    mainHandler.post {
                        isTesting.value = false
                        if (ok) {
                            syncStatusText.value = "连接成功！空间就绪可用"
                            ToastUtils.showShort("WebDAV 连接测试成功")
                        } else {
                            syncStatusText.value = "连接测试失败：无法创建或访问空间"
                            ToastUtils.showShort("WebDAV 连接测试失败")
                        }
                    }
                } catch (e: Throwable) {
                    mainHandler.post {
                        isTesting.value = false
                        syncStatusText.value = "连接失败: ${e.message}"
                        ToastUtils.showShort("连接错误: ${e.message}")
                    }
                }
            }
        }

        // 立即执行同步
        fun syncNow() {
            saveSync()
            if (!SyncTrigger.get().isSyncAllowed) {
                syncStatusText.value = "当前网络环境或配置不允许同步 (请检查开关或 Wi-Fi 限制)"
                ToastUtils.showShort("同步受阻，请检查 Wi-Fi 设置")
                return
            }

            isSyncing.value = true
            syncStatusText.value = "正在双向同步中..."

            SyncTrigger.get().performSync(SyncEngine.SyncMode.BIDIRECTIONAL, object : SyncEngine.SyncCallback {
                override fun onApplied(snapshot: SyncSnapshot?) {
                    mainHandler.post {
                        isSyncing.value = false
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        syncStatusText.value = "同步完成 (${sdf.format(Date())})"
                        ToastUtils.showShort("数据同步完成")
                    }
                }

                override fun onError(message: String?, error: Throwable?) {
                    mainHandler.post {
                        isSyncing.value = false
                        syncStatusText.value = "同步失败: $message"
                        ToastUtils.showLong("同步失败: $message")
                    }
                }
            })
        }

        // 本地备份请求权限与执行
        fun requestAndBackup() {
            if (XXPermissions.isGranted(this@BackupRestoreActivity, Permission.MANAGE_EXTERNAL_STORAGE)) {
                doLocalBackup { loadBackups() }
            } else {
                XXPermissions.with(this@BackupRestoreActivity)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) doLocalBackup { loadBackups() }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("请在系统设置中允许管理所有文件权限")
                                XXPermissions.startPermissionActivity(this@BackupRestoreActivity, permissions)
                            } else {
                                ToastUtils.showShort("获取存储权限失败，无法执行备份")
                            }
                        }
                    })
            }
        }

        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TvBoxTopBar(
                    title = "备份与恢复",
                    onBack = { finish() }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ==================== 1. 云端数据同步卡片组 ====================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_sync_48),
                                    contentDescription = null,
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "云端数据同步",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    syncStatusText.value,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Switch(
                                checked = syncEnabled.value,
                                onCheckedChange = { checked ->
                                    syncEnabled.value = checked
                                    saveSync()
                                }
                            )
                        }

                        // 操作按钮（立即同步与测试连接）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { syncNow() },
                                enabled = syncEnabled.value && !isSyncing.value,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isSyncing.value) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("立即同步", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { testConnection() },
                                enabled = syncEnabled.value && !isTesting.value,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isTesting.value) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("测试连接", fontSize = 13.sp)
                            }
                        }

                        // 展开的 WebDAV 配置表单
                        AnimatedVisibility(
                            visible = syncEnabled.value,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(14.dp))

                                Text(
                                    "WebDAV 服务器配置",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = syncUrl.value,
                                    onValueChange = { syncUrl.value = it; saveSync() },
                                    label = { Text("服务器地址 (如 https://dav.example.com/)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = syncDir.value,
                                        onValueChange = { syncDir.value = it; saveSync() },
                                        label = { Text("备份目录") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    OutlinedTextField(
                                        value = syncSpaceId.value,
                                        onValueChange = { syncSpaceId.value = it; saveSync() },
                                        label = { Text("空间标识 (Space ID)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = syncUser.value,
                                        onValueChange = { syncUser.value = it; saveSync() },
                                        label = { Text("账号") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    OutlinedTextField(
                                        value = syncPassword.value,
                                        onValueChange = { syncPassword.value = it; saveSync() },
                                        label = { Text("密码") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
                                                Icon(
                                                    painterResource(if (passwordVisible.value) R.drawable.ic_unlock else R.drawable.ic_lock),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("仅在 Wi-Fi 网络下同步", fontSize = 14.sp, color = colorScheme.onSurface)
                                        Text("避免移动蜂窝网络下产生意外数据流量", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = wifiOnly.value,
                                        onCheckedChange = { wifiOnly.value = it; saveSync() }
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "同步内容范围",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = scopeConfig.value,
                                        onClick = { scopeConfig.value = !scopeConfig.value; saveSync() },
                                        label = { Text("订阅配置", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    FilterChip(
                                        selected = scopeCollect.value,
                                        onClick = { scopeCollect.value = !scopeCollect.value; saveSync() },
                                        label = { Text("影视收藏", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    FilterChip(
                                        selected = scopeProgress.value,
                                        onClick = { scopeProgress.value = !scopeProgress.value; saveSync() },
                                        label = { Text("播放进度", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    FilterChip(
                                        selected = scopeHistory.value,
                                        onClick = { scopeHistory.value = !scopeHistory.value; saveSync() },
                                        label = { Text("历史记录", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    FilterChip(
                                        selected = scopePlayer.value,
                                        onClick = { scopePlayer.value = !scopePlayer.value; saveSync() },
                                        label = { Text("播放器设置", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ==================== 2. 本地备份与恢复卡片组 ====================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_backup_48),
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "本地备份与恢复",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    "备份数据库与配置到本地存储目录",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { requestAndBackup() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_add_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("创建本地备份", fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(14.dp))

                        Text(
                            "备份记录 (${localBackups.value.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        if (localBackups.value.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "暂无本地备份快照",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                localBackups.value.forEach { snapshot ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_database_48),
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    snapshot.formattedDate,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colorScheme.onSurface
                                                )
                                                Text(
                                                    snapshot.dirName,
                                                    fontSize = 11.sp,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = { confirmRestoreItem.value = snapshot },
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("恢复", fontSize = 12.sp)
                                            }
                                            IconButton(onClick = { confirmDeleteItem.value = snapshot }) {
                                                Icon(
                                                    painterResource(R.drawable.ic_delete_outline_24),
                                                    contentDescription = "删除",
                                                    tint = colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // ==================== 恢复备份确认对话框 ====================
        confirmRestoreItem.value?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { confirmRestoreItem.value = null },
                title = { Text("确认恢复此备份？") },
                text = {
                    Text(
                        "恢复快照「${snapshot.formattedDate}」将完全覆盖当前本地数据库与应用配置，并自动重启应用。\n\n此操作不可撤销，确定恢复吗？"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val item = confirmRestoreItem.value
                            confirmRestoreItem.value = null
                            if (item != null) doRestore(item.dirName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                    ) {
                        Text("确定恢复并重启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRestoreItem.value = null }) {
                        Text("取消")
                    }
                }
            )
        }

        // ==================== 删除备份确认对话框 ====================
        confirmDeleteItem.value?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { confirmDeleteItem.value = null },
                title = { Text("删除本地备份") },
                text = { Text("确定永久删除本地备份快照「${snapshot.formattedDate}」吗？") },
                confirmButton = {
                    Button(
                        onClick = {
                            val item = confirmDeleteItem.value
                            confirmDeleteItem.value = null
                            if (item != null) {
                                doDelete(item.path)
                                loadBackups()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteItem.value = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    // --- 业务辅助方法 ---

    private fun getSyncStatusDisplay(): String {
        val lastSync = SyncTrigger.get().store.lastSyncTs
        return if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            "上次同步于: ${sdf.format(Date(lastSync))}"
        } else {
            "尚未进行过同步"
        }
    }

    private fun migrateBackupDir(): String {
        var url = Hawk.get(HawkConfig.SYNC_URL, "")
        if (Hawk.contains(HawkConfig.SYNC_DIR)) return url
        val matcher = java.util.regex.Pattern.compile("/tvbox/?$").matcher(url)
        val dir = if (matcher.find()) {
            url = url.substring(0, matcher.start())
            Hawk.put(HawkConfig.SYNC_URL, url)
            "tvbox"
        } else if (TextUtils.isEmpty(url)) {
            "tvbox"
        } else {
            ""
        }
        Hawk.put(HawkConfig.SYNC_DIR, dir)
        return url
    }

    private fun loadLocalBackups(): List<BackupSnapshot> {
        val result = mutableListOf<BackupSnapshot>()
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val dir = File("$root/tvbox_backup/")
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: return emptyList()
                files.filter { it.isDirectory }
                    .sortedByDescending { it.name }
                    .forEach { f ->
                        val formatted = try {
                            val parser = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
                            val date = parser.parse(f.name)
                            if (date != null) {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                            } else {
                                f.name
                            }
                        } catch (_: Throwable) {
                            f.name
                        }
                        result.add(BackupSnapshot(f.name, formatted, f.absolutePath))
                    }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return result
    }

    private fun doLocalBackup(onSuccess: () -> Unit) {
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val dir = File("$root/tvbox_backup/")
            if (!dir.exists()) dir.mkdirs()

            val now = Date()
            val f = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
            val backup = File(dir, f.format(now))
            backup.mkdirs()

            val db = File(backup, "sqlite")
            if (AppDataManager.backup(db)) {
                var sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE)
                val jsonObject = JSONObject()
                for (key in sharedPreferences.all.keys) {
                    if (key != null && key.startsWith("sync_")) continue
                    jsonObject.put(key, sharedPreferences.getString(key, ""))
                }
                sharedPreferences = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE)
                for (key in sharedPreferences.all.keys) {
                    jsonObject.put(key, sharedPreferences.getString(key, ""))
                }
                if (!FileUtils.writeSimple(jsonObject.toString().toByteArray(Charsets.UTF_8), File(backup, "hawk"))) {
                    backup.delete()
                    ToastUtils.showShort("备份 Hawk 配置失败")
                } else {
                    ToastUtils.showShort("备份创建成功！")
                    onSuccess()
                }
            } else {
                backup.delete()
                ToastUtils.showShort("数据库备份失败")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            ToastUtils.showShort("备份失败: ${e.message}")
        }
    }

    private fun doRestore(dirName: String) {
        try {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val backup = File("$root/tvbox_backup/$dirName")
            if (backup.exists()) {
                val db = File(backup, "sqlite")
                if (AppDataManager.restore(db)) {
                    val data = FileUtils.readSimple(File(backup, "hawk"))
                    if (data != null) {
                        val hawkJson = String(data, Charsets.UTF_8)
                        val jsonObject = JSONObject(hawkJson)
                        val it = jsonObject.keys()
                        val sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE)
                        while (it.hasNext()) {
                            val key = it.next()
                            val value = jsonObject.getString(key)
                            if (key == "cipher_key") {
                                App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE)
                                    .edit().putString(key, value).commit()
                            } else {
                                sharedPreferences.edit().putString(key, value).commit()
                            }
                        }
                        SubscriptionManager.invalidateMainStore()
                        ToastUtils.showLong("恢复成功，即将重启应用...")
                        mainHandler.postDelayed({ AppUtils.relaunchApp(true) }, 1500)
                    } else {
                        ToastUtils.showShort("Hawk 配置恢复失败")
                    }
                } else {
                    ToastUtils.showShort("数据库恢复失败")
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            ToastUtils.showShort("恢复失败: ${e.message}")
        }
    }

    private fun doDelete(path: String) {
        try {
            val backup = File(path)
            FileUtils.recursiveDelete(backup)
            ToastUtils.showShort("已删除备份快照")
        } catch (e: Throwable) {
            e.printStackTrace()
            ToastUtils.showShort("删除失败")
        }
    }
}
