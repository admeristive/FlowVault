package com.xiaz.flowvault.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaz.flowvault.core.model.Flash
import com.xiaz.flowvault.core.model.FlashDeck
import com.xiaz.flowvault.core.model.Novel
import com.xiaz.flowvault.core.model.Pomodoro
import com.xiaz.flowvault.ui.components.ActionLabel
import com.xiaz.flowvault.ui.components.CaptureBar
import com.xiaz.flowvault.ui.components.CardFlow
import com.xiaz.flowvault.ui.components.CardRole
import com.xiaz.flowvault.ui.components.EmptyGlyph
import com.xiaz.flowvault.ui.components.EmptyNote
import com.xiaz.flowvault.ui.components.OutlinedCard
import com.xiaz.flowvault.ui.components.ReaderPage
import com.xiaz.flowvault.ui.components.ReaderProgress
import com.xiaz.flowvault.ui.components.ReaderScroll
import com.xiaz.flowvault.ui.components.TollPanel
import com.xiaz.flowvault.ui.components.readerColorsOf
import com.xiaz.flowvault.ui.components.headCount
import com.xiaz.flowvault.ui.state.DayBoard
import com.xiaz.flowvault.ui.state.DeckShelf
import com.xiaz.flowvault.ui.state.DeckState
import com.xiaz.flowvault.ui.state.NovelShelf
import com.xiaz.flowvault.ui.state.PomodoroClock
import com.xiaz.flowvault.ui.state.ReaderStyle
import com.xiaz.flowvault.ui.state.ShelfState
import com.xiaz.flowvault.ui.state.TollStage

@Composable
internal fun BreakScreen(
    board: DayBoard,
    shelf: NovelShelf,
    clock: PomodoroClock,
    decks: DeckShelf,
    modifier: Modifier = Modifier,
) {
    val state by shelf.state.collectAsStateWithLifecycle()
    val deckState by decks.state.collectAsStateWithLifecycle()
    // 闸开着、或者没过的时候，书架和正文都不该从缝里点出来。放行不挡：那时候书已经开了。
    val blocking = deckState.toll.stage == TollStage.QUIZ || deckState.toll.stage == TollStage.BLOCKED

    Box(modifier = modifier.fillMaxSize()) {
        if (state.current == null) {
            ShelfView(state, shelf, board, clock, decks, deckState, Modifier.fillMaxSize())
        } else {
            ReadingView(state, shelf, board, clock, Modifier.fillMaxSize())
        }
        if (blocking) {
            TollPanel(
                toll = deckState.toll,
                blocking = true,
                onReveal = decks::reveal,
                onAnswer = decks::answer,
                onDismiss = decks::dismiss,
                onRetry = decks::retry,
            )
        }
    }
}

