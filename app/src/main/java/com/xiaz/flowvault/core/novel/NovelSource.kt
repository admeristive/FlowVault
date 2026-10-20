package com.xiaz.flowvault.core.novel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.xiaz.flowvault.core.debug.DebugLog
import com.xiaz.flowvault.core.debug.guarding
import java.io.ByteArrayOutputStream

/**
 * 小说文件的读写口。**只走 SAF URI**：应用不申请存储权限、不自己翻目录，
 * 用户选中的那一个文件是唯一的读法，卸载后手机里不留任何东西。
 */
internal class NovelSource(private val context: Context) {

    /**
     * 把读权限钉住。没这一步，进程一死 URI 就失效，下次打开"读过的书"会直接 SecurityException。
     * 个别文档提供器只给一次性权限，那种情况降级为"本次进程内能读"，并在日志里点名。
     */
    fun takeReadPermission(uri: Uri): Boolean {
        val persisted = guarding("novel") {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        } ?: false
        if (!persisted) DebugLog.warn(TAG, "该文件给不了持久读权限，只能在这次运行里读：$uri")
        return persisted
    }

    /** 删书时顺手把持久权限还掉，不然系统会一直替这个应用记着这条 URI。 */
    fun releaseReadPermission(uri: Uri) {
        guarding("novel") {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /**
     * 整本读进内存，超过上限就拒。上限是字节数：解码成 UTF-16 正文还要再翻一倍，
     * 这两份内存都在同一时刻存在，所以宁可在门口就把话说明白。
     */
    fun readBounded(uri: Uri, maxBytes: Long): ByteArray? = guarding("novel") {
        val stream = context.contentResolver.openInputStream(uri)
            ?: run {
                DebugLog.warn(TAG, "打不开：$uri")
                return@guarding null
            }
        stream.use { input ->
            val buffer = ByteArray(CHUNK)
            val out = ByteArrayOutputStream(INITIAL)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    DebugLog.warn(TAG, "文件 $total 字节，超过上限 $maxBytes，拒绝导入")
                    return@guarding null
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    fun displayName(uri: Uri): String = guarding("novel") {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
            ?: fallbackName(uri)
    } ?: fallbackName(uri)

    fun sizeOf(uri: Uri): Long? = guarding("novel") {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }

    private fun fallbackName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "未命名.txt"

    private companion object {
        const val TAG = "novel"
        const val CHUNK = 64 * 1024
        const val INITIAL = 128 * 1024
    }
}
