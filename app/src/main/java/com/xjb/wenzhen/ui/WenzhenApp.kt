package com.xjb.wenzhen.ui

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xjb.wenzhen.ui.screens.CaseLibraryScreen
import com.xjb.wenzhen.ui.screens.ChatScreen
import com.xjb.wenzhen.ui.screens.FinishConsultationScreen
import com.xjb.wenzhen.ui.screens.HistoryDetailScreen
import com.xjb.wenzhen.ui.screens.HistoryScreen
import com.xjb.wenzhen.ui.screens.SettingsScreen
import com.xjb.wenzhen.ui.screens.StatsScreen
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.TealPrimary
import kotlinx.coroutines.launch

private const val CONSULTATION_STARTED_AT = "consultationStartedAt"
private const val RESULT_RECORD_ID = "resultRecordId"

private object Routes {
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CHAT = "chat"
    const val FINISH = "finish"
    const val RESULT = "result"
    const val HISTORY_DETAIL = "history/{recordId}"

    fun historyDetail(id: Long): String = "history/$id"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.LIBRARY, "病例库", Icons.Outlined.LocalHospital),
    BottomDestination(Routes.HISTORY, "历史", Icons.Outlined.History),
    BottomDestination(Routes.STATS, "统计", Icons.Outlined.BarChart),
    BottomDestination(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
)

