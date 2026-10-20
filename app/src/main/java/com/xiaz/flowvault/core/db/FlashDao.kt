package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.Flash
import com.xiaz.flowvault.core.model.FlashCard
import com.xiaz.flowvault.core.model.FlashCardDraft
import com.xiaz.flowvault.core.model.FlashDeck
import com.xiaz.flowvault.core.model.GateAttempt

/**
 * 闪卡读写。三张表的关系很浅：卡组 1—N 卡，过路费记录谁也不连，所以没有一堆 JOIN。
 * 全文件不出现 upsert / RETURNING 这类 SQLite 3.24+ 语法（本库 minSdk 26）。
 */
internal class FlashDao(private val db: Lazy<SQLiteDatabase>) {

    /**
     * 整组导入：组行 + 每张卡 + 一条时间线在**同一个事务**里落。
     * 半途崩了不能出现"卡组在、卡只有半本"。同名不合并——再导入一次就是第二个卡组，
     * 合并要动已经答过的盒子，那种"帮你整理"最容易把复习记录洗掉。
     */
    fun putDeck(name: String, sourcePath: String?, cards: List<FlashCardDraft>, now: Long): Long? =
        guarding("flash") {
            if (cards.isEmpty()) return@guarding null
            db.value.transaction {
                val deckValues = ContentValues().apply {
                    put("name", name)
                    put("source_path", sourcePath)
                    put("card_count", cards.size)
                    put("imported_at", now)
                }
                val deckId = insertOrThrow("flash_decks", null, deckValues)
                cards.forEach { draft ->
                    val values = ContentValues().apply {
                        put("deck_id", deckId)
                        put("front", draft.front)
                        put("back", draft.back)
                        put("box", Flash.BOX_FIRST)
                        // 新卡当天就见，不等一天：不然刚导完一组，过路费无卡可问。
                        put("due_at", now)
                    }
                    insertOrThrow("flash_cards", null, values)
                }
                TimelineWriter.append(
                    this,
                    TimelineWriter.MODULE_FLASH,
                    TimelineWriter.ACTION_ADD,
                    "flash_decks",
                    deckId,
                    "$name · ${cards.size} 张",
                    atMillis = now,
                )
                deckId
            }
        }

    fun listDecks(now: Long): List<FlashDeck> = guarding("flash") {
        db.value.rawQuery(
            "SELECT d.id, d.name, d.source_path, d.card_count, d.imported_at, " +
                "(SELECT count(*) FROM flash_cards c WHERE c.deck_id = d.id AND c.due_at <= ?) " +
                "FROM flash_decks d ORDER BY d.imported_at DESC, d.id DESC",
            arrayOf(now.toString()),
        ).use { c -> if (!c.moveToFirst()) emptyList() else iterateDecks(c) }
    } ?: emptyList()

    /**
     * 抽这一趟要问的卡。到期最早的先出，同到期按盒子（越生越靠前）再按 id，
     * 所以刚导进来的一批一定排在最前——新卡躲不掉才是这套东西的意义。
     * [deckId] 传 null 表示跨所有卡组抽。
     */
    fun dueCards(deckId: Long?, limit: Int, now: Long): List<FlashCard> = guarding("flash") {
        if (limit <= 0) return@guarding emptyList()
        // 两条各写完整的 SQL，不拼 "WHERE ..." 片段：拼出来的那条按 deckId 是否为 null 分成两种，
        // 少写一个 WHERE 只在其中一条上炸，而这种炸会被 guarding 吞成"今天没卡"，界面上一句错都不露。
        val sql = if (deckId == null) {
            "SELECT $COLUMNS FROM flash_cards WHERE due_at IS NOT NULL AND due_at <= ? " +
                "ORDER BY due_at, box, id LIMIT ?"
        } else {
            "SELECT $COLUMNS FROM flash_cards WHERE deck_id = ? AND due_at IS NOT NULL AND due_at <= ? " +
                "ORDER BY due_at, box, id LIMIT ?"
        }
        val args = if (deckId == null) {
            arrayOf(now.toString(), limit.toString())
        } else {
            arrayOf(deckId.toString(), now.toString(), limit.toString())
        }
        db.value.rawQuery(sql, args).use { c -> if (!c.moveToFirst()) emptyList() else iterateCards(c) }
    } ?: emptyList()

