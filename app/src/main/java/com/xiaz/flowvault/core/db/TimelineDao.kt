package com.xiaz.flowvault.core.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.TimelineEvent

/** 时间线只读——写一律走 TimelineWriter，读这里出问题不影响任何业务数据。 */
internal class TimelineDao(private val db: Lazy<SQLiteDatabase>) {

    fun listByDay(dayKey: String, limit: Int): List<TimelineEvent> = guarding("timeline") {
        db.value.rawQuery(
            "SELECT id, ts, day_key, module, action, ref_table, ref_id, payload " +
                "FROM timeline_events WHERE day_key = ? ORDER BY ts DESC, id DESC LIMIT ?",
            arrayOf(dayKey, limit.toString()),
        ).use { c -> if (!c.moveToFirst()) emptyList() else iterate(c) }
    } ?: emptyList()

    /**
     * 今天走满了几趟**某一种**番茄钟。时间线上只有 module/action，分不出专注和休息，
     * 而 pomodoro_sessions 没有 day_key 不能按天框它——所以用时间线框天、JOIN 会话表挑 kind。
     */
    fun countSessionOfDay(dayKey: String, action: String, kind: Int): Int = guarding("timeline") {
        db.value.rawQuery(
            "SELECT count(*) FROM timeline_events t " +
                "JOIN pomodoro_sessions p ON p.id = t.ref_id " +
                "WHERE t.day_key = ? AND t.module = ? AND t.action = ? AND p.kind = ?",
            arrayOf(dayKey, TimelineWriter.MODULE_POMO, action, kind.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    } ?: 0

    /**
     * 今天走完的某一种番茄钟，一共实际走了多少秒。会话表没有 day_key，
     * 和时间线 JOIN 出来——和上面按趟数数的是同一条路子，两处口径不会走岔。
     */
    fun secondsOfDay(dayKey: String, action: String, kind: Int): Int = guarding("timeline") {
        db.value.rawQuery(
            "SELECT COALESCE(sum(p.elapsed_sec), 0) FROM timeline_events t " +
                "JOIN pomodoro_sessions p ON p.id = t.ref_id " +
                "WHERE t.day_key = ? AND t.module = ? AND t.action = ? AND p.kind = ?",
            arrayOf(dayKey, TimelineWriter.MODULE_POMO, action, kind.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    } ?: 0

    private fun iterate(c: Cursor): List<TimelineEvent> {
        val out = ArrayList<TimelineEvent>(c.count)
        do {
            out.add(
                TimelineEvent(
                    id = c.getLong(0),
                    ts = c.getLong(1),
                    dayKey = c.getString(2),
                    module = c.getString(3),
                    action = c.getString(4),
                    refTable = if (c.isNull(5)) null else c.getString(5),
                    refId = if (c.isNull(6)) null else c.getLong(6),
                    payload = c.getString(7),
                ),
            )
        } while (c.moveToNext())
        return out
    }
}
