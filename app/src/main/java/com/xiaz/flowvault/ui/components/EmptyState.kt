package com.xiaz.flowvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 空态小图分别对应哪一块地方：只用来认路，不冒充数据。 */
internal enum class EmptyGlyph { TODO, LEDGER, NOVEL, DECK, MARK, TRACE }

/** 托盘直径。比一行字（18dp）高一档，又还没高过压住标题的程度。 */
private val PlateSize = 30.dp

/** 描边宽度跟卡片的实心描边取同一档，两处看起来是一套东西。 */
private val GlyphEdge = 1.5.dp

/**
 * 空态：一枚描边小图 + 一句话说清"这里为什么是空的、下一步做什么"。
 *
 * 小图全用矢量当场画，不引图标库也不靠 emoji——AOSP 系统镜像里不一定带彩色 emoji 字体，
 * 拿它当空态的锚点，在一台机器上是圆、在另一台上是一个方块。底色一层薄纱、外面一圈发丝边，
 * 层级靠"有托盘／没托盘"分，不往主色上蹭（主色只留给这一态该喊的地方）。
 */
@Composable
internal fun EmptyNote(
    glyph: EmptyGlyph,
    text: String,
    modifier: Modifier = Modifier,
) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val plateFill = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(PlateSize)) {
            val edge = Stroke(
                width = GlyphEdge.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            val radius = size.minDimension / 2f
            drawCircle(color = plateFill, radius = radius)
            drawCircle(
                color = neutral.copy(alpha = 0.32f),
                radius = radius - edge.width / 2f,
                style = edge,
            )
            // 图符画在托盘正中一个正方形区域里，占比约一半：再大就顶到描边，看着像个错。
            val g = size.minDimension * 0.50f
            val left = (size.width - g) / 2f
            val top = (size.height - g) / 2f
            when (glyph) {
                EmptyGlyph.TODO -> drawTodo(left, top, g, ink, edge)
                EmptyGlyph.LEDGER -> drawLedger(left, top, g, ink, edge)
                EmptyGlyph.NOVEL -> drawNovel(left, top, g, ink, edge)
                EmptyGlyph.DECK -> drawDeck(left, top, g, ink, edge)
                EmptyGlyph.MARK -> drawMark(left, top, g, ink, edge)
                EmptyGlyph.TRACE -> drawTrace(left, top, g, ink, edge)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = neutral,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 两行清单：左边一格方框，右边一条短线。 */
private fun DrawScope.drawTodo(left: Float, top: Float, g: Float, ink: Color, edge: Stroke) {
    val box = g * 0.26f
    listOf(g * 0.12f, g * 0.62f).forEach { dy ->
        drawRoundRect(
            color = ink,
            topLeft = Offset(left, top + dy),
            size = Size(box, box),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = edge,
        )
        drawLine(
            color = ink.copy(alpha = 0.55f),
            start = Offset(left + box + g * 0.14f, top + dy + box / 2f),
            end = Offset(left + g, top + dy + box / 2f),
            strokeWidth = edge.width,
            cap = StrokeCap.Round,
        )
    }
}

/** 一枚方孔钱：圆外圈 + 当中一个小方。 */
private fun DrawScope.drawLedger(left: Float, top: Float, g: Float, ink: Color, edge: Stroke) {
    val c = Offset(left + g / 2f, top + g / 2f)
    drawCircle(color = ink, radius = g / 2f, center = c, style = edge)
    val hole = g * 0.34f
    drawRoundRect(
        color = ink.copy(alpha = 0.7f),
        topLeft = Offset(c.x - hole / 2f, c.y - hole / 2f),
        size = Size(hole, hole),
        cornerRadius = CornerRadius(1.dp.toPx()),
        style = edge,
    )
}

/** 一本书：封面外框 + 靠左一根书脊。 */
private fun DrawScope.drawNovel(left: Float, top: Float, g: Float, ink: Color, edge: Stroke) {
    val w = g * 0.82f
    val x = left + (g - w) / 2f
    drawRoundRect(
        color = ink,
        topLeft = Offset(x, top),
        size = Size(w, g),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = edge,
    )
    drawLine(
        color = ink.copy(alpha = 0.6f),
        start = Offset(x + w * 0.24f, top + g * 0.12f),
        end = Offset(x + w * 0.24f, top + g * 0.88f),
        strokeWidth = edge.width,
        cap = StrokeCap.Round,
    )
}

/** 两张卡错开叠着：后面那张淡，前面那张实。 */
private fun DrawScope.drawDeck(left: Float, top: Float, g: Float, ink: Color, edge: Stroke) {
    val w = g * 0.68f
    val h = g * 0.86f
    val shift = g * 0.16f
    drawRoundRect(
        color = ink.copy(alpha = 0.4f),
        topLeft = Offset(left + shift, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = edge,
    )
    drawRoundRect(
        color = ink,
        topLeft = Offset(left, top + shift),
        size = Size(w, h),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = edge,
    )
}

/** 书签：一块下沿剪了 V 口的薄纱加一圈描边。 */
private fun DrawScope.drawMark(left: Float, top: Float, g: Float, ink: Color, edge: Stroke) {
    val w = g * 0.66f
    val x = left + (g - w) / 2f
    val shape = Path().apply {
        moveTo(x, top)
        lineTo(x + w, top)
        lineTo(x + w, top + g)
        lineTo(x + w / 2f, top + g * 0.72f)
        lineTo(x, top + g)
        close()
    }
    drawPath(shape, ink.copy(alpha = 0.14f))
    drawPath(shape, ink, style = edge)
}

/** 时间线：一根竖线串三个点，对应"这一天还没留下动作"。 */
private fun DrawScope.drawTrace(left: Float, top: Float, g: Float, ink: Color, edge: Stroke) {
    val x = left + g * 0.3f
    drawLine(
        color = ink.copy(alpha = 0.4f),
        start = Offset(x, top + g * 0.1f),
        end = Offset(x, top + g * 0.9f),
        strokeWidth = edge.width,
        cap = StrokeCap.Round,
    )
    val dot = edge.width * 1.35f
    listOf(g * 0.1f, g * 0.5f, g * 0.9f).forEach { dy ->
        drawCircle(color = ink, radius = dot, center = Offset(x, top + dy))
    }
    drawLine(
        color = ink.copy(alpha = 0.7f),
        start = Offset(x + dot * 2f, top + g * 0.5f),
        end = Offset(left + g, top + g * 0.5f),
        strokeWidth = edge.width,
        cap = StrokeCap.Round,
    )
}