    fun countDue(now: Long): Int = guarding("flash") {
        db.value.rawQuery(
            "SELECT count(*) FROM flash_cards WHERE due_at IS NOT NULL AND due_at <= ?",
            arrayOf(now.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    } ?: 0

    /**
     * 记一次作答。答对升一盒、按新盒子往后推到期；答错跌回盒子 1，**到期时间就压在当下**，
     * 下一趟闸还会再问到它。这里**不写时间线**：一张卡一条会把今天的日子淹掉，组级和过关的动作才值得留痕。
     */
    fun review(cardId: Long, correct: Boolean, atMillis: Long): Boolean = guarding("flash") {
        db.value.transaction {
            var box = -1
            var correctCount = 0
            var wrongCount = 0
            rawQuery(
                "SELECT box, correct_count, wrong_count FROM flash_cards WHERE id = ?",
                arrayOf(cardId.toString()),
            ).use { c ->
                if (c.moveToFirst()) {
                    box = c.getInt(0)
                    correctCount = c.getInt(1)
                    wrongCount = c.getInt(2)
                }
            }
            if (box < 0) return@transaction false
            val next = Flash.nextBox(box, correct)
            // 计数用现读出来的值加一，不写 correct_count = correct_count + 1：
            // ContentValues 表达不了相对增减，而 execSQL 不回传影响行数，就分不清"改了"和"这卡没了"。
            val values = ContentValues().apply {
                put("box", next)
                put("last_review_at", atMillis)
                put("due_at", Flash.nextDueAt(next, correct, atMillis))
                put("correct_count", if (correct) correctCount + 1 else correctCount)
                put("wrong_count", if (correct) wrongCount else wrongCount + 1)
            }
            update("flash_cards", values, "id = ?", arrayOf(cardId.toString())) > 0
        }
    } ?: false

    /**
     * 记一次过路费。**没过也照实落一行**，过没过由六成线当场算出来给回界面。
     * [grantedSec] 是过关换来的阅读秒数，没给就是 0。
     */
    fun recordAttempt(
        context: Int,
        asked: Int,
        correct: Int,
        grantedSec: Int,
        atMillis: Long,
    ): GateAttempt? = guarding("flash") {
        val passed = correct >= Flash.required(asked)
        db.value.transaction {
            val values = ContentValues().apply {
                put("context", context)
                put("asked", asked)
                put("correct", correct)
                put("passed", if (passed) 1 else 0)
                put("granted_sec", if (passed) grantedSec else 0)
                put("created_at", atMillis)
            }
            val id = insertOrThrow("gate_attempts", null, values)
            TimelineWriter.append(
                this,
                TimelineWriter.MODULE_FLASH,
                if (passed) TimelineWriter.ACTION_PASS else TimelineWriter.ACTION_FAIL,
                "gate_attempts",
                id,
                "${Flash.contextLabel(context)}：问 $asked 张对 $correct 张",
                atMillis = atMillis,
            )
            GateAttempt(id, context, asked, correct, passed, if (passed) grantedSec else 0, atMillis)
        }
    }

    /**
     * 今天交过几次过路费、其中几次真过关。`gate_attempts` 没有 day_key，跟番茄钟同一条形：
     * 用时间线框住这一天，再 JOIN 回这张表数。答够六成才算过关，所以第二个数是第一个数的子集。
     */
    fun tollOfDay(dayKey: String): Pair<Int, Int> = guarding("flash") {
        db.value.rawQuery(
            "SELECT count(*), COALESCE(sum(g.passed), 0) FROM timeline_events t " +
                "JOIN gate_attempts g ON g.id = t.ref_id " +
                "WHERE t.day_key = ? AND t.module = ? AND t.ref_table = 'gate_attempts'",
            arrayOf(dayKey, TimelineWriter.MODULE_FLASH),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) to c.getInt(1) else 0 to 0 }
    } ?: (0 to 0)

    /**
     * 卡都已经在库里，`source_path` 只是"当初从哪个文件导的"这条线索。
     * 恢复之后把它接回一个真存在的文件，为的是下次重导不用从头再找一遍。
     */
    fun repointSource(id: Long, sourcePath: String): Boolean = guarding("flash") {
        val values = ContentValues().apply { put("source_path", sourcePath) }
        db.value.update("flash_decks", values, "id = ?", arrayOf(id.toString())) > 0
    } ?: false

    fun dropDeck(id: Long): Boolean = guarding("flash") {
        db.value.transaction {
            val changed = delete("flash_decks", "id = ?", arrayOf(id.toString()))
            if (changed > 0) {
                // 卡靠 ON DELETE CASCADE 一起走，这里只补一条时间线。
                TimelineWriter.append(
                    this,
                    TimelineWriter.MODULE_FLASH,
                    TimelineWriter.ACTION_DELETE,
                    "flash_decks",
                    id,
                    null,
                )
            }
            changed > 0
        }
    } ?: false

    private fun iterateDecks(c: Cursor): List<FlashDeck> {
        val out = ArrayList<FlashDeck>(c.count)
        do {
            out.add(
                FlashDeck(
                    id = c.getLong(0),
                    name = c.getString(1),
                    sourcePath = c.getString(2),
                    cardCount = c.getInt(3),
                    importedAt = c.getLong(4),
                    dueCount = c.getInt(5),
                ),
            )
        } while (c.moveToNext())
        return out
    }

    private fun iterateCards(c: Cursor): List<FlashCard> {
        val out = ArrayList<FlashCard>(c.count)
        do {
            out.add(
                FlashCard(
                    id = c.getLong(0),
                    deckId = c.getLong(1),
                    front = c.getString(2),
                    back = c.getString(3),
                    box = c.getInt(4),
                    lastReviewAt = if (c.isNull(5)) null else c.getLong(5),
                    dueAt = if (c.isNull(6)) null else c.getLong(6),
                    correctCount = c.getInt(7),
                    wrongCount = c.getInt(8),
                ),
            )
        } while (c.moveToNext())
        return out
    }

    private companion object {
        /** 顺序必须和 [iterateCards] 的下标一一对应，改这里要同时改它。 */
        const val COLUMNS = "id, deck_id, front, back, box, last_review_at, due_at, " +
            "correct_count, wrong_count"
    }
}
