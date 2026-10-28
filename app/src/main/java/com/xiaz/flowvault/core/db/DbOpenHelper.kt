package com.xiaz.flowvault.core.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import com.xiaz.flowvault.core.debug.DebugLog

internal class DbOpenHelper(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, Schema.VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // 外键约束在 SQLite 里默认是关的，必须每次连接都开。
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            db.transaction {
                Schema.CREATE_TABLES.forEach { execSQL(it) }
                Indexes.CREATE.forEach { execSQL(it) }
            }
            DebugLog.info(TAG, "建表完成，共 ${Schema.CREATE_TABLES.size} 张表 / ${Indexes.CREATE.size} 个索引")
        } catch (t: Throwable) {
            DebugLog.error(TAG, "建表失败", t)
            throw t
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DebugLog.warn(TAG, "数据库升级 $oldVersion -> $newVersion，逐版本走 Migrations")
        Migrations.upgrade(db, oldVersion, newVersion)
    }

    private companion object {
        const val TAG = "db"
        const val DB_NAME = "flowvault.db"
    }
}

internal object Migrations {
    /**
     * 一级一级往前推，每级执行 Schema.MIGRATIONS 里那几条语句。它对不上号就硬失败——
     * 宁可启动时库打不开、日志浮窗里看得见，也不许悄悄建出半张表结构然后到处缺列。
     */
    fun upgrade(db: SQLiteDatabase, from: Int, to: Int) {
        for (target in (from + 1)..to) {
            val steps = Schema.MIGRATIONS[target]
                ?: error("数据库升到 $target 尚无迁移脚本")
            db.transaction {
                steps.forEach { execSQL(it) }
            }
            DebugLog.warn(TAG, "迁移 $target：补了 ${steps.size} 条语句")
        }
    }

    private const val TAG = "db"
}
