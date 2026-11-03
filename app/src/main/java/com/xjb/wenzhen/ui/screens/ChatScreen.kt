package com.xjb.wenzhen.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.ui.components.DepartmentMark
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary

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
) {
    val listState = rememberLazyListState()
    val messageCount = messages.size + if (isPatientTyping) 1 else 0
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = medicalCase.title,
                subtitle = "${medicalCase.department} · ${medicalCase.patient.name} · ${medicalCase.patient.age}",
                onBack = onBack,
                actions = {
                    TextButton(onClick = onFinish, enabled = !isPatientTyping) {
                        Text("结束问诊", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
        bottomBar = {
            ChatComposer(
                enabled = hasApiKey && !isPatientTyping,
                onSend = onSendMessage,
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
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
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
                    MessageBubble(message = message)
                }
                if (isPatientTyping) {
                    item {
                        TypingBubble()
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
                        text = "标准化病人",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(TealContainer, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = medicalCase.patientSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DepartmentMark(department = medicalCase.department, size = 38)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
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
        }
    }
}

@Composable
private fun TypingBubble() {
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
                    text = "患者正在输入…",
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
    onSend: (String) -> Unit,
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
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
