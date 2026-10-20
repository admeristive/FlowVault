package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.Pomodoro

/**
 * 番茄钟会话的读写。一趟只有两个动作：开始建行、结束回填。
 * 计时本身不落库——每秒写一次磁盘是白白耗电，中途崩溃最多丢最后一趟没走完的秒数。
 */
internal class PomodoroDao(private val db: Lazy<SQLiteDatabase>) {

    /** 返回新行的 id；打不开库返回 null，界面照样能走秒，只是这一笔不会留痕。 */
    fun open(kind: Int, plannedSec: Int, todoId: Long?): Long? = guarding("pomo") {
        db.value.transaction {
            val values = ContentValues().apply {
                put("kind", kind)
                put("planned_sec", plannedSec)
                put("elapsed_sec", 0)
                put("started_at", System.currentTimeMillis())
                put("interrupt_count", 0)
                put("fuse_triggered", 0)
                if (todoId != null) put("todo_id", todoId)
            }
            val id = insertOrThrow("pomodoro_sessions", null, values)
            TimelineWriter.append(
                this,
                TimelineWriter.MODULE_POMO,
                TimelineWriter.ACTION_START,
                "pomodoro_sessions",
                id,
                "${Pomodoro.label(kind)} ${plannedSec / 60} 分钟",
            )
            id
        }
    }

    /**
     * 回填结束时刻与走到的秒数。走满记 finish、没走满记 abort、超时被熔断收的记 fuse，
     * 这样"今天完成几趟"直接数 finish，也不用回来猜哪趟是半途停的。
     */
    fun close(
        id: Long,
        elapsedSec: Int,
        interrupts: Int,
        endedAt: Long,
        fuse: Boolean = false,
    ): Boolean = guarding("pomo") {
        db.value.transaction {
            var plannedSec = -1
            var kind = Pomodoro.KIND_FOCUS
            var todoId = 0L
            rawQuery(
                "SELECT planned_sec, kind, todo_id FROM pomodoro_sessions WHERE id = ?",
                arrayOf(id.toString()),
            ).use { c ->
                if (c.moveToFirst()) {
                    plannedSec = c.getInt(0)
                    kind = c.getInt(1)
                    todoId = if (c.isNull(2)) 0L else c.getLong(2)
                }
            }
            // 行不存在（库半路坏过）就什么都不写，免得凭空补出一条没开始过的会话。
            if (plannedSec < 0) return@transaction false
            val values = ContentValues().apply {
                put("elapsed_sec", elapsedSec)
                put("ended_at", endedAt)
                put("interrupt_count", interrupts)
                put("fuse_triggered", if (fuse) 1 else 0)
            }
            val changed = update("pomodoro_sessions", values, "id = ?", arrayOf(id.toString())) > 0
            if (changed) {
                val action = when {
                    fuse -> TimelineWriter.ACTION_FUSE
                    elapsedSec >= plannedSec -> TimelineWriter.ACTION_FINISH
                    else -> TimelineWriter.ACTION_ABORT
                }
                // 走完的专注才给这件事记一趟，且跟上面的回填同一条事务：
                // 半途停的那趟一秒没走满，不能把"试了一下"算成"做完一趟"。
                if (action == TimelineWriter.ACTION_FINISH && kind == Pomodoro.KIND_FOCUS && todoId > 0) {
                    execSQL(
                        "UPDATE todos SET done_pomos = done_pomos + 1 WHERE id = ?",
                        arrayOf(todoId.toString()),
                    )
                }
                TimelineWriter.append(
                    this,
                    TimelineWriter.MODULE_POMO,
                    action,
                    "pomodoro_sessions",
                    id,
                    "${Pomodoro.label(kind)}走了 ${elapsedSec / 60} 分钟",
                    atMillis = endedAt,
                )
            }
            changed
        }
    } ?: false

    /** 每被打断一次就 +1，写在库而不是内存里，用户中途杀进程也不丢"这趟被切了几次"。 */
    fun addInterrupt(id: Long): Boolean = guarding("pomo") {
        db.value.execSQL(
            "UPDATE pomodoro_sessions SET interrupt_count = interrupt_count + 1 WHERE id = ?",
            arrayOf(id.toString()),
        )
        true
    } ?: false
}
