package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.download.PlayUrlResolver
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.MenuRow
import com.github.tvbox.osc.ui.compose.SectionTitle
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import com.github.tvbox.osc.util.HeavyTaskUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

/**
 * 关于页(Compose): 版本信息 + GitHub release 检查更新。
 */
class AboutActivity : BaseActivity() {

    companion object {
        /** 开源仓库主页地址 */
        private const val REPO_URL = "https://github.com/mxsail/TVBox-mobile"
        /** GitHub 最新 release 查询接口 */
        private const val UPDATE_API = "https://api.github.com/repos/mxsail/TVBox-mobile/releases/latest"
        /** 下载页地址(releases/latest 始终重定向到最新版) */
        private const val RELEASE_URL = "https://github.com/mxsail/TVBox-mobile/releases/latest"

        /** 版本号逐段数值比较(tag 形如 v3.1.0): a>b 返回 1, a<b 返回 -1, 相等返回 0 */
        fun compareVersion(a: String, b: String): Int {
            val sa = a.trim().replaceFirst("^[vV]".toRegex(), "").split(".")
            val sb = b.trim().replaceFirst("^[vV]".toRegex(), "").split(".")
            val n = maxOf(sa.size, sb.size)
            for (i in 0 until n) {
                val va = if (i < sa.size) parseSegment(sa[i]) else 0
                val vb = if (i < sb.size) parseSegment(sb[i]) else 0
                if (va != vb) return if (va > vb) 1 else -1
            }
            return 0
        }

        private fun parseSegment(s: String): Int = s.trim().toIntOrNull() ?: 0
    }

    /** 检查更新状态文本(Compose 状态, 后台线程结果回主线程更新) */
    private var checkState = mutableStateOf("检查更新")

    override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeView)
        composeView.setContent { TvBoxTheme { AboutScreen() } }
    }

    override fun init() {
    }

    private fun checkUpdate() {
        ToastUtils.showShort("正在检查更新…")
        checkState.value = "正在检查…"
        HeavyTaskUtil.executeNewTask {
            var tag: String? = null
            try {
                val json = PlayUrlResolver.fetchText(UPDATE_API)
                if (!TextUtils.isEmpty(json)) {
                    tag = JSONObject(json).optString("tag_name", null)
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
            val latest = tag
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (TextUtils.isEmpty(latest)) {
                    checkState.value = "检查失败,点此重试"
                    ToastUtils.showShort("检查失败,请检查网络后重试")
                    return@runOnUiThread
                }
                val current = AppUtils.getAppVersionName()
                if (compareVersion(latest!!, current) > 0) {
                    checkState.value = "发现新版本 $latest"
                    MaterialAlertDialogBuilder(this)
                        .setTitle("发现新版本 $latest")
                        .setMessage("当前版本 v$current,是否前往下载页面?")
                        .setPositiveButton("去下载") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_URL)))
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    checkState.value = "已是最新版本"
                    ToastUtils.showShort("已是最新版本")
                }
            }
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun AboutScreen() {
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("关于", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
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
                    .padding(horizontal = 14.dp)
            ) {
                SectionTitle("版本")
                GroupCard {
                    MenuRow(
                        R.drawable.ic_about_48,
                        "TVBox-Mobile v" + AppUtils.getAppVersionName(),
                        value = checkState.value
                    ) { checkUpdate() }
                }
                SectionTitle("项目")
                GroupCard {
                    MenuRow(R.drawable.ic_copyright_48, "开源地址", value = "GitHub") {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                    }
                }
            }
        }
    }
}
