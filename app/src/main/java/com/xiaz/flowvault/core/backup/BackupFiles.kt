package com.xiaz.flowvault.core.backup

import android.content.Context
import android.net.Uri
import com.xiaz.flowvault.core.db.Backup
import com.xiaz.flowvault.core.debug.DebugLog
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.util.DayKey
import java.io.ByteArrayOutputStream

/**
 * 写出去的结果。`Saved` 也可能带一句话——那是"这个位置不能覆盖截断"，
 * 必须原样给用户看：目标文件原本比新内容长时尾部会留着上一次的残渣，那种文件再拿回来恢复会报读不动，
 * 不能让他以为备份是好的。
 */
internal sealed class WriteOutcome {
    data class Saved(val caveat: String?) : WriteOutcome()
    data class Refused(val reason: String) : WriteOutcome()
}

/**
 * 备份文件唯一的出入口，仍然只走 SAF URI：不申请存储权限、不自己翻目录，导出落到**用户选定的那一个文件**，
 * 卸载后手机里不留任何东西。
 *
 * 读写都收在这里，日志也就都归 `backup` 这个标签——不复用小说那边的 `NovelSource`，
 * 免得一次失败的备份在日志里看起来像书出了事。
 */
internal class BackupFiles(private val context: Context) {

    /** 建议的文件名。日期在前，备份攒一文件夹时靠名字就排得开。 */
    fun suggestedName(atMillis: Long): String = "flowvault-${DayKey.of(atMillis)}.json"

    fun write(uri: Uri, text: String): WriteOutcome {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // 先要"写并截断"。个别文档提供器不认 wt 模式，降级成普通写，但要留一句提醒。
        val strict = openAndWrite(uri, bytes, "wt")
        if (strict) return WriteOutcome.Saved(null)
        DebugLog.warn("backup", "目标位置不接受 wt（写并截断），改用普通写：$uri")
        if (openAndWrite(uri, bytes, null)) {
            return WriteOutcome.Saved("这个位置不支持覆盖截断：要是选中了更长的旧文件，尾部会留残渣，恢复时会报读不动")
        }
        return WriteOutcome.Refused("打不开那个位置，一个字都没写下去：原因在日志浮窗里")
    }

    /**
     * 整份读回来。字节上限先挡一道，防止把 10MB 的小说当成备份选中（那种文件解出来也一定不是 JSON）。
     * BOM 去掉再交出去：自己导出的不带，手改过的编辑器经常加。
     */
    fun read(uri: Uri): String? = guarding("backup") {
        val input = context.contentResolver.openInputStream(uri) ?: run {
            DebugLog.warn("backup", "读不到那个文件：$uri")
            return@guarding null
        }
        val out = ByteArrayOutputStream(DEFAULT_SIZE)
        input.use { stream ->
            val buffer = ByteArray(CHUNK)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > Backup.MAX_FILE_BYTES) {
                    DebugLog.warn("backup", "备份文件 $total 字节，超过上限，拒绝解析")
                    return@guarding null
                }
                out.write(buffer, 0, read)
            }
        }
        String(out.toByteArray(), Charsets.UTF_8).removePrefix(BOM)
    }

    private fun openAndWrite(uri: Uri, bytes: ByteArray, mode: String?): Boolean = guarding("backup") {
        val stream = if (mode == null) {
            context.contentResolver.openOutputStream(uri)
        } else {
            context.contentResolver.openOutputStream(uri, mode)
        } ?: return@guarding false
        stream.use {
            it.write(bytes)
            it.flush()
        }
        true
    } ?: false

    private companion object {
        const val BOM = "\uFEFF"
        const val CHUNK = 64 * 1024
        const val DEFAULT_SIZE = 128 * 1024
    }
}
