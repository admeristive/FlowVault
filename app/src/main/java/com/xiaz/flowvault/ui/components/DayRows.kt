package com.xiaz.flowvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.core.model.Capture
import com.xiaz.flowvault.core.model.LedgerTx
import com.xiaz.flowvault.core.model.TimelineEvent
import com.xiaz.flowvault.core.model.Todo
import com.xiaz.flowvault.core.util.Money
import com.xiaz.flowvault.ui.theme.hairline
import com.xiaz.flowvault.ui.theme.stateColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 分钟数 -> 14:00。没排时间段的待办显示一个横杠，不用"未定时"这种底层说法。 */
internal fun clockOf(minuteOfDay: Int?): String =
    if (minuteOfDay == null) "—" else "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

private val clockAt = SimpleDateFormat("HH:mm", Locale.US)

// 一行的上下留白、列与列的间隔、数字列的宽度都从这三条走，别在每个行里各写各的。
private val RowGap = 8.dp
private val ColGap = 12.dp
private val TimeCol = 48.dp

/**
 * 一条待办。点整行就是切完成/未完成，勾框自己画：Material 的 Checkbox 带状态层，
 * 在这台荣耀上会闪成噪点。
 */
@Composable
internal fun TodoRow(
    todo: Todo,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val done = todo.status == Todo.STATUS_DONE
    val dropped = todo.status == Todo.STATUS_DROPPED
    val rowSource = remember { MutableInteractionSource() }
    // 勾内块常驻不增删，只让透明度动：if 起来的图层是即时创建/销毁，正是这台机器碎成雪花的那条文路。
    val markAlpha by animateFloatAsState(
        targetValue = if (done || dropped) 1f else 0f,
        animationSpec = tween(durationMillis = if (done || dropped) 120 else 180),
        label = "todoMarkAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(pressedFill(rowSource))
            .clickable(
                interactionSource = rowSource,
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .border(1.dp, hairline(), MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .background(
                        (if (dropped) MaterialTheme.colorScheme.outline else stateColors().good)
                            .copy(alpha = markAlpha),
                    ),
            )
        }
        Spacer(Modifier.width(ColGap))
        Text(
            text = clockOf(todo.startMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(TimeCol),
        )
        Text(
            text = todo.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done || dropped) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // "2/4"这种比要有计划数才成得了比，而计划数目前没人填。没填也不让这一格空着：
        // 走完几趟是库里真数出来的，就报真数。
        if (todo.donePomos > 0 || todo.plannedPomos > 0) {
            Spacer(Modifier.width(ColGap))
            Text(
                text = if (todo.plannedPomos > 0) "${todo.donePomos}/${todo.plannedPomos}"
                else "${todo.donePomos} 趟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一笔流水：分类、备注、金额三列对齐。金额是这一屏里唯一上色的数字——支给暗红、进给暗绿，正负号照旧留着。 */
@Composable
internal fun LedgerRow(tx: LedgerTx, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clockAt.format(Date(tx.occurredAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(TimeCol),
        )
        Spacer(Modifier.width(ColGap))
        Text(
            text = tx.categoryName ?: "未归类",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(ColGap))
        Text(
            text = tx.note ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(ColGap))
        Text(
            text = Money.signed(tx.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                tx.amountCents < 0 -> stateColors().bad
                tx.amountCents > 0 -> stateColors().good
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 时间线一行。库里的英文键在这里翻成中文，界面不出现 todo / ledger 这种字段名。 */
@Composable
internal fun TimelineRow(event: TimelineEvent, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clockAt.format(Date(event.ts)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(TimeCol),
        )
        Spacer(Modifier.width(ColGap))
        Text(
            text = "${moduleWord(event.module)} · ${actionWord(event.action)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = event.payload ?: "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 这条闪念是读书时记下的，就把原文位置带回来——回血态和日常态之间只有这一条真实联系。
 * 书被删过就只剩位置没有名字，那时报"读书时记的"，不编书名。
 */
internal fun captureNote(capture: Capture?): String? {
    if (capture == null) return null
    val offset = capture.novelOffset ?: return null
    val name = capture.novelName ?: return "读书时记的 · 第 $offset 字"
    return "《$name》第 $offset 字"
}

private fun moduleWord(module: String): String = when (module) {
    "todo" -> "待办"
    "ledger" -> "记账"
    "capture" -> "闪念"
    // 专注和休息都记在同一个模块下，具体是哪一种在正文里，标签别替它选边。
    "pomo" -> "番茄"
    "novel" -> "小说"
    "flash" -> "闪卡"
    else -> module
}

private fun actionWord(action: String): String = when (action) {
    "add" -> "记下"
    "done" -> "完成"
    "drop" -> "放弃"
    "delete" -> "删除"
    "start" -> "开始"
    "finish" -> "走完"
    "abort" -> "中断"
    "fuse" -> "超时收了"
    "pass" -> "放行"
    "fail" -> "没过"
    else -> action
}
