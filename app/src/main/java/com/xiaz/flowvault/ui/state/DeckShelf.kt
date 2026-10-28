package com.xiaz.flowvault.ui.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaz.flowvault.FlowVaultApp
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.db.FlashDao
import com.xiaz.flowvault.core.flash.DeckParse
import com.xiaz.flowvault.core.flash.DeckParser
import com.xiaz.flowvault.core.model.Flash
import com.xiaz.flowvault.core.model.FlashCard
import com.xiaz.flowvault.core.model.FlashDeck
import com.xiaz.flowvault.core.model.GateAttempt
import com.xiaz.flowvault.core.novel.NovelSource
import com.xiaz.flowvault.core.novel.TextDecode
import com.xiaz.flowvault.core.util.DayKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 闸门走到哪一步。IDLE 表示屏幕上不该有闸，BLOCKED 表示没答够、正文还不该出来。 */
internal enum class TollStage { IDLE, QUIZ, PASSED, BLOCKED }

/** 一趟过路费当场的数据。界面只读它，不自己数。 */
internal data class Toll(
    val stage: TollStage = TollStage.IDLE,
    val context: Int = Flash.CTX_ENTER_BREAK,
    val cards: List<FlashCard> = emptyList(),
    val index: Int = 0,
    val correct: Int = 0,
    val revealed: Boolean = false,
    val grantedSec: Int = 0,
    val result: GateAttempt? = null,
) {
    val card: FlashCard? get() = cards.getOrNull(index)
    val asked: Int get() = cards.size
    val need: Int get() = Flash.required(asked)
    val busy: Boolean get() = stage == TollStage.QUIZ
}

internal data class DeckState(
    val decks: List<FlashDeck> = emptyList(),
    val dueCount: Int = 0,
    /** 今天交过几次路费、其中几次真过关。闸是不是真在拦人，只有这两个数说得出。 */
    val tollAsked: Int = 0,
    val tollPassed: Int = 0,
    val busy: Boolean = false,
    val note: String? = null,
    val toll: Toll = Toll(),
)

/**
 * 闪卡书架 + 过路费闸。三件事都在这里：导入卡组、抽到期卡、把作答写回盒子。
 * 闸的口径写死在这里而不是界面里：**答够六成才放行**，一张到期卡都没有就免过路费——
 * 不能因为"今天没卡可问"把他锁在门外，那是这套机制最容易被讨厌的地方。
 */
internal class DeckShelf(app: Application) : AndroidViewModel(app) {

    private val dao = FlashDao(getApplication<FlowVaultApp>().database)
    private val source = NovelSource(app)

    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    /** 过关之后要干的事（打开他刚点的那本书）。闸门结算时在主线程跑一次，跑完就清空。 */
    private var onPass: (() -> Unit)? = null

