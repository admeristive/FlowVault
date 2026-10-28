package com.xiaz.flowvault.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaz.flowvault.core.model.Pomodoro
import com.xiaz.flowvault.core.model.Todo
import com.xiaz.flowvault.ui.components.ActionLabel
import com.xiaz.flowvault.ui.components.CaptureBar
import com.xiaz.flowvault.ui.components.CardFlow
import com.xiaz.flowvault.ui.components.CardRole
import com.xiaz.flowvault.ui.components.EmptyGlyph
import com.xiaz.flowvault.ui.components.EmptyNote
import com.xiaz.flowvault.ui.components.OutlinedCard
import com.xiaz.flowvault.ui.components.TodoRow
import com.xiaz.flowvault.ui.components.headCount
import com.xiaz.flowvault.ui.state.DayBoard
import com.xiaz.flowvault.ui.state.PomodoroClock

@Composable
internal fun FocusScreen(
    board: DayBoard,
    clock: PomodoroClock,
    modifier: Modifier = Modifier,
) {
    val data by board.snapshot.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        CardFlow(
            cards = listOf(
                {
                    OutlinedCard("专注计时", role = CardRole.HERO) {
                        TimerBlock(clock, data.focusSec, data.todos)
                    }
                },
                {
                    OutlinedCard("这段时间要做的事" + headCount(data.todos.size, "条")) {
                        Spacer(Modifier.height(8.dp))
                        if (data.todos.isEmpty()) {
                            EmptyNote(
                                EmptyGlyph.TODO,
                                "还没有排进来的事，用下面的输入条丢一条",
                            )
                        } else {
                            data.todos.forEach { todo ->
                                TodoRow(todo) {
                                    board.setStatus(
                                        todo.id,
                                        if (todo.status == Todo.STATUS_DONE) Todo.STATUS_OPEN else Todo.STATUS_DONE,
                                    ) {}
                                }
                            }
                        }
                    }
                },
                {
                    OutlinedCard("闪念速记") {
                        Spacer(Modifier.height(8.dp))
                        CaptureBar(placeholder = "想到什么先丢进来") { raw, then -> board.submit(raw, then) }
                    }
                },
            ),
        )
    }
}

@Composable
private fun TimerBlock(clock: PomodoroClock, focusSec: Int, todos: List<Todo>) {
    val current by clock.timing.collectAsStateWithLifecycle()
    val bound by clock.boundTodo.collectAsStateWithLifecycle()
    val open = todos.filter { it.status == Todo.STATUS_OPEN }

    // 这一屏的主角：今天真正走完的专注分钟数。没在计时的时候，这张卡全靠它有内容。
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "${focusSec / 60}",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "分钟 · 今天走完",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Spacer(Modifier.height(12.dp))

    val timing = current ?: run {
        // 这一趟算在哪件事头上——横着滚，标题在 12 字处掐断：整句在下面的清单里看得到，
        // 这里只要认得出是哪一条。一条都不选就记成"没绑"，那趟照样走，只是不摊给任何事。
        if (open.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ActionLabel("不绑", option = true, selected = bound == null) { clock.bind(null) }
                open.forEach { todo ->
                    ActionLabel(todo.title.take(12), option = true, selected = bound == todo.id) { clock.bind(todo.id) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FOCUS_MINUTES.forEach { minutes ->
                ActionLabel("$minutes 分") { clock.start(Pomodoro.KIND_FOCUS, minutes * 60, bound) }
            }
            ActionLabel("休息 ${Pomodoro.BREAK_DEFAULT_SEC / 60} 分") {
                clock.start(Pomodoro.KIND_BREAK, Pomodoro.BREAK_DEFAULT_SEC)
            }
        }
        return
    }

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = Pomodoro.clockText(maxOf(timing.plannedSec - timing.elapsedSec, 0)),
            style = MaterialTheme.typography.headlineSmall,
            color = if (timing.running) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = Pomodoro.label(timing.kind) + if (timing.running) "进行中" else "已停下",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    // 走着的时候把"算在哪件事上"钉在计时卡里：中途上面那排选中态改了也不影响这一趟，
    // 这里显示的是开行那一刻记进库的那条，不是当前选中值。
    if (timing.kind == Pomodoro.KIND_FOCUS) {
        timing.todoId?.let { id ->
            todos.firstOrNull { it.id == id }?.let { todo ->
                Text(
                    text = "这趟算在：${todo.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (timing.running) {
            ActionLabel("停下") { clock.pause() }
        } else {
            ActionLabel("接着走") { clock.resume() }
        }
        ActionLabel("结束这趟") { clock.stop() }
    }
}

private val FOCUS_MINUTES = listOf(15, 25, 45)
