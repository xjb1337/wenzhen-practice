package com.xjb.wenzhen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.PregnantWoman
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.ui.theme.BlueSoft
import com.xjb.wenzhen.ui.theme.IndigoSoft
import com.xjb.wenzhen.ui.theme.Rose
import com.xjb.wenzhen.ui.theme.TealContainer
import com.xjb.wenzhen.ui.theme.TealPrimary
import com.xjb.wenzhen.ui.theme.TealSecondary
import com.xjb.wenzhen.ui.theme.Warm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WenzhenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
fun LoadingState(
    message: String = "正在加载…",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun NoticeBanner(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val background = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val foreground = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = foreground)
                }
            } else if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = foreground)
                }
            }
        }
    }
}

@Composable
fun DifficultyBadge(
    difficulty: Difficulty,
    modifier: Modifier = Modifier,
) {
    val background = when (difficulty) {
        Difficulty.EASY -> TealContainer
        Difficulty.MEDIUM -> BlueSoft
        Difficulty.HARD -> IndigoSoft
    }
    val foreground = when (difficulty) {
        Difficulty.EASY -> Color(0xFF00695C)
        Difficulty.MEDIUM -> Color(0xFF006064)
        Difficulty.HARD -> Color(0xFF3949AB)
    }
    Text(
        text = difficulty.label,
        modifier = modifier
            .background(background, CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = foreground,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun DepartmentMark(
    department: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    val icon = departmentIcon(department)
    Box(
        modifier = modifier
            .size(size.dp)
            .background(departmentBackground(department), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = departmentForeground(department),
            modifier = Modifier.size((size * 0.52f).dp),
        )
    }
}

fun departmentIcon(department: String): ImageVector = when (department) {
    Departments.CARDIOLOGY -> Icons.Outlined.Favorite
    Departments.RESPIRATORY -> Icons.Outlined.WaterDrop
    Departments.GASTROENTEROLOGY -> Icons.Outlined.Restaurant
    Departments.NEUROLOGY -> Icons.Outlined.Psychology
    Departments.ENDOCRINOLOGY -> Icons.Outlined.WaterDrop
    Departments.NEPHROLOGY -> Icons.Outlined.WaterDrop
    Departments.HEMATOLOGY -> Icons.Outlined.Favorite
    Departments.RHEUMATOLOGY -> Icons.Outlined.Healing
    Departments.INFECTIOUS_DISEASE -> Icons.Outlined.LocalHospital
    Departments.GENERAL_SURGERY -> Icons.Outlined.MedicalServices
    Departments.ORTHOPEDICS -> Icons.Outlined.MedicalServices
    Departments.UROLOGY -> Icons.Outlined.WaterDrop
    Departments.OBSTETRICS_GYNECOLOGY -> Icons.Outlined.PregnantWoman
    Departments.PEDIATRICS -> Icons.Outlined.ChildCare
    Departments.PSYCHIATRY -> Icons.Outlined.Psychology
    Departments.ENT -> Icons.Outlined.LocalHospital
    Departments.OPHTHALMOLOGY -> Icons.Outlined.LocalHospital
    Departments.DERMATOLOGY -> Icons.Outlined.Healing
    else -> Icons.Outlined.LocalHospital
}

fun departmentForeground(department: String): Color = when (department) {
    Departments.CARDIOLOGY -> Rose
    Departments.GASTROENTEROLOGY -> Warm
    Departments.RESPIRATORY -> TealSecondary
    Departments.NEUROLOGY -> Color(0xFF5E6BC7)
    Departments.ENDOCRINOLOGY -> Color(0xFF00838F)
    Departments.NEPHROLOGY -> Color(0xFF00838F)
    Departments.HEMATOLOGY -> Color(0xFFC62828)
    Departments.RHEUMATOLOGY -> Color(0xFF6A1B9A)
    Departments.INFECTIOUS_DISEASE -> Color(0xFFEF6C00)
    Departments.GENERAL_SURGERY -> Color(0xFF1565C0)
    Departments.ORTHOPEDICS -> Color(0xFF5D4037)
    Departments.UROLOGY -> Color(0xFF1976D2)
    Departments.OBSTETRICS_GYNECOLOGY -> Color(0xFFAD1457)
    Departments.PEDIATRICS -> Color(0xFFEF6C00)
    Departments.PSYCHIATRY -> Color(0xFF5E6BC7)
    Departments.ENT -> Color(0xFF00796B)
    Departments.OPHTHALMOLOGY -> Color(0xFF00838F)
    Departments.DERMATOLOGY -> Color(0xFF8D6E63)
    else -> TealPrimary
}

fun departmentBackground(department: String): Color =
    departmentForeground(department).copy(alpha = 0.12f)

fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