@Composable
fun WenzhenApp(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }
    var settingsExitRequest by remember { mutableStateOf<SettingsExit?>(null) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.SETTINGS) settingsExitRequest = null
    }

    fun exitSettings(exit: SettingsExit) {
        settingsExitRequest = null
        when (exit) {
            SettingsExit.Back -> navController.popBackStack()
            is SettingsExit.Tab -> navigateToRoot(navController, exit.route, saveCurrentState = false)
        }
    }

    ReportDrawnWhen { uiState.settingsRead != SettingsReadState.Loading && !uiState.draftLoading }
    when (val read = uiState.settingsRead) {
        SettingsReadState.Loading -> {
            Scaffold { padding ->
                LoadingState(message = "正在读取设置…", modifier = Modifier.fillMaxSize().padding(padding))
            }
            return
        }
        is SettingsReadState.Unavailable -> if (!read.temporaryOffline) {
            Scaffold { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Settings,
                        title = "暂时无法读取本机设置",
                        description = "可以重新读取，或本次使用离线练习与历史记录。",
                        actionLabel = "重新读取",
                        onAction = viewModel::reloadSettings,
                    )
                    TextButton(onClick = viewModel::continueOfflineWithoutSettings) {
                        Text("本次离线使用")
                    }
                }
            }
            return
        }
        SettingsReadState.Ready -> Unit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppEvent.ShowMessage -> {
                    snackbarScope.launch { snackbarHostState.showSnackbar(event.message) }
                }
                is AppEvent.OpenChat -> {
                    if (viewModel.uiState.value.consultationStartedAt == event.startedAt) {
                        navController.navigate(Routes.CHAT) { launchSingleTop = true }
                        navController.currentBackStackEntry?.savedStateHandle
                            ?.set(CONSULTATION_STARTED_AT, event.startedAt)
                    }
                }
                is AppEvent.OpenResult -> {
                    if (viewModel.uiState.value.currentResult?.recordId == event.recordId) {
                        navController.navigate(Routes.RESULT) {
                            popUpTo(Routes.CHAT) { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.currentBackStackEntry?.savedStateHandle
                            ?.set(RESULT_RECORD_ID, event.recordId)
                    }
                }
                AppEvent.OpenSettings -> navController.navigate(Routes.SETTINGS) {
                    launchSingleTop = true
                }
                is AppEvent.SettingsSaved -> {
                    if (event.exit != null && navController.currentDestination?.route == Routes.SETTINGS) {
                        exitSettings(event.exit)
                    }
                    snackbarScope.launch { snackbarHostState.showSnackbar("设置已保存") }
                }
            }
        }
    }

    if (!uiState.settings.privacyAccepted) {
        AlertDialog(
            onDismissRequest = { /* 必须明确同意后才能使用 */ },
            title = { Text("隐私与 AI 使用说明") },
            text = {
                Text(
                    "本应用用于医学生问诊练习。问诊对话、病例生成、查体/检查请求、病历和评分会发送到你配置的 AI 服务；" +
                        "AI 生成内容可能有误，不构成医疗建议。请勿输入真实患者身份信息或敏感个人资料。",
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::acceptPrivacyNotice,
                    enabled = !uiState.acceptingPrivacyNotice,
                ) {
                    Text(if (uiState.acceptingPrivacyNotice) "正在保存…" else "同意并继续")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                WenzhenBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute == Routes.SETTINGS) {
                            if (route != Routes.SETTINGS && !uiState.settingsFormSaving) {
                                settingsExitRequest = SettingsExit.Tab(route)
                            }
                        } else {
                            navigateToRoot(navController, route)
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.LIBRARY) {
                CaseLibraryScreen(
                    cases = uiState.filteredCases,
                    allCases = uiState.cases,
                    selectedDepartment = uiState.selectedDepartment,
                    hasApiKey = uiState.hasApiKey,
                    canPractice = uiState.canPractice,
                    offlineMode = uiState.offlineMode,
                    onEnableOfflinePractice = { viewModel.setOfflineMode(true) },
                    onDepartmentSelected = viewModel::selectDepartment,
                    onCaseSelected = viewModel::openCase,
                    savedCaseIds = uiState.savedCases.filter { it.caseJson.isNotBlank() }.map { it.id }.toSet(),
                    favoriteCaseIds = uiState.savedCases.filter { it.favorite }.map { it.id }.toSet(),
                    onFavoriteChange = viewModel::setCaseFavorite,
                    onDeleteSavedCase = viewModel::deleteSavedCase,
                    onGenerateCase = viewModel::generateCase,
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    },
                    onClearGenerationError = viewModel::clearGenerationError,
                    draftSummary = uiState.draft?.let {
                        val typeLabel = when (it.encounterType) {
                            "INPATIENT" -> "住院病人"
                            else -> "门诊病人"
                        }
                        val timeLabel = android.text.format.DateFormat.format("MM-dd HH:mm", it.updatedAt)
                        "$typeLabel · 上次保存于 $timeLabel"
                    },
                    onResumeDraft = viewModel::resumeDraft,
                    onDiscardDraft = viewModel::discardDraft,
                    isGenerating = uiState.isGeneratingCase,
                    generationError = uiState.generationError,
                    isLoading = uiState.draftLoading,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    records = uiState.history,
                    isLoading = uiState.historyLoading,
                    errorMessage = uiState.historyError,
                    onRecordClick = { recordId ->
                        navController.navigate(Routes.historyDetail(recordId))
                    },
                    onRetry = viewModel::retryHistory,
                    query = uiState.historyQuery,
                    onQueryChange = viewModel::setHistoryQuery,
                    onDeleteRecord = viewModel::deleteHistory,
                    onClearAll = viewModel::clearHistory,
                    transfer = uiState.historyTransfer,
                    onRestore = viewModel::restoreHistory,
                    onSaveBackup = viewModel::saveHistoryBackup,
                    loadForExport = viewModel::loadHistoryForExport,
                    loadDuration = viewModel::loadHistoryDuration,
                    onBrowseCases = { navigateToRoot(navController, Routes.LIBRARY) },
                )
            }
            composable(Routes.STATS) {
                val stats by viewModel.statsState.collectAsStateWithLifecycle()
                StatsScreen(
                    state = stats,
                    onRetry = viewModel::retryStats,
                    onDepartmentClick = { department ->
                        viewModel.setHistoryQuery(department)
                        navController.navigate(Routes.HISTORY) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = uiState.settings,
                    onSave = viewModel::saveSettings,
                    isSaving = uiState.settingsFormSaving,
                    exitRequest = settingsExitRequest,
                    onRequestExit = { settingsExitRequest = it },
                    onExit = ::exitSettings,
                    offlineMode = uiState.offlineMode,
                    offlineModeSaving = uiState.pendingOfflineMode != null,
                    onOfflineModeChange = viewModel::setOfflineMode,
                    temporarySettings = uiState.settingsRead is SettingsReadState.Unavailable,
                    onRetrySettings = viewModel::reloadSettings,
                    saveError = uiState.settingsSaveError,
                    onDismissError = viewModel::clearSettingsSaveError,
                    onTestConnection = viewModel::testConnection,
                    connectionTest = uiState.connectionTest,
                    onCheckUpdate = viewModel::checkForUpdate,
                    updateState = uiState.updateState,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onDismissUpdate = viewModel::dismissUpdateDialog,
                    currentVersionName = uiState.currentVersionName,
                    currentVersionCode = uiState.currentVersionCode,
                    theme = uiState.settings.theme,
                    onThemeChange = viewModel::setTheme,
                )
            }
            composable(Routes.CHAT) { entry ->
                val medicalCase = uiState.activeCase
                if (medicalCase == null) {
                    RestoreConsultationScreen(
                        startedAt = entry.savedStateHandle[CONSULTATION_STARTED_AT],
                        uiState = uiState,
                        onRestore = viewModel::restoreConsultation,
                        title = "问诊不可用",
                        description = "当前没有进行中的病例，请返回病例库重新开始。",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    ChatScreen(
                        medicalCase = medicalCase,
                        messages = uiState.messages,
                        isPatientTyping = uiState.isPatientTyping,
                        canPractice = uiState.canPractice,
                        offlineMode = uiState.offlineMode,
                        errorMessage = uiState.chatError,
                        onSendMessage = viewModel::sendMessage,
                        onFinish = {
                            navController.navigate(Routes.FINISH) { launchSingleTop = true }
                            navController.currentBackStackEntry?.savedStateHandle
                                ?.set(CONSULTATION_STARTED_AT, uiState.consultationStartedAt)
                        },
                        onBack = {
                            viewModel.flushDraft()
                            navController.popBackStack()
                        },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                        onDismissError = viewModel::clearChatError,
                        onRetryMessage = viewModel::retryFailedMessage,
                        onRequestClinical = viewModel::requestClinical,
                        isQueryingClinical = uiState.isQueryingClinical,
                        encounterType = uiState.encounterType,
                        startedAtMillis = uiState.consultationStartedAt ?: System.currentTimeMillis(),
                        lastReplyMillis = uiState.lastReplyMillis,
                        lastReplyTokens = uiState.lastReplyTokens,
                        requestNotice = uiState.requestNotice,
                        contextWarning = uiState.contextWarning,
                        onCancelRequest = viewModel::cancelCurrentRequest,
                        onDismissNotice = viewModel::clearRequestNotice,
                    )
                }
            }
            composable(Routes.FINISH) { entry ->
                val medicalCase = uiState.activeCase
                if (medicalCase == null) {
                    RestoreConsultationScreen(
                        startedAt = entry.savedStateHandle[CONSULTATION_STARTED_AT],
                        uiState = uiState,
                        onRestore = viewModel::restoreConsultation,
                        title = "无法结束问诊",
                        description = "当前病例已不可用，请返回病例库重新开始。",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    FinishConsultationScreen(
                        caseTitle = medicalCase.title,
                        patientName = medicalCase.patient.name,
                        department = medicalCase.department,
                        onBack = {
                            viewModel.flushDraft()
                            navController.popBackStack()
                        },
                        onSubmit = viewModel::finishConsultation,
                        initialGenerateRecord = uiState.settings.generateRecord,
                        thinkingLevel = uiState.settings.toLlmConfig().thinkingLevel,
                        isSubmitting = uiState.isFinishing,
                        errorMessage = uiState.finishError,
                        onDismissError = viewModel::clearFinishError,
                    )
                }
            }
            composable(Routes.RESULT) { entry ->
                val recordId: Long? = entry.savedStateHandle[RESULT_RECORD_ID]
                val liveResult = uiState.currentResult?.takeIf { recordId == null || it.recordId == recordId }
                if (liveResult == null && recordId == null) {
                    UnavailableScreen(
                        title = "结果暂不可用",
                        description = "可到历史记录中查看已保存的问诊结果。",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    ConsultationResult(
                        recordId = recordId ?: liveResult?.recordId,
                        liveResult = liveResult,
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.HISTORY_DETAIL) { entry ->
                val recordId = entry.arguments?.getString("recordId")?.toLongOrNull()
                ConsultationResult(
                    recordId = recordId,
                    uiState = uiState,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun ConsultationResult(
    recordId: Long?,
    uiState: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    liveResult: ResultViewData? = null,
) {
    LaunchedEffect(recordId, liveResult == null) {
        if (recordId != null && liveResult == null) viewModel.loadHistoryDetail(recordId)
    }

    val record = liveResult ?: uiState.historyDetail?.takeIf { it.recordId == recordId }
    HistoryDetailScreen(
        record = record,
        isLoading = uiState.historyDetailLoading,
        errorMessage = uiState.historyDetailError,
        onRetry = { if (recordId != null) viewModel.loadHistoryDetail(recordId) },
        onBack = onBack,
        onRegenerateAiComment = record?.takeIf { it.recordId > 0L }?.let {
            { viewModel.regenerateAiComment(it.recordId) }
        },
        isRegeneratingAiComment = record != null && uiState.regeneratingCommentId == record.recordId,
    )
}

@Composable
private fun WenzhenBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        bottomDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = TealPrimary,
                    selectedTextColor = TealPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun RestoreConsultationScreen(
    startedAt: Long?,
    uiState: AppUiState,
    onRestore: (Long) -> Unit,
    title: String,
    description: String,
    onBack: () -> Unit,
) {
    val canRestore = startedAt != null && uiState.draft?.startedAt == startedAt
    var attempted by remember(startedAt) { mutableStateOf(false) }
    LaunchedEffect(startedAt, uiState.draftLoading, canRestore) {
        if (!uiState.draftLoading && canRestore && startedAt != null && !attempted) {
            onRestore(startedAt)
            attempted = true
        }
    }
    if (startedAt != null && (uiState.draftLoading || (canRestore && !attempted))) {
        LoadingState(message = "正在恢复问诊…", modifier = Modifier.fillMaxSize())
    } else {
        UnavailableScreen(title, description, onBack)
    }
}

@Composable
private fun UnavailableScreen(
    title: String,
    description: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { WenzhenTopBar(title = title, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        EmptyState(
            icon = Icons.Outlined.LocalHospital,
            title = title,
            description = description,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            actionLabel = "返回",
            onAction = onBack,
        )
    }
}

private fun navigateToRoot(navController: NavHostController, route: String, saveCurrentState: Boolean = true) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = saveCurrentState
        }
        launchSingleTop = true
        restoreState = true
    }
}
