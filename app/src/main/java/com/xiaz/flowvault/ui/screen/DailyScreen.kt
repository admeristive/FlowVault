package com.xiaz.flowvault.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaz.flowvault.core.model.Flash
import com.xiaz.flowvault.core.model.Todo
import com.xiaz.flowvault.core.util.Money
import com.xiaz.flowvault.ui.components.ActionLabel
import com.xiaz.flowvault.ui.components.CaptureBar
import com.xiaz.flowvault.ui.components.CardFlow
import com.xiaz.flowvault.ui.components.CardRole
import com.xiaz.flowvault.ui.components.EmptyGlyph
import com.xiaz.flowvault.ui.components.EmptyNote
import com.xiaz.flowvault.ui.components.LedgerRow
import com.xiaz.flowvault.ui.components.OutlinedCard
import com.xiaz.flowvault.ui.components.TimelineRow
import com.xiaz.flowvault.ui.components.captureNote
import com.xiaz.flowvault.ui.components.TollPanel
import com.xiaz.flowvault.ui.components.TodoRow
import com.xiaz.flowvault.ui.components.headCount
import com.xiaz.flowvault.ui.state.BackupKit
import com.xiaz.flowvault.ui.state.BackupState
import com.xiaz.flowvault.ui.state.DayBoard
import com.xiaz.flowvault.ui.state.DeckShelf
import com.xiaz.flowvault.ui.state.NeedsReauth
import com.xiaz.flowvault.ui.state.NovelShelf
import com.xiaz.flowvault.ui.state.PomodoroClock
import com.xiaz.flowvault.ui.state.TollStage

