package com.xiaz.flowvault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 固定色板，不跟手机壁纸走：三态靠版式和描边区分，不靠大面积彩色。
// 面色分五级容器（越靠上越"凹"），卡片浮在页底上、嵌套块再往回沉一档。
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2A4C7D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF465464),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8F9),
    onBackground = Color(0xFF14171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171A),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF5B636B),
    outline = Color(0xFFD6DBE0),
    outlineVariant = Color(0xFFE7EBEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF3F5F7),
    surfaceContainerHigh = Color(0xFFEAEEF1),
    surfaceContainerHighest = Color(0xFFE1E5E9),
    surfaceDim = Color(0xFFE4E8EC),
    surfaceBright = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF83ABD8),
    onPrimary = Color(0xFF0F1620),
    secondary = Color(0xFFA7B2BE),
    onSecondary = Color(0xFF12161A),
    background = Color(0xFF101315),
    onBackground = Color(0xFFE4E7E9),
    surface = Color(0xFF161A1D),
    onSurface = Color(0xFFE4E7E9),
    surfaceVariant = Color(0xFF1D2227),
    onSurfaceVariant = Color(0xFF9BA4AD),
    outline = Color(0xFF313840),
    outlineVariant = Color(0xFF232A31),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF14171A),
    surfaceContainer = Color(0xFF181C20),
    surfaceContainerHigh = Color(0xFF1D2226),
    surfaceContainerHighest = Color(0xFF22272C),
    surfaceDim = Color(0xFF0F1214),
    surfaceBright = Color(0xFF1F252A),
)

/**
 * 一整套字号自己写死，不吃 Material 的默认标度。默认那套是给英文界面定的：
 * 中文上行距偏紧、字重全平，屏幕上就是一片齐刷刷的灰——之前整个界面 60 处文字全在它的档位上。
 * 只碰字号、行距、字重、字距这四件，配色一律留给色板。
 */
private fun type(size: Int, line: Int, weight: FontWeight, track: Double): TextStyle = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    // 表格数字：倒计时和金额列跳一秒/一分不左右挪。系统字体不认时只是不齐，不会报错。
    fontFeatureSettings = "tnum",
    letterSpacing = track.sp,
)

private val FlowVaultTypography = Typography(
    // 大字读数：专注计时器。负字距只给这种大数字，中文标题压负值会挤成一块。
    displayLarge = type(24, 28, FontWeight.SemiBold, -0.3),
    displayMedium = type(24, 28, FontWeight.SemiBold, -0.3),
    displaySmall = type(24, 28, FontWeight.SemiBold, -0.3),
    headlineLarge = type(20, 26, FontWeight.SemiBold, 0.0),
    headlineMedium = type(20, 26, FontWeight.SemiBold, 0.0),
    headlineSmall = type(24, 28, FontWeight.SemiBold, -0.3),
    titleLarge = type(16, 22, FontWeight.SemiBold, 0.0),
    titleMedium = type(14, 20, FontWeight.Medium, 0.0),
    // 卡片标题
    titleSmall = type(14, 20, FontWeight.SemiBold, 0.0),
    // 正文：待办标题、备注、闪念。"朴素"的一半来自行距太紧，这里给到 1.57 倍。
    bodyLarge = type(14, 22, FontWeight.Normal, 0.1),
    bodyMedium = type(14, 22, FontWeight.Normal, 0.1),
    // 次要：说明句、空态句
    bodySmall = type(13, 18, FontWeight.Normal, 0.1),
    // 按钮
    labelLarge = type(13, 16, FontWeight.Medium, 0.2),
    // 标签、时间列、金额、比分——用得最多的一档
    labelMedium = type(12, 16, FontWeight.Medium, 0.3),
    labelSmall = type(12, 14, FontWeight.Normal, 0.2),
)

// 五档圆角实际只有三个在用（small 7 处、medium 2 处、extraSmall 1 处）。
// 原来 5/7/9 挨得太近，看着像随机数；收成 4-6-8-10 的等差，大圆角那两档跟它们并掉。
private val FlowVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
internal fun FlowVaultTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = FlowVaultTypography,
        shapes = FlowVaultShapes,
        content = content,
    )
}

/**
 * 发丝描边：不取一块固定灰，取当前字色蒙一层薄纱。底色在容器五级里怎么挪，描边都跟得上，
 * 不会出现"卡片换了底、边框还留着上一块底的颜色"那种硬边。
 * 暗底要的 alpha 比亮底大——同一层纱落在黑底上几乎看不见。
 */
@Composable
internal fun hairline(): Color {
    val scheme = MaterialTheme.colorScheme
    return scheme.onSurface.copy(alpha = if (scheme.isDarkSurface()) 0.20f else 0.12f)
}

/**
 * 语义色只留给"状态"：完成了、没过、日志里的警告。别处一律不上——
 * 屏幕上只要同时出现三四种颜色，读的人就得分心去找哪个是重点。
 * 明暗各一套，都是压过饱和度的：暗底上的鲜绿会亮成指示灯。
 */
internal data class StateColors(val good: Color, val warn: Color, val bad: Color)

private val LightState = StateColors(
    good = Color(0xFF2F6B4C),
    warn = Color(0xFF8A5A2B),
    bad = Color(0xFF8C3B3B),
)

private val DarkState = StateColors(
    good = Color(0xFF74B18C),
    warn = Color(0xFFC89B5C),
    bad = Color(0xFFD18B8B),
)

@Composable
internal fun stateColors(): StateColors =
    if (MaterialTheme.colorScheme.isDarkSurface()) DarkState else LightState

private fun ColorScheme.isDarkSurface(): Boolean = surface.luminance() < 0.5f
