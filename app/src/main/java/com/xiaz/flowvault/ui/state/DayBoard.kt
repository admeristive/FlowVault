package com.xiaz.flowvault.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaz.flowvault.FlowVaultApp
import com.xiaz.flowvault.core.db.CaptureDao
import com.xiaz.flowvault.core.db.LedgerDao
import com.xiaz.flowvault.core.db.TimelineDao
import com.xiaz.flowvault.core.db.TimelineWriter
import com.xiaz.flowvault.core.db.TodoDao
import com.xiaz.flowvault.core.model.Capture
import com.xiaz.flowvault.core.model.LedgerTx
import com.xiaz.flowvault.core.model.NovelRef
import com.xiaz.flowvault.core.model.Pomodoro
import com.xiaz.flowvault.core.model.TimelineEvent
import com.xiaz.flowvault.core.model.Todo
import com.xiaz.flowvault.core.util.DayKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 今天这一整块看板的唯一持有者。读全在 IO 线程，写完之后立刻重查一遍再回主线程发布——
 * 界面永远只显示库里真有的东西，库坏了对应的块就是空，不会半渲染。
 */
internal class DayBoard(app: Application) : AndroidViewModel(app) {

    private val database = (app as FlowVaultApp).database
    private val todos = TodoDao(database)
    private val ledger = LedgerDao(database)
    private val timeline = TimelineDao(database)
    private val captures = CaptureDao(database, todos, ledger)

    data class Snapshot(
        val dayKey: String,
        val monthKey: String,
        val todos: List<Todo>,
        val txs: List<LedgerTx>,
        val monthCents: Long,
        val events: List<TimelineEvent>,
        val focusCount: Int,
        val focusSec: Int,
        val captures: List<Capture>,
    ) {
        // 收支分开算，是为了在界面上给出「今天花掉 / 今天进账」两个数；
        // 直接从已读出来的当天流水累加，不再多开一条 SQL。
        val outCents: Long get() = txs.filter { it.amountCents < 0 }.sumOf { it.amountCents }
        val inCents: Long get() = txs.filter { it.amountCents > 0 }.sumOf { it.amountCents }

        /** 时间线上那条闪念对应的胶囊原文。别的模块的行没有这个指针，返回 null。 */
        fun captureFor(event: TimelineEvent): Capture? {
            if (event.refTable != "captures") return null
            val id = event.refId ?: return null
            return captures.firstOrNull { it.id == id }
        }
    }

    private val _snapshot = MutableStateFlow(blank())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    init {
        // 建 ViewModel 时不许碰库：第一次 read() 一定发生在 IO 线程上。
        refresh()
    }

    /**
     * 落一条胶囊；结果回主线程，输入条据此决定清不清空。
     * `boundTo` 只在回血态读书时给：胶囊里存下"这句是读哪本、第几个字时想到的"。
     */
    fun submit(raw: String, onResult: (Boolean) -> Unit, boundTo: NovelRef? = null) =
        write({ captures.record(raw, boundTo?.novelId, boundTo?.charOffset?.toLong()) != null }, onResult)

    fun setStatus(id: Long, status: Int, onResult: (Boolean) -> Unit) =
        write({ todos.setStatus(id, status) }, onResult)

    fun refresh() = write({ true }, {})

    private fun write(action: () -> Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = action()
            val next = read()
            withContext(Dispatchers.Main.immediate) {
                _snapshot.value = next
                onResult(ok)
            }
        }
    }

    private fun read(): Snapshot {
        val now = System.currentTimeMillis()
        val dayKey = DayKey.of(now)
        val monthKey = DayKey.monthOf(now)
        return Snapshot(
            dayKey = dayKey,
            monthKey = monthKey,
            todos = todos.listByDay(dayKey),
            txs = ledger.listByDay(dayKey),
            monthCents = ledger.sumByMonth(monthKey),
            events = timeline.listByDay(dayKey, EVENT_LIMIT),
            focusCount = timeline.countSessionOfDay(dayKey, TimelineWriter.ACTION_FINISH, Pomodoro.KIND_FOCUS),
            focusSec = timeline.secondsOfDay(dayKey, TimelineWriter.ACTION_FINISH, Pomodoro.KIND_FOCUS),
            captures = captures.listByDay(dayKey, CAPTURE_LIMIT),
        )
    }

    private companion object {
        const val EVENT_LIMIT = 40
        const val CAPTURE_LIMIT = 80

        /** 第一次读回来之前先占位：键是今天的，列表是空的，不编造任何数。 */
        fun blank() = Snapshot(
            dayKey = DayKey.today(),
            monthKey = DayKey.monthOf(System.currentTimeMillis()),
            todos = emptyList(),
            txs = emptyList(),
            monthCents = 0L,
            events = emptyList(),
            focusCount = 0,
            focusSec = 0,
            captures = emptyList(),
        )
    }
}
