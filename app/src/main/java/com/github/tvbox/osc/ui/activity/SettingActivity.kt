package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.event.AppearanceChangedEvent
import com.github.tvbox.osc.ui.compose.DownloadSettingsSheet
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.M3ConfirmDialog
import com.github.tvbox.osc.ui.compose.M3SingleChoiceBottomSheet
import com.github.tvbox.osc.ui.compose.MenuRow
import com.github.tvbox.osc.ui.compose.SectionTitle
import com.github.tvbox.osc.ui.compose.SwitchRow
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.TvBoxTopBar
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.PlayerHelper
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

/**
 * 设置页(Compose): 常规/播放与缓存/数据 三组配置。
 * 配置项选择弹窗与确认弹窗已全面升级为 Material Design 3 Expressive 风格的底部抽屉与对话框。
 */
class SettingActivity : BaseActivity() {

    private var homeRec = Hawk.get(HawkConfig.HOME_REC, 0)
    private var dnsOpt = Hawk.get(HawkConfig.DOH_URL, 0)

    private val subSourceSummaryState = mutableStateOf("")
    private val liveSourceSummaryState = mutableStateOf("")
    private val backupSyncSummaryState = mutableStateOf("")

    private fun getSubscriptionSummary(): String {
        val subs: List<com.github.tvbox.osc.bean.Subscription> = com.github.tvbox.osc.util.SubscriptionManager.getSubscriptions()
        return subs.find { it.isChecked }?.name ?: "未设置"
    }

    private fun getLiveSourceSummary(): String {
        return com.github.tvbox.osc.util.live.LiveSourceManager.getSelectedSourceName()
    }

    private fun getBackupSyncSummary(): String {
        return if (Hawk.get(HawkConfig.SYNC_ENABLED, false)) "云端同步已开启" else "本地备份与恢复"
    }

