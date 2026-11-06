package com.xjb.wenzhen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.EncounterType
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
import com.xjb.wenzhen.ui.components.bringIntoViewOnFocus
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    encounterType: EncounterType = EncounterType.OUTPATIENT,
    startedAtMillis: Long = System.currentTimeMillis(),
    coverageInitiallyExpanded: Boolean = false,
    lastReplyMillis: Long? = null,
    lastReplyTokens: Int? = null,
    requestNotice: String? = null,
    contextWarning: Boolean = false,
    onCancelRequest: () -> Unit = {},
    onDismissNotice: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var chatListHeight by remember { mutableIntStateOf(0) }
    var autoFollow by remember { mutableStateOf(true) }
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 2
        }
    }
    // 用户手指一碰列表就把「自动贴底」关掉；松手时如果已经回到底部再重新打开。
    // 这样患者流式回复时不会把正在上翻看历史的用户强行拽回底部（dsh-Go 的 pinned/held 思路）。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> autoFollow = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> autoFollow = isAtBottom
            }
        }
    }
    // 拖拽结束后的惯性滑动可能继续把列表带回底部，到底后自动恢复跟随，避免按钮一直浮着。
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) autoFollow = true
    }
    val showTyping = isPatientTyping && messages.lastOrNull()?.let {
        it.role != MessageRole.PATIENT || it.deliveryState != MessageDeliveryState.SENDING
    } != false
    val messageCount = messages.size + if (showTyping || isQueryingClinical) 1 else 0
    var coverageExpanded by rememberSaveable { mutableStateOf(coverageInitiallyExpanded) }
    var clinicalType by remember { mutableStateOf<ClinicalRequestType?>(null) }
    // 流式 token 只改 SENDING 气泡的内容，不影响问诊覆盖；把它排除后，
    // 覆盖率不会在每个 token 到达时重新分析。
    val coverageMessages = remember(messages) {
        messages.filterNot { it.role == MessageRole.PATIENT && it.deliveryState == MessageDeliveryState.SENDING }
    }
    val coverage = remember(coverageMessages, encounterType, medicalCase.patient.gender, medicalCase.patient.age) {
        CoverageAnalyzer.analyze(
            messages = coverageMessages,
            encounterType = encounterType,
            gender = medicalCase.patient.gender,
            age = medicalCase.patient.age,
        )
    }

    // 消息变化或输入法弹起/收起导致 LazyColumn 高度变化时，先定位到最后一个 item，再向下
    // 滚到底（scrollBy 会在列表底部自动截断）。这样不依赖 WindowInsets.ime：Android 9 的
    // adjustResize 下键盘动画期间 ime inset 可能一直是 0，只能靠布局高度变化来判断。
    LaunchedEffect(messageCount, chatListHeight) {
        if (messageCount > 0 && chatListHeight > 0) {
            // 自己刚发出消息（最后一条是医生）时始终贴底；患者新增内容只在原本贴底时跟随，
            // 用户正在上翻历史时保持当前位置，由右下角「回到最新消息」按钮手动回到底部。
            val lastRole = messages.lastOrNull()?.role
            if (autoFollow || lastRole == MessageRole.DOCTOR) {
                // LazyColumn 的第 0 项是问诊提示、第 1 项是患者卡，消息从第 2 项开始，
                // 所以最后一项的下标是 messageCount + 1（messageCount 已包含“患者正在输入”项）。
                listState.scrollToItem(messageCount + 1)
                listState.scrollBy(100_000f)
                autoFollow = true
            }
        }
    }
    Scaffold(
        // IME padding 必须加在 Scaffold 外层：如果加在底部输入栏内部，Scaffold 会把整块键盘高度
        // 计入 bottomBar，聊天列表被压到接近 0 高，键盘弹起后无论怎么滚都看不到最新消息。
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            WenzhenTopBar(
                title = medicalCase.title,
                subtitle = "${medicalCase.department} · ${medicalCase.patient.name} · ${medicalCase.patient.age}",
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
            Column {
                if (isPatientTyping || isQueryingClinical) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isQueryingClinical) "正在获取检查结果…" else "患者正在回复…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancelRequest) { Text("取消请求") }
                    }
                }
                if (requestNotice != null) {
                    NoticeBanner(
                        message = requestNotice,
                        actionLabel = "知道了",
                        onAction = onDismissNotice,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
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
            }
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
                    message = "尚未完成 AI 服务配置，无法获得患者回复。",
                    actionLabel = "去设置",
                    onAction = onOpenSettings,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
            if (lastReplyMillis != null || contextWarning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    lastReplyMillis?.let { millis ->
                        Text(
                            text = "上次回复 ${"%.1f".format(millis / 1000.0)}s" +
                                (lastReplyTokens?.let { " · 约 $it tokens" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (contextWarning) {
                        Text(
                            text = "对话较长，继续练习会增加耗时和费用，建议适时结束。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { chatListHeight = it.height },
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        CoverageHintBar(
                            report = coverage,
                            startedAtMillis = startedAtMillis,
                            expanded = coverageExpanded,
                            onToggle = { coverageExpanded = !coverageExpanded },
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
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
                    itemsIndexed(
                        items = messages,
                        key = { index, message -> "${message.timestamp}-${message.role}-$index" },
                    ) { _, message ->
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
                if (!autoFollow && messageCount > 0) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(messageCount + 1)
                                listState.scrollBy(100_000f)
                                autoFollow = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "回到最新消息")
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
    startedAtMillis: Long,
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
                            text = rememberElapsedText(startedAtMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = TealPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "问诊提示 · 已覆盖 ${report.coveredCount}/${report.topics.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (!expanded) {
                        Text(
                            text = if (report.missing.isEmpty()) {
                                "所有方面均已问及"
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
                        Brush.linearGradient(listOf(GradientStart, GradientEnd)),
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
            .navigationBarsPadding(),
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
                        .widthIn(min = 0.dp)
                        .bringIntoViewOnFocus(),
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
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
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

@Composable
private fun rememberElapsedText(startedAtMillis: Long): String {
    var elapsedSeconds by remember(startedAtMillis) {
        mutableLongStateOf(((System.currentTimeMillis() - startedAtMillis) / 1000L).coerceAtLeast(0L))
    }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    return formatElapsed(elapsedSeconds)
}

private fun formatElapsed(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
