package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.LedgerTx
import com.xiaz.flowvault.core.util.DayKey

/**
 * 账本读写。金额是带符号「分」，支出为负。
 * 按月聚合走 day_key 的 LIKE 前缀，不用 strftime：那样会把时区假设写死在 SQL 里。
 * 全文件不出现 upsert / RETURNING 这类 SQLite 3.24+ 语法（本库 minSdk 26）。
 */
internal class LedgerDao(private val db: Lazy<SQLiteDatabase>) {

    /**
     * 单独拆出来是给胶囊路由用的：captures 行和这笔流水必须在**同一个事务**里落，
     * 否则崩溃之后会出现"胶囊有记录、账上没有"这种对不上账的半条数据。
     */
    internal fun insertIn(
        tr: SQLiteDatabase,
        amountCents: Long,
        categoryName: String?,
        note: String?,
        occurredAt: Long,
    ): Long {
        val values = ContentValues().apply {
            put("amount_cents", amountCents)
            put("note", note)
            put("occurred_at", occurredAt)
            put("day_key", DayKey.of(occurredAt))
        }
        if (categoryName != null) values.put("category_id", ensureCategory(tr, categoryName))
        val id = tr.insertOrThrow("ledger_tx", null, values)
        TimelineWriter.append(
            tr,
            TimelineWriter.MODULE_LEDGER,
            TimelineWriter.ACTION_ADD,
            "ledger_tx",
            id,
            note,
        )
        return id
    }

    fun listByDay(dayKey: String): List<LedgerTx> =
        guarding("ledger") {
            db.value.rawQuery(
                "SELECT t.id, t.amount_cents, t.category_id, c.name, t.note, t.occurred_at, t.day_key " +
                    "FROM ledger_tx t LEFT JOIN ledger_categories c ON c.id = t.category_id " +
                    "WHERE t.day_key = ? ORDER BY t.occurred_at DESC, t.id DESC",
                arrayOf(dayKey),
            ).use { c -> if (!c.moveToFirst()) emptyList() else iterate(c) }
        } ?: emptyList()

    fun sumByMonth(monthKey: String): Long = total("day_key LIKE ?", arrayOf(DayKey.monthPrefix(monthKey)))

    private fun total(where: String, args: Array<String>): Long =
        guarding("ledger") {
            db.value.rawQuery(
                "SELECT IFNULL(SUM(amount_cents), 0) FROM ledger_tx WHERE $where",
                args,
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        } ?: 0L

    /**
     * 按名字取分类 id，没有就建。INSERT OR IGNORE 撞上 UNIQUE(name) 后再 SELECT，
     * 这是 upsert 的旧写法替身，在 Android 8 的库上也成立。调用点已处于事务内。
     */
    private fun ensureCategory(tr: SQLiteDatabase, name: String): Long {
        tr.execSQL("INSERT OR IGNORE INTO ledger_categories(name, sort) VALUES(?, 0)", arrayOf(name))
        return tr.rawQuery("SELECT id FROM ledger_categories WHERE name = ?", arrayOf(name)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else error("分类 $name 建不出来")
        }
    }

    private fun iterate(c: Cursor): List<LedgerTx> {
        val out = ArrayList<LedgerTx>(c.count)
        do {
            out.add(
                LedgerTx(
                    id = c.getLong(0),
                    amountCents = c.getLong(1),
                    categoryId = if (c.isNull(2)) null else c.getLong(2),
                    categoryName = c.getString(3),
                    note = c.getString(4),
                    occurredAt = c.getLong(5),
                    dayKey = c.getString(6),
                ),
            )
        } while (c.moveToNext())
        return out
    }
}
