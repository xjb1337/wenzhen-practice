package com.xjb.wenzhen.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.domain.DepartmentAverage
import com.xjb.wenzhen.domain.MissedQuestionCount
import com.xjb.wenzhen.domain.ScoreTrendPoint
import com.xjb.wenzhen.domain.StatsSummary
import com.xjb.wenzhen.ui.components.EmptyState
import com.xjb.wenzhen.ui.components.LoadingState
import com.xjb.wenzhen.ui.components.WenzhenTopBar
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.GradientStart
import com.xjb.wenzhen.ui.theme.GradientEnd
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary

@Composable
fun StatsScreen(
    stats: StatsSummary,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onDepartmentClick: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WenzhenTopBar(
                title = "成绩统计",
                subtitle = "看见进步，也看见遗漏",
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            isLoading -> LoadingState(
                message = "正在整理成绩…",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            errorMessage != null -> EmptyState(
                icon = Icons.Outlined.BarChart,
                title = "统计加载失败",
                description = errorMessage,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                actionLabel = "重新加载",
                onAction = onRetry,
            )
            stats.attempts == 0 -> EmptyState(
                icon = Icons.Outlined.BarChart,
                title = "还没有可统计的成绩",
                description = "完成至少一次问诊后，这里会显示成绩趋势、科室均分和常遗漏要点。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { StatsHero(stats = stats) }
                item { ScoreTrendCard(points = stats.trend) }
                item { DepartmentAverageCard(items = stats.departmentAverages, onDepartmentClick = onDepartmentClick) }
                item { MissedQuestionsCard(items = stats.topMissedQuestions) }
                item { StatsFootnote() }
            }
        }
    }
}

@Composable
private fun StatsHero(stats: StatsSummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                RoundedCornerShape(24.dp),
            )
            .padding(19.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "练习概览",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric(
                    value = "${stats.averageScore}",
                    label = "平均分",
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    value = "${stats.attempts}",
                    label = "完成次数",
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    value = "${stats.departmentAverages.size}",
                    label = "覆盖科室",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.15f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ScoreTrendCard(points: List<ScoreTrendPoint>) {
    StatsCard(title = "历史成绩趋势", subtitle = "按完成时间从早到晚") {
        ScoreTrendChart(points = points)
        if (points.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = points.first().label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "最新 ${points.last().score} 分",
                    style = MaterialTheme.typography.bodySmall,
                    color = TealPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ScoreTrendChart(points: List<ScoreTrendPoint>) {
    val lineColor = TealPrimary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = TealSecondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp),
    ) {
        if (points.isEmpty()) return@Canvas
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        val chartHeight = bottom - top
        repeat(4) { index ->
            val y = top + chartHeight * index / 3f
            drawLine(
                color = gridColor.copy(alpha = 0.7f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val denominator = (points.size - 1).coerceAtLeast(1)
        val offsets = points.mapIndexed { index, point ->
            val x = if (points.size == 1) {
                (left + right) / 2f
            } else {
                left + (right - left) * index / denominator
            }
            val y = bottom - chartHeight * point.score.coerceIn(0, 100) / 100f
            Offset(x, y)
        }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { offset -> lineTo(offset.x, offset.y) }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        offsets.forEach { offset ->
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = offset)
            drawCircle(color = dotColor, radius = 3.dp.toPx(), center = offset)
        }
    }
}

@Composable
private fun DepartmentAverageCard(
    items: List<DepartmentAverage>,
    onDepartmentClick: (String) -> Unit = {},
) {
    StatsCard(title = "各科室平均分", subtitle = "按平均分从高到低，点击查看该科室历史") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDepartmentClick(item.department) },
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.department,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${formatScore(item.averageScore)} 分 · ${item.attempts} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(TealContainer, CircleShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((item.averageScore / 100.0).toFloat().coerceIn(0.04f, 1f))
                                .height(8.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(GradientStart, GradientEnd)),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedQuestionsCard(items: List<MissedQuestionCount>) {
    StatsCard(title = "最常遗漏的问诊要点", subtitle = "Top ${minOf(10, items.size)}") {
        if (items.isEmpty()) {
            Text(
                text = "暂未记录到遗漏要点，继续保持结构化问诊。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items.forEachIndexed { index, item ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (index < 3) TealPrimary else BlueSoft,
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (index < 3) Color.White else TealPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${item.count} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsFootnote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BlueSoft.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalHospital,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "统计来自已保存的问诊记录；继续练习会实时更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

private fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
