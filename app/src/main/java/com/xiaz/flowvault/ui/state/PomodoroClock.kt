package com.xiaz.flowvault.ui.state

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaz.flowvault.FlowVaultApp
import com.xiaz.flowvault.core.db.PomodoroDao
import com.xiaz.flowvault.core.model.Pomodoro
import com.xiaz.flowvault.core.state.GlobalMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 屏幕上那一趟计时的样子。 */
internal data class Timing(
    val sessionId: Long,
    val kind: Int,
    val plannedSec: Int,
    val elapsedSec: Int,
    val running: Boolean,
    /** 这一趟开行的时候绑的是哪件事；没绑就是 null。绑定只到"开行"为止，中途改绑不影响已经在走的这趟。 */
    val todoId: Long? = null,
)

/**
 * 番茄钟。秒数只在**看得见的时候**累加：锚点在回到前台时重新对齐、退到后台时结算，
 * 所以切去别的应用五分钟回来，这趟不会凭空被吃掉五分钟。
 * 库打不开时计时照走，只是这一趟不留痕——不因存档失败把界面卡住。
 */
internal class PomodoroClock(app: Application) : AndroidViewModel(app) {

    private val dao = PomodoroDao((app as FlowVaultApp).database)

    private val _timing = MutableStateFlow(null as Timing?)
    val timing: StateFlow<Timing?> = _timing.asStateFlow()

    /** 走满之后要不要切态。界面读一次就清空，计时器不直接指挥全局状态机。 */
    private val _handoff = MutableStateFlow<GlobalMode?>(null)
    val handoff: StateFlow<GlobalMode?> = _handoff.asStateFlow()

    private var anchor: Long? = null
    private var bankedSec = 0
    private var interrupts = 0

    /**
     * 软熔断到没到点。休息走满时置真，界面据此提示"翻过这页就收"；
     * 置真**不切态**——切态发生在他翻页那一下，或者超时顶到硬上限。
     */
    private val _fuseArmed = MutableStateFlow(false)
    val fuseArmed: StateFlow<Boolean> = _fuseArmed.asStateFlow()

    /**
     * 建行要过一次 IO，界面在这半秒里仍是"没在计时"。没有这个开关，连点两下会开出行、
     * 后一行的 _timing 把前一行顶掉——前一行永远没人回填，库里留一条没结束的会话。
     */
    private var opening = false

    /**
     * 下一趟专注要算到哪件事头上。放在计时器而不是界面里，是为了切去回血、再切回来时
     * 还认得这一条——界面上的选中态一被销毁就没了，那这趟到底绑没绑就变成猜。
     */
    private val _boundTodo = MutableStateFlow(null as Long?)
    val boundTodo: StateFlow<Long?> = _boundTodo.asStateFlow()

    fun bind(todoId: Long?) {
        _boundTodo.value = todoId
    }

