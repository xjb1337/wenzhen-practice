package com.xjb.wenzhen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 用户可选主题。持久化存 name，不存 ordinal，避免以后插入主题导致错位。 */
enum class AppTheme(val label: String) {
    TEAL("诊室青（现有）"),
    CLINICAL_BLUE("临床蓝"),
    AMBER("暖杏"),
    JADE("竹青"),
    ROSE("胭脂"),
    ;

    companion object {
        val DEFAULT: AppTheme = TEAL

        fun from(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * MaterialTheme.colorScheme 之外的扩展语义色。
 * Color.kt 的顶层品牌色（Teal 系列、BlueSoft、Ink 系列、Canvas、Line 等）已改为
 * @Composable getter 委托到本对象，因此 11 个 UI 文件约 100 处调用点无需改动即可整体换色。
 */
@Immutable
data class WenzhenColors(
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val tertiary: Color,
    val container: Color,
    val soft: Color,
    val blueSoft: Color,
    val ink: Color,
    val inkMuted: Color,
    val canvas: Color,
    val line: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val onPrimary: Color,
    val onSurface: Color,
)

/** 语义标识色：全主题共用，保证「橙=中难度、玫红=心内科」这类含义不随皮肤漂移。 */
object SemanticColors {
    val Warm = Color(0xFFFFB74D)
    val Rose = Color(0xFFE57373)
    val IndigoSoft = Color(0xFFE8EAF6)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)
}

// ── TEAL 诊室青：现有配色，像素级保留 ─────────────────────────────────
private val TealLight = WenzhenColors(
    primary = Color(0xFF00796B), primaryDark = Color(0xFF004D40),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF4DB6AC),
    container = Color(0xFFD5EFEA), soft = Color(0xFFE8F5F2),
    blueSoft = Color(0xFFE1F5F8), ink = Color(0xFF16302C),
    inkMuted = Color(0xFF526B67), canvas = Color(0xFFF5FAF9),
    line = Color(0xFFD8E6E3),
    gradientStart = Color(0xFF00796B), gradientEnd = Color(0xFF0097A7),
    onPrimary = Color.White, onSurface = Color(0xFF16302C),
)
private val TealDark = TealLight.copy(
    primary = Color(0xFF80CBC4), primaryDark = Color(0xFF005047),
    secondary = Color(0xFF4DD0E1), container = Color(0xFF005047),
    soft = Color(0xFF243A36), blueSoft = Color(0xFF1D3339),
    ink = Color(0xFFE0F2EF), inkMuted = Color(0xFFB9CBC7),
    canvas = Color(0xFF0E1B19), line = Color(0xFF526B67),
    gradientStart = Color(0xFF004D40), gradientEnd = Color(0xFF00695C),
    onPrimary = Color(0xFF003731), onSurface = Color(0xFFE0F2EF),
)

// ── 01 临床蓝 Clinical Blue ──────────────────────────────────────────
private val BlueLight = WenzhenColors(
    primary = Color(0xFF1565C0), primaryDark = Color(0xFF0D47A1),
    secondary = Color(0xFF0288D1), tertiary = Color(0xFF4FC3F7),
    container = Color(0xFFD7E9FC), soft = Color(0xFFE9F1FC),
    blueSoft = Color(0xFFDDF3FC), ink = Color(0xFF12263F),
    inkMuted = Color(0xFF4C6480), canvas = Color(0xFFF6F9FE),
    line = Color(0xFFD2E0F0),
    gradientStart = Color(0xFF1565C0), gradientEnd = Color(0xFF0288D1),
    onPrimary = Color.White, onSurface = Color(0xFF12263F),
)
private val BlueDark = BlueLight.copy(
    primary = Color(0xFF90CAF9), primaryDark = Color(0xFF0D47A1),
    secondary = Color(0xFF81D4FA), container = Color(0xFF0D47A1),
    soft = Color(0xFF1B3049), blueSoft = Color(0xFF12313F),
    ink = Color(0xFFE3EEFA), inkMuted = Color(0xFFA9BFD6),
    canvas = Color(0xFF0B1622), line = Color(0xFF5B7089),
    gradientStart = Color(0xFF0D47A1), gradientEnd = Color(0xFF1565C0),
    onPrimary = Color(0xFF0A2542), onSurface = Color(0xFFE3EEFA),
)

// ── 02 暖杏 Amber ────────────────────────────────────────────────────
private val AmberLight = WenzhenColors(
    primary = Color(0xFFB45309), primaryDark = Color(0xFF7A3E06),
    secondary = Color(0xFFD97706), tertiary = Color(0xFFF59E0B),
    container = Color(0xFFFCEBD3), soft = Color(0xFFF6EDE0),
    blueSoft = Color(0xFFFDF0DA), ink = Color(0xFF3A2412),
    inkMuted = Color(0xFF7A5B3C), canvas = Color(0xFFFDF9F3),
    line = Color(0xFFE9DCC8),
    gradientStart = Color(0xFFB45309), gradientEnd = Color(0xFFD97706),
    onPrimary = Color.White, onSurface = Color(0xFF3A2412),
)
private val AmberDark = AmberLight.copy(
    primary = Color(0xFFF5B97A), primaryDark = Color(0xFF7A3E06),
    secondary = Color(0xFFFCD34D), container = Color(0xFF7A3E06),
    soft = Color(0xFF3A2818), blueSoft = Color(0xFF3A2A14),
    ink = Color(0xFFF7EDE0), inkMuted = Color(0xFFC9AC8B),
    canvas = Color(0xFF1C130B), line = Color(0xFF8C7153),
    gradientStart = Color(0xFF7A3E06), gradientEnd = Color(0xFF9A5000),
    onPrimary = Color(0xFF3A2412), onSurface = Color(0xFFF7EDE0),
)

// ── 03 竹青 Jade ─────────────────────────────────────────────────────
private val JadeLight = WenzhenColors(
    primary = Color(0xFF2E7D32), primaryDark = Color(0xFF1B5E20),
    secondary = Color(0xFF43A047), tertiary = Color(0xFF81C784),
    container = Color(0xFFDDEFD9), soft = Color(0xFFE9F3E3),
    blueSoft = Color(0xFFE4F3E1), ink = Color(0xFF142B12),
    inkMuted = Color(0xFF4C6B48), canvas = Color(0xFFF7FBF5),
    line = Color(0xFFD5E6CF),
    gradientStart = Color(0xFF2E7D32), gradientEnd = Color(0xFF43A047),
    onPrimary = Color.White, onSurface = Color(0xFF142B12),
)
private val JadeDark = JadeLight.copy(
    primary = Color(0xFFA5D6A7), primaryDark = Color(0xFF1B5E20),
    secondary = Color(0xFF81C784), container = Color(0xFF1B5E20),
    soft = Color(0xFF1D3019), blueSoft = Color(0xFF193021),
    ink = Color(0xFFE5F3E2), inkMuted = Color(0xFFAEC7A9),
    canvas = Color(0xFF0C170B), line = Color(0xFF5B7A56),
    gradientStart = Color(0xFF1B5E20), gradientEnd = Color(0xFF2E7D32),
    onPrimary = Color(0xFF142B12), onSurface = Color(0xFFE5F3E2),
)

// ── 04 胭脂 Rose ─────────────────────────────────────────────────────
private val RoseLight = WenzhenColors(
    primary = Color(0xFFB03A5B), primaryDark = Color(0xFF7A2038),
    secondary = Color(0xFFD2567A), tertiary = Color(0xFFF08FB0),
    container = Color(0xFFFCE1EA), soft = Color(0xFFF8E9EF),
    blueSoft = Color(0xFFFDE9F0), ink = Color(0xFF361624),
    inkMuted = Color(0xFF7A5061), canvas = Color(0xFFFEF8FA),
    line = Color(0xFFEEDCE4),
    gradientStart = Color(0xFFB03A5B), gradientEnd = Color(0xFFD2567A),
    onPrimary = Color.White, onSurface = Color(0xFF361624),
)
private val RoseDark = RoseLight.copy(
    primary = Color(0xFFF2A6C0), primaryDark = Color(0xFF7A2038),
    secondary = Color(0xFFF7BBD1), container = Color(0xFF7A2038),
    soft = Color(0xFF3C202C), blueSoft = Color(0xFF3A1E2B),
    ink = Color(0xFFF8E9EF), inkMuted = Color(0xFFD3ACBD),
    canvas = Color(0xFF1C0E14), line = Color(0xFF8E6273),
    gradientStart = Color(0xFF7A2038), gradientEnd = Color(0xFF9C2B4A),
    onPrimary = Color(0xFF361624), onSurface = Color(0xFFF8E9EF),
)

internal fun paletteFor(theme: AppTheme, dark: Boolean): WenzhenColors = when (theme) {
    AppTheme.TEAL -> if (dark) TealDark else TealLight
    AppTheme.CLINICAL_BLUE -> if (dark) BlueDark else BlueLight
    AppTheme.AMBER -> if (dark) AmberDark else AmberLight
    AppTheme.JADE -> if (dark) JadeDark else JadeLight
    AppTheme.ROSE -> if (dark) RoseDark else RoseLight
}

/**
 * 默认值给 TealLight 而不是 error()：Roborazzi 截图测试与部分单元测试直接渲染
 * 单个 Composable 而未包裹 WenzhenTheme，给默认配色可保持既有基线不变且不崩溃。
 */
val LocalWenzhenColors = staticCompositionLocalOf<WenzhenColors> { TealLight }

private fun schemeFor(theme: AppTheme, dark: Boolean) = paletteFor(theme, dark).let { c ->
    if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            primaryContainer = c.container, onPrimaryContainer = c.ink,
            secondary = c.secondary, onSecondary = c.onPrimary,
            secondaryContainer = c.blueSoft, onSecondaryContainer = c.ink,
            tertiary = c.tertiary, onTertiary = c.onPrimary,
            background = c.canvas, onBackground = c.ink,
            surface = c.canvas, onSurface = c.onSurface,
            surfaceVariant = c.soft, onSurfaceVariant = c.inkMuted,
            outline = c.line, outlineVariant = c.line,
            error = SemanticColors.DarkError, onError = SemanticColors.DarkOnError,
            errorContainer = SemanticColors.DarkErrorContainer,
            onErrorContainer = SemanticColors.DarkOnErrorContainer,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            primaryContainer = c.container, onPrimaryContainer = c.primaryDark,
            secondary = c.secondary, onSecondary = c.onPrimary,
            secondaryContainer = c.blueSoft, onSecondaryContainer = c.ink,
            tertiary = c.tertiary, onTertiary = c.onPrimary,
            background = c.canvas, onBackground = c.ink,
            surface = Color.White, onSurface = c.onSurface,
            surfaceVariant = c.soft, onSurfaceVariant = c.inkMuted,
            outline = c.line, outlineVariant = c.line,
            error = SemanticColors.Error, onError = Color.White,
            errorContainer = SemanticColors.ErrorContainer,
            onErrorContainer = SemanticColors.OnErrorContainer,
        )
    }
}

internal val WenzhenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * darkTheme 保持 isSystemInDarkTheme() 跟随系统，不提供三态开关。
 * ViewModel 必须在 WenzhenTheme 之外创建，否则切换主题会触发整棵 composition 重建。
 */
@Composable
fun WenzhenTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWenzhenColors provides paletteFor(theme, darkTheme)) {
        MaterialTheme(
            colorScheme = schemeFor(theme, darkTheme),
            typography = WenzhenTypography,
            shapes = WenzhenShapes,
            content = content,
        )
    }
}
