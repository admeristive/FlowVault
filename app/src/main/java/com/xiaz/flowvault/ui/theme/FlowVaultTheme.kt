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
import com.xiaz.flowvault.core.state.GlobalMode

/**
 * 三态各带一个色温：攻坚番茄、回血叶绿、日常天青。切过去整屏换一个颜色，
 * 不用读标题就知道自己在哪一态。彩度只给强调那一层，底始终是奶白／墨底。
 */
enum class ModeTint { BERRY, LEAF, SKY }

internal fun GlobalMode.tint(): ModeTint = when (this) {
    GlobalMode.FOCUS -> ModeTint.BERRY
    GlobalMode.BREAK -> ModeTint.LEAF
    GlobalMode.DAILY -> ModeTint.SKY
}

// 明档三个主色都压在 4.7:1 以上：primary 在分段控件和按钮里是当文字用的，
// 落在染色底上一旦低于 4.5 就成了看不清的小字。暗档反过来要提亮，不能沿用。
private fun lightAccent(tint: ModeTint): Color = when (tint) {
    ModeTint.BERRY -> Color(0xFFA6423A)
    ModeTint.LEAF -> Color(0xFF35704A)
    ModeTint.SKY -> Color(0xFF35699A)
}

private fun darkAccent(tint: ModeTint): Color = when (tint) {
    ModeTint.BERRY -> Color(0xFFF0A79C)
    ModeTint.LEAF -> Color(0xFF9BD1A8)
    ModeTint.SKY -> Color(0xFF9CC6EA)
}

private fun darkOnAccent(tint: ModeTint): Color = when (tint) {
    ModeTint.BERRY -> Color(0xFF3A1310)
    ModeTint.LEAF -> Color(0xFF0E2413)
    ModeTint.SKY -> Color(0xFF10293C)
}

// 面色分五级容器（越靠上越"凹"）。这一套往暖里偏：奶白底配暖墨字，
// 冷灰底上放彩色只会显得脏。
private val LightBase = lightColorScheme(
    primary = Color(0xFFA6423A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7A5F63),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF6F3),
    onBackground = Color(0xFF3B2F33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF3B2F33),
    surfaceVariant = Color(0xFFF7EDE9),
    onSurfaceVariant = Color(0xFF6E5F63),
    outline = Color(0xFFE6D8D3),
    outlineVariant = Color(0xFFF0E5E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF9F7),
    surfaceContainer = Color(0xFFF8F1EE),
    surfaceContainerHigh = Color(0xFFF2E7E3),
    surfaceContainerHighest = Color(0xFFEBDDD8),
    surfaceDim = Color(0xFFE8DCD8),
    surfaceBright = Color(0xFFFFFFFF),
)

private val DarkBase = darkColorScheme(
    primary = Color(0xFFF0A79C),
    onPrimary = Color(0xFF3A1310),
    secondary = Color(0xFFC7AEB3),
    onSecondary = Color(0xFF3A2C30),
    background = Color(0xFF191519),
    onBackground = Color(0xFFEDE4E7),
    surface = Color(0xFF211C22),
    onSurface = Color(0xFFEDE4E7),
    surfaceVariant = Color(0xFF2B2429),
    onSurfaceVariant = Color(0xFFB9A9AE),
    outline = Color(0xFF4A3F45),
    outlineVariant = Color(0xFF352D33),
    surfaceContainerLowest = Color(0xFF141014),
    surfaceContainerLow = Color(0xFF1D181E),
    surfaceContainer = Color(0xFF221C23),
    surfaceContainerHigh = Color(0xFF29222A),
    surfaceContainerHighest = Color(0xFF312930),
    surfaceDim = Color(0xFF120F13),
    surfaceBright = Color(0xFF2E272F),
)

private fun lightScheme(tint: ModeTint): ColorScheme = LightBase.copy(primary = lightAccent(tint))

private fun darkScheme(tint: ModeTint): ColorScheme = DarkBase.copy(
    primary = darkAccent(tint),
    onPrimary = darkOnAccent(tint),
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

// 赛璐璐的形是圆胖的：原来 4-6-8-10 那档是工控台的收法，整层放到 6-10-14-18。
private val FlowVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
internal fun FlowVaultTheme(dark: Boolean, tint: ModeTint, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkScheme(tint) else lightScheme(tint),
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
 * 完成态特意走青绿而不是叶绿，否则在回血态里它跟主色撞成同一个颜色，分不清是状态还是装饰。
 */
internal data class StateColors(val good: Color, val warn: Color, val bad: Color)

private val LightState = StateColors(
    good = Color(0xFF1F6B63),
    warn = Color(0xFF8A5A2B),
    bad = Color(0xFF8C3B3B),
)

private val DarkState = StateColors(
    good = Color(0xFF7FC3BB),
    warn = Color(0xFFC89B5C),
    bad = Color(0xFFD18B8B),
)

@Composable
internal fun stateColors(): StateColors =
    if (MaterialTheme.colorScheme.isDarkSurface()) DarkState else LightState

private fun ColorScheme.isDarkSurface(): Boolean = surface.luminance() < 0.5f
