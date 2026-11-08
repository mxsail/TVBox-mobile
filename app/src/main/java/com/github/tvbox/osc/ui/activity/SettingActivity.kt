package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.MenuRow
import com.github.tvbox.osc.ui.compose.SectionTitle
import com.github.tvbox.osc.ui.compose.SwitchRow
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.BackupDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.PlayerHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File

/**
 * 设置页(Compose): 常规/播放与缓存/数据 三组配置。
 * 选择交互沿用既有 View 版弹窗(SelectDialog/LiveApiDialog/BackupDialog/SyncDialog),
 * 通过 Compose↔View 互操作调用, 避免同轮重写全部弹窗。
 */
class SettingActivity : BaseActivity() {

    private var homeRec = Hawk.get(HawkConfig.HOME_REC, 0)
    private var dnsOpt = Hawk.get(HawkConfig.DOH_URL, 0)

    private val subSourceSummaryState = mutableStateOf("")
    private val liveSourceSummaryState = mutableStateOf("")

    private fun getSubscriptionSummary(): String {
        val subs: List<com.github.tvbox.osc.bean.Subscription>? = Hawk.get(HawkConfig.SUBSCRIPTIONS, null)
        return subs?.find { it.isChecked }?.name ?: "未设置"
    }

    private fun getLiveSourceSummary(): String {
        return com.github.tvbox.osc.util.live.LiveSourceManager.getSelectedSourceName()
    }

