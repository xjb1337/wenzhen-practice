package com.xjb.wenzhen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.domain.CoverageAnalyzer
import com.xjb.wenzhen.domain.CoverageReport
import com.xjb.wenzhen.domain.TopicCoverage
import com.xjb.wenzhen.ui.components.DepartmentMark
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    medicalCase: MedicalCase,
    messages: List<ChatMessage>,
    isPatientTyping: Boolean,
    hasApiKey: Boolean,
    errorMessage: String?,
    onSendMessage: (String) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryMessage: () -> Unit = {},
    onRequestClinical: (ClinicalRequestType, String) -> Unit = { _, _ -> },
    isQueryingClinical: Boolean = false,
    startedAtMillis: Long = System.currentTimeMillis(),
    coverageInitiallyExpanded: Boolean = false,
) {
    val listState = rememberLazyListState()
    val showTyping = isPatientTyping && messages.lastOrNull()?.let {
        it.role != MessageRole.PATIENT || it.deliveryState != MessageDeliveryState.SENDING
    } != false
    val messageCount = messages.size + if (showTyping) 1 else 0
    var elapsedSeconds by remember(startedAtMillis) { mutableLongStateOf(0L) }
    var coverageExpanded by rememberSaveable { mutableStateOf(coverageInitiallyExpanded) }
    var clinicalType by remember { mutableStateOf<ClinicalRequestType?>(null) }
    val coverage = remember(messages) { CoverageAnalyzer.analyze(messages) }
    val timer = formatElapsed(elapsedSeconds)

    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount)
        }
    }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).coerceAtLeast(0L)
            delay(1_000)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = medicalCase.title,
                subtitle = "${medicalCase.department} · ${medicalCase.patient.name} · ${medicalCase.patient.age} · $timer",
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = onFinish,
                        enabled = !isPatientTyping && !isQueryingClinical,
                    ) {
                        Text("结束问诊", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
        bottomBar = {
            ChatComposer(
                enabled = hasApiKey && !isPatientTyping && !isQueryingClinical,
                isBusy = isPatientTyping || isQueryingClinical,
                onSend = onSendMessage,
                onRequestExam = {
                    if (hasApiKey) clinicalType = ClinicalRequestType.PHYSICAL_EXAM else onOpenSettings()
                },
                onRequestLab = {
                    if (hasApiKey) clinicalType = ClinicalRequestType.LAB_OR_IMAGING else onOpenSettings()
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!hasApiKey) {
                NoticeBanner(
                    message = "尚未配置 API Key，无法获得患者回复。",
                    actionLabel = "去设置",
                    onAction = onOpenSettings,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
            CoverageHintBar(
                report = coverage,
                timer = timer,
                expanded = coverageExpanded,
                onToggle = { coverageExpanded = !coverageExpanded },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PatientSummaryCard(medicalCase = medicalCase)
                }
                if (messages.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "开始你的问诊",
                            description = "从主诉、起病时间和伴随症状开始，逐步了解患者。",
                        )
                    }
                }
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = onRetryMessage,
                    )
                }
                if (showTyping || isQueryingClinical) {
                    item {
                        TypingBubble(
                            label = if (isQueryingClinical) "正在整理检查结果…" else "患者正在输入…",
                        )
                    }
                }
            }
            if (errorMessage != null) {
                NoticeBanner(
                    message = errorMessage,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }

    clinicalType?.let { type ->
        ClinicalRequestDialog(
            requestType = type,
            onDismiss = { clinicalType = null },
            onConfirm = { item ->
                clinicalType = null
                onRequestClinical(type, item)
            },
        )
    }
}

@Composable
private fun CoverageHintBar(
    report: CoverageReport,
    timer: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = TealContainer, shape = CircleShape) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = timer,
                            style = MaterialTheme.typography.labelMedium,
                            color = TealPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "问诊提示 · 已覆盖 ${report.coveredCount}/7",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (!expanded) {
                        Text(
                            text = if (report.missing.isEmpty()) {
                                "七个方面均已问及"
                            } else {
                                "尚未问及：${report.missing.joinToString("、") { it.label }}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起问诊提示" else "展开问诊提示",
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(report.topics) { topic ->
                        TopicChip(topic)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicChip(topic: TopicCoverage) {
    val background = if (topic.covered) TealContainer else MaterialTheme.colorScheme.errorContainer
    val foreground = if (topic.covered) TealPrimary else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = CircleShape, color = background) {
        Text(
            text = "${if (topic.covered) "✓" else "○"} ${topic.topic.label}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PatientSummaryCard(medicalCase: MedicalCase) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(TealPrimary, TealSecondary)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = medicalCase.patient.name.take(1),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = medicalCase.patient.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.size(7.dp))
                    Text(
                        text = medicalCase.patientSummary.removePrefix(medicalCase.patient.name + " · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "主诉通常需要从症状、时间和伴随表现逐步追问",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DepartmentMark(department = medicalCase.department, size = 38)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
) {
    if (message.role == MessageRole.SYSTEM) {
        SystemResultCard(message = message)
        return
    }
    val isDoctor = message.role == MessageRole.DOCTOR
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .align(if (isDoctor) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalAlignment = if (isDoctor) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = if (isDoctor) "你" else "患者",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            )
            Surface(
                shape = if (isDoctor) {
                    RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp)
                } else {
                    RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp)
                },
                color = if (isDoctor) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = if (isDoctor) null else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDoctor) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            when (message.deliveryState) {
                MessageDeliveryState.FAILED -> TextButton(onClick = onRetry) {
                    Text("发送失败，重试", color = MaterialTheme.colorScheme.error)
                }
                MessageDeliveryState.SENDING -> if (message.role == MessageRole.DOCTOR) {
                    Text(
                        text = "发送中…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                }
                MessageDeliveryState.SENT -> Unit
            }
        }
    }
}

@Composable
private fun SystemResultCard(message: ChatMessage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = BlueSoft,
        border = BorderStroke(1.dp, TealSecondary.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(color = Color.White.copy(alpha = 0.72f), shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(17.dp),
                    )
                }
                Text(
                    text = message.systemTitle ?: "检查结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = TealPrimary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TypingBubble(label: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(TealSecondary, CircleShape),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    enabled: Boolean,
    isBusy: Boolean,
    onSend: (String) -> Unit,
    onRequestExam: () -> Unit,
    onRequestLab: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val send = {
        val text = input.trim()
        if (text.isNotEmpty() && enabled) {
            onSend(text)
            input = ""
            focusManager.clearFocus()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRequestExam,
                    enabled = !isBusy,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("查体")
                }
                OutlinedButton(
                    onClick = onRequestLab,
                    enabled = !isBusy,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("化验/检查")
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 0.dp),
                    placeholder = { Text("输入你的问诊问题…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                FilledIconButton(
                    onClick = send,
                    enabled = enabled && input.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = "发送",
                    )
                }
            }
        }
    }
}

@Composable
private fun ClinicalRequestDialog(
    requestType: ClinicalRequestType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var item by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(requestType.actionLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "选择推荐项目或自行输入。结果会以系统卡片插入对话，并用于后续病历书写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(requestType.suggestions) { suggestion ->
                        FilterChip(
                            selected = item == suggestion,
                            onClick = { item = suggestion },
                            label = { Text(suggestion) },
                            shape = CircleShape,
                        )
                    }
                }
                OutlinedTextField(
                    value = item,
                    onValueChange = { item = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${requestType.label}项目") },
                    placeholder = {
                        Text(
                            if (requestType == ClinicalRequestType.PHYSICAL_EXAM) {
                                "例如：心脏听诊"
                            } else {
                                "例如：血常规"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(item.trim()) },
                enabled = item.isNotBlank(),
            ) {
                Text("生成结果")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun formatElapsed(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
