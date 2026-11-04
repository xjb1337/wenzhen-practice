package com.xjb.wenzhen.ui.screens

import com.xjb.wenzhen.ui.components.bringIntoViewOnFocus
import com.xjb.wenzhen.data.settings.BaseUrlPolicy
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.data.update.InstallLaunchResult
import com.xjb.wenzhen.data.update.UpdateInstaller
import com.xjb.wenzhen.data.update.UpdateInfo
import com.xjb.wenzhen.ui.ConnectionTestUiState
import com.xjb.wenzhen.ui.UpdateUiState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.AppTheme
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import com.xjb.wenzhen.ui.theme.paletteFor

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (apiKey: String, model: String, baseUrl: String, thinkingLevel: ThinkingLevel, generateRecord: Boolean) -> Unit,
    onBack: () -> Unit,
    offlineMode: Boolean = settings.offlineMode,
    onOfflineModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    saveError: String? = null,
    onDismissError: () -> Unit = {},
    onTestConnection: (
        apiKey: String,
        model: String,
        baseUrl: String,
        thinkingLevel: ThinkingLevel,
    ) -> Unit = { _, _, _, _ -> },
    connectionTest: ConnectionTestUiState = ConnectionTestUiState(),
    onCheckUpdate: () -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    onDownloadUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    currentVersionName: String = "1.2.2",
    currentVersionCode: Int = 5,
    initialGuideExpanded: Boolean = false,
    theme: AppTheme = settings.theme,
    onThemeChange: (AppTheme) -> Unit = {},
) {
    val context = LocalContext.current
    val knownModels = listOf(AppSettings.MODEL_FLASH, AppSettings.MODEL_PRO)
    val initialModelIndex = knownModels.indexOf(settings.model).takeIf { it >= 0 } ?: 2
    // 明文 Key 不写进 savedInstanceState（上轮审查 F-03）。
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var selectedModelIndex by rememberSaveable { mutableIntStateOf(initialModelIndex) }
    var customModel by rememberSaveable {
        mutableStateOf(if (initialModelIndex == 2) settings.model else "")
    }
    var baseUrl by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var guideExpanded by rememberSaveable { mutableStateOf(initialGuideExpanded) }
    var thinkingLevel by rememberSaveable { mutableStateOf(settings.thinkingLevel) }
    var generateRecord by rememberSaveable { mutableStateOf(settings.generateRecord) }
    var localOfflineMode by rememberSaveable { mutableStateOf(offlineMode) }

    fun selectedModel(): String = when (selectedModelIndex) {
        0 -> AppSettings.MODEL_FLASH
        1 -> AppSettings.MODEL_PRO
        else -> customModel.trim()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "设置",
                subtitle = "连接 DeepSeek，管理练习与更新",
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                                RoundedCornerShape(22.dp),
                            )
                            .padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "密钥只留在本机",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                        }
                        Text(
                            text = "API Key 使用系统加密存储保存，不会写入日志。问诊内容会发送到你配置的 DeepSeek 服务。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }
                }
            }

            if (saveError != null) {
                item { NoticeBanner(message = saveError, onDismiss = onDismissError) }
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
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
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
                    KeyGuideCard(
                        expanded = guideExpanded,
                        onToggle = { guideExpanded = !guideExpanded },
                        onOpenWebsite = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(DEEPSEEK_PLATFORM_URL))
                                )
                            }.onFailure {
                                Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )

                    ConnectionTestStatus(state = connectionTest)
                    OutlinedButton(
                        onClick = {
                            val model = selectedModel()
                            localError = when {
                                apiKey.isBlank() -> "请先填写 DeepSeek API Key。"
                                selectedModelIndex == 2 && model.isBlank() -> "请填写自定义模型名称。"
                                BaseUrlPolicy.normalizeHttps(baseUrl) == null ->
                                    "Base URL 必须使用 HTTPS（以 https:// 开头）。"
                                else -> null
                            }
                            if (localError == null) {
                                onTestConnection(apiKey, model, baseUrl, thinkingLevel)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connectionTest.isTesting,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        if (connectionTest.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("正在测试连接…")
                        } else {
                            Text("测试连接")
                        }
                    }
                    Text(
                        text = "发送一条 max_tokens=1 的最小请求，只验证 Key、余额、限流和网络状态，不产生问诊内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
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
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
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
                SettingsSection(title = "外观主题") {
                    Text(
                        text = "深浅配色跟随系统自动切换；选择后立即生效并保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppTheme.entries) { option ->
                            val swatch = paletteFor(option, false).primary
                            androidx.compose.material3.FilterChip(
                                selected = theme == option,
                                onClick = { onThemeChange(option) },
                                label = { Text(option.label) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(swatch),
                                    )
                                },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "思考强度") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThinkingLevel.entries.forEach { level ->
                            androidx.compose.material3.FilterChip(
                                selected = thinkingLevel == level,
                                onClick = { thinkingLevel = level },
                                label = { Text(level.label) },
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("生成病历", style = MaterialTheme.typography.titleSmall)
                            Text("结束问诊时默认生成病历", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = generateRecord, onCheckedChange = { generateRecord = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("离线基础训练", style = MaterialTheme.typography.titleSmall)
                            Text("使用内置病例练习，不调用 AI 服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = localOfflineMode,
                            onCheckedChange = {
                                localOfflineMode = it
                                onOfflineModeChange(it)
                            },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val model = selectedModel()
                        when {
                            selectedModelIndex == 2 && model.isBlank() -> {
                                localError = "请填写自定义模型名称。"
                            }
                            BaseUrlPolicy.normalizeHttps(baseUrl) == null -> {
                                localError = "Base URL 必须使用 HTTPS（以 https:// 开头）。"
                            }
                            else -> {
                                localError = null
                                onSave(apiKey, model, baseUrl, thinkingLevel, generateRecord)
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
                SettingsSection(title = "应用更新") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "问诊练习 v$currentVersionName",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "versionCode $currentVersionCode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = onCheckUpdate,
                                    enabled = updateState !is UpdateUiState.Checking &&
                                        updateState !is UpdateUiState.Downloading,
                                ) {
                                    if (updateState is UpdateUiState.Checking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text("检查更新")
                                    }
                                }
                            }
                            val updateStatusText = when (val state = updateState) {
                                UpdateUiState.UpToDate -> "已是最新版本。"
                                is UpdateUiState.Failed -> state.message
                                else -> "从 GitHub Releases 获取更新，下载后校验 SHA-256 与签名证书。"
                            }
                            Text(
                                text = updateStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (updateState) {
                                    is UpdateUiState.Failed -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = BlueSoft.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "请使用自己有权限的 DeepSeek API 账号。应用不会上传联系人、相册或设备文件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    updateDialogUpdate(updateState)?.let { update ->
        val progress = (updateState as? UpdateUiState.Downloading)?.progress
        UpdateDialog(
            update = update,
            progress = progress,
            isDownloaded = updateState is UpdateUiState.Downloaded,
            errorMessage = (updateState as? UpdateUiState.Failed)?.message,
            onDownload = onDownloadUpdate,
            onInstall = {
                val downloaded = updateState as? UpdateUiState.Downloaded
                if (downloaded != null) {
                    when (UpdateInstaller.install(context, downloaded.file)) {
                        InstallLaunchResult.PERMISSION_REQUIRED ->
                            Toast.makeText(context, "请允许本应用安装未知来源应用，然后再次点击安装。", Toast.LENGTH_LONG).show()
                        InstallLaunchResult.FAILED ->
                            Toast.makeText(context, "无法启动安装程序，请稍后重试。", Toast.LENGTH_SHORT).show()
                        InstallLaunchResult.STARTED -> Unit
                    }
                }
            },
            onDismiss = onDismissUpdate,
        )
    }
}

@Composable
private fun KeyGuideCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenWebsite: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TealContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.16f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "如何获取 DeepSeek API Key",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起引导" else "展开引导",
                    tint = TealPrimary,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    listOf(
                        "打开 platform.deepseek.com，完成注册并登录。",
                        "进入账户中心完成充值，确认余额可用。",
                        "在左侧菜单点击 API keys。",
                        "创建一个新的 API key。",
                        "复制以 sk- 开头的 Key；完整内容通常只显示一次。",
                        "回到本页粘贴到 API Key 输入框，保存后可测试连接。",
                    ).forEachIndexed { index, step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.8f),
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TealPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = onOpenWebsite, shape = CircleShape) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("打开 platform.deepseek.com")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTestStatus(state: ConnectionTestUiState) {
    val message = state.message ?: return
    val success = state.isSuccess == true
    val background = if (success) {
        TealContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val foreground = if (success) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (success) TealPrimary else foreground,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
            )
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

private fun updateDialogUpdate(state: UpdateUiState): UpdateInfo? = when (state) {
    is UpdateUiState.Available -> state.update
    is UpdateUiState.Downloading -> state.update
    is UpdateUiState.Downloaded -> state.update
    is UpdateUiState.Failed -> state.update
    else -> null
}

private const val DEEPSEEK_PLATFORM_URL = "https://platform.deepseek.com"
