package com.xjb.wenzhen.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.ui.ResultViewData
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.WenzhenTopBar

@Composable
fun HistoryDetailScreen(
    record: ResultViewData?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        record != null -> ResultScreen(
            caseTitle = record.caseTitle,
            patientName = record.patientName,
            department = record.department,
            difficulty = record.difficulty,
            createdAt = record.createdAt,
            medicalRecord = record.medicalRecord,
            score = record.score,
            initialDiagnosis = record.initialDiagnosis,
            messages = record.messages,
            onBack = onBack,
            modifier = modifier,
        )
        else -> Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = { WenzhenTopBar(title = "问诊详情", onBack = onBack) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            if (isLoading) {
                LoadingState(
                    message = "正在读取问诊详情…",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "无法查看这条记录",
                    description = errorMessage ?: "问诊记录不存在或已被删除。",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp),
                    actionLabel = "重新加载",
                    onAction = onRetry,
                )
            }
        }
    }
}