    override fun onResume() {
        super.onResume()
        subSourceSummaryState.value = getSubscriptionSummary()
        liveSourceSummaryState.value = getLiveSourceSummary()
        backupSyncSummaryState.value = getBackupSyncSummary()
    }

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { SettingScreen() } }
    }

    override fun init() {
    }

    private fun openAppearance() {
        jumpActivity(AppearanceActivity::class.java)
    }

    private fun openBackupRestore() {
        jumpActivity(BackupRestoreActivity::class.java)
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
        var dnsValue by remember { mutableStateOf(OkGoHelper.dnsHttpsList[Hawk.get(HawkConfig.DOH_URL, 0)]) }
        var historyValue by remember { mutableStateOf(HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0))) }

        // M3 抽屉与弹窗状态
        var sheetTitle by remember { mutableStateOf("") }
        var sheetOptions by remember { mutableStateOf<List<String>>(emptyList()) }
        var sheetSelectedPos by remember { mutableIntStateOf(0) }
        var onSheetPick by remember { mutableStateOf<(Int) -> Unit>({}) }
        var isSheetVisible by remember { mutableStateOf(false) }

        var ijkCodesSheetVisible by remember { mutableStateOf(false) }
        var clearCacheDialogVisible by remember { mutableStateOf(false) }
        var downloadSettingsSheetVisible by remember { mutableStateOf(false) }

        fun showM3Select(title: String, options: List<String>, selectedPos: Int, onPick: (Int) -> Unit) {
            sheetTitle = title
            sheetOptions = options
            sheetSelectedPos = selectedPos
            onSheetPick = onPick
            isSheetVisible = true
        }

        val isDark = isAppDarkTheme()
        val pageBg = if (isDark) colorScheme.background else Color(0xFFF3F3F4)

        val scrollState = rememberScrollState()
        val backdrop = rememberLayerBackdrop()
        val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
        val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 44.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .layerBackdrop(backdrop)
                    .padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(topBarPadding + 8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                ) {
                    SectionTitle("常规")
                    GroupCard {
                        MenuRow(R.drawable.ic_palette_48, "外观设置", appearanceValue, showDivider = true) { openAppearance() }
                        MenuRow(R.drawable.ic_home, "主页内容", homeRecValue, showDivider = true) {
                            showM3Select("主页内容显示", listOf("豆瓣热播", "站点推荐", "关闭"), Hawk.get(HawkConfig.HOME_REC, 0)) { pos ->
                                Hawk.put(HawkConfig.HOME_REC, pos)
                                homeRecValue = homeRecName(pos)
                            }
                        }
                        SwitchRow(R.drawable.ic_incognito_48, "无痕浏览", privateBrowsing, showDivider = false) { newValue ->
                            privateBrowsing = newValue
                            Hawk.put(HawkConfig.PRIVATE_BROWSING, newValue)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SectionTitle("数据源")
                    GroupCard {
                        MenuRow(R.drawable.ic_database_48, "订阅源", subSourceSummaryState.value, showDivider = true) {
                            jumpActivity(SubscriptionActivity::class.java)
                        }
                        MenuRow(R.drawable.ic_live_tv_48, "直播源", liveSourceSummaryState.value, showDivider = false) {
                            jumpActivity(LiveSourceActivity::class.java)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SectionTitle("播放与下载")
                    GroupCard {
                        MenuRow(R.drawable.ic_m3_play_arrow, "播放器", playValue, showDivider = true) {
                            val players = PlayerHelper.getExistPlayerTypes()
                            val names = players.map { PlayerHelper.getPlayerName(it) }
                            val playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0)
                            var defaultPos = 0
                            players.indices.forEach { if (players[it] == playerType) defaultPos = it }
                            showM3Select("请选择默认播放器", names, defaultPos) { pos ->
                                Hawk.put(HawkConfig.PLAY_TYPE, players[pos])
                                playValue = PlayerHelper.getPlayerName(players[pos])
                                PlayerHelper.init()
                            }
                        }
                        SwitchRow(R.drawable.ic_filter_list, "广告过滤", videoPurify, showDivider = true) { newValue ->
                            videoPurify = newValue
                            Hawk.put(HawkConfig.VIDEO_PURIFY, newValue)
                        }
                        val currentConcurrent = Hawk.get(HawkConfig.DOWNLOAD_MAX_CONCURRENT, 1)
                        val wifiOnlyDesc = if (Hawk.get(HawkConfig.DOWNLOAD_WIFI_ONLY, false)) " · 仅Wi-Fi" else ""
                        MenuRow(R.drawable.ic_download_18, "下载设置", "${currentConcurrent}任务并发$wifiOnlyDesc", showDivider = true) {
                            downloadSettingsSheetVisible = true
                        }
                        MenuRow(R.drawable.ic_speed_48, "长按倍速", speedValue, showDivider = true) {
                            val types = listOf("2.0", "3.0", "4.0", "5.0", "6.0", "8.0", "10.0")
                            val defaultPos = types.indexOf(Hawk.get(HawkConfig.VIDEO_SPEED, 2.0f).toString())
                            showM3Select("请选择倍速", types, if (defaultPos < 0) 0 else defaultPos) { pos ->
                                Hawk.put(HawkConfig.VIDEO_SPEED, types[pos].toFloat())
                                speedValue = types[pos]
                            }
                        }
                        MenuRow(R.drawable.ic_m3_pip, "后台播放", bgPlayValue, showDivider = true) {
                            val types = listOf("关闭", "开启", "画中画")
                            showM3Select("后台播放", types, Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0)) { pos ->
                                Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE, pos)
                                bgPlayValue = types[pos]
                                com.tvbox.sync.adapter.AppRoomSyncAdapter.recordPlayerSettingsUpsert()
                            }
                        }
                        MenuRow(R.drawable.ic_memory_48, "解码方式", ijkCodecValue, showDivider = true) {
                            val ijkCodes = ApiConfig.get().ijkCodes
                            if (!ijkCodes.isNullOrEmpty()) {
                                ijkCodesSheetVisible = true
                            }
                        }
                        MenuRow(R.drawable.ic_material_48, "渲染方式", renderValue, showDivider = true) {
                            val renderTypes = PlayerHelper.getRenderTypes()
                            val renderNames = renderTypes.map { PlayerHelper.getRenderName(it) }
                            val currentRender = Hawk.get(HawkConfig.PLAY_RENDER, PlayerHelper.RENDER_TEXTURE_VIEW)
                            var defaultPos = renderTypes.indexOf(currentRender)
                            if (defaultPos < 0) defaultPos = 0
                            showM3Select("请选择默认渲染方式", renderNames, defaultPos) { pos ->
                                val selectedType = renderTypes[pos]
                                Hawk.put(HawkConfig.PLAY_RENDER, selectedType)
                                renderValue = PlayerHelper.getRenderName(selectedType)
                                PlayerHelper.init()
                                com.tvbox.sync.adapter.AppRoomSyncAdapter.recordPlayerSettingsUpsert()
                            }
                        }
                        MenuRow(R.drawable.ic_scale_48, "画面缩放", scaleValue, showDivider = false) {
                            val scales = (0..5).toList()
                            showM3Select(
                                "请选择画面缩放",
                                scales.map { PlayerHelper.getScaleName(it) },
                                Hawk.get(HawkConfig.PLAY_SCALE, 0)
                            ) { pos ->
                                Hawk.put(HawkConfig.PLAY_SCALE, pos)
                                scaleValue = PlayerHelper.getScaleName(pos)
                                com.tvbox.sync.adapter.AppRoomSyncAdapter.recordPlayerSettingsUpsert()
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SectionTitle("数据与系统")
                    GroupCard {
                        MenuRow(R.drawable.ic_backup_48, "备份与恢复", backupSyncSummaryState.value, showDivider = true) { openBackupRestore() }
                        MenuRow(R.drawable.ic_dns_48, "安全DNS", dnsValue, showDivider = true) {
                            val dohUrl = Hawk.get(HawkConfig.DOH_URL, 0)
                            showM3Select("请选择安全DNS", OkGoHelper.dnsHttpsList, dohUrl) { pos ->
                                Hawk.put(HawkConfig.DOH_URL, pos)
                                dnsValue = OkGoHelper.dnsHttpsList[pos]
                                OkGoHelper.updateDns()
                            }
                        }
                        MenuRow(R.drawable.ic_history_48, "历史记录", historyValue, showDivider = true) {
                            showM3Select(
                                "保留历史记录数量",
                                listOf(0, 1, 2).map { HistoryHelper.getHistoryNumName(it) },
                                Hawk.get(HawkConfig.HISTORY_NUM, 0)
                            ) { pos ->
                                Hawk.put(HawkConfig.HISTORY_NUM, pos)
                                historyValue = HistoryHelper.getHistoryNumName(pos)
                            }
                        }
                        MenuRow(R.drawable.ic_clear, "清空缓存", null, showDivider = false) { clearCacheDialogVisible = true }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            TvBoxTopBar(
                title = "设置",
                isScrolled = isScrolled,
                backdrop = backdrop,
                onBack = { onBackPressedDispatcher.onBackPressed() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ---------------- M3 Expressive Sheets & Dialogs ----------------
        M3SingleChoiceBottomSheet(
            visible = isSheetVisible,
            title = sheetTitle,
            options = sheetOptions,
            selectedPos = sheetSelectedPos,
            onDismissRequest = { isSheetVisible = false },
            onSelected = { pos, _ ->
                onSheetPick(pos)
            }
        )

        val ijkCodes = ApiConfig.get().ijkCodes ?: emptyList()
        val currentIjkCodeName = Hawk.get(HawkConfig.IJK_CODEC, "")
        val ijkSelectedPos = ijkCodes.indexOfFirst { it.name == currentIjkCodeName }.let { if (it < 0) 0 else it }

        M3SingleChoiceBottomSheet(
            visible = ijkCodesSheetVisible,
            title = "请选择解码方式",
            options = ijkCodes,
            selectedPos = ijkSelectedPos,
            getDisplayName = { it.name ?: "" },
            onDismissRequest = { ijkCodesSheetVisible = false },
            onSelected = { _, item ->
                item.selected(true)
                ijkCodecValue = item.name ?: ""
            }
        )

        M3ConfirmDialog(
            visible = clearCacheDialogVisible,
            title = "清空缓存",
            message = "确定要清理本地缓存数据吗？此操作不会影响已保存的历史记录和配置。",
            confirmText = "清空",
            cancelText = "取消",
            isDestructive = true,
            iconRes = R.drawable.ic_clear,
            onDismissRequest = { clearCacheDialogVisible = false },
            onConfirm = { clearCache() }
        )

        DownloadSettingsSheet(
            visible = downloadSettingsSheetVisible,
            onDismissRequest = { downloadSettingsSheetVisible = false }
        )
    }
}
