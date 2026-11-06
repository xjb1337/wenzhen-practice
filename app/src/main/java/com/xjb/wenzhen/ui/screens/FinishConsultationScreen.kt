package com.xjb.wenzhen.ui.screens

import com.xjb.wenzhen.ui.components.bringIntoViewOnFocus
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.TealPrimary

@Composable
fun FinishConsultationScreen(
    caseTitle: String,
    patientName: String,
    department: String,
    onBack: () -> Unit,
    onSubmit: (String, String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialDiagnosis: String = "",
    initialDiagnosisBasis: String = "",
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    initialTreatment: String = "",
    initialGenerateRecord: Boolean = false,
    thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
) {
    var diagnosis by rememberSaveable { mutableStateOf(initialDiagnosis) }
    var basis by rememberSaveable { mutableStateOf(initialDiagnosisBasis) }
    var treatment by rememberSaveable { mutableStateOf(initialTreatment) }
    var generateRecord by rememberSaveable { mutableStateOf(initialGenerateRecord) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "结束问诊",
                subtitle = "$caseTitle · $patientName",
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = TealContainer.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(45.dp)
                                .background(TealPrimary, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalHospital,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Column {
                            Text(
                                text = "最后一步：写下你的判断",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "病历和评分将基于本次对话生成。先独立作出诊断，再看反馈，练习效果更好。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = department,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(TealContainer, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Text(
                            text = "$patientName 的问诊已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    NoticeBanner(
                        message = errorMessage,
                        onDismiss = onDismissError,
                    )
                }
            }
            if (localError != null) {
                item {
                    NoticeBanner(
                        message = localError.orEmpty(),
                        onDismiss = { localError = null },
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "初步诊断",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "必填",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    OutlinedTextField(
                        value = diagnosis,
                        onValueChange = {
                            diagnosis = it
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        placeholder = { Text("例如：急性阑尾炎") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        supportingText = { Text("请写出你认为最可能的诊断") },
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "诊断依据",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "选填",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    OutlinedTextField(
                        value = basis,
                        onValueChange = { basis = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        placeholder = { Text("简要写下支持诊断的病史、查体或线索…") },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("治疗方案", style = MaterialTheme.typography.titleMedium)
                        Text("选填", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedTextField(value = treatment, onValueChange = { treatment = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), placeholder = { Text("写下治疗原则、药物和随访…") }, minLines = 3, maxLines = 6, shape = RoundedCornerShape(16.dp))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("生成病历", style = MaterialTheme.typography.titleMedium)
                        Text("关闭时不生成病历，仍评分并给出参考诊疗方案", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = generateRecord, onCheckedChange = { generateRecord = it })
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            text = if (thinkingLevel == ThinkingLevel.MAX) {
                                "对话未涉及的内容会在大病历中标注为“未问及”。当前为最高思考强度，生成可能需要 1-3 分钟，请耐心等待。"
                            } else {
                                "对话未涉及的内容会在大病历中标注为“未问及”。提交后请稍候，生成通常需要十几秒。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isSubmitting) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TealContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Column {
                            Text(
                                text = if (generateRecord) "正在生成病历与点评…" else "正在生成点评与参考诊疗方案…",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = if (thinkingLevel == ThinkingLevel.MAX) {
                                    "最高思考强度较慢，请保持网络连接，不要关闭页面。"
                                } else {
                                    "请保持网络连接，不要关闭页面。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (diagnosis.trim().isEmpty()) {
                            localError = "请先填写你的初步诊断。"
                        } else {
                            localError = null
                            onSubmit(diagnosis.trim(), basis.trim(), treatment.trim(), generateRecord)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (generateRecord) "生成病历并评分" else "提交评分",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
