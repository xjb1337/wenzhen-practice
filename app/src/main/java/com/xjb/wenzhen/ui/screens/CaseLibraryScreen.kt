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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.ui.components.DepartmentMark
import com.xjb.wenzhen.ui.components.DifficultyBadge
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary

private enum class CaseLibraryFilter(val label: String) {
    ALL("全部"),
    FAVORITE("收藏"),
    AI("AI 生成"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseLibraryScreen(
    cases: List<MedicalCase>,
    selectedDepartment: String,
    hasApiKey: Boolean,
    modifier: Modifier = Modifier,
    canPractice: Boolean = hasApiKey,
    offlineMode: Boolean = false,
    onEnableOfflinePractice: () -> Unit = {},
    // 全量病例（未按科室筛选），用于筛选行补齐「仅 AI 生成」科室里已经生成的病例。
    allCases: List<MedicalCase> = cases,
    onDepartmentSelected: (String) -> Unit = {},
    onCaseSelected: (MedicalCase, PatientPersonality, EncounterType) -> Unit = { _, _, _ -> },
    savedCaseIds: Set<String> = emptySet(),
    favoriteCaseIds: Set<String> = emptySet(),
    onFavoriteChange: (String, Boolean) -> Unit = { _, _ -> },
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
) {
    var showGenerateDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDraftDialog by rememberSaveable { mutableStateOf(false) }
    var personalityCaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var libraryFilter by rememberSaveable { mutableStateOf(CaseLibraryFilter.ALL) }
    var pendingDeleteCaseId by rememberSaveable { mutableStateOf<String?>(null) }

    val normalizedQuery = searchQuery.trim()
    val visibleCases = cases.filter { medicalCase ->
        val filterMatch = when (libraryFilter) {
            CaseLibraryFilter.ALL -> true
            CaseLibraryFilter.FAVORITE -> medicalCase.id in favoriteCaseIds
            CaseLibraryFilter.AI -> medicalCase.isAiGenerated || medicalCase.id in savedCaseIds
        }
        val queryMatch = normalizedQuery.isEmpty() ||
            medicalCase.title.contains(normalizedQuery, ignoreCase = true) ||
            medicalCase.department.contains(normalizedQuery, ignoreCase = true) ||
            medicalCase.patientSummary.contains(normalizedQuery, ignoreCase = true) ||
            medicalCase.openingLine.contains(normalizedQuery, ignoreCase = true)
        filterMatch && queryMatch
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "病例库",
                subtitle = if (offlineMode) "离线练习" else null,
                actions = {
                    TextButton(
                        onClick = { showGenerateDialog = true },
                        enabled = !isGenerating && !isLoading,
                    ) {
                        Text(if (isGenerating) "生成中…" else "AI 生成病例")
                    }
                },
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
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (draftSummary != null) {
                    item {
                        ConsultationDraftCard(
                            summary = draftSummary,
                            onResume = onResumeDraft,
                            onDiscard = { showDiscardDraftDialog = true },
                        )
                    }
                }
                if (!canPractice) {
                    item {
                        NoticeBanner(
                            message = "开启离线练习，即可使用内置病例。",
                            actionLabel = "开启离线练习",
                            onAction = onEnableOfflinePractice,
                            isError = false,
                        )
                    }
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
                                text = "${visibleCases.size} 个病例",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("搜索病例") },
                            placeholder = { Text("病例标题、科室或症状") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CaseLibraryFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = libraryFilter == option,
                                    onClick = { libraryFilter = option },
                                    label = { Text(option.label) },
                                    shape = CircleShape,
                                )
                            }
                        }
                        DepartmentFilterRow(
                            selectedDepartment = selectedDepartment,
                            onDepartmentSelected = onDepartmentSelected,
                            allCases = allCases,
                        )
                    }
                }
                if (visibleCases.isEmpty()) {
                    item {
                        val hasFilter = normalizedQuery.isNotEmpty() ||
                            libraryFilter != CaseLibraryFilter.ALL || selectedDepartment != Departments.ALL
                        val noFavorites = libraryFilter == CaseLibraryFilter.FAVORITE && favoriteCaseIds.isEmpty()
                        EmptyState(
                            icon = if (noFavorites) Icons.Outlined.StarBorder else Icons.Outlined.SearchOff,
                            title = when {
                                noFavorites -> "还没有收藏病例"
                                hasFilter -> "没有匹配的病例"
                                else -> "该科室暂无病例"
                            },
                            description = when {
                                noFavorites -> "点击病例卡片上的星标即可收藏。"
                                hasFilter -> "试试调整搜索关键词，或重置筛选条件。"
                                else -> "切换其他科室，或使用 AI 生成一个新病例。"
                            },
                            actionLabel = when {
                                noFavorites -> "浏览病例"
                                hasFilter -> "重置筛选"
                                else -> "AI 生成病例"
                            },
                            onAction = {
                                if (hasFilter) {
                                    searchQuery = ""
                                    libraryFilter = CaseLibraryFilter.ALL
                                    onDepartmentSelected(Departments.ALL)
                                } else {
                                    showGenerateDialog = true
                                }
                            },
                        )
                    }
                } else {
                    items(visibleCases, key = { it.id }) { medicalCase ->
                        CaseCard(
                            medicalCase = medicalCase,
                            onClick = { personalityCaseId = medicalCase.id },
                            isSaved = medicalCase.id in savedCaseIds,
                            isFavorite = medicalCase.id in favoriteCaseIds,
                            onFavoriteChange = { favorite -> onFavoriteChange(medicalCase.id, favorite) },
                            onDelete = { pendingDeleteCaseId = medicalCase.id },
                        )
                    }
                }
            }
        }
    }

    val pendingDeleteCase = cases.firstOrNull { it.id == pendingDeleteCaseId && it.id in savedCaseIds }
    if (pendingDeleteCase != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCaseId = null },
            title = { Text("删除这个 AI 病例？") },
            text = { Text("将从病例库移除「${pendingDeleteCase.title}」，历史问诊记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSavedCase(pendingDeleteCase.id)
                    pendingDeleteCaseId = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCaseId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showDiscardDraftDialog && draftSummary != null) {
        AlertDialog(
            onDismissRequest = { showDiscardDraftDialog = false },
            title = { Text("放弃这次问诊？") },
            text = { Text("这份草稿和其中的对话将被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDraftDialog = false
                    onDiscardDraft()
                }) {
                    Text("放弃草稿", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDraftDialog = false }) { Text("保留草稿") }
            },
        )
    }

    val personalityCase = cases.firstOrNull { it.id == personalityCaseId }
    if (personalityCase != null) {
        PersonalityPickerDialog(
            medicalCase = personalityCase,
            replacesDraft = draftSummary != null,
            onDismiss = { personalityCaseId = null },
            onConfirm = { personality, encounterType ->
                personalityCaseId = null
                onCaseSelected(personalityCase, personality, encounterType)
            },
        )
    }

    if (showGenerateDialog) {
        val unavailable = when {
            offlineMode -> AppSettings.OFFLINE_AI_MESSAGE
            !hasApiKey -> "请先在设置中填写 DeepSeek API Key。"
            else -> null
        }
        if (unavailable != null) {
            AlertDialog(
                onDismissRequest = { showGenerateDialog = false },
                title = { Text("AI 生成病例") },
                text = { Text(unavailable) },
                confirmButton = {
                    TextButton(onClick = {
                        showGenerateDialog = false
                        onOpenSettings()
                    }) { Text("去设置") }
                },
                dismissButton = {
                    TextButton(onClick = { showGenerateDialog = false }) { Text("取消") }
                },
            )
        } else {
            GenerateCaseDialog(
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
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaseCard(
    medicalCase: MedicalCase,
    onClick: () -> Unit,
    isSaved: Boolean = false,
    isFavorite: Boolean = false,
    onFavoriteChange: (Boolean) -> Unit,
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
                            maxLines = 2,
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = medicalCase.department,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DifficultyBadge(difficulty = medicalCase.difficulty)
                    }
                }
                IconToggleButton(checked = isFavorite, onCheckedChange = onFavoriteChange) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "取消收藏${medicalCase.title}" else "收藏${medicalCase.title}",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                horizontalArrangement = if (isSaved) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSaved) {
                    TextButton(onClick = onDelete) {
                        Text("删除")
                    }
                }
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
    initialDepartment: String,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (CaseGenerationSelection) -> Unit,
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
                    if (department != CaseGenerationSelection.RANDOM_DEPARTMENT &&
                        department != CaseGenerationSelection.RANDOM
                    ) {
                        Text(
                            text = "常见主诉：" +
                                CaseGenerationDepartments.hintFor(department).substringBefore("；"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    onGenerate(
                        CaseGenerationSelection(
                            department = department,
                            groupLabel = groupLabel,
                            difficulty = difficulty,
                            personality = personality,
                            encounterType = encounterType,
                        ),
                    )
                },
                enabled = !isGenerating,
            ) {
                Text("生成病例")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsultationDraftCard(
    summary: String,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("未完成的问诊", style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume) { Text("继续问诊") }
                TextButton(onClick = onDiscard) { Text("放弃草稿") }
            }
        }
    }
}

@Composable
internal fun PersonalityPickerDialog(
    medicalCase: MedicalCase,
    onDismiss: () -> Unit,
    onConfirm: (PatientPersonality, EncounterType) -> Unit,
    replacesDraft: Boolean = false,
) {
    var personality by rememberSaveable { mutableStateOf(PatientPersonality.COOPERATIVE) }
    var encounterType by rememberSaveable { mutableStateOf(EncounterType.OUTPATIENT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始问诊") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (replacesDraft) {
                    Text(
                        "开始新问诊会替换现有草稿。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
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
                Text(if (replacesDraft) "替换草稿并开始" else "开始问诊")
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
        if (selected != null) {
            Text(
                text = selected.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
