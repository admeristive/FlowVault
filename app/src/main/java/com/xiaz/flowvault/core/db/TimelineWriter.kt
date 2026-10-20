package com.xiaz.flowvault.core.db

import android.database.sqlite.SQLiteDatabase
import com.xiaz.flowvault.core.util.DayKey

/**
 * timeline_events 的唯一写入口。
 * 调用方必须把**自己那个事务里的连接**传进来：业务行和时间线要一起提交，
 * 否则一次崩溃就会出现"钱记上了、时间线上没有"的对不上账。
 */
internal object TimelineWriter {

    const val MODULE_TODO = "todo"
    const val MODULE_LEDGER = "ledger"
    const val MODULE_CAPTURE = "capture"
    const val MODULE_POMO = "pomo"
    const val MODULE_NOVEL = "novel"
    const val MODULE_FLASH = "flash"

    const val ACTION_ADD = "add"
    const val ACTION_DONE = "done"
    const val ACTION_DROP = "drop"
    const val ACTION_DELETE = "delete"

    /** 过路费闸专用：pass 是答够六成放行了，fail 是没答够。没答够也要留痕，不然次数看着全对。 */
    const val ACTION_PASS = "pass"
    const val ACTION_FAIL = "fail"

    /** 番茄钟专用：start 是按下开始，finish 是走满，abort 是没走满就停。 */
    const val ACTION_START = "start"
    const val ACTION_FINISH = "finish"
    const val ACTION_ABORT = "abort"

    /** 休息超点后被熔断收掉。单记一个动作，是为了跟"走满"区分开——那是他读完一页主动收的。 */
    const val ACTION_FUSE = "fuse"

    /** 默认取当下；番茄钟收尾要传会话真正结束的那一刻，别把两笔挤在同一毫秒看不出先后。 */
    fun append(
        db: SQLiteDatabase,
        module: String,
        action: String,
        refTable: String?,
        refId: Long?,
        payload: String?,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        db.execSQL(
            "INSERT INTO timeline_events(ts, day_key, module, action, ref_table, ref_id, payload) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(atMillis, DayKey.of(atMillis), module, action, refTable, refId, payload),
        )
    }
}