    override fun onResume() {
        super.onResume()
        subSourceSummaryState.value = getSubscriptionSummary()
        liveSourceSummaryState.value = getLiveSourceSummary()
    }

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { SettingScreen() } }
    }

    override fun init() {
    }

    // ---------------- 交互(互操作调用既有弹窗) ----------------

    private fun showSelect(
        tip: String,
        options: List<String>,
        defaultPos: Int,
        onPick: (Int) -> Unit
    ) {
        val dialog = SelectDialog<String>(this)
        dialog.setTip(tip)
        dialog.setAdapter(object : SelectDialogInterface<String?> {
            override fun click(value: String?, pos: Int) = onPick(pos)

            override fun getDisplay(name: String?): String = name ?: ""
        }, SelectDialogAdapter.stringDiff, ArrayList(options), defaultPos)
        dialog.show()
    }

    private fun openAppearance() {
        jumpActivity(AppearanceActivity::class.java)
    }

    private fun openBackup() {
        if (XXPermissions.isGranted(this, Permission.MANAGE_EXTERNAL_STORAGE)) {
            BackupDialog(this).show()
        } else {
            XXPermissions.with(this)
                .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                .request(object : OnPermissionCallback {
                    override fun onGranted(permissions: List<String>, all: Boolean) {
                        if (all) BackupDialog(this@SettingActivity).show()
                    }

                    override fun onDenied(permissions: List<String>, never: Boolean) {
                        if (never) {
                            ToastUtils.showLong("获取存储权限失败,请在系统设置中开启")
                            XXPermissions.startPermissionActivity(this@SettingActivity, permissions)
                        } else {
                            ToastUtils.showShort("获取存储权限失败")
                        }
                    }
                })
        }
    }

    private fun confirmClearCache() {
        MaterialAlertDialogBuilder(this)
            .setTitle("提示")
            .setMessage("确定清空吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ -> clearCache() }
            .show()
    }

    private fun clearCache() {
        val cacheDir = File(FileUtils.getCachePath())
        if (!cacheDir.exists()) return
        Thread {
            try {
                FileUtils.cleanDirectory(cacheDir)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
        ToastUtils.showLong("缓存已清空")
    }

    private fun homeRecName(type: Int): String = when (type) {
        0 -> "豆瓣热播"
        1 -> "站点推荐"
        else -> "关闭"
    }

    private fun themeColorLabel(): String {
        return if (ThemeManager.useDynamicColor()) "动态取色"
        else ThemeManager.presetOf(ThemeManager.colorScheme())?.label ?: "动态取色"
    }

    /** 主题级变化(外观页触发)时刷新本页 */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAppearanceChanged(event: AppearanceChangedEvent) {
        if (event.type == AppearanceChangedEvent.TYPE_RECREATE) {
            recreate()
        }
    }

    // 返回键: 常规配置有变更时重载主页(与历史版本行为一致)
    override fun keepBackIntercept(): Boolean = true

    override fun onBackHandled() {
        if (homeRec != Hawk.get(HawkConfig.HOME_REC, 0) || dnsOpt != Hawk.get(
                HawkConfig.DOH_URL, 0
            )
        ) {
            val bundle = Bundle()
            bundle.putBoolean(IntentKey.CACHE_CONFIG_CHANGED, true)
            jumpActivity(MainActivity::class.java, bundle)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        } else {
            finish()
        }
    }

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingScreen() {
        var appearanceValue by remember { mutableStateOf(themeColorLabel()) }
        var homeRecValue by remember { mutableStateOf(homeRecName(Hawk.get(HawkConfig.HOME_REC, 0))) }
        var privateBrowsing by remember { mutableStateOf(Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) }
        var playValue by remember { mutableStateOf(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0))) }
        var videoPurify by remember { mutableStateOf(Hawk.get(HawkConfig.VIDEO_PURIFY, true)) }
        var wifiOnly by remember { mutableStateOf(Hawk.get(HawkConfig.DOWNLOAD_WIFI_ONLY, false)) }
        var speedValue by remember { mutableStateOf(Hawk.get(HawkConfig.VIDEO_SPEED, 2.0f).toString()) }
        var bgPlayValue by remember {
            mutableStateOf(
                listOf("关闭", "开启", "画中画")[Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0)]
            )
        }
        var ijkCodecValue by remember { mutableStateOf(Hawk.get(HawkConfig.IJK_CODEC, "")) }
        var renderValue by remember { mutableStateOf(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, PlayerHelper.RENDER_TEXTURE_VIEW))) }
        var scaleValue by remember { mutableStateOf(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0))) }
        var ijkCache by remember { mutableStateOf(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) }
        var syncValue by remember { mutableStateOf(if (Hawk.get(HawkConfig.SYNC_ENABLED, false)) "已启用" else "未启用") }
        var dnsValue by remember { mutableStateOf(OkGoHelper.dnsHttpsList[Hawk.get(HawkConfig.DOH_URL, 0)]) }
        var historyValue by remember { mutableStateOf(HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0))) }

        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("设置", fontWeight = FontWeight.Bold) },
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
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SectionTitle("常规")
                GroupCard {
                    MenuRow(R.drawable.ic_palette_48, "外观设置", appearanceValue) { openAppearance() }
                    MenuRow(R.drawable.ic_home, "主页内容", homeRecValue) {
                        showSelect("主页内容显示", listOf("豆瓣热播", "站点推荐", "关闭"), Hawk.get(HawkConfig.HOME_REC, 0)) { pos ->
                            Hawk.put(HawkConfig.HOME_REC, pos)
                            homeRecValue = homeRecName(pos)
                        }
                    }
                    SwitchRow(R.drawable.ic_incognito_48, "无痕浏览", privateBrowsing) { newValue ->
                        privateBrowsing = newValue
                        Hawk.put(HawkConfig.PRIVATE_BROWSING, newValue)
                    }
                }

                SectionTitle("播放源管理")
                GroupCard {
                    MenuRow(R.drawable.ic_database_48, "订阅源", subSourceSummaryState.value) {
                        jumpActivity(SubscriptionActivity::class.java)
                    }
                    MenuRow(R.drawable.ic_live_tv_48, "直播源", liveSourceSummaryState.value) {
                        jumpActivity(LiveSourceActivity::class.java)
                    }
                }

                SectionTitle("播放与下载")
                GroupCard {
                    MenuRow(R.drawable.ic_m3_play_arrow, "播放器", playValue) {
                        val players = PlayerHelper.getExistPlayerTypes()
                        val names = players.map { PlayerHelper.getPlayerName(it) }
                        val playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0)
                        var defaultPos = 0
                        players.indices.forEach { if (players[it] == playerType) defaultPos = it }
                        showSelect("请选择默认播放器", names, defaultPos) { pos ->
                            Hawk.put(HawkConfig.PLAY_TYPE, players[pos])
                            playValue = PlayerHelper.getPlayerName(players[pos])
                            PlayerHelper.init()
                        }
                    }
                    SwitchRow(R.drawable.ic_filter_list, "广告过滤", videoPurify) { newValue ->
                        videoPurify = newValue
                        Hawk.put(HawkConfig.VIDEO_PURIFY, newValue)
                    }
                    SwitchRow(R.drawable.ic_download_18, "仅Wi-Fi下载", wifiOnly) { newValue ->
                        wifiOnly = newValue
                        Hawk.put(HawkConfig.DOWNLOAD_WIFI_ONLY, newValue)
                    }
                    MenuRow(R.drawable.ic_speed_48, "长按倍速", speedValue) {
                        val types = listOf("2.0", "3.0", "4.0", "5.0", "6.0", "8.0", "10.0")
                        val defaultPos = types.indexOf(Hawk.get(HawkConfig.VIDEO_SPEED, 2.0f).toString())
                        showSelect("请选择倍速", types, if (defaultPos < 0) 0 else defaultPos) { pos ->
                            Hawk.put(HawkConfig.VIDEO_SPEED, types[pos].toFloat())
                            speedValue = types[pos]
                        }
                    }
                    MenuRow(R.drawable.ic_m3_pip, "后台播放", bgPlayValue) {
                        val types = listOf("关闭", "开启", "画中画")
                        showSelect("后台播放", types, Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0)) { pos ->
                            Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE, pos)
                            bgPlayValue = types[pos]
                        }
                    }
                    MenuRow(R.drawable.ic_memory_48, "IJK解码方式", ijkCodecValue) {
                        val ijkCodes = ApiConfig.get().ijkCodes
                        if (ijkCodes.isNullOrEmpty()) return@MenuRow
                        val ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "")
                        var defaultPos = 0
                        ijkCodes.indices.forEach { if (ijkSel == ijkCodes[it].name) defaultPos = it }
                        val dialog = SelectDialog<IJKCode>(this@SettingActivity)
                        dialog.setTip("请选择IJK解码")
                        dialog.setAdapter(object : SelectDialogInterface<IJKCode?> {
                            override fun click(value: IJKCode?, pos: Int) {
                                value?.selected(true)
                                ijkCodecValue = value?.name ?: ""
                            }

                            override fun getDisplay(code: IJKCode?): String = code?.name ?: ""
                        }, object : DiffUtil.ItemCallback<IJKCode>() {
                            override fun areItemsTheSame(oldItem: IJKCode, newItem: IJKCode): Boolean =
                                oldItem === newItem

                            override fun areContentsTheSame(oldItem: IJKCode, newItem: IJKCode): Boolean =
                                oldItem.name.contentEquals(newItem.name)
                        }, ijkCodes, defaultPos)
                        dialog.show()
                    }
                    MenuRow(R.drawable.ic_material_48, "渲染方式", renderValue) {
                        val renderTypes = PlayerHelper.getRenderTypes()
                        val renderNames = renderTypes.map { PlayerHelper.getRenderName(it) }
                        val currentRender = Hawk.get(HawkConfig.PLAY_RENDER, PlayerHelper.RENDER_TEXTURE_VIEW)
                        var defaultPos = renderTypes.indexOf(currentRender)
                        if (defaultPos < 0) defaultPos = 0
                        showSelect("请选择默认渲染方式", renderNames, defaultPos) { pos ->
                            val selectedType = renderTypes[pos]
                            Hawk.put(HawkConfig.PLAY_RENDER, selectedType)
                            renderValue = PlayerHelper.getRenderName(selectedType)
                            PlayerHelper.init()
                        }
                    }
                    MenuRow(R.drawable.ic_scale_48, "画面缩放", scaleValue) {
                        val scales = (0..5).toList()
                        showSelect(
                            "请选择画面缩放",
                            scales.map { PlayerHelper.getScaleName(it) },
                            Hawk.get(HawkConfig.PLAY_SCALE, 0)
                        ) { pos ->
                            Hawk.put(HawkConfig.PLAY_SCALE, pos)
                            scaleValue = PlayerHelper.getScaleName(pos)
                        }
                    }
                    SwitchRow(R.drawable.ic_cache_48, "IJK缓存", ijkCache) { newValue ->
                        ijkCache = newValue
                        Hawk.put(HawkConfig.IJK_CACHE_PLAY, newValue)
                    }
                }

                SectionTitle("数据")
                GroupCard {
                    MenuRow(R.drawable.ic_backup_48, "数据备份还原", null) { openBackup() }
                    MenuRow(R.drawable.ic_sync_48, "多设备数据同步", syncValue) {
                        val dialog = com.github.tvbox.osc.ui.dialog.SyncDialog(this@SettingActivity)
                        dialog.setOnDismissListener {
                            syncValue = if (Hawk.get(HawkConfig.SYNC_ENABLED, false)) "已启用" else "未启用"
                        }
                        dialog.show()
                    }
                    MenuRow(R.drawable.ic_dns_48, "安全DNS", dnsValue) {
                        val dohUrl = Hawk.get(HawkConfig.DOH_URL, 0)
                        showSelect("请选择安全DNS", OkGoHelper.dnsHttpsList, dohUrl) { pos ->
                            Hawk.put(HawkConfig.DOH_URL, pos)
                            dnsValue = OkGoHelper.dnsHttpsList[pos]
                            OkGoHelper.updateDns()
                            IjkMediaPlayer.toggleDotPort(pos > 0)
                        }
                    }
                    MenuRow(R.drawable.ic_history_48, "历史记录", historyValue) {
                        showSelect(
                            "保留历史记录数量",
                            listOf(0, 1, 2).map { HistoryHelper.getHistoryNumName(it) },
                            Hawk.get(HawkConfig.HISTORY_NUM, 0)
                        ) { pos ->
                            Hawk.put(HawkConfig.HISTORY_NUM, pos)
                            historyValue = HistoryHelper.getHistoryNumName(pos)
                        }
                    }
                    MenuRow(R.drawable.ic_clear, "清空缓存", null) { confirmClearCache() }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
