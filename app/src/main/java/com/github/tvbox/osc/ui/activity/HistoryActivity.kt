package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
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
                    onClear = { confirmClear() },
                    onItemClick = { card ->
                        val bundle = Bundle()
                        bundle.putString("id", card.key.substringAfter('|'))
                        bundle.putString("sourceKey", card.key.substringBefore('|'))
                        jumpActivity(DetailActivity::class.java, bundle)
                    },
                    onItemLongClick = { card ->
                        val info = rawInfos.firstOrNull {
                            it.sourceKey + "|" + it.id == card.key
                        } ?: return@VodGridScreen
                        RoomDataManger.deleteVodRecord(info.sourceKey, info)
                        cards.value = cards.value.filterNot { it == card }
                        rawInfos.removeAll { it.sourceKey + "|" + it.id == card.key }
                    }
                )
            }
        }
        initData()
    }

    override fun init() {
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

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("提示")
            .setMessage("确定清空?")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                showLoadingDialog()
                lifecycleScope.launch(Dispatchers.IO) {
                    RoomDataManger.deleteVodRecordAll()
                    withContext(Dispatchers.Main) {
                        dismissLoadingDialog()
                        cards.value = emptyList()
                    }
                }
            }
            .show()
    }
}
