package com.xjb.wenzhen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xjb.wenzhen.ui.AppViewModel
import com.xjb.wenzhen.ui.WenzhenApp
import com.xjb.wenzhen.ui.theme.WenzhenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WenzhenApplication).container
        setContent {
            WenzhenTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModel.factory(container),
                )
                WenzhenApp(viewModel = appViewModel)
            }
        }
    }
}
