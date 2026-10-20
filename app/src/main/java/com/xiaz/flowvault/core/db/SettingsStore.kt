package com.xiaz.flowvault.core.db

import android.database.sqlite.SQLiteOpenHelper
import com.xiaz.flowvault.core.debug.guarding

/** 键值配置。写失败一律返回 false 并留下日志，业务层不该因为存盘失败而崩。 */
internal class SettingsStore(private val helper: SQLiteOpenHelper) {

    fun get(key: String): String? = guarding("settings") {
        helper.readableDatabase
            .rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    fun put(key: String, value: String): Boolean = guarding("settings") {
        // 不用 ON CONFLICT DO UPDATE：那是 SQLite 3.24 才有的语法，Android 11 以下的系统库会当场报错，
        // 而本库 minSdk=26。settings 表没有被任何外键指向，REPLACE 的"删再插"语义在这里无损。
        helper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings(key, value) VALUES(?, ?)",
            arrayOf(key, value),
        )
        true
    } ?: false
}