/** 没打开书时的书架：休息条 + 书单 + 卡组 + 速记。 */
@Composable
private fun ShelfView(
    state: ShelfState,
    shelf: NovelShelf,
    board: DayBoard,
    clock: PomodoroClock,
    decks: DeckShelf,
    deckState: DeckState,
    modifier: Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) shelf.import(uri)
    }
    val deckPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) decks.import(uri)
    }

    // 想读哪本都得先交过路费：闸在这里收，过关那一下才真去开书。
    // 门票记的是这一轮休息的计划秒数；没在计时就按默认的 5 分钟记。
    val openNovel: (Novel) -> Unit = { novel ->
        val timing = clock.timing.value
        val granted = if (timing != null && timing.kind == Pomodoro.KIND_BREAK) {
            timing.plannedSec
        } else {
            Pomodoro.BREAK_DEFAULT_SEC
        }
        decks.beginToll(Flash.CTX_ENTER_BREAK, granted) { shelf.open(novel) }
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
                    OutlinedCard("这轮休息", role = CardRole.HERO) {
                        Spacer(Modifier.height(8.dp))
                        RestBar(clock)
                    }
                },
                {
                    OutlinedCard("本地小说" + headCount(state.novels.size, "本")) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionLabel("选一本 TXT") { picker.launch(arrayOf("text/*", "application/octet-stream")) }
                            if (state.busy) {
                                Text(
                                    text = "正在读这份文件",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        state.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (state.novels.isEmpty()) {
                            EmptyNote(
                                EmptyGlyph.NOVEL,
                                "书架是空的。TXT 从手机本地选，应用不申请存储权限，也不往手机其他角落写东西",
                            )
                        } else {
                            state.novels.forEach { novel ->
                                NovelRow(
                                    novel = novel,
                                    captured = state.capturedCount(novel.id),
                                    onOpen = { openNovel(novel) },
                                    onRemove = { shelf.remove(novel) },
                                )
                            }
                        }
                    }
                },
                {
                    OutlinedCard("闪卡卡组" + headCount(deckState.decks.size, "组")) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionLabel("导一份 CSV/JSON") {
                                deckPicker.launch(arrayOf("text/*", "application/csv", "application/json", "application/octet-stream"))
                            }
                            if (deckState.busy) {
                                Text(
                                    text = "正在读这份文件",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        deckState.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Text(
                            text = buildString {
                                append("现在到期 ${deckState.dueCount} 张 · 一趟问 ${Flash.TOLL_CARDS} 张")
                                // 一次都没交过就不写"0 次"：那串零看着像闸坏了，其实只是今天还没开过书。
                                if (deckState.tollAsked > 0) {
                                    append(" · 今天交过 ${deckState.tollAsked} 次，过关 ${deckState.tollPassed} 次")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (deckState.decks.isEmpty()) {
                            EmptyNote(
                                EmptyGlyph.DECK,
                                "还没有卡组。CSV 每行是「正面,背面」，JSON 要对象数组；没有到期卡时不拦你",
                            )
                        } else {
                            deckState.decks.forEach { deck ->
                                DeckRow(deck = deck, onRemove = { decks.remove(deck) })
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

/** 正在读书：正文吃掉整个高度，控制条压在下面。 */
@Composable
private fun ReadingView(
    state: ShelfState,
    shelf: NovelShelf,
    board: DayBoard,
    clock: PomodoroClock,
    modifier: Modifier,
) {
    var marksOpen by remember { mutableStateOf(false) }
    val colors = readerColorsOf(state.style.surface)
    val body = textStyleOf(state.style)
    val armed by clock.fuseArmed.collectAsStateWithLifecycle()

    // 往前翻一页＝这一屏读完了，熔断到点的话就在这一下收掉。书签跳转是定点走，不算翻页。
    val nextPage: (Int) -> Unit = { to ->
        shelf.turnTo(to)
        clock.onReaderPageTurn()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.style.paginated) {
                ReaderPage(
                    text = state.text,
                    offset = state.offset,
                    style = body,
                    colors = colors,
                    onNext = nextPage,
                    onPrev = shelf::turnBack,
                )
            } else {
                ReaderScroll(
                    text = state.text,
                    offset = state.offset,
                    style = body,
                    colors = colors,
                    onNext = nextPage,
                )
            }
        }
        ReaderProgress(
            text = state.text,
            offset = state.offset,
            colors = colors,
            hint = if (armed) "到点了，翻过这页就收" else null,
        )

        if (marksOpen) {
            MarkList(state, onJump = shelf::turnTo, onDrop = shelf::dropMark)
        }

        CaptureBar(placeholder = "读到这儿想到什么") { raw, then ->
            // 记成了才回查条数：书架上"在这本里记了 N 条"得跟着跳，没记上就不动。
            board.submit(
                raw,
                { ok ->
                    then(ok)
                    if (ok) shelf.refreshCaptureCounts()
                },
                shelf.binding(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            ActionLabel("回书架") { shelf.leaveReader() }
            ActionLabel("加书签") { shelf.addMark() }
            ActionLabel(if (marksOpen) "收起书签" else "书签 ${state.marks.size}", option = true) { marksOpen = !marksOpen }
            Spacer(Modifier.weight(1f))
            ActionLabel(state.style.surface.label, option = true) { shelf.surface() }
            ActionLabel(if (state.style.paginated) "翻页" else "滚动", option = true) {
                shelf.paging(!state.style.paginated)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            ActionLabel("字−") { shelf.font(-1) }
            Text(
                text = "${state.style.fontSp} 号",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionLabel("字+") { shelf.font(1) }
            Spacer(Modifier.width(12.dp))
            ActionLabel("行−") { shelf.line(-1) }
            Text(
                text = "行距 ${state.style.lineHeightSp}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionLabel("行+") { shelf.line(1) }
            Spacer(Modifier.weight(1f))
            if (state.offset > 0) {
                ActionLabel("上一页") { shelf.turnBack() }
            }
        }
    }
}

@Composable
private fun NovelRow(novel: Novel, captured: Int?, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = novel.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = buildString {
                    append("${novel.encoding} · ${novel.charCount} 字 · 读到 ${novel.percent()}%")
                    // 读的时候记的胶囊攒在这儿——回血和日常之间就这一条看得见的账。
                    if (captured != null && captured > 0) append(" · 在这本里记了 $captured 条")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionLabel("打开") { onOpen() }
        ActionLabel("删") { onRemove() }
    }
}

@Composable
private fun DeckRow(deck: FlashDeck, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = deck.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${deck.cardCount} 张 · 到期 ${deck.dueCount} 张",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionLabel("删") { onRemove() }
    }
}

@Composable
private fun MarkList(state: ShelfState, onJump: (Int) -> Unit, onDrop: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        if (state.marks.isEmpty()) {
            EmptyNote(EmptyGlyph.MARK, "还没有书签")
        } else {
            state.marks.forEach { mark ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${mark.charOffset} 字 · ${mark.label}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    ActionLabel("去") { onJump(mark.charOffset) }
                    ActionLabel("删") { onDrop(mark.id) }
                }
            }
        }
    }
}

/** 一根没有动效、没有波纹的进度条：荣耀上任何带状态层的组件都可能在瞬时界面里碎成雪花。 */
@Composable
private fun RestBar(clock: PomodoroClock) {
    val timing by clock.timing.collectAsStateWithLifecycle()
    val armed by clock.fuseArmed.collectAsStateWithLifecycle()
    val current = timing
    if (current == null) {
        Text(
            text = "现在没在计时。到攻坚态按一趟，走满会自动切到这里",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        return
    }
    val left = maxOf(current.plannedSec - current.elapsedSec, 0)
    val over = maxOf(current.elapsedSec - current.plannedSec, 0)
    val fraction = if (current.plannedSec <= 0) 0f else current.elapsedSec.toFloat() / current.plannedSec
    // 这张卡的主角：这一轮还剩多久。数字先站上来说话，进度条只负责"走了多少"这件事。
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (armed) "+${Pomodoro.clockText(over)}" else Pomodoro.clockText(left),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = Pomodoro.label(current.kind),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MaterialTheme.colorScheme.surfaceDim),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
    Spacer(Modifier.height(8.dp))
    if (armed) {
        // 停在书架上没有"下一页"可翻，翻页那一路触发不了，所以这里给一个手动收的口子。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "到点了。要读就翻一页收手，或现在收",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ActionLabel("现在收") { clock.fuseNow() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun textStyleOf(style: ReaderStyle) = TextStyle(
    fontSize = style.fontSp.sp,
    lineHeight = style.lineHeightSp.sp,
    fontFamily = FontFamily.Serif,
)
