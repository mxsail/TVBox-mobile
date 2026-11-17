package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.compose.VodCard
import com.github.tvbox.osc.ui.compose.VodGridScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 观看历史(Compose): 3 列海报网格, 点击续播, 长按删除, 右上清空。
 */
class HistoryActivity : BaseActivity() {

    private val cards = mutableStateOf<List<VodCard>>(emptyList())

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            com.github.tvbox.osc.ui.compose.TvBoxTheme {
                VodGridScreen(
                    title = "观看历史",
                    cards = cards.value,
                    onBack = { finish() },
                    onItemClick = { card ->
                        val bundle = Bundle()
                        bundle.putString("id", card.key.substringAfter('|'))
                        bundle.putString("sourceKey", card.key.substringBefore('|'))
                        jumpActivity(DetailActivity::class.java, bundle)
                    },
                    onDeleteSelected = { selectedKeys ->
                        deleteSelected(selectedKeys)
                    }
                )
            }
        }
        initData()
    }

    override fun init() {
    }

    override fun onResume() {
        super.onResume()
        initData()
    }

    override fun refresh(event: RefreshEvent) {
        super.refresh(event)
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData()
        }
    }

    private fun initData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val all = RoomDataManger.getAllVodRecord(100)
            val list = ArrayList<Pair<VodCard, VodInfo>>()
            for (vodInfo in all) {
                if (vodInfo.playNote != null && vodInfo.playNote.isNotEmpty()) vodInfo.note = vodInfo.playNote
                val sourceName = try {
                    ApiConfig.get().getSource(vodInfo.sourceKey)?.name ?: ""
                } catch (e: Exception) {
                    ""
                }
                list.add(
                    VodCard(
                        key = vodInfo.sourceKey + "|" + vodInfo.id,
                        name = vodInfo.name ?: "",
                        pic = vodInfo.pic ?: "",
                        badge = sourceName,
                        note = vodInfo.note
                    ) to vodInfo
                )
            }
            withContext(Dispatchers.Main) {
                // 保留原始 VodInfo 供删除使用
                cards.value = list.map { it.first }
                rawInfos.clear()
                rawInfos.addAll(list.map { it.second })
            }
        }
    }

    private val rawInfos = mutableListOf<VodInfo>()

    private fun deleteSelected(selectedKeys: Set<String>) {
        if (selectedKeys.isEmpty()) return
        showLoadingDialog()
        lifecycleScope.launch(Dispatchers.IO) {
            val toRemove = rawInfos.filter { selectedKeys.contains(it.sourceKey + "|" + it.id) }
            for (info in toRemove) {
                try {
                    RoomDataManger.deleteVodRecord(info.sourceKey, info)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                dismissLoadingDialog()
                cards.value = cards.value.filterNot { selectedKeys.contains(it.key) }
                rawInfos.removeAll(toRemove)
                org.greenrobot.eventbus.EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH))
                com.blankj.utilcode.util.ToastUtils.showShort("已删除 ${toRemove.size} 项历史记录")
            }
        }
    }
}