    fun start(kind: Int, plannedSec: Int, todoId: Long? = null) {
        if (_timing.value != null || opening) return
        opening = true
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.open(kind, plannedSec, todoId)
            withContext(Dispatchers.Main.immediate) {
                opening = false
                bankedSec = 0
                interrupts = 0
                anchor = SystemClock.elapsedRealtime()
                _fuseArmed.value = false
                _timing.value = Timing(id ?: NO_ROW, kind, plannedSec, 0, running = true, todoId = todoId)
            }
        }
    }

    /** 由界面每秒喂一次；没在跑或不在前台就是空转。 */
    fun tick() {
        val current = _timing.value ?: return
        if (!current.running) return
        val sec = elapsed(current)
        if (sec < current.plannedSec) {
            _timing.value = current.copy(elapsedSec = sec)
            return
        }
        if (current.kind == Pomodoro.KIND_FOCUS) {
            settle(current, current.plannedSec, natural = true)
            return
        }
        // 休息走满了：不硬切，等他翻过这一页；一直不翻页（去倒水、接电话）就按硬上限自己收。
        val over = sec - current.plannedSec
        _fuseArmed.value = true
        if (over >= Pomodoro.BREAK_MAX_OVERDUE_SEC) {
            settle(current, sec, natural = true, fuse = true)
        } else {
            _timing.value = current.copy(elapsedSec = sec)
        }
    }

    /**
     * 读书翻页时喂给计时器：熔断已经 armed，这一页读完了就该收了。
     * "软"就软在这里——到点不掐断句子，这一屏读完再走。
     */
    fun onReaderPageTurn() {
        if (!_fuseArmed.value) return
        val current = _timing.value ?: return
        settle(current, maxOf(elapsed(current), current.plannedSec), natural = true, fuse = true)
    }

    /**
     * 手动收掉这一轮休息。停在书架上时没有"下一页"可翻，翻页触发不了，
     * 所以给一个入口；没到点就不该出现这个按钮，这里再挡一道，免得把没走完的休息记成超时。
     */
    fun fuseNow() {
        if (!_fuseArmed.value) return
        val current = _timing.value ?: return
        if (current.kind != Pomodoro.KIND_BREAK) return
        settle(current, maxOf(elapsed(current), current.plannedSec), natural = true, fuse = true)
    }

    fun pause() {
        val current = _timing.value ?: return
        val sec = fold()
        interrupts += 1
        val id = current.sessionId
        if (id > 0) viewModelScope.launch(Dispatchers.IO) { dao.addInterrupt(id) }
        _timing.value = current.copy(elapsedSec = sec, running = false)
    }

    fun resume() {
        val current = _timing.value ?: return
        if (current.running) return
        anchor = SystemClock.elapsedRealtime()
        _timing.value = current.copy(running = true)
    }

    /** 没走满就停：秒数照实回填，时间线上记的是"中断"而不是"走完"。 */
    fun stop() {
        val current = _timing.value ?: return
        val sec = fold()
        settle(current, sec, natural = false)
    }

    fun onBackground() {
        if (_timing.value?.running != true) return
        val current = _timing.value ?: return
        _timing.value = current.copy(elapsedSec = fold())
    }

    fun onForeground() {
        val current = _timing.value ?: return
        if (!current.running) return
        anchor = SystemClock.elapsedRealtime()
    }

    fun consumeHandoff() {
        _handoff.value = null
    }

    private fun elapsed(current: Timing): Int {
        val from = anchor ?: return current.elapsedSec
        return bankedSec + ((SystemClock.elapsedRealtime() - from) / 1000L).toInt()
    }

    /** 把锚点到现在这一段结算进 bankedSec，并摘掉锚点。 */
    private fun fold(): Int {
        val current = _timing.value ?: return 0
        val from = anchor ?: return bankedSec
        bankedSec = minOf(bankedSec + ((SystemClock.elapsedRealtime() - from) / 1000L).toInt(), ceiling(current))
        anchor = null
        return bankedSec
    }

    /**
     * 结算时的封顶。专注不许超过计划；休息要能超到熔断上限——
     * 不然超时那几分钟只要退一次后台就被抹回 5 分钟，熔断永远顶不到上限。
     */
    private fun ceiling(current: Timing): Int =
        current.plannedSec + if (current.kind == Pomodoro.KIND_BREAK) Pomodoro.BREAK_MAX_OVERDUE_SEC else 0

    private fun settle(current: Timing, sec: Int, natural: Boolean, fuse: Boolean = false) {
        _timing.value = null
        _fuseArmed.value = false
        anchor = null
        bankedSec = 0
        if (current.sessionId > 0) {
            val id = current.sessionId
            val count = interrupts
            viewModelScope.launch(Dispatchers.IO) { dao.close(id, sec, count, System.currentTimeMillis(), fuse) }
        }
        interrupts = 0
        if (!natural) return
        if (current.kind == Pomodoro.KIND_FOCUS) {
            _handoff.value = GlobalMode.BREAK
            start(Pomodoro.KIND_BREAK, Pomodoro.BREAK_DEFAULT_SEC)
        } else {
            _handoff.value = GlobalMode.FOCUS
        }
    }

    private companion object {
        /** 库没开成也要能走秒，用这个占位 id 表示"这一笔不会回填"。 */
        const val NO_ROW = -1L
    }
}
