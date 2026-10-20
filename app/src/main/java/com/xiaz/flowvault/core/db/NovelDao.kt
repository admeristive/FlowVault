package com.xiaz.flowvault.core.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.model.Novel
import com.xiaz.flowvault.core.model.NovelBookmark

/**
 * 小说行与书签的读写。正文**不落库**：库里只存 URI、编码判定结果和读到第几个字，
 * 一本 10MB 的书进了 SQLite 会让备份导出变成几十兆的 JSON，而那份副本卸载也带不走。
 */
internal class NovelDao(private val db: Lazy<SQLiteDatabase>) {

    /**
     * 同名再来一次算"重导"：换 URI、换编码、换字数，但**读到哪儿了原样留着**——
     * 用户多半只是把同一本书从别的文件夹再选了一遍。
     */
    fun put(
        displayName: String,
        uri: String,
        fileSize: Long?,
        charCount: Int,
        encoding: String,
    ): Long? = guarding("novel") {
        db.value.transaction {
            val existing = rawQuery(
                "SELECT id FROM novels WHERE display_name = ?",
                arrayOf(displayName),
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            val values = ContentValues().apply {
                put("display_name", displayName)
                put("saf_uri", uri)
                if (fileSize == null) putNull("file_size") else put("file_size", fileSize)
                put("char_count", charCount)
                put("encoding", encoding)
                put("imported_at", System.currentTimeMillis())
            }
            if (existing != null) {
                update("novels", values, "id = ?", arrayOf(existing.toString()))
                return@transaction existing
            }
            val id = insertOrThrow("novels", null, values)
            TimelineWriter.append(
                this,
                TimelineWriter.MODULE_NOVEL,
                TimelineWriter.ACTION_ADD,
                "novels",
                id,
                displayName,
            )
            id
        }
    }

    fun list(): List<Novel> = guarding("novel") {
        db.value.rawQuery(
            "SELECT $COLUMNS FROM novels ORDER BY last_opened_at IS NULL, last_opened_at DESC, id DESC",
            null,
        ).use { c -> if (!c.moveToFirst()) emptyList() else iterate(c) }
    } ?: emptyList()

    /**
     * 恢复之后把这一行重新指认到文件上。**只换 URI**：读到第几个字、字数、编码一概不动。
     * 正文本来就不落库，恢复回来的小说除了这么改一次 URI，没有别的办法再打开。
     */
    fun repoint(id: Long, uri: String): Boolean = guarding("novel") {
        val values = ContentValues().apply { put("saf_uri", uri) }
        db.value.update("novels", values, "id = ?", arrayOf(id.toString())) > 0
    } ?: false

    /** 翻页时调：读到第几个字 + 顺手把"最近打开"顶到最前。 */
    fun saveProgress(id: Long, charOffset: Int): Boolean = guarding("novel") {
        val values = ContentValues().apply {
            put("last_char_offset", charOffset)
            put("last_opened_at", System.currentTimeMillis())
        }
        db.value.update("novels", values, "id = ?", arrayOf(id.toString())) > 0
    } ?: false

    fun addMark(novelId: Long, charOffset: Int, label: String): Long? = guarding("novel") {
        val values = ContentValues().apply {
            put("novel_id", novelId)
            put("char_offset", charOffset)
            put("label", label)
            put("created_at", System.currentTimeMillis())
        }
        db.value.insertOrThrow("novel_bookmarks", null, values)
    }

    fun marks(novelId: Long): List<NovelBookmark> = guarding("novel") {
        db.value.rawQuery(
            "SELECT id, novel_id, char_offset, label, created_at FROM novel_bookmarks " +
                "WHERE novel_id = ? ORDER BY char_offset",
            arrayOf(novelId.toString()),
        ).use { c -> if (!c.moveToFirst()) emptyList() else iterateMarks(c) }
    } ?: emptyList()

    fun dropMark(id: Long): Boolean = guarding("novel") {
        db.value.delete("novel_bookmarks", "id = ?", arrayOf(id.toString())) > 0
    } ?: false

    /** 删书。书签由外键 ON DELETE CASCADE 一起带走，不留孤儿。 */
    fun delete(id: Long): Boolean = guarding("novel") {
        db.value.transaction {
            val title = rawQuery("SELECT display_name FROM novels WHERE id = ?", arrayOf(id.toString()))
                .use { c -> if (c.moveToFirst()) c.getString(0) else null }
            val changed = delete("novels", "id = ?", arrayOf(id.toString())) > 0
            if (changed) {
                TimelineWriter.append(
                    this,
                    TimelineWriter.MODULE_NOVEL,
                    TimelineWriter.ACTION_DELETE,
                    "novels",
                    id,
                    title,
                )
            }
            changed
        }
    } ?: false

    private fun iterate(c: Cursor): List<Novel> {
        val out = ArrayList<Novel>(c.count)
        do {
            out.add(
                Novel(
                    id = c.getLong(0),
                    displayName = c.getString(1),
                    safUri = c.getString(2),
                    fileSize = if (c.isNull(3)) null else c.getLong(3),
                    charCount = c.getInt(4),
                    encoding = c.getString(5),
                    lastCharOffset = c.getInt(6),
                    lastOpenedAt = if (c.isNull(7)) null else c.getLong(7),
                ),
            )
        } while (c.moveToNext())
        return out
    }

    private fun iterateMarks(c: Cursor): List<NovelBookmark> {
        val out = ArrayList<NovelBookmark>(c.count)
        do {
            out.add(
                NovelBookmark(
                    id = c.getLong(0),
                    novelId = c.getLong(1),
                    charOffset = c.getInt(2),
                    label = c.getString(3),
                    createdAt = c.getLong(4),
                ),
            )
        } while (c.moveToNext())
        return out
    }

    private companion object {
        // 顺序必须和 iterate() 的下标一一对应，改这里要同时改它。
        const val COLUMNS = "id, display_name, saf_uri, file_size, char_count, encoding, " +
            "last_char_offset, last_opened_at"
    }
}
