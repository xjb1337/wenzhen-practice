package com.xjb.wenzhen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.TealContainer

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (apiKey: String, model: String, baseUrl: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    saveError: String? = null,
    onDismissError: () -> Unit = {},
) {
    val knownModels = listOf(AppSettings.MODEL_FLASH, AppSettings.MODEL_PRO)
    val initialModelIndex = knownModels.indexOf(settings.model).takeIf { it >= 0 } ?: 2
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var selectedModelIndex by rememberSaveable { mutableIntStateOf(initialModelIndex) }
    var customModel by rememberSaveable {
        mutableStateOf(if (initialModelIndex == 2) settings.model else "")
    }
    var baseUrl by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "设置",
                subtitle = "连接你的 DeepSeek 服务",
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = TealContainer.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                        Column {
                            Text(
                                text = "密钥只留在本机",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "API Key 使用系统加密存储保存，不会写入日志。问诊内容会发送到你配置的 DeepSeek 服务。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (saveError != null) {
                item {
                    NoticeBanner(message = saveError, onDismiss = onDismissError)
                }
            }
            if (localError != null) {
                item {
                    NoticeBanner(message = localError.orEmpty(), onDismiss = { localError = null })
                }
            }

            item {
                SettingsSection(title = "DeepSeek API Key") {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        placeholder = { Text("sk-…") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (showKey) "隐藏 API Key" else "显示 API Key",
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                if (apiKey.isBlank()) {
                                    "未设置时无法使用 AI 对话、病例生成和评分功能"
                                } else {
                                    "密钥已遮罩显示，点击右侧图标可临时查看"
                                }
                            )
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "模型") {
                    ModelOption(
                        title = "DeepSeek V4 Flash",
                        description = "响应更快，适合日常问诊练习",
                        selected = selectedModelIndex == 0,
                        onClick = { selectedModelIndex = 0 },
                    )
                    ModelOption(
                        title = "DeepSeek V4 Pro",
                        description = "推理更充分，适合复杂病例复盘",
                        selected = selectedModelIndex == 1,
                        onClick = { selectedModelIndex = 1 },
                    )
                    ModelOption(
                        title = "自定义模型",
                        description = "填写服务端支持的模型名称",
                        selected = selectedModelIndex == 2,
                        onClick = { selectedModelIndex = 2 },
                    )
                    if (selectedModelIndex == 2) {
                        OutlinedTextField(
                            value = customModel,
                            onValueChange = { customModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模型名称") },
                            placeholder = { Text("例如 deepseek-chat") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "接口地址") {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL") },
                        placeholder = { Text(AppSettings.DEFAULT_BASE_URL) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = {
                            Text("将调用 /chat/completions，默认地址为 ${AppSettings.DEFAULT_BASE_URL}")
                        },
                    )
                    TextButton(
                        onClick = { baseUrl = AppSettings.DEFAULT_BASE_URL },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("恢复默认地址")
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val model = when (selectedModelIndex) {
                            0 -> AppSettings.MODEL_FLASH
                            1 -> AppSettings.MODEL_PRO
                            else -> customModel.trim()
                        }
                        when {
                            selectedModelIndex == 2 && model.isBlank() -> {
                                localError = "请填写自定义模型名称。"
                            }
                            !baseUrl.trim().startsWith("https://") &&
                                !baseUrl.trim().startsWith("http://") -> {
                                localError = "Base URL 必须以 http:// 或 https:// 开头。"
                            }
                            else -> {
                                localError = null
                                onSave(apiKey, model, baseUrl)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("保存设置", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Text(
                    text = "提示：请使用自己有权限的 DeepSeek API 账号。应用不会上传联系人、相册或设备文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        content()
    }
}

@Composable
private fun ModelOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
