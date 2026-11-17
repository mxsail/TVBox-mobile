package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.ui.compose.VodCard
import com.github.tvbox.osc.ui.compose.VodGridScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 收藏(Compose): 3 列海报网格, 点击进详情(源失效转聚合搜索), 长按删除, 右上清空。
 */
class CollectActivity : BaseActivity() {

    private val cards = mutableStateOf<List<VodCard>>(emptyList())
    private val rawCollects = mutableListOf<VodCollect>()

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            com.github.tvbox.osc.ui.compose.TvBoxTheme {
                VodGridScreen(
                    title = "收藏",
                    cards = cards.value,
                    onBack = { finish() },
                    onItemClick = { card ->
                        val item = rawCollects.firstOrNull { it.id.toString() == card.key }
                        if (item != null) {
                            if (ApiConfig.get().getSource(item.sourceKey) != null) {
                                val bundle = Bundle()
                                bundle.putString("id", item.vodId)
                                bundle.putString("sourceKey", item.sourceKey)
                                jumpActivity(DetailActivity::class.java, bundle)
                            } else {
                                val newIntent = Intent(this, FastSearchActivity::class.java)
                                newIntent.putExtra("title", item.name)
                                newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(newIntent)
                            }
                        }
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

    private fun initData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val all = RoomDataManger.getAllVodCollect()
            withContext(Dispatchers.Main) {
                rawCollects.clear()
                rawCollects.addAll(all)
                cards.value = all.map { item ->
                    val sourceName = try {
                        ApiConfig.get().getSource(item.sourceKey)?.name ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    VodCard(
                        key = item.id.toString(),
                        name = item.name ?: "",
                        pic = item.pic ?: "",
                        badge = sourceName
                    )
                }
            }
        }
    }

    private fun deleteSelected(selectedKeys: Set<String>) {
        if (selectedKeys.isEmpty()) return
        showLoadingDialog()
        lifecycleScope.launch(Dispatchers.IO) {
            for (key in selectedKeys) {
                try {
                    RoomDataManger.deleteVodCollect(key.toInt())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                dismissLoadingDialog()
                cards.value = cards.value.filterNot { selectedKeys.contains(it.key) }
                rawCollects.removeAll { selectedKeys.contains(it.id.toString()) }
                com.blankj.utilcode.util.ToastUtils.showShort("已删除 ${selectedKeys.size} 项收藏")
            }
        }
    }
}
