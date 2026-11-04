package com.xjb.wenzhen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjb.wenzhen.ui.AppViewModel
import com.xjb.wenzhen.ui.WenzhenApp
import com.xjb.wenzhen.ui.theme.WenzhenTheme

class MainActivity : ComponentActivity() {
    /** 仅用于 onStop 时立即落盘草稿。 */
    private var appViewModel: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WenzhenApplication).container
        setContent {
            // ViewModel 必须在 WenzhenTheme 之外创建，否则切换主题会重建整个 ViewModel。
            val vm: AppViewModel = viewModel(
                factory = AppViewModel.factory(container),
            )
            appViewModel = vm
            // StateFlow.collectAsState() 在组合期订阅并以当前值作为初值，
            // 再用 derivedStateOf 只把 theme 变化向下传递：问诊/历史等其它状态
            // 变化不会触发 WenzhenTheme 重组（也避免在组合里直接读 .value 触发 lint）。
            val uiState by vm.uiState.collectAsState()
            val theme by remember { derivedStateOf { uiState.settings.theme } }
            WenzhenTheme(theme = theme) {
                WenzhenApp(viewModel = vm)
            }
        }
    }

    override fun onStop() {
        // 退到后台即落盘草稿：跳过 350ms 防抖，避免进程被回收时丢掉最后一轮对话。
        appViewModel?.flushDraft()
        super.onStop()
    }
}