    init {
        // 建 ViewModel 时不许碰库：第一次读一定发生在 IO 线程上。
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val decks = dao.listDecks(now)
            val due = dao.countDue(now)
            val toll = dao.tollOfDay(DayKey.of(now))
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(
                    decks = decks,
                    dueCount = due,
                    tollAsked = toll.first,
                    tollPassed = toll.second,
                )
            }
        }
    }

    fun import(uri: Uri) {
        _state.value = _state.value.copy(busy = true, note = null)
        viewModelScope.launch(Dispatchers.IO) {
            val persisted = source.takeReadPermission(uri)
            val bytes = source.readBounded(uri, Flash.MAX_SOURCE_BYTES)
            if (bytes == null) {
                publish { it.copy(busy = false, note = "这份文件读不出来，原因在日志浮窗里") }
                return@launch
            }
            val decoded = guarding("flash") { TextDecode.decode(bytes) }
            if (decoded == null) {
                publish { it.copy(busy = false, note = "这份文件解不出文字，原因在日志浮窗里") }
                return@launch
            }
            val fileName = source.displayName(uri)
            when (val parsed = DeckParser.parse(fileName, decoded.text)) {
                is DeckParse.Refused -> publish { it.copy(busy = false, note = parsed.message) }
                is DeckParse.Ok -> {
                    val name = parsed.deckName?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
                    val deckId = dao.putDeck(name, uri.toString(), parsed.cards, System.currentTimeMillis())
                    if (deckId == null) {
                        publish { it.copy(busy = false, note = "《$name》认出了 ${parsed.cards.size} 张，但没写进库：库写入失败，原因在日志浮窗里") }
                        return@launch
                    }
                    val now = System.currentTimeMillis()
                    val suffix = if (persisted) "" else "（系统没给持久权限，下次要重选）"
                    val skipped = if (parsed.skipped > 0) "，另有 ${parsed.skipped} 行不像卡片被我扔了" else ""
                    publish {
                        it.copy(
                            busy = false,
                            decks = dao.listDecks(now),
                            dueCount = dao.countDue(now),
                            note = "《$name》进了 ${parsed.cards.size} 张$skipped$suffix",
                        )
                    }
                }
            }
        }
    }

    fun remove(deck: FlashDeck) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.dropDeck(deck.id)
            val now = System.currentTimeMillis()
            publish {
                it.copy(
                    decks = dao.listDecks(now),
                    dueCount = dao.countDue(now),
                    note = "《${deck.name}》和它的卡一起删掉了，答过的次数还留着",
                )
            }
        }
    }

    /**
     * 开一道闸。[grantedSec] 是过关换来的阅读秒数，日常抽查给 0（抽查不发门票）。
     * 没有到期卡时**不记这一笔**、直接跑 [pass]——没问就没有"过没过"，记一行空的反而脏。
     */
    fun beginToll(context: Int, grantedSec: Int, pass: () -> Unit) {
        onPass = pass
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cards = dao.dueCards(null, Flash.TOLL_CARDS, now)
            if (cards.isEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    val action = onPass
                    onPass = null
                    val idle = Toll(stage = TollStage.IDLE)
                    _state.value = _state.value.copy(
                        toll = idle,
                        note = if (context == Flash.CTX_ENTER_BREAK) "没有到期的卡，直接读" else "没有到期的卡，今天不用抽查",
                    )
                    action?.invoke()
                }
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(
                    toll = Toll(stage = TollStage.QUIZ, context = context, cards = cards, grantedSec = grantedSec),
                )
            }
        }
    }

    /** 先看正面自己在心里答，再掀开答案对照。掀开之前不给"记住了"的口子，自评才不会顺手点。 */
    fun reveal() {
        val toll = _state.value.toll
        if (toll.stage != TollStage.QUIZ) return
        if (toll.revealed) return
        _state.value = _state.value.copy(toll = toll.copy(revealed = true))
    }

    /**
     * 记一张的自评。状态**先在主线程推进**再去写库，所以连点两下也不会把同一张记两次：
     * 第二下已经是下一张了。
     */
    fun answer(correct: Boolean) {
        val toll = _state.value.toll
        if (toll.stage != TollStage.QUIZ) return
        val card = toll.card ?: return
        val next = toll.copy(index = toll.index + 1, revealed = false, correct = toll.correct + if (correct) 1 else 0)
        viewModelScope.launch(Dispatchers.IO) { dao.review(card.id, correct, System.currentTimeMillis()) }
        if (next.index >= next.cards.size) {
            settle(next)
        } else {
            _state.value = _state.value.copy(toll = next)
        }
    }

    /** 中途走掉**不记这一笔**：没答完就不算一次过关，记一半的比例只会把统计做脏。 */
    fun dismiss() {
        onPass = null
        _state.value = _state.value.copy(toll = Toll())
    }

    /** 没过还想再来一遍：重新抽一批到期卡，这一笔也照实再记一行。 */
    fun retry() {
        val toll = _state.value.toll
        beginToll(toll.context, toll.grantedSec, onPass ?: {})
    }

    private fun settle(toll: Toll) {
        // 先落定 stage 再去写库：PASS 会立刻开书，BLOCKED 会亮出"要几张才过"，两条都要一次到位。
        _state.value = _state.value.copy(toll = toll.copy(stage = TollStage.QUIZ))
        viewModelScope.launch(Dispatchers.IO) {
            val attempt = dao.recordAttempt(
                toll.context,
                toll.asked,
                toll.correct,
                toll.grantedSec,
                System.currentTimeMillis(),
            )
            val passed = attempt?.passed == true
            val now = System.currentTimeMillis()
            val counts = dao.tollOfDay(DayKey.of(now))
            // 这两读数留在 IO 线程上取：换到 Main 之后再查库，就是把一次磁盘读塞进画帧里。
            val freshDecks = dao.listDecks(now)
            val freshDue = dao.countDue(now)
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(
                    decks = freshDecks,
                    dueCount = freshDue,
                    tollAsked = counts.first,
                    tollPassed = counts.second,
                    toll = toll.copy(
                        stage = if (passed) TollStage.PASSED else TollStage.BLOCKED,
                        result = attempt,
                    ),
                )
                // 过了才把"开门的人"叫回来并摘掉；没过要留着，"再来一遍"才能接上他刚才点的那本书。
                if (passed) {
                    val action = onPass
                    onPass = null
                    action?.invoke()
                }
            }
        }
    }

    private fun publish(transform: (DeckState) -> DeckState) =
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _state.value = transform(_state.value)
        }
}
