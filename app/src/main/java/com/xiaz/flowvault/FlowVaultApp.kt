package com.xiaz.flowvault

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.xiaz.flowvault.core.db.DbOpenHelper
import com.xiaz.flowvault.core.db.Schema
import com.xiaz.flowvault.core.db.SettingsStore
import com.xiaz.flowvault.core.debug.DebugLog
import com.xiaz.flowvault.core.debug.guarding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlowVaultApp : Application() {

    private val _databaseStatus = MutableStateFlow("尚未打开")
    val databaseStatus = _databaseStatus.asStateFlow()

    @Volatile
    private var store: SettingsStore? = null

    override fun onCreate() {
        super.onCreate()
        DebugLog.attachStorage(filesDir)
        installCrashBridge()
        DebugLog.info("app", "启动")
    }

    /** 第一次用到才真正建 helper；WAL 打开后读写拿的是同一条连接，所以全局只留这一个池。 */
    private val helperLazy = lazy { DbOpenHelper(this) }

    /**
     * DAO 共用的连接入口。它**会抛**（库打不开时），所以只允许出现在 guarding 包裹的代码块里，
     * 不许在任何界面渲染路径上裸用。
     */
    internal val database: Lazy<SQLiteDatabase> = lazy { helperLazy.value.writableDatabase }

    /** 第一次用到才真正开库；打不开就返回 null，调用方自行降级，绝不抛出到界面。 */
    internal fun settings(): SettingsStore? = store ?: openDatabase()

    private fun openDatabase(): SettingsStore? {
        val opened = guarding("app") {
            val helper = helperLazy.value
            val (tables, indexes) = probeSchema(helper.writableDatabase)
            _databaseStatus.value = "已就绪 flowvault.db · $tables 表 / $indexes 索引"
            SettingsStore(helper).also { store = it }
        }
        if (opened == null) _databaseStatus.value = "打开失败，见下方日志"
        return opened
    }

    /**
     * 每次启动把库里的真实表和索引数出来，而不是依赖"建表完成"那行日志——那行只在装完后
     * 第一次启动打一次（onCreate 只跑一次），且 INFO 不落盘，重启就看不到。
     * 数量对不上时要把**具体表名**报出来，光报个数字我照样猜不出是谁建的。
     */
    private fun probeSchema(db: SQLiteDatabase): Pair<Int, Int> {
        val actual = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { c -> while (c.moveToNext()) actual.add(c.getString(0)) }
        val indexes = countOf(db, "type='index' AND name LIKE 'ix_%'")

        val expected = Schema.CREATE_TABLES.map { it.tableName() }
        // 框架建的表，不由本项目 DDL 产生，见 BENIGN_TABLES 的说明。
        val mine = actual.filterNot { it in BENIGN_TABLES }
        val extra = mine.filter { it !in expected }
        val missing = expected.filter { it !in mine }
        if (extra.isNotEmpty() || missing.isNotEmpty()) {
            DebugLog.warn(
                "db",
                "库与 Schema 不一致：多出=[${extra.joinToString(" ")}] 缺少=[${missing.joinToString(" ")}]",
            )
        }
        DebugLog.info("db", "库内实测 ${mine.size} 张表 / $indexes 个索引")
        return mine.size to indexes
    }

    /** 从 "CREATE TABLE settings (...)" 里取出 settings。DDL 只在 Schema 里写死，不含反引号。 */
    private fun String.tableName(): String = trim().substringAfter("CREATE TABLE ").substringBefore("(").trim()

    private fun countOf(db: SQLiteDatabase, where: String): Int =
        db.rawQuery("SELECT count(*) FROM sqlite_master WHERE $where", null)
            .use { if (it.moveToFirst()) it.getInt(0) else -1 }

    private fun installCrashBridge() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DebugLog.error("crash", "未捕获异常 on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        /**
         * 存在于库里、但不是本项目 DDL 建的表，比对 Schema 时跳过，免得 WARN 狼来了。
         * android_metadata：2026-10-14 卸载重装后全新建库实测存在（建表日志报 11，探测报多出它）。
         * 取证排除法：APK 内无 assets/无 .db 资源、全部 dex 与本项目源码都零命中这个名字 ⇒ 系统建库时加的。
         */
        val BENIGN_TABLES = setOf("android_metadata")
    }
}
