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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConsultationListItem
import com.xjb.wenzhen.ui.HistoryTransfer
import com.xjb.wenzhen.ui.components.DepartmentMark
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.components.formatDateTime
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import com.xjb.wenzhen.util.HistoryBackup
import com.xjb.wenzhen.util.HistoryExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal enum class HistoryDateFilter(val label: String) {
    ALL("全部"),
    TODAY("今天"),
    LAST_WEEK("最近 7 天"),
    LAST_MONTH("最近 30 天");

    fun rangeAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange = when (this) {
        ALL -> Long.MIN_VALUE..Long.MAX_VALUE
        TODAY -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()..now
        LAST_WEEK -> (now - Duration.ofDays(7).toMillis())..now
        LAST_MONTH -> (now - Duration.ofDays(30).toMillis())..now
    }
}

@Composable
fun HistoryScreen(
    records: List<ConsultationListItem>,
    isLoading: Boolean,
    errorMessage: String?,
    loadForExport: suspend () -> List<ConsultationEntity>,
    loadDuration: suspend (Long) -> Long?,
    onRecordClick: (Long) -> Unit = {},
    onRetry: () -> Unit = {},
    onBrowseCases: () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onDeleteRecord: (Long) -> Unit = {},
    onClearAll: () -> Unit = {},
    transfer: HistoryTransfer? = null,
    onRestore: (Flow<ConsultationEntity>) -> Unit = {},
    onSaveBackup: (suspend (List<ConsultationEntity>) -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }
    var selectedDepartment by rememberSaveable { mutableStateOf("全部") }
    var scoreBand by rememberSaveable { mutableStateOf("全部") }
    var dateBand by rememberSaveable { mutableStateOf(HistoryDateFilter.ALL.label) }
    val dateFilter = HistoryDateFilter.entries.firstOrNull { it.label == dateBand } ?: HistoryDateFilter.ALL
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dateFilter) {
        if (dateFilter != HistoryDateFilter.ALL) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L)
            }
        }
    }
    var isExporting by remember { mutableStateOf(false) }
    val isFileBusy = transfer != null || isExporting
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !isFileBusy) {
            onRestore(HistoryBackup.read(context.applicationContext, uri))
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && !isFileBusy) {
            val applicationContext = context.applicationContext
            onSaveBackup { records -> HistoryBackup.write(applicationContext, uri, records) }
        }
    }
    fun exportFile(create: suspend (List<ConsultationEntity>) -> java.io.File, mime: String, title: String) {
        if (isFileBusy) return
        isExporting = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { create(loadForExport()) }
                shareFile(context, file, mime, title)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(context, "导出失败：${error.message}", Toast.LENGTH_LONG).show()
            } finally {
                isExporting = false
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部历史记录？") },
            text = { Text("将删除全部 ${records.size} 条问诊记录，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
    records.firstOrNull { it.id == pendingDeleteId }?.let { pendingDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除这条问诊记录？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecord(pendingDelete.id)
                    pendingDeleteId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
    val departments = remember(records) {
        listOf("全部") + records.map { it.department }.filter { it.isNotBlank() }.distinct()
    }
    val activeDepartment = if (selectedDepartment in departments) selectedDepartment else "全部"
    val normalizedQuery = query.trim()
    val dateRange = dateFilter.rangeAt(now)
    val isFiltered = normalizedQuery.isNotEmpty() || activeDepartment != "全部" ||
        scoreBand != "全部" || dateFilter != HistoryDateFilter.ALL
    val visibleRecords = remember(records, normalizedQuery, activeDepartment, scoreBand, dateRange) {
        if (!isFiltered) records else records.filter { record ->
            val queryMatch = normalizedQuery.isEmpty() ||
                record.caseTitle.contains(normalizedQuery, ignoreCase = true) ||
                record.department.contains(normalizedQuery, ignoreCase = true) ||
                record.patientName.contains(normalizedQuery, ignoreCase = true)
            val departmentMatch = activeDepartment == "全部" || record.department == activeDepartment
            val scoreMatch = when (scoreBand) {
                "60 分以下" -> record.totalScore < 60
                "60-79 分" -> record.totalScore in 60..79
                "80 分以上" -> record.totalScore >= 80
                else -> true
            }
            queryMatch && departmentMatch && scoreMatch && record.createdAt in dateRange
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "历史记录",
                subtitle = when {
                    transfer == HistoryTransfer.RESTORING -> "正在恢复备份…"
                    transfer == HistoryTransfer.SAVING -> "正在保存备份…"
                    isExporting -> "正在导出文件…"
                    else -> null
                },
                actions = {
                    if (isFileBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }, enabled = !isFileBusy) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = if (records.isNotEmpty()) "导出或恢复历史记录" else "从备份恢复历史记录",
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                        ) {
                            if (records.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("导出 Markdown") },
                                    onClick = {
                                        showExportMenu = false
                                        exportFile(
                                            create = { HistoryExporter.writeMarkdown(context, it) },
                                            mime = "text/markdown",
                                            title = "导出问诊历史",
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("导出 PDF") },
                                    onClick = {
                                        showExportMenu = false
                                        exportFile(
                                            create = { HistoryExporter.writePdf(context, it) },
                                            mime = "application/pdf",
                                            title = "导出问诊历史",
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("保存 JSON 备份") },
                                    onClick = {
                                        showExportMenu = false
                                        saveLauncher.launch(HistoryBackup.suggestedFilename())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("分享 JSON 备份") },
                                    onClick = {
                                        showExportMenu = false
                                        exportFile(
                                            create = { HistoryBackup.write(context, it) },
                                            mime = "application/json",
                                            title = "备份问诊历史",
                                        )
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("从 JSON 备份恢复") },
                                onClick = {
                                    showExportMenu = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                            )
                        }
                    }
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }, enabled = !isFileBusy) {
                            Icon(Icons.Outlined.Delete, contentDescription = "清空历史记录")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            isLoading -> LoadingState(
                message = "正在读取问诊记录…",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            errorMessage != null -> EmptyState(
                icon = Icons.Outlined.History,
                title = "历史记录加载失败",
                description = errorMessage,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                actionLabel = "重新加载",
                onAction = onRetry,
            )
            records.isEmpty() -> EmptyState(
                icon = Icons.Outlined.History,
                title = "还没有问诊记录",
                description = "完成一次问诊后，大病历、评分和对话时间会保存在这里。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                actionLabel = "去病例库",
                onAction = onBrowseCases,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索病例、科室或患者") },
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            departments.forEach { department ->
                                FilterChip(
                                    selected = activeDepartment == department,
                                    onClick = { selectedDepartment = department },
                                    label = { Text(department) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("全部", "60 分以下", "60-79 分", "80 分以上").forEach { band ->
                                FilterChip(
                                    selected = scoreBand == band,
                                    onClick = { scoreBand = band },
                                    label = { Text(band) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HistoryDateFilter.entries.forEach { band ->
                                FilterChip(
                                    selected = dateFilter == band,
                                    onClick = { dateBand = band.label },
                                    label = { Text(band.label) },
                                )
                            }
                        }
                    }
                }
                if (visibleRecords.isNotEmpty()) {
                    item {
                        HistorySummary(records = visibleRecords)
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isFiltered) "筛选结果" else "全部记录",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "共 ${visibleRecords.size} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (visibleRecords.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.History,
                            title = "没有符合条件的记录",
                            actionLabel = "清除筛选",
                            onAction = {
                                onQueryChange("")
                                selectedDepartment = "全部"
                                scoreBand = "全部"
                                dateBand = HistoryDateFilter.ALL.label
                            },
                        )
                    }
                }
                items(visibleRecords, key = { it.id }) { record ->
                    HistoryCard(
                        record = record,
                        loadDuration = loadDuration,
                        onClick = { onRecordClick(record.id) },
                        onDelete = { pendingDeleteId = record.id },
                    )
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: java.io.File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, title))
    }.onFailure {
        Toast.makeText(context, "未找到可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun HistorySummary(records: List<ConsultationListItem>) {
    val average = remember(records) { (records.sumOf { it.totalScore.toLong() } / records.size).toInt() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                RoundedCornerShape(22.dp),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "练习汇总",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "已完成 ${records.size} 次问诊",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$average",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "平均分",
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    record: ConsultationListItem,
    loadDuration: suspend (Long) -> Long?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val durationMinutes by produceState<Long?>(initialValue = null, key1 = record.id) {
        value = loadDuration(record.id)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DepartmentMark(department = record.department, size = 42)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.caseTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${record.department} · ${record.patientName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(formatDateTime(record.createdAt))
                        durationMinutes?.let { append(" · 用时 $it 分钟") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.totalScore}分",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
