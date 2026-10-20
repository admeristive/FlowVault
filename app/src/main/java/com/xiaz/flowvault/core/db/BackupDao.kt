package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.xiaz.flowvault.core.debug.DebugLog
import com.xiaz.flowvault.core.debug.guarding
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 备份的口径。三件事定死在这里：
 * ① **一张 JSON 装下整个库**，逐表逐行按列名存，不再分文件——分文件的备份会缺一个文件而整体作废；
 * ② **数字列读回来必须是 JSON 数字**。实测：INTEGER 亲和列会把 "1730000000000" 这样的数字字符串救回整数
 *    （typeof 仍是 integer），但救不回 "abc" 与 "0x10"——那两种会真存成文本，之后 `due_at <= ?` 命中 0 行，
 *    这张卡就永远不再到期。所以一律按类型硬校验：宁可拒收一份本来还能用的文件并报明是哪一行，
 *    也不要靠亲和性蒙过去、把一颗不会响的雷种回库里；
 * ③ **正文不进来**（库里本来也不存），所以恢复出来的小说和卡组多半要重新指认文件，那是界面上另一件事。
 */
internal object Backup {

    const val APP = "flowvault"

    /** 备份文件自己的格式号，跟 [Schema.VERSION]（库结构版本）是两回事，别混用。 */
    const val FORMAT = 1

    /** 读回来的字符上限。自用应用攒几年也就几百 KB，超这么多一定是选错了文件。 */
    const val MAX_CHARS = 32_000_000

    /** 读回来的**字节**上限，比字符上限宽一档：正文里全是中文时一个字符要占三个字节。 */
    const val MAX_FILE_BYTES = 64L * 1024 * 1024

    const val MAX_ROWS_PER_TABLE = 200_000

    /**
     * 写库顺序＝父先子后；清空取它的反序＝子先父后。
     * 外键开关在事务中途改不了（SQLite 禁止在事务里切 foreign_keys），所以只能靠顺序过关。
     */
    val INSERT_ORDER = listOf(
        "settings", "novels", "todos", "ledger_categories", "flash_decks",
        "captures", "pomodoro_sessions", "ledger_tx", "flash_cards",
        "novel_bookmarks", "gate_attempts", "timeline_events",
    )

    val DELETE_ORDER: List<String> = INSERT_ORDER.asReversed()
}

/** 一张表要写回去的行。 */
internal data class TableRows(val table: String, val rows: JSONArray)

/**
 * 恢复前的一轮体检结果。`Reject` 带的是能照做的话，不是异常栈；
 * `Ready` 同时带着"要写多少行"和"会洗掉多少行"，界面第二下确认必须把两组数摆出来。
 */
internal sealed class RestorePlan {
    data class Reject(val reason: String) : RestorePlan()

    data class Ready(
        val tables: List<TableRows>,
        /** 当前库里各表行数，只列非零的——全零时面板上不该刷一长排 0。 */
        val before: List<Pair<String, Int>>,
        val incomingTotal: Int,
    ) : RestorePlan()
}

/** 真写下去之后的结果。哪一行插不进去整个事务就回滚，所以这里的数只会是"全进了"。 */
internal sealed class RestoreOutcome {
    data class Done(val rowsWritten: Int, val perTable: List<Pair<String, Int>>) : RestoreOutcome()
    data class Failed(val reason: String) : RestoreOutcome()
}

/**
 * 整库导出与恢复。两处刻意保守：
 * ① 备份里出现**我不认识的表名或列名就整份拒收**，不"跳过认不出的继续恢复"——那等于把他手改过的
 *    备份里的真数据悄悄丢掉一块，而事后看不出来；
 * ② 恢复＝**整表替换**，不做合并。合并要判"id 相同算不算同一条""改过的以哪边为准"，
 *    一个离线自用应用担不起这套判定；代价是恢复会洗掉当前库，所以界面要两步确认。
 */
internal class BackupDao(private val db: Lazy<SQLiteDatabase>) {

