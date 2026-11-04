package com.xjb.wenzhen.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.xjb.wenzhen.ui.screens.ResultScreen
import com.xjb.wenzhen.ui.screens.SettingsScreen
import com.xjb.wenzhen.ui.screens.StatsScreen
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.TealPrimary

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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                AppEvent.OpenChat -> navController.navigate(Routes.CHAT) {
                    launchSingleTop = true
                }
                AppEvent.OpenResult -> navController.navigate(Routes.RESULT) {
                    popUpTo(Routes.CHAT) { inclusive = true }
                    launchSingleTop = true
                }
                AppEvent.OpenSettings -> navController.navigate(Routes.SETTINGS) {
                    launchSingleTop = true
                }
                AppEvent.SettingsSaved -> {
                    snackbarHostState.showSnackbar("设置已保存")
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                WenzhenBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navigateToRoot(navController, route) },
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
                    builtInCount = uiState.builtInCaseCount,
                    onDepartmentSelected = viewModel::selectDepartment,
                    onCaseSelected = viewModel::openCase,
                    savedCaseIds = uiState.savedCases.map { it.id }.toSet(),
                    favoriteCaseIds = uiState.savedCases.filter { it.favorite }.map { it.id }.toSet(),
                    onToggleFavorite = viewModel::toggleSavedCaseFavorite,
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
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    records = uiState.filteredHistory,
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
                    onRestore = viewModel::restoreHistory,
                    onBrowseCases = { navigateToRoot(navController, Routes.LIBRARY) },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    stats = uiState.stats,
                    isLoading = uiState.historyLoading,
                    errorMessage = uiState.historyError,
                    onRetry = viewModel::retryHistory,
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
                    onBack = { navController.popBackStack() },
                    offlineMode = uiState.settings.offlineMode,
                    onOfflineModeChange = viewModel::setOfflineMode,
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
            composable(Routes.CHAT) {
                val medicalCase = uiState.activeCase
                if (medicalCase == null) {
                    UnavailableScreen(
                        title = "问诊不可用",
                        description = "当前没有进行中的病例，请返回病例库重新开始。",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    ChatScreen(
                        medicalCase = medicalCase,
                        messages = uiState.messages,
                        isPatientTyping = uiState.isPatientTyping,
                        hasApiKey = uiState.canPractice,
                        errorMessage = uiState.chatError,
                        onSendMessage = viewModel::sendMessage,
                        onFinish = {
                            navController.navigate(Routes.FINISH) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() },
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
            composable(Routes.FINISH) {
                val medicalCase = uiState.activeCase
                if (medicalCase == null) {
                    UnavailableScreen(
                        title = "无法结束问诊",
                        description = "当前病例已不可用，请返回病例库重新开始。",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    FinishConsultationScreen(
                        caseTitle = medicalCase.title,
                        patientName = medicalCase.patient.name,
                        department = medicalCase.department,
                        onBack = { navController.popBackStack() },
                        onSubmit = viewModel::finishConsultation,
                        initialGenerateRecord = uiState.settings.generateRecord,
                        isSubmitting = uiState.isFinishing,
                        errorMessage = uiState.finishError,
                        onDismissError = viewModel::clearFinishError,
                    )
                }
            }
            composable(Routes.RESULT) {
                val result = uiState.currentResult
                if (result == null) {
                    UnavailableScreen(
                        title = "结果暂不可用",
                        description = "本次问诊结果没有找到，请到历史记录中查看已保存的内容。",
                        onBack = {
                            navController.navigate(Routes.LIBRARY) {
                                popUpTo(Routes.LIBRARY) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                } else {
                    ResultScreen(
                        caseTitle = result.caseTitle,
                        patientName = result.patientName,
                        department = result.department,
                        difficulty = result.difficulty,
                        createdAt = result.createdAt,
                        medicalRecord = result.medicalRecord,
                        score = result.score,
                        initialDiagnosis = result.initialDiagnosis,
                        messages = result.messages,
                        notice = result.notice,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.HISTORY_DETAIL) { entry ->
                val recordId = entry.arguments?.getString("recordId")?.toLongOrNull()
                LaunchedEffect(recordId) {
                    if (recordId != null) {
                        viewModel.loadHistoryDetail(recordId)
                    }
                }
                val visibleRecord = uiState.historyDetail
                    ?.takeIf { it.recordId == recordId }
                HistoryDetailScreen(
                    record = visibleRecord,
                    isLoading = uiState.historyDetailLoading,
                    errorMessage = uiState.historyDetailError,
                    onRetry = {
                        if (recordId != null) viewModel.loadHistoryDetail(recordId)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
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

private fun navigateToRoot(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