@Composable
internal fun DailyScreen(
    board: DayBoard,
    decks: DeckShelf,
    shelf: NovelShelf,
    clock: PomodoroClock,
    backup: BackupKit,
    modifier: Modifier = Modifier,
) {
    val data by board.snapshot.collectAsStateWithLifecycle()
    val deckState by decks.state.collectAsStateWithLifecycle()
    val backupState by backup.state.collectAsStateWithLifecycle()

    // 番茄钟走满是在计时器那边写的库，这边不会自动知道——进这一屏就重读一次。闪卡同理。
    LaunchedEffect(Unit) {
        board.refresh()
        decks.refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        CardFlow(
            cards = listOf(
                {
                    OutlinedCard("今天的时间" + headCount(data.todos.size, "条")) {
                        Spacer(Modifier.height(8.dp))
                        if (data.todos.isEmpty()) {
                            EmptyNote(EmptyGlyph.TODO, "还没有排进来的事")
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
                        if (data.focusCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "今天走完 ${data.focusCount} 趟专注",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                {
                    OutlinedCard(
                        title = "记账" + headCount(data.txs.size, "笔"),
                        role = CardRole.HERO,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        CaptureBar(placeholder = "-15 午饭 · +500 工资") { raw, then -> board.submit(raw, then) }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Figure("今天花掉", Money.signed(data.outCents), Modifier.weight(1f))
                            Figure("今天进账", Money.signed(data.inCents), Modifier.weight(1f))
                            Figure("本月合计", Money.signed(data.monthCents), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        if (data.txs.isEmpty()) {
                            EmptyNote(EmptyGlyph.LEDGER, "今天还没有记过钱")
                        } else {
                            data.txs.forEach { LedgerRow(it) }
                        }
                    }
                },
                {
                    OutlinedCard("闪卡抽查") {
                        Spacer(Modifier.height(8.dp))
                        if (deckState.toll.stage == TollStage.IDLE) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ActionLabel("抽一轮") { decks.beginToll(Flash.CTX_DAILY_SPOT, 0) {} }
                                Text(
                                    text = if (deckState.decks.isEmpty()) "还没导卡组，回血态那边导一份" else "现在到期 ${deckState.dueCount} 张",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            deckState.note?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            TollPanel(
                                toll = deckState.toll,
                                blocking = false,
                                onReveal = decks::reveal,
                                onAnswer = decks::answer,
                                onDismiss = decks::dismiss,
                                onRetry = decks::retry,
                            )
                        }
                    }
                },
                {
                    BackupCard(backupState, backup) {
                        // 库是整个换过的：三个持有者各拿各的读数，谁也不知道对方那边已经变了，一律重读。
                        // 计时器更要当场收掉——它那一趟对应的行可能已经不在新库里，让它接着走就是在骗他。
                        clock.stop()
                        board.refresh()
                        decks.refresh()
                        shelf.reloadAfterRestore()
                    }
                },
                {
                    OutlinedCard("今天的轨迹" + headCount(data.events.size, "条")) {
                        Spacer(Modifier.height(8.dp))
                        if (data.events.isEmpty()) {
                            EmptyNote(EmptyGlyph.TRACE, "今天还没有留下动作")
                        } else {
                            data.events.forEach { TimelineRow(it, note = captureNote(data.captureFor(it))) }
                        }
                    }
                },
            ),
        )
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            // 三个数一样重要，所以一起抬到 20sp 而不是 24sp：这一屏要的是"读得出数"，不是喊话。
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

/**
 * 表名 → 界面上叫它什么。认不出的名字照原样显示：宁可让他看见一个奇怪的英文表名，
 * 也不要因为"翻译不出来"就悄悄把那张表的行数藏掉——那正是他要用来判断能不能按确认的数字。
 */
private val TABLE_NAMES = mapOf(
    "settings" to "设置",
    "novels" to "小说",
    "todos" to "待办",
    "ledger_categories" to "分类",
    "flash_decks" to "卡组",
    "captures" to "闪念",
    "pomodoro_sessions" to "专注",
    "ledger_tx" to "流水",
    "flash_cards" to "闪卡",
    "novel_bookmarks" to "书签",
    "gate_attempts" to "过路费",
    "timeline_events" to "轨迹",
)

/** 备份与恢复：导出＝另存一个文件，恢复＝读回来先验一遍，再由他按第二下。 */
@Composable
private fun BackupCard(state: BackupState, kit: BackupKit, onRestored: () -> Unit) {
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) kit.export(uri) }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) kit.stage(uri)
    }
    // 指认来源文件走的是书和卡组那两类文件，跟备份文件不是一回事，所以另开一个选择器。
    val reauthPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) kit.completeReauth(uri) else kit.cancelPick()
    }

    OutlinedCard("备份与恢复") {
        Spacer(Modifier.height(8.dp))

        val plan = state.plan
        if (plan == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionLabel("导出一份") { exporter.launch(kit.suggestedName()) }
                ActionLabel("选一份来恢复") {
                    restorer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
        } else {
            val incoming = plan.tables.map { it.table to it.rows.length() }
            Text(
                text = "要装回来 ${plan.incomingTotal} 行",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Caption(shapeOf(incoming))
            Spacer(Modifier.height(8.dp))
            val washed = plan.before.sumOf { it.second }
            Caption(
                if (washed == 0) "现在库里是空的，洗不掉任何东西"
                else "现在库里的 $washed 行会一起没了",
            )
            if (washed > 0) {
                Spacer(Modifier.height(4.dp))
                Caption(shapeOf(plan.before))
            }
            Spacer(Modifier.height(8.dp))
            Caption("按下去正在走的那趟会当场收掉；小说和卡组的来源文件恢复后要重新指认")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionLabel("确认整表替换") { kit.confirmRestore(onRestored) }
                ActionLabel("算了") { kit.discardPlan() }
            }
        }

        state.busyLabel?.let {
            Spacer(Modifier.height(8.dp))
            Caption(it)
        }
        state.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.needs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Caption("这些的来源文件现在认不出来，指认一次就能接着读")
            Spacer(Modifier.height(4.dp))
            state.needs.forEach { item ->
                ReauthRow(item) {
                    kit.pick(item)
                    reauthPicker.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                }
            }
        }
    }
}

@Composable
private fun ReauthRow(item: NeedsReauth, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = item.meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionLabel("指认") { onPick() }
    }
}

private fun shapeOf(items: List<Pair<String, Int>>): String =
    items.joinToString(" · ") { "${TABLE_NAMES[it.first] ?: it.first} ${it.second}" }

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
