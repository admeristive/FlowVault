package com.xiaz.flowvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.core.model.Flash
import com.xiaz.flowvault.ui.state.Toll
import com.xiaz.flowvault.ui.state.TollStage
import com.xiaz.flowvault.ui.theme.stateColors

/**
 * 过路费闸的那张卡面。`blocking` 为真时它吃满整屏并吞掉点击——正文不该从缝里露出来。
 * 所有点击都走 [ActionLabel]，遮罩自己也显式关掉波纹：这台机器上任何带状态层的瞬时浮层都会碎成雪花。
 */
@Composable
internal fun TollPanel(
    toll: Toll,
    blocking: Boolean,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (toll.stage == TollStage.IDLE) return
    val content: @Composable () -> Unit = { QuizBody(toll, onReveal, onAnswer, onDismiss, onRetry) }
    if (blocking) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) { content() }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun QuizBody(
    toll: Toll,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${Flash.contextLabel(toll.context)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "要答对 ${toll.need} 张才放行",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))

    when (toll.stage) {
        TollStage.QUIZ -> {
            val card = toll.card
            if (card == null) {
                Text(text = "在记这一笔…", style = MaterialTheme.typography.bodyMedium)
                return
            }
            Text(
                text = "第 ${toll.index + 1} / ${toll.asked} 张 · ${Flash.boxLabel(card.box)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = card.front, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            if (toll.revealed) {
                Text(
                    text = card.back,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionLabel("记住了") { onAnswer(true) }
                    ActionLabel("没记住") { onAnswer(false) }
                    Spacer(Modifier.weight(1f))
                    ActionLabel("先不答了") { onDismiss() }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionLabel("看答案") { onReveal() }
                    Spacer(Modifier.weight(1f))
                    ActionLabel("先不答了") { onDismiss() }
                }
            }
        }

        TollStage.PASSED, TollStage.BLOCKED -> {
            val attempt = toll.result
            if (attempt == null) {
                // 结算那笔没写进库（库半路坏过）。界面不能因此一句话都不出、连口子都不留。
                Text(
                    text = "这一笔没记进库：原因在日志浮窗里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = stateColors().warn,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionLabel("再来一遍") { onRetry() }
                    ActionLabel("先不读了") { onDismiss() }
                }
                return
            }
            Text(
                text = when {
                    attempt.passed -> "问 ${attempt.asked} 张对 ${attempt.correct} 张 · 放行"
                    else -> "问 ${attempt.asked} 张只对了 ${attempt.correct} 张，要 ${toll.need} 张才够"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (attempt.passed) stateColors().good else stateColors().bad,
            )
            Spacer(Modifier.height(8.dp))
            if (attempt.passed) {
                if (attempt.grantedSec > 0) {
                    Text(
                        text = "这张票买 ${attempt.grantedSec / 60} 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (attempt.context == Flash.CTX_DAILY_SPOT) {
                    ActionLabel("知道了") { onDismiss() }
                }
            } else {
                Text(
                    text = "错的几张还留在到期里，再来一遍就跑不掉",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionLabel("再来一遍") { onRetry() }
                    ActionLabel("先不读了") { onDismiss() }
                }
            }
        }

        else -> Unit
    }
}
