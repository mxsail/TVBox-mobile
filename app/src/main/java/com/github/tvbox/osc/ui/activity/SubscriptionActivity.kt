package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.github.tvbox.osc.base.ThemeManager
import com.github.tvbox.osc.bean.Source
import com.github.tvbox.osc.bean.Subscription
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.ui.compose.M3ActionsCenterDialog
import com.github.tvbox.osc.ui.compose.M3ConfirmDialog
import com.github.tvbox.osc.ui.compose.M3InputDialog
import com.github.tvbox.osc.ui.compose.M3ItemAction
import com.github.tvbox.osc.ui.compose.M3SingleChoiceCenterDialog
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.ui.compose.TvBoxTopBar
import com.github.tvbox.osc.ui.compose.isAppDarkTheme
import com.github.tvbox.osc.util.HawkConfig
import com.google.gson.JsonParser
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
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
    private val mSubscriptions: MutableList<Subscription> = ArrayList()
    private val mSources: MutableList<Source> = ArrayList()

    /** 列表 UI 快照(Subscription 为可变 POJO, 每次操作后重建快照驱动重组) */
    private data class SubRow(val name: String, val url: String, val checked: Boolean, val isTop: Boolean)
    private val rows = mutableStateOf<List<SubRow>>(emptyList())
    private val loading = mutableStateOf(false)
    private val showAddDialogState = mutableStateOf(false)
    private val showTipDialogState = mutableStateOf(false)
    private val chooseSourcesState = mutableStateOf<Pair<List<Source>, Boolean>?>(null)
    private val showPermissionTipState = mutableStateOf<Boolean?>(null)

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { SubscriptionScreen() } }
    }

    override fun init() {
        loadDataFromStorage()
    }

    override fun onResume() {
        super.onResume()
        loadDataFromStorage()
    }

    private fun loadDataFromStorage() {
        mSubscriptions.clear()
        mSubscriptions.addAll(com.github.tvbox.osc.util.SubscriptionManager.getSubscriptions())
        val currentApiUrl = Hawk.get(HawkConfig.API_URL, "")
        if (mSelectedUrl.isEmpty()) {
            mSelectedUrl = currentApiUrl
        }
        var foundSelected = false
        mSubscriptions.forEach { item: Subscription ->
            if (item.isChecked) {
                mSelectedUrl = item.url
                foundSelected = true
            }
        }
        if (!foundSelected && !currentApiUrl.isNullOrEmpty()) {
            mSubscriptions.find { it.url == currentApiUrl }?.let {
                it.isChecked = true
                mSelectedUrl = it.url
            }
        }
        refresh()
    }

    private fun saveSubscriptions() {
        com.github.tvbox.osc.util.SubscriptionManager.setSelectedApiUrl(mSelectedUrl)
        com.github.tvbox.osc.util.SubscriptionManager.saveSubscriptions(mSubscriptions)
        if (mBeforeUrl != mSelectedUrl) {
            com.tvbox.sync.adapter.AppRoomSyncAdapter.recordContentConfigUpsert()
        }
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
        showAddDialogState.value = true
    }

    private fun showUseTip() {
        showTipDialogState.value = true
    }

    private fun requestStoragePermission(checked: Boolean, onDeniedRetry: () -> Unit) {
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
                        onDeniedRetry()
                    }
                }
            })
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
                                    saveSubscriptions()
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
                                    chooseSourcesState.value = ArrayList(mSources) to checked
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
        saveSubscriptions()
    }

    // ---------------- 列表操作 ----------------

    private fun pickSubscription(positionInData: Int) {
        for (i in mSubscriptions.indices) {
            mSubscriptions[i].setChecked(i == positionInData)
        }
        mSelectedUrl = mSubscriptions[positionInData].url
        saveSubscriptions()
        refresh()
    }



    // ---------------- 生命周期语义 ----------------

    override fun finish() {
        // 所有增删改都已即时落盘，退出时不再用内存列表覆盖存储：
        // onResume 一旦读取失败返回空列表，这里的写回会把订阅全部清空。
        if (!TextUtils.isEmpty(mSelectedUrl) && mBeforeUrl != mSelectedUrl) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(IntentKey.CACHE_CONFIG_CHANGED, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
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
        val isFabVisible = remember { mutableStateOf(true) }
        var itemForActions by remember { mutableStateOf<SubRow?>(null) }
        var itemForRename by remember { mutableStateOf<SubRow?>(null) }
        var itemForDelete by remember { mutableStateOf<SubRow?>(null) }

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
                    title = "订阅源",
                    onBack = { finish() },
                    actions = {
                        IconButton(onClick = { showUseTip() }) {
                            Icon(
                                painterResource(R.drawable.ic_info_outline_24),
                                contentDescription = "使用说明",
                                tint = colorScheme.onSurface
                            )
                        }
                    }
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
                            contentDescription = "添加订阅",
                            tint = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        ) { padding ->
            if (rows.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_inbox_24),
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        "暂无订阅源",
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "添加订阅源以获取丰富的影视与直播内容",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )
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
                        Text("立即添加", fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .nestedScroll(nestedScrollConnection)
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(rows.value) { _, row ->
                        val selected = row.checked
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow,
                            border = if (selected) BorderStroke(1.5.dp, colorScheme.primary) else null,
                            onClick = { indexOfUrl(row.url).takeIf { it >= 0 }?.let { pickSubscription(it) } },
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
                                            onClick = { indexOfUrl(row.url).takeIf { it >= 0 }?.let { pickSubscription(it) } },
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
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }

        // 更多操作卡片居中弹窗
        itemForActions?.let { row ->
            val actions = listOf(
                M3ItemAction(
                    title = if (row.isTop) "取消置顶" else "置顶",
                    iconRes = R.drawable.ic_m3_pin_24,
                    onClick = {
                        val pos = indexOfUrl(row.url)
                        if (pos >= 0) {
                            mSubscriptions[pos].isTop = !mSubscriptions[pos].isTop
                            saveSubscriptions()
                            refresh()
                        }
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
                        val pos = indexOfUrl(row.url)
                        if (pos >= 0 && mSubscriptions[pos].isChecked) {
                            ToastUtils.showShort("不能删除当前使用的订阅")
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
                label = "订阅名称",
                maxLength = 20,
                confirmText = "保存",
                cancelText = "取消",
                onDismissRequest = { itemForRename = null },
                onConfirm = { newName ->
                    val pos = indexOfUrl(row.url)
                    if (pos >= 0) {
                        mSubscriptions[pos].name = newName
                        saveSubscriptions()
                        refresh()
                        ToastUtils.showShort("已修改")
                    }
                }
            )
        }

        // 删除确认弹窗
        itemForDelete?.let { row ->
            M3ConfirmDialog(
                visible = true,
                title = "删除订阅",
                message = "确定删除「${row.name}」吗？",
                confirmText = "删除",
                cancelText = "取消",
                isDestructive = true,
                iconRes = R.drawable.ic_delete_outline_24,
                onDismissRequest = { itemForDelete = null },
                onConfirm = {
                    val pos = indexOfUrl(row.url)
                    if (pos >= 0) {
                        mSubscriptions.removeAt(pos)
                        saveSubscriptions()
                        refresh()
                        ToastUtils.showShort("已删除")
                    }
                }
            )
        }

        // 存储权限提示弹窗
        showPermissionTipState.value?.let { checked ->
            M3ConfirmDialog(
                visible = true,
                title = "存储权限提示",
                message = "导入本地订阅文件需要读取设备存储权限，是否前往授权？",
                confirmText = "去授权",
                cancelText = "取消",
                iconRes = R.drawable.ic_file_24,
                onDismissRequest = { showPermissionTipState.value = null },
                onConfirm = {
                    requestStoragePermission(checked) {
                        showPermissionTipState.value = checked
                    }
                }
            )
        }

        // 多仓线路选择弹窗
        chooseSourcesState.value?.let { (sources, checked) ->
            M3SingleChoiceCenterDialog(
                visible = true,
                title = "选择多仓线路",
                subtitle = "检测到多仓配置，共 ${sources.size} 条线路可选",
                options = sources,
                getDisplayName = { it.sourceName },
                getSecondaryText = { it.sourceUrl },
                onDismissRequest = { chooseSourcesState.value = null },
                onSelected = { _, source ->
                    addSubscription(source.sourceName, source.sourceUrl, checked)
                }
            )
        }

        // 添加订阅弹窗
        if (showAddDialogState.value) {
            AddSubscriptionDialog(
                visible = true,
                defaultName = "订阅: ${mSubscriptions.size + 1}",
                onDismiss = { showAddDialogState.value = false },
                onChooseLocal = { checked ->
                    showAddDialogState.value = false
                    if (!XXPermissions.isGranted(this@SubscriptionActivity, Permission.MANAGE_EXTERNAL_STORAGE)) {
                        showPermissionTipState.value = checked
                    } else {
                        pickFile(checked)
                    }
                },
                onConfirm = { name, url, checked ->
                    for (item in mSubscriptions) {
                        if (item.url == url) {
                            ToastUtils.showLong("订阅地址与${item.name}相同")
                            return@AddSubscriptionDialog
                        }
                    }
                    showAddDialogState.value = false
                    addSubscription(name, url, checked)
                }
            )
        }

        // 使用说明与免责声明弹窗
        if (showTipDialogState.value) {
            SubscriptionTipDialog(
                visible = true,
                onDismissRequest = { showTipDialogState.value = false }
            )
        }
    }

    /**
     * Material Design 3 Expressive 风格新增订阅弹窗
     */
    @Composable
    private fun AddSubscriptionDialog(
        visible: Boolean,
        defaultName: String,
        onDismiss: () -> Unit,
        onChooseLocal: (check: Boolean) -> Unit,
        onConfirm: (name: String, url: String, check: Boolean) -> Unit
    ) {
        if (!visible) return
        var name by remember(defaultName) { mutableStateOf(defaultName) }
        var url by remember { mutableStateOf("") }
        var check by remember { mutableStateOf(false) }
        val colorScheme = MaterialTheme.colorScheme
        val isDark = isAppDarkTheme()

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) colorScheme.surfaceContainerHigh else Color.White,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // 顶部标题与本地导入按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "添加订阅",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Surface(
                            onClick = {
                                onDismiss()
                                onChooseLocal(check)
                            },
                            shape = RoundedCornerShape(20.dp),
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
                                    modifier = Modifier.size(14.dp)
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

                    // 输入框 1：订阅名称
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("订阅名称") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // 输入框 2：订阅地址
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("订阅地址") },
                        placeholder = {
                            Text(
                                "https://... 或 clan://...",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
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

                    // 设为当前订阅开关
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
                                    text = "设为当前订阅",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "添加完成后立即生效并切换线路",
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
                                    ToastUtils.showShort("请输入订阅地址")
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

    /**
     * Material Design 3 Expressive 风格使用说明与免责声明弹窗
     */
    @Composable
    private fun SubscriptionTipDialog(
        visible: Boolean,
        onDismissRequest: () -> Unit
    ) {
        if (!visible) return
        val colorScheme = MaterialTheme.colorScheme
        val isDark = isAppDarkTheme()
        val context = androidx.compose.ui.platform.LocalContext.current

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) colorScheme.surfaceContainerHigh else Color.White,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info_outline_24),
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "订阅说明与免责声明",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(weight = 1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 订阅提示卡片
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "订阅提示",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = context.getString(R.string.subs_use_tip).trim(),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 免责说明卡片
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "免责声明",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.error
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = context.getString(R.string.subs_tip).trim(),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("我知道了", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
