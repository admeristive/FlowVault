package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.xiaz.flowvault.core.capture.CapturePlan
import com.xiaz.flowvault.core.capture.CaptureRoute
import com.xiaz.flowvault.core.capture.CaptureRouter
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.Capture
import com.xiaz.flowvault.core.model.Todo
import com.xiaz.flowvault.core.util.DayKey

/**
 * 闪念胶囊的落库处。一次提交写两行（captures 和它路由出来的业务行），并且必须在同一个事务里：
 * "胶囊记上了、账上却没有"比整条丢更容易骗过眼睛。
 */
internal class CaptureDao(
    private val db: Lazy<SQLiteDatabase>,
    private val todos: TodoDao,
    private val ledger: LedgerDao,
) {

    /** 读不出任何东西时返回 null，界面把原文留在输入框里，不假装记过了。 */
    fun record(raw: String, novelId: Long? = null, novelOffset: Long? = null): Long? =
        guarding("capture") {
            val plan = CaptureRouter.parse(raw) ?: return@guarding null
            val text = raw.trim()
            val now = System.currentTimeMillis()
            val dayKey = DayKey.of(now)
            val route = when (plan) {
                is CapturePlan.Ledger -> CaptureRoute.LEDGER
                is CapturePlan.Todo -> CaptureRoute.TODO
                is CapturePlan.Note -> CaptureRoute.NOTE
            }
            db.value.transaction {
                val values = ContentValues().apply {
                    put("raw_text", text)
                    put("route", route)
                    put("created_at", now)
                    put("day_key", dayKey)
                    // 读书时随手记的胶囊要能回溯到原文位置，没在读书就两个都留空。
                    if (novelId != null) {
                        put("novel_id", novelId)
                        put("novel_offset", novelOffset)
                    }
                }
                val captureId = insertOrThrow("captures", null, values)
                val bound: Pair<String, Long> = when (plan) {
                    // 分类候选直接交给 ensureCategory，没建过的名字当场建出来——这就是"快捷记账"的本意。
                    is CapturePlan.Ledger -> "ledger_tx" to
                        ledger.insertIn(this, plan.amountCents, plan.word, plan.note, now)
                    is CapturePlan.Todo -> "todos" to
                        todos.insertIn(this, plan.title, null, Todo.PRIORITY_NORMAL, dayKey, plan.startMin, null, 0)
                    is CapturePlan.Note -> "captures" to captureId
                }
                update(
                    "captures",
                    ContentValues().apply {
                        put("ref_table", bound.first)
                        put("ref_id", bound.second)
                    },
                    "id = ?",
                    arrayOf(captureId.toString()),
                )
                TimelineWriter.append(
                    this,
                    TimelineWriter.MODULE_CAPTURE,
                    TimelineWriter.ACTION_ADD,
                    "captures",
                    captureId,
                    text,
                )
                captureId
            }
        }

    /**
     * 今天记下的胶囊，连"记的时候在读哪本、第几个字"一起带回来。
     * 书被删过就取不到名字（`ON DELETE SET NULL` 会把 novel_id 清掉），那时只报"读书时记的"。
     */
    fun listByDay(dayKey: String, limit: Int): List<Capture> = guarding("capture") {
        db.value.rawQuery(
            "SELECT c.id, c.raw_text, c.route, c.ref_table, c.ref_id, c.novel_id, c.novel_offset, " +
                "c.created_at, c.day_key, n.display_name " +
                "FROM captures c LEFT JOIN novels n ON n.id = c.novel_id " +
                "WHERE c.day_key = ? ORDER BY c.created_at DESC, c.id DESC LIMIT ?",
            arrayOf(dayKey, limit.toString()),
        ).use { c -> if (!c.moveToFirst()) emptyList() else iterate(c) }
    } ?: emptyList()

    /** 每本书里记下过多少条胶囊。书架上那一行要用它，一次 GROUP BY 拿全，不按本逐条查。 */
    fun countByNovel(): Map<Long, Int> = guarding("capture") {
        db.value.rawQuery(
            "SELECT novel_id, count(*) FROM captures WHERE novel_id IS NOT NULL GROUP BY novel_id",
            null,
        ).use { c ->
            val out = HashMap<Long, Int>(c.count)
            while (c.moveToNext()) out[c.getLong(0)] = c.getInt(1)
            out
        }
    } ?: emptyMap()

    private fun iterate(c: Cursor): List<Capture> {
        val out = ArrayList<Capture>(c.count)
        do {
            out.add(
                Capture(
                    id = c.getLong(0),
                    rawText = c.getString(1),
                    route = c.getInt(2),
                    refTable = if (c.isNull(3)) null else c.getString(3),
                    refId = if (c.isNull(4)) null else c.getLong(4),
                    novelId = if (c.isNull(5)) null else c.getLong(5),
                    novelOffset = if (c.isNull(6)) null else c.getLong(6),
                    createdAt = c.getLong(7),
                    dayKey = c.getString(8),
                    novelName = if (c.isNull(9)) null else c.getString(9),
                ),
            )
        } while (c.moveToNext())
        return out
    }
}