    /** 全库 → 一段可读的 JSON 文本。任何一张表读挂了都返回 null，不交出半本备份。 */
    fun exportText(): String? = guarding("backup") {
        val root = JSONObject()
        root.put(KEY_APP, Backup.APP)
        root.put(KEY_FORMAT, Backup.FORMAT)
        root.put(KEY_SCHEMA, Schema.VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        val tables = JSONObject()
        for (table in Backup.INSERT_ORDER) {
            val rows = dumpTable(table) ?: return@guarding null
            tables.put(table, rows)
        }
        root.put(KEY_TABLES, tables)
        root.toString(2)
    }

    /**
     * 只验不写：把备份读成 [RestorePlan]。这一步现查每张表的列，所以备份与本机结构对不上
     * 在这里就报出来，不会写一半才发现。
     */
    fun inspect(raw: String): RestorePlan {
        if (raw.length > Backup.MAX_CHARS) {
            return RestorePlan.Reject(
                "这份文件 ${raw.length} 字符，超过 ${Backup.MAX_CHARS / 1000 / 1000} MB 量级的上限，不像备份",
            )
        }
        val root = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            DebugLog.error("backup", "JSON 读不动", e)
            return RestorePlan.Reject("这不是一个能读的备份：JSON 在开头就断了，详情在日志浮窗里")
        }
        val app = root.optString(KEY_APP)
        if (app != Backup.APP) {
            return RestorePlan.Reject("这不是流转秘境的备份（app 标记是\"${app.ifBlank { "缺失" }}\"）")
        }
        val format = root.optInt(KEY_FORMAT, -1)
        if (format != Backup.FORMAT) {
            return RestorePlan.Reject("备份格式号是 $format，这台机器只认 ${Backup.FORMAT}")
        }
        // 结构版本比本机新：它有的列我造不出来，装不进去。比本机旧：缺的表按空处理，
        // 老库升级本来就走 Migrations，现在这张库的结构已经是最新的了。
        val schema = root.optInt(KEY_SCHEMA, -1)
        if (schema < 0) return RestorePlan.Reject("备份里没写它出自哪一版库结构")
        if (schema > Schema.VERSION) {
            return RestorePlan.Reject(
                "这份备份出自库结构第 $schema 版，这台机器是第 ${Schema.VERSION} 版，装不进去",
            )
        }
        val tables = root.optJSONObject(KEY_TABLES) ?: return RestorePlan.Reject("备份里没有 tables 这一段")
        val unknown = tables.keys().asSequence().toSet() - Backup.INSERT_ORDER.toSet()
        if (unknown.isNotEmpty()) {
            return RestorePlan.Reject("备份里有我不认识的表：${unknown.joinToString(" ")}，整份先不动")
        }

        val prepared = ArrayList<TableRows>(Backup.INSERT_ORDER.size)
        var total = 0
        // 按我自己的顺序走，不跟文件里的键序：文件顺序是给人看的，写库顺序是有外键约束的。
        for (table in Backup.INSERT_ORDER) {
            val rows = tables.optJSONArray(table) ?: JSONArray()
            checkTable(table, rows)?.let { return RestorePlan.Reject(it) }
            if (rows.length() > 0) prepared.add(TableRows(table, rows))
            total += rows.length()
        }
        val before = Backup.INSERT_ORDER.mapNotNull { table -> countRows(table)?.takeIf { it > 0 }?.let { table to it } }
        return RestorePlan.Ready(prepared, before, total)
    }

    /**
     * 整表替换，一个事务包到底。
     * ★回滚只认**抛出异常**：androidx 的 `transaction { }` 在 API 30+ 走 `markSuccess = true` 那条实现，
     * 块里返回 null 照样提交。所以中途出问题一律 throw，靠 finally 里的 endTransaction 回滚。
     */
    fun apply(plan: RestorePlan.Ready): RestoreOutcome {
        val work = ArrayList<Triple<String, Columns, JSONArray>>(plan.tables.size)
        for (item in plan.tables) {
            val meta = columnsOf(item.table)
                ?: return RestoreOutcome.Failed("列信息读不出来：${item.table}，先不动库")
            work.add(Triple(item.table, meta, item.rows))
        }
        return try {
            val real = db.value
            real.beginTransaction()
            val perTable: List<Pair<String, Int>>
            try {
                Backup.DELETE_ORDER.forEach { real.delete(it, null, null) }
                perTable = work.map { (table, meta, rows) -> table to real.insertRows(table, meta, rows) }
                real.setTransactionSuccessful()
            } finally {
                real.endTransaction()
            }
            RestoreOutcome.Done(perTable.sumOf { it.second }, perTable)
        } catch (t: Throwable) {
            DebugLog.error("backup", "恢复失败，已回滚", t)
            RestoreOutcome.Failed("没写进去，库回滚到原样：${t.message}（详情在日志浮窗里）")
        }
    }

    // ---- 写回（全部跑在同一条事务连接上）----

    /**
     * 逐行插回，列名与类型都按 PRAGMA 现读出来的形状走。
     * **不去动 sqlite_sequence**：实测 SQLite 在插入显式 rowid 时自己把序号抬到最大
     * （本机 seq 停在 2 的库恢复进 id 1..40 之后，seq 变 40、下一次自增拿到 41、不撞）。
     * 手工 DELETE 再 INSERT 反而多一条错路：备份里有行没带 id 时我会算出个偏小的最大值，把序号压低。
     */
    private fun SQLiteDatabase.insertRows(table: String, meta: Columns, rows: JSONArray): Int {
        rows.iterate { row ->
            val values = ContentValues()
            for (name in meta.names) {
                if (!row.has(name) || row.isNull(name)) continue
                if (meta.isInteger(name)) values.put(name, row.getLong(name))
                else values.put(name, row.getString(name))
            }
            insertOrThrow(table, null, values)
        }
        return rows.length()
    }

