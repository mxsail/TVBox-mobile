package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.AppUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.ui.activity.AboutActivity
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.compose.GroupCard
import com.github.tvbox.osc.ui.compose.MenuRow
import com.github.tvbox.osc.ui.compose.TvBoxTheme
import me.jessyan.autosize.AutoSize

/**
 * 我的页(Compose): 历史收藏缓存直播 + 订阅设置关于 两组卡片入口。
 */
class MyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AutoSize.autoConvertDensity(activity, 360f, true)
        val composeView = ComposeView(requireContext())
        composeView.setContent { TvBoxTheme { MyScreen() } }
        return composeView
    }

    private fun jumpActivity(clazz: Class<*>) {
        startActivity(Intent(requireActivity(), clazz))
    }

    private fun goMainTab(index: Int) {
        (requireActivity() as? MainActivity)?.goToTab(index)
    }

    @Composable
    private fun MyScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.app_name),
                color = colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 32.dp, top = 40.dp)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                GroupCard {
                    MenuRow(R.drawable.ic_history_48, "观看历史") { jumpActivity(HistoryActivity::class.java) }
                    MenuRow(R.drawable.ic_collect_48, "收藏") { jumpActivity(CollectActivity::class.java) }
                    MenuRow(R.drawable.ic_cache_48, "我的缓存") { goMainTab(2) }
                    MenuRow(R.drawable.ic_live_tv_48, "直播") { goMainTab(1) }
                }
                Spacer(Modifier.height(12.dp))
                GroupCard {
                    MenuRow(R.drawable.ic_database_48, "订阅管理") { jumpActivity(SubscriptionActivity::class.java) }
                    MenuRow(R.drawable.ic_settings, "设置") { jumpActivity(SettingActivity::class.java) }
                    MenuRow(R.drawable.ic_about_48, "关于") { jumpActivity(AboutActivity::class.java) }
                }
                Text(
                    "v" + AppUtils.getAppVersionName(),
                    color = colorScheme.outline,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}
