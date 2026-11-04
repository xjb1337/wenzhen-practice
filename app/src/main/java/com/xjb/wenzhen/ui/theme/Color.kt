package com.xjb.wenzhen.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 品牌色 ────────────────────────────────────────────────────────────────
// 这些常量名被 11 个 UI 文件约 100 处直接引用。改成由 WenzhenTheme 提供的
// CompositionLocal getter 后，切换主题时无需改动任何调用点即可整体换色。
// 只能在 @Composable 上下文中读取（含 @Composable 函数的参数表达式、Modifier 链）。
val TealPrimary: Color
    @Composable get() = LocalWenzhenColors.current.primary
val TealPrimaryDark: Color
    @Composable get() = LocalWenzhenColors.current.primaryDark
val TealSecondary: Color
    @Composable get() = LocalWenzhenColors.current.secondary
val TealTertiary: Color
    @Composable get() = LocalWenzhenColors.current.tertiary
val TealContainer: Color
    @Composable get() = LocalWenzhenColors.current.container
val TealSoft: Color
    @Composable get() = LocalWenzhenColors.current.soft
val BlueSoft: Color
    @Composable get() = LocalWenzhenColors.current.blueSoft
val Ink: Color
    @Composable get() = LocalWenzhenColors.current.ink
val InkMuted: Color
    @Composable get() = LocalWenzhenColors.current.inkMuted
val Canvas: Color
    @Composable get() = LocalWenzhenColors.current.canvas
val Line: Color
    @Composable get() = LocalWenzhenColors.current.line
// 大色块渐变专用：深色配色板里刻意压暗，保证叠加其上的白色文字仍可读。
val GradientStart: Color
    @Composable get() = LocalWenzhenColors.current.gradientStart
val GradientEnd: Color
    @Composable get() = LocalWenzhenColors.current.gradientEnd

// ── 语义标识色 ────────────────────────────────────────────────────────────
// 按设计决策固定，不随皮肤漂移：橙=评分星标/消化内科，玫红=心内科，
// 靛蓝=高难度。它们也会被非 @Composable 的纯函数（科室图标/配色）使用。
val Warm = Color(0xFFFFB74D)
val Rose = Color(0xFFE57373)
val IndigoSoft = Color(0xFFE8EAF6)

// 旧深色常量：仅供兼容引用，深色配色统一由 paletteFor(theme, true) 提供。
val DarkTealPrimary = Color(0xFF80CBC4)
val DarkTealSecondary = Color(0xFF4DD0E1)
val DarkCanvas = Color(0xFF0E1B19)
val DarkSurface = Color(0xFF132421)
val DarkInk = Color(0xFFE0F2EF)

// ── 非 @Composable 上下文使用的固定品牌色 ──────────────────────────────────
// CommonComponents.departmentForeground() 是纯函数，不能读 CompositionLocal，
// 科室语义色按设计决策保持固定，因此保留一份字面量。
object FixedBrandColors {
    val Primary = Color(0xFF00796B)
    val Secondary = Color(0xFF0097A7)
}
