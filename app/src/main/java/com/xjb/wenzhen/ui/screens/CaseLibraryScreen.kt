package com.xjb.wenzhen.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.ui.components.DepartmentMark
import com.xjb.wenzhen.ui.components.DifficultyBadge
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary

@Composable
fun CaseLibraryScreen(
    cases: List<MedicalCase>,
    selectedDepartment: String,
    hasApiKey: Boolean,
    builtInCount: Int,
    onDepartmentSelected: (String) -> Unit = {},
    onCaseSelected: (MedicalCase, PatientPersonality) -> Unit = { _, _ -> },
    onGenerateCase: (String, Difficulty, PatientPersonality) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit = {},
    onClearGenerationError: () -> Unit = {},
    isGenerating: Boolean = false,
    generationError: String? = null,
    isLoading: Boolean = false,
    loadError: String? = null,
    modifier: Modifier = Modifier,
) {
    var showGenerateDialog by rememberSaveable { mutableStateOf(false) }
    var personalityCaseId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "病例库",
                subtitle = "从一次真实感的问诊开始",
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            isLoading -> LoadingState(
                message = "正在准备病例…",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            loadError != null -> EmptyState(
                icon = Icons.Outlined.LocalHospital,
                title = "病例加载失败",
                description = loadError,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    PracticeHero(builtInCount = builtInCount)
                }
                item {
                    AiCaseCard(
                        hasApiKey = hasApiKey,
                        isGenerating = isGenerating,
                        onOpenGenerator = { showGenerateDialog = true },
                        onOpenSettings = onOpenSettings,
                    )
                }
                if (generationError != null) {
                    item {
                        NoticeBanner(
                            message = generationError,
                            actionLabel = "重试",
                            onAction = {
                                onClearGenerationError()
                                showGenerateDialog = true
                            },
                            onDismiss = onClearGenerationError,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "选择病例",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${cases.size} 例可用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DepartmentFilterRow(
                            selectedDepartment = selectedDepartment,
                            onDepartmentSelected = onDepartmentSelected,
                        )
                    }
                }
                if (cases.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "该科室暂无病例",
                            description = "切换其他科室，或使用 AI 生成一个新病例。",
                            actionLabel = "AI 生成病例",
                            onAction = { showGenerateDialog = true },
                        )
                    }
                } else {
                    items(cases, key = { it.id }) { medicalCase ->
                        CaseCard(
                            medicalCase = medicalCase,
                            onClick = { personalityCaseId = medicalCase.id },
                        )
                    }
                }
            }
        }
    }

    val personalityCase = cases.firstOrNull { it.id == personalityCaseId }
    if (personalityCase != null) {
        PersonalityPickerDialog(
            medicalCase = personalityCase,
            onDismiss = { personalityCaseId = null },
            onConfirm = { personality ->
                personalityCaseId = null
                onCaseSelected(personalityCase, personality)
            },
        )
    }

    if (showGenerateDialog) {
        GenerateCaseDialog(
            hasApiKey = hasApiKey,
            initialDepartment = if (selectedDepartment == Departments.ALL) {
                Departments.CARDIOLOGY
            } else {
                selectedDepartment
            },
            isGenerating = isGenerating,
            onDismiss = { showGenerateDialog = false },
            onGenerate = { department, difficulty, personality ->
                showGenerateDialog = false
                onGenerateCase(department, difficulty, personality)
            },
            onOpenSettings = {
                showGenerateDialog = false
                onOpenSettings()
            },
        )
    }
}

@Composable
private fun PracticeHero(builtInCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(TealPrimary, TealSecondary),
                )
            )
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
            ) {
                Text(
                    text = "临床问诊 · 刻意练习",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "把每一次开口\n练成临床思维",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "问诊结束后生成规范大病历与结构化评分，回看自己的问诊路径。",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroStat(value = "$builtInCount", label = "标准病例")
                HeroStat(value = "${Departments.all.size - 1}", label = "临床科室")
                HeroStat(value = "AI", label = "随机扩展")
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AiCaseCard(
    hasApiKey: Boolean,
    isGenerating: Boolean,
    onOpenGenerator: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGenerating, onClick = onOpenGenerator),
        shape = RoundedCornerShape(20.dp),
        color = TealContainer.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color.White, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = TealPrimary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGenerating) "正在生成新病例…" else "AI 随机生成病例",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (hasApiKey) {
                        "选择科室和难度，让标准化病人换个考题"
                    } else {
                        "尚未配置 API Key，先完成设置即可使用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!hasApiKey && !isGenerating) {
                TextButton(onClick = onOpenSettings) {
                    Text("去设置")
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "生成病例",
                    tint = TealPrimary,
                )
            }
        }
    }
}

@Composable
private fun DepartmentFilterRow(
    selectedDepartment: String,
    onDepartmentSelected: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(Departments.all) { department ->
            FilterChip(
                selected = selectedDepartment == department,
                onClick = { onDepartmentSelected(department) },
                label = { Text(department) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedDepartment == department,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun CaseCard(
    medicalCase: MedicalCase,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DepartmentMark(department = medicalCase.department)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = medicalCase.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (medicalCase.isAiGenerated) {
                            Text(
                                text = "AI",
                                modifier = Modifier
                                    .background(BlueSoft, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                color = TealPrimary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = medicalCase.department,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DifficultyBadge(difficulty = medicalCase.difficulty)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = medicalCase.patientSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = medicalCase.openingLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "预计 10–15 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onClick,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text("开始问诊")
                }
            }
        }
    }
}

@Composable
private fun GenerateCaseDialog(
    hasApiKey: Boolean,
    initialDepartment: String,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (String, Difficulty, PatientPersonality) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var department by rememberSaveable { mutableStateOf(initialDepartment) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }
    var personality by rememberSaveable { mutableStateOf(PatientPersonality.COOPERATIVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 随机生成病例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "选择科室与难度，DeepSeek 会生成一份带隐藏资料的标准化病人病例。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("科室", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(Departments.all.filter { it != Departments.ALL }) { item ->
                            FilterChip(
                                selected = department == item,
                                onClick = { department = item },
                                label = { Text(item) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("难度", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.values().forEach { item ->
                            FilterChip(
                                selected = difficulty == item,
                                onClick = { difficulty = item },
                                label = { Text(item.label) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
                PersonalitySelector(
                    selected = personality,
                    onSelected = { personality = it },
                )
                if (!hasApiKey) {
                    NoticeBanner(
                        message = "还没有配置 DeepSeek API Key，无法生成病例。",
                        isError = false,
                    )
                }
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("病例生成中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasApiKey) {
                        onGenerate(department, difficulty, personality)
                    } else {
                        onOpenSettings()
                    }
                },
                enabled = !isGenerating,
            ) {
                Text(if (hasApiKey) "生成病例" else "去设置")
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
private fun PersonalityPickerDialog(
    medicalCase: MedicalCase,
    onDismiss: () -> Unit,
    onConfirm: (PatientPersonality) -> Unit,
) {
    var personality by rememberSaveable { mutableStateOf(PatientPersonality.COOPERATIVE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择患者性格") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${medicalCase.title} · ${medicalCase.patientSummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PersonalitySelector(
                    selected = personality,
                    onSelected = { personality = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(personality) }) {
                Text("开始问诊")
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
private fun PersonalitySelector(
    selected: PatientPersonality,
    onSelected: (PatientPersonality) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("患者性格", style = MaterialTheme.typography.titleSmall)
        PatientPersonality.entries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { personality ->
                    FilterChip(
                        selected = selected == personality,
                        onClick = { onSelected(personality) },
                        label = {
                            Text(
                                text = personality.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
