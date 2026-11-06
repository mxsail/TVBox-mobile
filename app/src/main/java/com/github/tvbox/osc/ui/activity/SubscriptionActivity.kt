package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.Source
import com.github.tvbox.osc.bean.Subscription
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.dialog.ChooseSourceDialog
import com.github.tvbox.osc.ui.dialog.SubsTipDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog.OnSubsciptionListener
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.view.MaterialBarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonParser
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.obsez.android.lib.filechooser.ChooserDialog
import com.orhanobut.hawk.Hawk

/**
 * 订阅管理(Compose): 订阅列表(选中/删除/置顶/重命名/复制) + 添加订阅(网络/本地导入/多线路/多仓解析)。
 * 交互弹窗沿用 View 版(XPopup/Material/ChooserDialog), Compose 负责页面框架与列表渲染。
 */
class SubscriptionActivity : BaseActivity() {

    private var mBeforeUrl = Hawk.get(HawkConfig.API_URL, "")
    private var mSelectedUrl = ""
    private val mSubscriptions: MutableList<Subscription> = Hawk.get(HawkConfig.SUBSCRIPTIONS, ArrayList())
    private val mSources: MutableList<Source> = ArrayList()

    /** 列表 UI 快照(Subscription 为可变 POJO, 每次操作后重建快照驱动重组) */
    private data class SubRow(val name: String, val url: String, val checked: Boolean, val isTop: Boolean)
    private val rows = mutableStateOf<List<SubRow>>(emptyList())
    private val loading = mutableStateOf(false)

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { SubscriptionScreen() } }
    }

    override fun init() {
        mSubscriptions.forEach { item: Subscription ->
            if (item.isChecked) mSelectedUrl = item.url
        }
        refresh()
    }

    private fun refresh() {
        // 置顶的排前面, 保持既有列表语义
        val sorted = mSubscriptions.sortedByDescending { it.isTop }
        rows.value = sorted.map { SubRow(it.name, it.url, it.isChecked, it.isTop) }
    }

    /** url → mSubscriptions 定位(置顶排序后 UI 顺序≠数据顺序, 一律按 url 定位) */
    private fun indexOfUrl(url: String): Int = mSubscriptions.indexOfFirst { it.url == url }

    // ---------------- 添加订阅 ----------------

    private fun showAddDialog() {
        XPopup.Builder(this).hasBlurBg(MaterialBarHelper.popupBlurEnabled())
            .autoFocusEditText(false)
            .asCustom(
                SubsciptionDialog(
                    this,
                    "订阅: " + (mSubscriptions.size + 1),
                    object : OnSubsciptionListener {
                        override fun onConfirm(name: String, url: String, checked: Boolean) {
                            for (item in mSubscriptions) {
                                if (item.url == url) {
                                    ToastUtils.showLong("订阅地址与" + item.name + "相同")
                                    return
                                }
                            }
                            addSubscription(name, url, checked)
                        }

                        override fun chooseLocal(checked: Boolean) {
                            if (!XXPermissions.isGranted(this@SubscriptionActivity, Permission.MANAGE_EXTERNAL_STORAGE)) {
                                showPermissionTipPopup(checked)
                            } else {
                                pickFile(checked)
                            }
                        }
                    })
            ).show()
    }

    private fun showUseTip() {
        XPopup.Builder(this).hasBlurBg(MaterialBarHelper.popupBlurEnabled())
            .asCustom(SubsTipDialog(this))
            .show()
    }

    private fun showPermissionTipPopup(checked: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle("提示")
            .setMessage("这将访问您设备文件的读取权限")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                XXPermissions.with(this)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) pickFile(checked)
                            else ToastUtils.showLong("部分权限未正常授予,请授权")
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("读写文件权限被永久拒绝，请手动授权")
                                XXPermissions.startPermissionActivity(this@SubscriptionActivity, permissions)
                            } else {
                                ToastUtils.showShort("获取权限失败")
                                showPermissionTipPopup(checked)
                            }
                        }
                    })
            }
            .show()
    }

    private fun pickFile(checked: Boolean) {
        ChooserDialog(this, R.style.FileChooser)
            .withFilter(false, false, "txt", "json")
            .withStartFile(
                if (TextUtils.isEmpty(Hawk.get<String>("before_selected_path"))) "/storage/emulated/0/Download"
                else Hawk.get("before_selected_path")
            )
            .withChosenListener(ChooserDialog.Result { _, pathFile ->
                Hawk.put("before_selected_path", pathFile.parent)
                val clanPath = pathFile.absolutePath.replace("/storage/emulated/0", "clan://localhost")
                for (item in mSubscriptions) {
                    if (item.url == clanPath) {
                        ToastUtils.showLong("订阅地址与" + item.name + "相同")
                        return@Result
                    }
                }
                addSubscription(pathFile.name, clanPath, checked)
            })
            .build()
            .show()
    }

    private fun addSubscription(name: String, url: String, checked: Boolean) {
        if (url.startsWith("clan://")) {
            addSub2List(name, url, checked)
            refresh()
        } else if (url.startsWith("http")) {
            loading.value = true
            showLoadingDialog()
            OkGo.get<String>(url)
                .tag("get_subscription")
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: Response<String?>) {
                        dismissLoadingDialog()
                        loading.value = false
                        try {
                            val json = JsonParser.parseString(response.body()).asJsonObject
                            val urls = json["urls"]
                            val storeHouse = json["storeHouse"]
                            if (urls != null && urls.isJsonArray) { // 多线路
                                if (checked) ToastUtils.showLong("多条线路请主动选择")
                                val urlList = urls.asJsonArray
                                if (urlList.size() > 0 && urlList[0].isJsonObject
                                    && urlList[0].asJsonObject.has("url")
                                    && urlList[0].asJsonObject.has("name")
                                ) {
                                    for (i in 0 until urlList.size()) {
                                        val obj = urlList[i] as com.google.gson.JsonObject
                                        val n = obj["name"].asString.trim { it <= ' ' }
                                            .replace("<|>|《|》|-".toRegex(), "")
                                        val u = obj["url"].asString.trim { it <= ' ' }
                                        mSubscriptions.add(Subscription(n, u))
                                    }
                                }
                            } else if (storeHouse != null && storeHouse.isJsonArray) { // 多仓
                                val storeHouseList = storeHouse.asJsonArray
                                if (storeHouseList.size() > 0 && storeHouseList[0].isJsonObject
                                    && storeHouseList[0].asJsonObject.has("sourceName")
                                    && storeHouseList[0].asJsonObject.has("sourceUrl")
                                ) {
                                    mSources.clear()
                                    for (i in 0 until storeHouseList.size()) {
                                        val obj = storeHouseList[i] as com.google.gson.JsonObject
                                        val n = obj["sourceName"].asString.trim { it <= ' ' }
                                            .replace("<|>|《|》|-".toRegex(), "")
                                        val u = obj["sourceUrl"].asString.trim { it <= ' ' }
                                        mSources.add(Source(n, u))
                                    }
                                    XPopup.Builder(this@SubscriptionActivity)
                                        .hasBlurBg(MaterialBarHelper.popupBlurEnabled())
                                        .asCustom(
                                            ChooseSourceDialog(
                                                this@SubscriptionActivity,
                                                mSources
                                            ) { position: Int, _: String? ->
                                                addSubscription(
                                                    mSources[position].sourceName,
                                                    mSources[position].sourceUrl,
                                                    checked
                                                )
                                            })
                                        .show()
                                }
                            } else { // 单线路/其余
                                addSub2List(name, url, checked)
                            }
                        } catch (th: Throwable) {
                            addSub2List(name, url, checked)
                        }
                        refresh()
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        return response.body!!.string()
                    }

                    override fun onError(response: Response<String?>) {
                        super.onError(response)
                        dismissLoadingDialog()
                        loading.value = false
                        ToastUtils.showLong("订阅失败,请检查地址或网络状态")
                    }
                })
        } else {
            ToastUtils.showShort("订阅格式不正确")
        }
    }

    /** 仅本地文件/单线路订阅用此生效; 多线路全量添加, 多仓展开选择 */
    private fun addSub2List(name: String, url: String, checkNewest: Boolean) {
        if (checkNewest) {
            for (subscription in mSubscriptions) {
                if (subscription.isChecked) subscription.setChecked(false)
            }
            mSelectedUrl = url
            mSubscriptions.add(Subscription(name, url).setChecked(true))
        } else {
            mSubscriptions.add(Subscription(name, url).setChecked(false))
        }
    }

    // ---------------- 列表操作 ----------------

    private fun pickSubscription(positionInData: Int) {
        for (i in mSubscriptions.indices) {
            mSubscriptions[i].setChecked(i == positionInData)
        }
        mSelectedUrl = mSubscriptions[positionInData].url
        refresh()
    }

    private fun deleteSubscription(url: String) {
        val pos = indexOfUrl(url)
        if (pos < 0) return
        if (mSubscriptions[pos].isChecked) {
            ToastUtils.showShort("不能删除当前使用的订阅")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("删除订阅")
            .setMessage("确定删除订阅吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                mSubscriptions.removeAt(pos)
                refresh()
            }
            .show()
    }

    private fun showItemMenu(url: String) {
        val pos = indexOfUrl(url)
        if (pos < 0) return
        val item = mSubscriptions[pos]
        val options = arrayOf(
            if (item.isTop) "取消置顶" else "置顶",
            "重命名",
            "复制链接",
            "删除"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        item.isTop = !item.isTop
                        refresh()
                    }
                    1 -> {
                        XPopup.Builder(this)
                            .hasBlurBg(MaterialBarHelper.popupBlurEnabled())
                            .asInputConfirm("重命名", null, item.name, "请输入名称") { text ->
                                if (!TextUtils.isEmpty(text)) {
                                    val trimmed = text.trim()
                                    if (trimmed.length > 20) {
                                        ToastUtils.showShort("名称过长")
                                    } else {
                                        item.name = trimmed
                                        refresh()
                                    }
                                }
                            }
                            .show()
                    }
                    2 -> {
                        ClipboardUtils.copyText(item.url)
                        ToastUtils.showShort("已复制")
                    }
                    3 -> deleteSubscription(item.url)
                }
            }
            .show()
    }

    // ---------------- 生命周期语义(沿用旧版) ----------------

    override fun onPause() {
        super.onPause()
        Hawk.put(HawkConfig.API_URL, mSelectedUrl)
        Hawk.put<List<Subscription>?>(HawkConfig.SUBSCRIPTIONS, mSubscriptions)
        if (mBeforeUrl != mSelectedUrl) {
            com.tvbox.sync.adapter.AppRoomSyncAdapter.recordContentConfigUpsert()
        }
    }

    override fun finish() {
        if (!TextUtils.isEmpty(mSelectedUrl) && mBeforeUrl != mSelectedUrl) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        OkGo.getInstance().cancelTag("get_subscription")
    }

    // ---------------- Compose 界面 ----------------

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun SubscriptionScreen() {
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("订阅源", fontWeight = FontWeight.Bold) },
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
                        IconButton(onClick = { showUseTip() }) {
                            Icon(
                                painterResource(R.drawable.ic_copyright_48),
                                contentDescription = "使用说明",
                                tint = colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showAddDialog() }) {
                            Icon(
                                painterResource(R.drawable.ic_add_24),
                                contentDescription = "添加订阅",
                                tint = colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
                )
            }
        ) { padding ->
            if (rows.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Text("暂无订阅, 点右上角添加", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(rows.value) { _, row ->
                        val selected = row.checked
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
                                    .clickable { indexOfUrl(row.url).takeIf { it >= 0 }?.let { pickSubscription(it) } }
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
                                    onClick = { indexOfUrl(row.url).takeIf { it >= 0 }?.let { pickSubscription(it) } },
                                    colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                                )
                                IconButton(onClick = { showItemMenu(row.url) }) {
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
            }
        }
    }
}