    // ---- 导出 ----

    private fun dumpTable(table: String): JSONArray? {
        val meta = columnsOf(table) ?: return null
        val arr = JSONArray()
        db.value.rawQuery("SELECT * FROM $table", null).use { c ->
            val names = (0 until c.columnCount).map { c.getColumnName(it) }
            while (c.moveToNext()) {
                val row = JSONObject()
                names.forEachIndexed { i, name ->
                    when {
                        c.isNull(i) -> row.put(name, JSONObject.NULL)
                        !meta.isInteger(name) -> row.put(name, c.getString(i))
                        else -> {
                            // 整数列也走字符串再判一次：真存进过文本的列，getLong 会静默给个 0，
                            // 那等于把损坏写成"看着正常"的数字。读不成数字就原样带出去，
                            // 让恢复端 checkTable 当场报"第 N 行的 X 不是数字"，响在明处。
                            val raw = c.getString(i)
                            val asLong = raw?.toLongOrNull()
                            if (asLong != null) row.put(name, asLong) else row.put(name, raw)
                        }
                    }
                }
                arr.put(row)
            }
        }
        return arr
    }

    // ---- 校验：返回 null 表示没问题，否则返回能直接给他看的那句话 ----

    private fun checkTable(table: String, rows: JSONArray): String? {
        if (rows.length() > Backup.MAX_ROWS_PER_TABLE) {
            return "$table 有 ${rows.length()} 行，超过 ${Backup.MAX_ROWS_PER_TABLE} 行上限，不像备份"
        }
        val meta = columnsOf(table) ?: return "$table 的列在本机库里查不出来"
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: return "$table 第 ${i + 1} 行不是对象"
            val keys = row.keys().asSequence().toSet()
            (keys - meta.nameSet).firstOrNull()?.let { return "$table 第 ${i + 1} 行有本机库里没有的列：$it" }
            meta.required.firstOrNull { !row.has(it) || row.isNull(it) }
                ?.let { return "$table 第 ${i + 1} 行缺了必填列：$it" }
            // 数字列必须真是数字。实测亲和性会把 "1730000000000" 救回整数，但 "abc"/"0x10" 救不回来，
            // 那两种会真存成文本、让 due_at <= ? 命中 0 行；与其赌哪串能被救，不如一律当场报出是哪一行。
            meta.integers.firstOrNull { row.has(it) && !row.isNull(it) && row.opt(it) !is Number }
                ?.let { return "$table 第 ${i + 1} 行的 $it 不是数字" }
        }
        return null
    }

    private fun countRows(table: String): Int? = guarding("backup") {
        db.value.rawQuery("SELECT count(*) FROM $table", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // ---- 列的形状。导出与恢复都读它，两处共用一份真相。----

    private class Columns(
        val names: List<String>,
        val integers: Set<String>,
        val required: Set<String>,
    ) {
        val nameSet: Set<String> = names.toSet()
        fun isInteger(name: String): Boolean = integers.contains(name)
    }

    /**
     * PRAGMA table_info 的列序固定为 cid, name, type, notnull, dflt_value, pk。
     * 整数判定按 SQLite 的亲和性来：声明里带 INT 就走整数。
     */
    private fun columnsOf(table: String): Columns? = guarding("backup") {
        val names = ArrayList<String>()
        val integers = LinkedHashSet<String>()
        val required = LinkedHashSet<String>()
        db.value.rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                names.add(name)
                if (c.getString(2).orEmpty().uppercase().contains("INT")) integers.add(name)
                val notNull = c.getInt(3) > 0
                if (notNull && c.isNull(4) && name != "id") required.add(name)
            }
        }
        if (names.isEmpty()) null else Columns(names, integers, required)
    }

    private companion object {
        const val KEY_APP = "app"
        const val KEY_FORMAT = "format"
        const val KEY_SCHEMA = "schema"
        const val KEY_EXPORTED_AT = "exported_at"
        const val KEY_TABLES = "tables"
    }
}

/** JSONArray 没有 Kotlin 友好的遍历；这里只保证拿到对象行，非对象行由校验层提前挡掉。 */
private inline fun JSONArray.iterate(body: (JSONObject) -> Unit) {
    for (i in 0 until length()) body(getJSONObject(i))
}
