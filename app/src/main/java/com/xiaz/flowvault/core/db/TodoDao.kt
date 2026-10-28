package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.Todo

/**
 * 待办的读写。每条写都在一个事务里同笔补时间线，异常一律被 guarding 吞掉并留痕，
 * 界面拿到的是 null / false，库坏掉不会传导成崩溃。
 */
internal class TodoDao(private val db: Lazy<SQLiteDatabase>) {

    /** 见 LedgerDao.insertIn 的同名理由：胶囊要和它生成的那条待办一起提交。 */
    internal fun insertIn(
        tr: SQLiteDatabase,
        title: String,
        note: String?,
        priority: Int,
        dayKey: String,
        startMin: Int?,
        endMin: Int?,
        plannedPomos: Int,
    ): Long {
        val values = ContentValues().apply {
            put("title", title)
            put("note", note)
            put("priority", priority)
            put("day_key", dayKey)
            put("start_min", startMin)
            put("end_min", endMin)
            put("status", Todo.STATUS_OPEN)
            put("planned_pomos", plannedPomos)
            put("done_pomos", 0)
            put("created_at", System.currentTimeMillis())
        }
        val id = tr.insertOrThrow("todos", null, values)
        TimelineWriter.append(
            tr,
            TimelineWriter.MODULE_TODO,
            TimelineWriter.ACTION_ADD,
            "todos",
            id,
            title,
        )
        return id
    }

    fun listByDay(dayKey: String): List<Todo> =
        guarding("todo") {
            db.value.rawQuery(
                "SELECT $COLUMNS FROM todos WHERE day_key = ? " +
                    "ORDER BY start_min IS NULL, start_min, priority DESC, id",
                arrayOf(dayKey),
            ).use { c -> if (!c.moveToFirst()) emptyList() else iterate(c) }
        } ?: emptyList()

    /** 传回 STATUS_OPEN 表示改回未完成，同时清空 finished_at。 */
    fun setStatus(id: Long, status: Int): Boolean = guarding("todo") {
        val values = ContentValues().apply {
            put("status", status)
            if (status == Todo.STATUS_OPEN) putNull("finished_at") else put("finished_at", System.currentTimeMillis())
        }
        val action = when (status) {
            Todo.STATUS_DONE -> TimelineWriter.ACTION_DONE
            Todo.STATUS_DROPPED -> TimelineWriter.ACTION_DROP
            else -> TimelineWriter.ACTION_ADD
        }
        db.value.transaction {
            val changed = update("todos", values, "id = ?", arrayOf(id.toString()))
            if (changed > 0) {
                TimelineWriter.append(this, TimelineWriter.MODULE_TODO, action, "todos", id, null)
            }
            changed > 0
        }
    } ?: false

    private fun iterate(c: Cursor): List<Todo> {
        val out = ArrayList<Todo>(c.count)
        do {
            out.add(read(c))
        } while (c.moveToNext())
        return out
    }

    private fun read(c: Cursor) = Todo(
        id = c.getLong(0),
        title = c.getString(1),
        note = c.getString(2),
        priority = c.getInt(3),
        dayKey = c.getString(4),
        startMin = if (c.isNull(5)) null else c.getInt(5),
        endMin = if (c.isNull(6)) null else c.getInt(6),
        status = c.getInt(7),
        plannedPomos = c.getInt(8),
        donePomos = c.getInt(9),
        createdAt = c.getLong(10),
        finishedAt = if (c.isNull(11)) null else c.getLong(11),
    )

    private companion object {
        // 顺序必须和 read() 的下标一一对应，改这里要同时改 read()。
        const val COLUMNS = "id, title, note, priority, day_key, start_min, end_min, " +
            "status, planned_pomos, done_pomos, created_at, finished_at"
    }
}
