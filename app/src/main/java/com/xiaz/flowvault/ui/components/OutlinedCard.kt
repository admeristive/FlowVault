package com.xiaz.flowvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * 描边卡片：靠 1dp 边框分层，不用阴影。
 *
 * 普通段不落底、不围边，只在头顶压一条发丝线——一屏里只让主角那一张有盒子，
 * 层级靠"有边／无边"这一下分出来，不靠把某张卡涂成彩色（色板里的语义色只留给状态）。
 */
@Composable
internal fun OutlinedCard(
    title: String,
    modifier: Modifier = Modifier,
    role: CardRole = CardRole.PLAIN,
    content: @Composable () -> Unit,
) {
    val hero = role == CardRole.HERO
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hero) {
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, hairline(), MaterialTheme.shapes.medium)
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** 尚未接入的能力一律写清在第几阶段落地，界面里不留假数据。 */
@Composable
internal fun PendingNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
