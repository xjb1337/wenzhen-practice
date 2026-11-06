package com.xjb.wenzhen.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.items
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.ChatMessage
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.domain.CarePlanSection
import com.xjb.wenzhen.domain.ReferenceCarePlan
import com.xjb.wenzhen.domain.ScoreResult
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.components.formatDateTime
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.Rose
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import com.xjb.wenzhen.ui.theme.Warm

enum class ResultTab {
    RECORD,
    REVIEW,
    TRANSCRIPT,
}

internal fun availableResultTabs(hasRecord: Boolean, hasTranscript: Boolean): List<ResultTab> = buildList {
    if (hasRecord) add(ResultTab.RECORD)
    add(ResultTab.REVIEW)
    if (hasTranscript) add(ResultTab.TRANSCRIPT)
}

@Composable
fun ResultScreen(
    caseTitle: String,
    patientName: String,
    department: String,
    difficulty: String,
    createdAt: Long,
    medicalRecord: String,
    score: ScoreResult,
    initialDiagnosis: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ResultTab = ResultTab.RECORD,
    messages: List<ChatMessage> = emptyList(),
    referenceCarePlan: ReferenceCarePlan? = null,
    notice: String? = null,
    onRegenerateAiComment: (() -> Unit)? = null,
    isRegeneratingAiComment: Boolean = false,
) {
    val tabs = availableResultTabs(hasRecord = medicalRecord.isNotBlank(), hasTranscript = messages.isNotEmpty())
    var selectedTabName by rememberSaveable { mutableStateOf(initialTab.name) }
    val selectedTab = tabs.firstOrNull { it.name == selectedTabName } ?: tabs.first()
    val context = LocalContext.current
    val currentText = when (selectedTab) {
        ResultTab.RECORD -> medicalRecord
        ResultTab.REVIEW -> reviewAsText(score, referenceCarePlan)
        ResultTab.TRANSCRIPT -> transcriptAsText(messages)
    }
    val copyLabel = when (selectedTab) {
        ResultTab.RECORD -> "病历已复制"
        ResultTab.REVIEW -> "点评已复制"
        ResultTab.TRANSCRIPT -> "对话已复制"
    }
    val shareSubjectSuffix = when (selectedTab) {
        ResultTab.RECORD -> "问诊病历"
        ResultTab.REVIEW -> "问诊点评"
        ResultTab.TRANSCRIPT -> "问诊对话"
    }
    val shareChooserTitle = "分享$shareSubjectSuffix"
    val shareContentDescription = "分享$shareSubjectSuffix"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "问诊结果",
                subtitle = "$caseTitle · $patientName",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("问诊资料", currentText))
                            Toast.makeText(context, copyLabel, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制当前内容",
                        )
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "$caseTitle $shareSubjectSuffix")
                                putExtra(Intent.EXTRA_TEXT, currentText)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                            }.onFailure {
                                Toast.makeText(context, "未找到可用的分享应用", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = shareContentDescription,
                        )
                    }
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
            ResultHeader(
                caseTitle = caseTitle,
                patientName = patientName,
                department = department,
                difficulty = difficulty,
                createdAt = createdAt,
                initialDiagnosis = initialDiagnosis,
                score = score,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (!notice.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (tabs.size == 1) {
                ReviewTab(
                    score = score,
                    referenceCarePlan = referenceCarePlan,
                    modifier = Modifier.weight(1f),
                    onRegenerateAiComment = onRegenerateAiComment,
                    isRegeneratingAiComment = isRegeneratingAiComment,
                )
                return@Column
            }
            val selectedTabIndex = tabs.indexOf(selectedTab)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
            ) {
                tabs.forEachIndexed { index, tab ->
                    val title = when (tab) {
                        ResultTab.RECORD -> "病历"
                        ResultTab.REVIEW -> "点评"
                        ResultTab.TRANSCRIPT -> "对话"
                    }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabName = tab.name },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
                    )
                }
            }
            when (selectedTab) {
                ResultTab.RECORD -> RecordTab(medicalRecord = medicalRecord, modifier = Modifier.weight(1f))
                ResultTab.REVIEW -> ReviewTab(
                    score = score,
                    referenceCarePlan = referenceCarePlan,
                    modifier = Modifier.weight(1f),
                    onRegenerateAiComment = onRegenerateAiComment,
                    isRegeneratingAiComment = isRegeneratingAiComment,
                )
                ResultTab.TRANSCRIPT -> TranscriptTab(messages = messages, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultHeader(
    caseTitle: String,
    patientName: String,
    department: String,
    difficulty: String,
    createdAt: Long,
    initialDiagnosis: String,
    score: ScoreResult,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            .padding(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderTag(department)
                    HeaderTag(difficulty)
                    HeaderTag("AI 生成")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = caseTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SizedBox(height = 4.dp)
                Text(
                    text = "${patientName} · ${formatDateTime(createdAt)}",
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "初步诊断：${initialDiagnosis.ifBlank { "未填写" }}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .size(82.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${score.totalScore}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = score.gradeLabel,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SizedBox(height: androidx.compose.ui.unit.Dp) {
    Spacer(modifier = Modifier.height(height))
}

@Composable
private fun HeaderTag(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun RecordTab(
    medicalRecord: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("大病历", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    RecordBody(medicalRecord = medicalRecord)
                }
            }
        }
        item {
            Text(
                text = "病历由 AI 根据本次对话生成，未问及的内容已标注。内容仅用于问诊练习。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun RecordBody(medicalRecord: String) {
    val headings = setOf(
        "一般情况", "主诉", "现病史", "既往史", "既往史/过敏史", "个人史",
        "婚育史", "家族史", "体格检查", "辅助检查", "系统回顾", "初步诊断",
        "处理意见", "诊疗计划",
    )
    var hasRenderedHeading = false
    Column {
        medicalRecord.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line in headings) {
                if (hasRenderedHeading) Spacer(modifier = Modifier.height(13.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                hasRenderedHeading = true
            } else {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewTab(
    score: ScoreResult,
    referenceCarePlan: ReferenceCarePlan?,
    modifier: Modifier = Modifier,
    onRegenerateAiComment: (() -> Unit)? = null,
    isRegeneratingAiComment: Boolean = false,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (score.aiCommentFailed) {
            item {
                AiCommentFailedCard(
                    onRegenerate = onRegenerateAiComment,
                    isRegenerating = isRegeneratingAiComment,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("分项得分", style = MaterialTheme.typography.titleMedium)
                    ScoreMetric(
                        label = "问诊要点覆盖",
                        value = score.coverageScore,
                        max = score.coverageMax,
                        color = TealPrimary,
                    )
                    ScoreMetric(
                        label = "问诊技巧与顺序",
                        value = score.techniqueScore,
                        max = score.techniqueMax,
                        color = TealSecondary,
                    )
                    ScoreMetric(
                        label = "医患沟通",
                        value = score.communicationScore,
                        max = score.communicationMax,
                        color = Color(0xFF00838F),
                    )
                    ScoreMetric(
                        label = "诊断准确",
                        value = score.diagnosisScore,
                        max = 20,
                        color = Color(0xFF5E6BC7),
                    )
                    if (!score.isLegacy) {
                        ScoreMetric(
                            label = "治疗方案",
                            value = score.treatmentScore,
                            max = 15,
                            color = Warm,
                        )
                    }
                }
            }
        }
        item {
            if (referenceCarePlan != null) {
                ReferenceCarePlanCard(referenceCarePlan)
            } else {
                ReviewListSection(
                    title = "正确诊疗方案",
                    items = listOf("该记录缺少可用的病例原始资料，无法重建参考诊疗方案。"),
                    accent = Warm,
                    icon = Icons.Outlined.LocalHospital,
                    emptyText = "暂无参考诊疗方案。",
                )
            }
        }
        item {
            ReviewListSection(
                title = "已覆盖要点",
                items = score.coveredPoints,
                accent = TealPrimary,
                icon = Icons.Outlined.CheckCircle,
                emptyText = "本次对话未明确覆盖问诊要点。",
            )
        }
        if (score.evidence.any { it.messageIndexes.isNotEmpty() }) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("评分证据链", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "每个已覆盖问题都能回指到具体对话编号，可对照复核。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        score.evidence.filter { it.messageIndexes.isNotEmpty() }.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.question, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    item.messageIndexes.joinToString("、") { "第 $it 条" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            ReviewListSection(
                title = "遗漏问题",
                items = score.missedQuestions,
                accent = Warm,
                icon = Icons.Outlined.WarningAmber,
                emptyText = "没有明显遗漏，继续保持。",
            )
        }
        item {
            ReviewListSection(
                title = "优点",
                items = score.strengths,
                accent = TealSecondary,
                icon = Icons.Outlined.CheckCircle,
                emptyText = "暂未给出具体优点。",
            )
        }
        item {
            ReviewListSection(
                title = "改进建议",
                items = score.improvements,
                accent = Color(0xFF5E6BC7),
                icon = Icons.Outlined.Lightbulb,
                emptyText = "暂未给出改进建议。",
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = TealContainer.copy(alpha = 0.65f),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "正确诊断解析",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    if (score.correctDiagnosis.isNotBlank()) {
                        Text(
                            text = score.correctDiagnosis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = score.diagnosisExplanation.ifBlank { "暂无诊断解析。" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        item {
            Text(
                text = "评分来自 AI 模拟反馈，建议结合带教老师意见复盘。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AiCommentFailedCard(
    onRegenerate: (() -> Unit)?,
    isRegenerating: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "AI 点评生成失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "分数由本地规则计算，已保存到历史记录；优点、改进建议和诊断解析暂缺。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (onRegenerate != null) {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !isRegenerating,
                ) {
                    Text(if (isRegenerating) "正在重新生成…" else "重新生成 AI 点评")
                }
            }
        }
    }
}

@Composable
private fun ScoreMetric(
    label: String,
    value: Int,
    max: Int,
    color: Color,
) {
    val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value / $max",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun ReferenceCarePlanCard(
    plan: ReferenceCarePlan,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(TealContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalHospital,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("正确诊疗方案", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "依据本病例原始资料整理，供复盘对照",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = TealContainer,
                ) {
                    Text(
                        text = plan.encounterLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = TealPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "正确诊断",
                    style = MaterialTheme.typography.labelLarge,
                    color = TealPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = plan.diagnosis.ifBlank { "病例未提供明确诊断。" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (plan.differentialDiagnoses.isNotEmpty()) {
                    Text(
                        text = "需鉴别：${plan.differentialDiagnoses.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CarePlanBulletSection(
                title = "诊断依据",
                items = plan.diagnosisBasis,
                accent = TealPrimary,
            )
            CarePlanBulletSection(
                title = "必要检查与结果",
                items = plan.necessaryExams,
                accent = Color(0xFF5E6BC7),
            )

            Text(
                text = "治疗与用药",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            plan.treatmentSections.forEach { section ->
                TreatmentPlanSection(section)
            }

            CarePlanBulletSection(
                title = "注意事项与随访",
                items = plan.precautions,
                accent = Warm,
            )

            Text(
                text = "本方案由 AI 依据病例资料生成，不补写病例之外的信息；药物与剂量仅用于教学复盘，请勿直接用于真实患者，具体诊疗须由带教老师确认。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CarePlanBulletSection(
    title: String,
    items: List<String>,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .background(accent, CircleShape),
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TreatmentPlanSection(section: CarePlanSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealContainer.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelLarge,
            color = TealPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = section.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReviewListSection(
    title: String,
    items: List<String>,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(6.dp)
                                .background(accent, CircleShape),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

internal fun reviewAsText(
    score: ScoreResult,
    referenceCarePlan: ReferenceCarePlan?,
): String = buildString {
    appendLine("问诊点评")
    appendLine("总分：${score.totalScore}/100（${score.gradeLabel}）")
    appendLine("问诊要点覆盖：${score.coverageScore}/${score.coverageMax}")
    appendLine("问诊技巧与顺序：${score.techniqueScore}/${score.techniqueMax}")
    appendLine("医患沟通：${score.communicationScore}/${score.communicationMax}")
    appendLine("诊断准确：${score.diagnosisScore}/20")
    if (!score.isLegacy) appendLine("治疗方案：${score.treatmentScore}/15")
    appendLine()
    appendLine("正确诊疗方案${referenceCarePlan?.let { "（${it.encounterLabel}）" } ?: ""}")
    if (referenceCarePlan != null) {
        appendLine("正确诊断：${referenceCarePlan.diagnosis}")
        if (referenceCarePlan.differentialDiagnoses.isNotEmpty()) {
            appendLine("需鉴别：${referenceCarePlan.differentialDiagnoses.joinToString("、")}")
        }
        appendLine("诊断依据：")
        appendLine(referenceCarePlan.diagnosisBasis.joinToString("\n") { "· $it" }.ifBlank { "· 暂无" })
        appendLine("必要检查与结果：")
        appendLine(referenceCarePlan.necessaryExams.joinToString("\n") { "· $it" }.ifBlank { "· 暂无" })
        appendLine("治疗与用药：")
        referenceCarePlan.treatmentSections.forEach { section ->
            appendLine("· ${section.label}：${section.content}")
        }
        appendLine("注意事项与随访：")
        appendLine(referenceCarePlan.precautions.joinToString("\n") { "· $it" }.ifBlank { "· 暂无" })
    } else {
        appendLine("该记录缺少可用的病例原始资料，无法重建参考诊疗方案。")
    }
    appendLine()
    appendLine("已覆盖要点：")
    appendLine(score.coveredPoints.joinToString("\n") { "· $it" }.ifBlank { "暂无" })
    appendLine()
    appendLine("遗漏问题：")
    appendLine(score.missedQuestions.joinToString("\n") { "· $it" }.ifBlank { "暂无" })
    appendLine()
    appendLine("优点：")
    appendLine(score.strengths.joinToString("\n") { "· $it" }.ifBlank { "暂无" })
    appendLine()
    appendLine("改进建议：")
    appendLine(score.improvements.joinToString("\n") { "· $it" }.ifBlank { "暂无" })
    appendLine()
    appendLine("正确诊断：${score.correctDiagnosis}")
    appendLine(score.diagnosisExplanation)
}

@Composable
private fun TranscriptTab(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages) { message ->
            val isDoctor = message.role == MessageRole.DOCTOR
            val isSystem = message.role == MessageRole.SYSTEM
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = when {
                    isSystem -> Alignment.CenterHorizontally
                    isDoctor -> Alignment.End
                    else -> Alignment.Start
                },
            ) {
                Text(
                    text = "${transcriptSpeaker(message)} · ${transcriptTime(message.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        isSystem -> MaterialTheme.colorScheme.secondaryContainer
                        isDoctor -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(if (isSystem) 1f else 0.85f),
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun transcriptSpeaker(message: ChatMessage): String = when (message.role) {
    MessageRole.DOCTOR -> if (message.deliveryState == MessageDeliveryState.FAILED) "医生（发送失败）" else "医生"
    MessageRole.PATIENT -> "患者"
    MessageRole.SYSTEM -> message.systemTitle ?: "检查结果"
}

private fun transcriptTime(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

internal fun transcriptAsText(messages: List<ChatMessage>): String =
    messages.joinToString("\n") { "${transcriptSpeaker(it)}（${transcriptTime(it.timestamp)}）：${it.content}" }
