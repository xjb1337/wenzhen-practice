package com.xjb.wenzhen.ui.screens

import androidx.activity.compose.BackHandler
import com.xjb.wenzhen.ui.components.bringIntoViewOnFocus
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
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
import com.xjb.wenzhen.ui.SettingsExit
import com.xjb.wenzhen.ui.UpdateUiState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.AppTheme
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import com.xjb.wenzhen.ui.theme.WenzhenColors
import com.xjb.wenzhen.ui.theme.paletteFor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (
        apiKey: String,
        model: String,
        baseUrl: String,
        thinkingLevel: ThinkingLevel,
        generateRecord: Boolean,
        exit: SettingsExit?,
    ) -> Unit,
    isSaving: Boolean,
    exitRequest: SettingsExit?,
    onRequestExit: (SettingsExit?) -> Unit,
    onExit: (SettingsExit) -> Unit,
    modifier: Modifier = Modifier,
    offlineMode: Boolean = settings.offlineMode,
    offlineModeSaving: Boolean = false,
    onOfflineModeChange: (Boolean) -> Unit = {},
    temporarySettings: Boolean = false,
    onRetrySettings: () -> Unit = {},
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
    val initialModelIndex = knownModels.indexOf(settings.model).takeIf { it >= 0 } ?: 0
    // 明文 Key 不写进 savedInstanceState（上轮审查 F-03）。
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var selectedModelIndex by rememberSaveable { mutableIntStateOf(initialModelIndex) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var guideExpanded by rememberSaveable { mutableStateOf(initialGuideExpanded) }
    var thinkingLevel by rememberSaveable { mutableStateOf(settings.thinkingLevel) }
    var generateRecord by rememberSaveable { mutableStateOf(settings.generateRecord) }

    fun selectedModel(): String = knownModels.getOrElse(selectedModelIndex) { AppSettings.MODEL_FLASH }

    val normalizedSavedModel = if (settings.model in knownModels) settings.model else AppSettings.MODEL_FLASH
    val draftModel = selectedModel()
    val hasUnsavedChanges = apiKey.trim() != settings.apiKey.trim() ||
        generateRecord != settings.generateRecord ||
        thinkingLevel != settings.thinkingLevel ||
        draftModel != normalizedSavedModel

    val canSave = !isSaving && !temporarySettings

    fun save(exit: SettingsExit? = null) {
        if (!canSave) return
        localError = null
        onRequestExit(null)
        onSave(apiKey, selectedModel(), AppSettings.DEFAULT_BASE_URL, thinkingLevel, generateRecord, exit)
    }

    val handleBack: () -> Unit = {
        if (!isSaving) onRequestExit(SettingsExit.Back)
    }
    BackHandler(enabled = isSaving || hasUnsavedChanges, onBack = handleBack)

    LaunchedEffect(exitRequest, hasUnsavedChanges, isSaving) {
        if (exitRequest != null && !hasUnsavedChanges && !isSaving) onExit(exitRequest)
    }
    if (exitRequest != null && hasUnsavedChanges) {
        AlertDialog(
            onDismissRequest = { onRequestExit(null) },
            title = { Text("有未保存的设置") },
            text = { Text("你修改了 AI 服务或生成选项但尚未保存，是否先保存再离开？") },
            confirmButton = {
                Button(onClick = { save(exitRequest) }, enabled = canSave) {
                    Text("保存并离开")
                }
            },
            dismissButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onRequestExit(null) }) {
                        Text("继续编辑")
                    }
                    TextButton(onClick = { onExit(exitRequest) }) {
                        Text("放弃更改", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "设置",
                subtitle = when {
                    isSaving -> "正在保存设置…"
                    hasUnsavedChanges -> "有未保存的更改"
                    else -> null
                },
                onBack = handleBack,
                actions = {
                    if (hasUnsavedChanges && !isSaving) {
                        TextButton(onClick = { save() }, enabled = canSave) {
                            Text("保存", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
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
            if (temporarySettings) {
                item {
                    NoticeBanner(
                        message = "本机加密设置暂时无法读取。本次使用离线练习。",
                        actionLabel = "重新读取",
                        onAction = onRetrySettings,
                    )
                }
            }
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
                                text = "密钥仅保存在本机",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                        }
                        Text(
                            text = "API Key 使用系统加密存储",
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
                SettingsSection(title = "AI 服务") {
                    val activeSummary = when {
                        offlineMode -> "当前生效：离线基础训练（不调用 AI 服务）"
                        settings.apiKey.isNotBlank() -> "当前生效：${settings.model}"
                        else -> "当前生效：待填写 API Key"
                    }
                    Text(
                        text = activeSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = TealPrimary,
                    )

                    if (settings.legacyEndpointNeedsKey) {
                        NoticeBanner(message = settings.aiSetupMessage, isError = false)
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        readOnly = isSaving,
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
                            if (apiKey.isBlank()) {
                                localError = "请先填写 DeepSeek API Key。"
                            } else {
                                localError = null
                                onTestConnection(
                                    apiKey,
                                    selectedModel(),
                                    AppSettings.DEFAULT_BASE_URL,
                                    thinkingLevel,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !offlineMode && !offlineModeSaving && !isSaving && !connectionTest.isTesting,
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
                }
            }

            item {
                SettingsSection(title = "模型", modifier = Modifier.selectableGroup()) {
                    ModelOption(
                        title = "DeepSeek V4 Flash",
                        description = "响应更快，适合日常问诊练习",
                        selected = selectedModelIndex == 0,
                        enabled = !isSaving,
                        onClick = { selectedModelIndex = 0 },
                    )
                    ModelOption(
                        title = "DeepSeek V4 Pro",
                        description = "推理更充分，适合复杂病例复盘",
                        selected = selectedModelIndex == 1,
                        enabled = !isSaving,
                        onClick = { selectedModelIndex = 1 },
                    )
                }
            }

            item {
                SettingsSection(title = "思考强度") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThinkingLevel.entries.forEach { level ->
                            FilterChip(
                                selected = thinkingLevel == level,
                                enabled = !isSaving,
                                onClick = { thinkingLevel = level },
                                label = { Text(level.label) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "外观主题") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(AppTheme.entries) { option ->
                            ThemePreviewCard(
                                theme = option,
                                selected = theme == option,
                                onClick = { onThemeChange(option) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "练习选项") {
                    SettingsToggle(
                        title = "生成病历",
                        description = "结束问诊时默认生成病历",
                        checked = generateRecord,
                        onCheckedChange = { generateRecord = it },
                        enabled = !isSaving,
                    )
                    SettingsToggle(
                        title = "离线基础训练",
                        description = if (offlineModeSaving) "正在保存练习模式…" else "使用内置病例练习，不调用 AI 服务",
                        checked = offlineMode,
                        onCheckedChange = onOfflineModeChange,
                        enabled = !temporarySettings && !offlineModeSaving,
                    )
                }
            }

            item {
                Button(
                    onClick = { save() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (isSaving) "正在保存…" else "保存设置", fontWeight = FontWeight.SemiBold)
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
                                Text(
                                    text = "问诊练习 v$currentVersionName ($currentVersionCode)",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
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
                                else -> null
                            }
                            if (updateStatusText != null) {
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
                                color = MaterialTheme.colorScheme.surface,
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
private fun ThemePreviewCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val light = paletteFor(theme, false)
    val dark = paletteFor(theme, true)
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) light.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniThemePreview(palette = light, modifier = Modifier.weight(1f))
            MiniThemePreview(palette = dark, modifier = Modifier.weight(1f))
        }
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (selected) "正在使用" else "浅色 / 深色",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) light.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniThemePreview(
    palette: WenzhenColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.canvas),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(Brush.horizontalGradient(listOf(palette.gradientStart, palette.gradientEnd))),
        )
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.78f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(palette.ink.copy(alpha = 0.8f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(palette.inkMuted.copy(alpha = 0.7f)),
            )
            Box(
                Modifier
                    .width(22.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.primary),
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
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
            RadioButton(selected = selected, onClick = null, enabled = enabled)
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
