package com.xjb.wenzhen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.xjb.wenzhen.data.model.CaseGenerationDepartments
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.domain.CaseGenerationSelection
import com.xjb.wenzhen.ui.components.DepartmentMark
import com.xjb.wenzhen.ui.components.DifficultyBadge
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary

@Composable
fun CaseLibraryScreen(
    cases: List<MedicalCase>,
    selectedDepartment: String,
    hasApiKey: Boolean,
    builtInCount: Int,
    // 全量病例（未按科室筛选），用于筛选行补齐「仅 AI 生成」科室里已经生成的病例。
    allCases: List<MedicalCase> = cases,
    onDepartmentSelected: (String) -> Unit = {},
    onCaseSelected: (MedicalCase, PatientPersonality, EncounterType) -> Unit = { _, _, _ -> },
    savedCaseIds: Set<String> = emptySet(),
    favoriteCaseIds: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onDeleteSavedCase: (String) -> Unit = {},
    onGenerateCase: (CaseGenerationSelection) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onClearGenerationError: () -> Unit = {},
    isGenerating: Boolean = false,
    generationError: String? = null,
    draftSummary: String? = null,
    onResumeDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {},
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
                if (draftSummary != null) {
                    item {
                        NoticeBanner(
                            message = "发现未完成的问诊：$draftSummary",
                            actionLabel = "继续问诊",
                            onAction = onResumeDraft,
                            onDismiss = onDiscardDraft,
                        )
                    }
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
                            allCases = allCases,
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
                            isSaved = medicalCase.id in savedCaseIds,
                            isFavorite = medicalCase.id in favoriteCaseIds,
                            onToggleFavorite = { onToggleFavorite(medicalCase.id) },
                            onDelete = { onDeleteSavedCase(medicalCase.id) },
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
            onConfirm = { personality, encounterType ->
                personalityCaseId = null
                onCaseSelected(personalityCase, personality, encounterType)
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
            onGenerate = { selection ->
                showGenerateDialog = false
                onGenerateCase(selection)
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
                    colors = listOf(GradientStart, GradientEnd),
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
                        "尚未配置 AI 服务，先完成设置即可使用"
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
    allCases: List<MedicalCase> = emptyList(),
) {
    // 内置 30 科室（Departments.all，含「全部」）固定展示；AI 生成的「仅 AI 专属」科室
    // 不在 Departments.all 中，只有真正生成过病例才补进筛选行，避免出现 0 例空 chip。
    val extraDepartments = allCases
        .map { it.department }
        .filter { it.isNotBlank() && it != Departments.ALL && it !in Departments.all }
        .distinct()
        .sorted()
    val departmentItems = Departments.all + extraDepartments
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(departmentItems) { department ->
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
    isSaved: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onDelete: () -> Unit = {},
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
            if (isSaved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onToggleFavorite) {
                        Text(if (isFavorite) "取消收藏" else "收藏")
                    }
                    TextButton(onClick = onDelete) {
                        Text("删除")
                    }
                }
            }
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

/**
 * 8 个系统分组共 54 个科室：内置 30（Departments.all）+ 仅 AI 生成 24。
 * 分组是纯 UI 局部状态，不写回 selectedDepartment、不进 AppUiState。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenerateCaseDialog(
    hasApiKey: Boolean,
    initialDepartment: String,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (CaseGenerationSelection) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups = CaseGenerationDepartments.groups
    var department by rememberSaveable { mutableStateOf(initialDepartment) }
    var groupLabel by rememberSaveable {
        mutableStateOf(
            CaseGenerationDepartments.groupOf(initialDepartment)?.label
                ?: groups.first().label
        )
    }
    var difficulty by rememberSaveable { mutableStateOf<Difficulty?>(Difficulty.MEDIUM) }
    var personality by rememberSaveable {
        mutableStateOf<PatientPersonality?>(PatientPersonality.COOPERATIVE)
    }
    var encounterType by rememberSaveable {
        mutableStateOf<EncounterType?>(EncounterType.OUTPATIENT)
    }
    val currentGroup = groups.first { it.label == groupLabel }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 随机生成病例") },
        text = {
            // 真机基线 review/screenshots/baseline-ai-dialog.png：1920×1080 横屏下原对话框已占大半屏，
            // 增加分组行后必须限高 + 可滚动，否则「就诊类型 / 生成按钮」被挤出可视区。
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "选择科室与难度，DeepSeek 会生成一份带隐藏资料的标准化病人病例。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("科室分组", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(groups, key = { it.label }) { group ->
                            FilterChip(
                                selected = groupLabel == group.label,
                                onClick = {
                                    groupLabel = group.label
                                    // 全库随机与本组随机在切组后保持语义；具体科室不在新组时回退到本组首项。
                                    if (department != CaseGenerationSelection.RANDOM_DEPARTMENT &&
                                        department != CaseGenerationSelection.RANDOM &&
                                        department !in group.departments
                                    ) {
                                        department = group.departments.first()
                                    }
                                },
                                label = { Text("${group.label} (${group.departments.size})") },
                                shape = CircleShape,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "科室",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = department == CaseGenerationSelection.RANDOM_DEPARTMENT,
                            onClick = { department = CaseGenerationSelection.RANDOM_DEPARTMENT },
                            label = { Text("随机科室") },
                            shape = CircleShape,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = department == CaseGenerationSelection.RANDOM,
                            onClick = { department = CaseGenerationSelection.RANDOM },
                            label = { Text("随机") },
                            shape = CircleShape,
                        )
                        currentGroup.departments.forEach { item ->
                            FilterChip(
                                selected = department == item,
                                onClick = { department = item },
                                label = { Text(item) },
                                shape = CircleShape,
                            )
                        }
                    }
                    // 症候提示摘要：只取第一段，避免把红旗里的诊断名直接展示给正在练问诊的用户。
                    Text(
                        text = when (department) {
                            CaseGenerationSelection.RANDOM_DEPARTMENT -> "症候参考：将从全部科室中随机"
                            CaseGenerationSelection.RANDOM -> "症候参考：将从${currentGroup.label}中随机"
                            else -> "症候参考：" +
                                CaseGenerationDepartments.hintFor(department).substringBefore("；")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        FilterChip(
                            selected = difficulty == null,
                            onClick = { difficulty = null },
                            label = { Text("随机") },
                            shape = CircleShape,
                        )
                    }
                }
                PersonalitySelector(
                    selected = personality,
                    onSelected = { personality = it },
                    includeRandom = true,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("就诊类型", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EncounterType.entries.forEach { type ->
                            FilterChip(
                                selected = encounterType == type,
                                onClick = { encounterType = type },
                                label = { Text(type.label) },
                                shape = CircleShape,
                            )
                        }
                        FilterChip(
                            selected = encounterType == null,
                            onClick = { encounterType = null },
                            label = { Text("随机") },
                            shape = CircleShape,
                        )
                    }
                }
                if (!hasApiKey) {
                    NoticeBanner(
                        message = "还没有配置 AI 服务，无法生成病例。",
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
                        onGenerate(
                            CaseGenerationSelection(
                                department = department,
                                groupLabel = groupLabel,
                                difficulty = difficulty,
                                personality = personality,
                                encounterType = encounterType,
                            ),
                        )
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
internal fun PersonalityPickerDialog(
    medicalCase: MedicalCase,
    onDismiss: () -> Unit,
    onConfirm: (PatientPersonality, EncounterType) -> Unit,
) {
    var personality by rememberSaveable { mutableStateOf(PatientPersonality.COOPERATIVE) }
    var encounterType by rememberSaveable { mutableStateOf(EncounterType.OUTPATIENT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始问诊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${medicalCase.title} · ${medicalCase.patientSummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PersonalitySelector(
                    age = medicalCase.patient.age,
                    selected = personality,
                    onSelected = { selected -> selected?.let { personality = it } },
                )
                Text("就诊类型", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EncounterType.entries.forEach { type ->
                        FilterChip(
                            selected = encounterType == type,
                            onClick = { encounterType = type },
                            label = { Text(type.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val safePersonality = if (personality.availableFor(medicalCase.patient.age)) personality else PatientPersonality.COOPERATIVE
                onConfirm(safePersonality, encounterType)
            }) {
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
    age: String? = null,
    selected: PatientPersonality?,
    onSelected: (PatientPersonality?) -> Unit,
    includeRandom: Boolean = false,
) {
    val options = buildList<PatientPersonality?> {
        if (includeRandom) add(null)
        addAll(PatientPersonality.entries.filter { age == null || it.availableFor(age) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("患者性格", style = MaterialTheme.typography.titleSmall)
        options.chunked(2).forEach { rowItems ->
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
                                text = personality?.label ?: "随机",
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
            text = selected?.description ?: "将从全部性格中随机选择",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
