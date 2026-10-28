package com.xiaz.flowvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.ui.theme.hairline

/**
 * 内容列的宽度上限。手机竖屏碰不到这个数（约 360–411dp），所以这条只在横屏、
 * 桌面模式和平板上生效——那时候一行字拉到七八十字就数不着边际，读不动。
 */
internal val ContentMaxWidth = 720.dp

/** 越过这条线就分两列：手机竖屏 360–411dp 碰不到，横屏／桌面模式／平板会越过。 */
internal val WideBreakpoint = 720.dp

/** 两列时整列内容的上限。再宽就只是两边留白，一行字数又开始失控。 */
internal val WideContentMaxWidth = 1080.dp

/** 这一屏的两种卡。主角卡带那一颗大读数，一屏只给一张；其余都是普通段。 */
internal enum class CardRole { HERO, PLAIN }

/**
 * 同色铺底的厚度。强调不靠另换一块彩底，靠把这一态的主色在底上压一层薄纱——
 * 8% 是量出来的：再浅看不出跟邻块的区别，到 12% 时铺满一屏就成了一片色纸。
 */
private const val TINT_FILL = 0.08f

/** 主角卡那圈描边的浓度：比发丝线实，但离纯色还有一档，免得整屏只有一个重点在喊。 */
private const val HERO_EDGE = 0.45f

/**
 * 赛璐璐卡片：一块底、一圈实心描边、标题前一根实色竖条，不靠阴影。
 *
 * 普通段不落底、不围边，只在头顶压一条发丝线加同一根竖条——一屏里只让主角那一张有盒子，
 * 层级靠"有底／无底"分，强调靠"同色薄纱"分，都不靠把某张卡涂成语义色（那几个色只留给状态）。
 */
@Composable
internal fun OutlinedCard(
    title: String,
    modifier: Modifier = Modifier,
    role: CardRole = CardRole.PLAIN,
    content: @Composable () -> Unit,
) {
    val hero = role == CardRole.HERO
    val accent = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hero) {
                    Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .background(accent.copy(alpha = TINT_FILL))
                        .border(1.5.dp, accent.copy(alpha = HERO_EDGE), shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = if (hero) 16.dp else 12.dp),
    ) {
        if (!hero) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(hairline()))
            Spacer(Modifier.height(11.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 竖条只在主角卡上染主色：普通段也来一根的话，一屏就是七八根同色旗，
            // 主色立刻不值钱，这一态的颜色也就喊不出来了。普通段退回中性色，
            // 只当"这里起一段"的记号。粗细仍给 3dp：2dp 在 2.75 倍屏上跟描边分不出来。
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (hero) 14.dp else 12.dp)
                    .background(if (hero) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (hero) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/**
 * 一屏的卡片流：窄屏排一列，宽屏劈成左右两列。
 * 两列只是按顺序折半，不做真瀑布流——那要预先知道每张卡多高，而这一屏的卡高全凭数据
 * （清单几条、轨迹几条）当场才定得下来。少于三张也不分列，两列里空一列更难看。
 */
@Composable
internal fun CardFlow(
    modifier: Modifier = Modifier,
    gap: Dp = 16.dp,
    cards: List<@Composable () -> Unit>,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < WideBreakpoint || cards.size < 3) {
            // 一列的时候也要掐住 720：不然窗口很宽、卡又不足三张，正文就摊满整屏宽，行数失控。
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = ContentMaxWidth),
            ) { Stacked(cards, gap) }
        } else {
            val half = (cards.size + 1) / 2
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { Stacked(cards.subList(0, half), gap) }
                Spacer(Modifier.width(gap))
                Column(Modifier.weight(1f)) { Stacked(cards.subList(half, cards.size), gap) }
            }
        }
    }
}

@Composable
private fun ColumnScope.Stacked(cards: List<@Composable () -> Unit>, gap: Dp) {
    cards.forEachIndexed { index, card ->
        card()
        if (index < cards.lastIndex) Spacer(Modifier.height(gap))
    }
}

/**
 * 分组头后面缀的真实计数。空的时候不写"0 条"：那串零看着像闸坏了，
 * 而同一张卡里的空态那句话已经把"这里还没有东西"说过了。
 */
internal fun headCount(count: Int, word: String): String =
    if (count <= 0) "" else " · $count $word"
