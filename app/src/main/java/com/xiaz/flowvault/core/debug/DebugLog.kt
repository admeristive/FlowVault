package com.xiaz.flowvault.core.debug

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Level { INFO, WARN, ERROR }

data class LogEntry(
    val atMillis: Long,
    val level: Level,
    val tag: String,
    val message: String,
)

/**
 * 进程内环形日志 + 只落 ERROR 的小文件。
 * 文件的意义：崩溃后进程没了，下次启动还能在日志浮窗里看到上一条错误。
 */
object DebugLog {

    private const val MEMORY_CAPACITY = 400
    private const val FILE_LIMIT_BYTES = 256 * 1024
    private const val FILE_KEEP_LINES = 200

    private val ring = ArrayDeque<LogEntry>()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    /** 由 Application 在 onCreate 里调用；任何失败只影响日志本身，不外抛。 */
    fun attachStorage(dir: File) {
        try {
            val f = File(dir, "flowvault-debug.log")
            file = f
            if (f.exists()) {
                val lines = f.readLines().takeLast(FILE_KEEP_LINES)
                synchronized(ring) {
                    lines.forEach { raw -> parse(raw)?.let { ring.addLast(it) } }
                    trimAndPublish()
                }
            }
            info("debug", "日志浮窗就绪，已恢复 ${_entries.value.size} 条历史记录")
        } catch (t: Throwable) {
            // 日志系统自己坏掉时只能留在内存里。
            record(Level.ERROR, "debug", "日志文件不可用", t)
        }
    }

    fun info(tag: String, message: String) = record(Level.INFO, tag, message, null)
    fun warn(tag: String, message: String) = record(Level.WARN, tag, message, null)
    fun error(tag: String, message: String, cause: Throwable? = null) =
        record(Level.ERROR, tag, message, cause)

    private fun record(level: Level, tag: String, message: String, cause: Throwable?) {
        val full = if (cause == null) message else message + "\n" + flatten(cause)
        val entry = LogEntry(System.currentTimeMillis(), level, tag, full)
        synchronized(ring) {
            ring.addLast(entry)
            trimAndPublish()
        }
        if (level == Level.ERROR) persist(entry)
    }

    private fun trimAndPublish() {
        while (ring.size > MEMORY_CAPACITY) ring.removeFirst()
        _entries.value = ring.toList()
    }

    private fun flatten(t: Throwable): String = buildString {
        append(t.javaClass.name).append(": ").append(t.message)
        t.stackTrace.take(10).forEach {
            append("\n    at ").append(it.className).append('.').append(it.methodName)
                .append('(').append(it.fileName).append(':').append(it.lineNumber).append(')')
        }
        t.cause?.let {
            append("\n  caused by ").append(it.javaClass.name).append(": ").append(it.message)
        }
    }

    private fun persist(entry: LogEntry) {
        val f = file ?: return
        try {
            if (f.length() > FILE_LIMIT_BYTES) {
                val kept = f.readLines().takeLast(FILE_KEEP_LINES / 2)
                f.writeText(kept.joinToString("\n") + "\n")
            }
            f.appendText(encode(entry) + "\n")
        } catch (_: Throwable) {
            // 落盘失败不再递归记日志，否则会和日志系统互相打穿。
        }
    }

    private fun encode(e: LogEntry): String =
        "${e.atMillis}|${e.level.name}|${e.tag}|${e.message.replace("\n", "\\n")}"

    private fun parse(raw: String): LogEntry? {
        val parts = raw.split('|', limit = 4)
        if (parts.size < 4) return null
        val at = parts[0].toLongOrNull() ?: return null
        val level = runCatching { Level.valueOf(parts[1]) }.getOrNull() ?: return null
        return LogEntry(at, level, parts[2], parts[3].replace("\\n", "\n"))
    }

    fun formatTime(atMillis: Long): String = clock.format(Date(atMillis))

    /** 给浮窗的「复制全部」用，纯文本，保持发生顺序。 */
    fun asText(): String = _entries.value.joinToString("\n") { e ->
        val head = "${clock.format(Date(e.atMillis))} ${e.level.name.padEnd(5)} ${e.tag}"
        "$head ${e.message.replace("\n", "\n        ")}"
    }
}

/** 所有本地 I/O、数据库、URI 权限操作的统一兜底：吞掉异常但必须留下可读痕迹。 */
inline fun <T> guarding(tag: String, block: () -> T): T? =
    try {
        block()
    } catch (t: Throwable) {
        DebugLog.error(tag, "操作失败", t)
        null
    }
