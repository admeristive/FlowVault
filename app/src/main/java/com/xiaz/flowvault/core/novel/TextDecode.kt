package com.xiaz.flowvault.core.novel

import com.xiaz.flowvault.core.debug.DebugLog
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 解码结果：正文和判定出来的编码名。编码名原样存进 novels.encoding，出问题时日志里能对上是哪一路。 */
internal data class Decoded(val text: String, val encoding: String)

/**
 * TXT 编码判定。本地小说的现实是 UTF-8 与 GBK 两分天下，判错的代价是满屏乱码，
 * 所以不用统计启发式，用**严格解码当探针**：一种编码能不能一个字节都不报错地解完整本，
 * 能就赢。顺序按"错了最疼的排前面"：带 BOM 的定死 → UTF-8 → GBK → GB18030 → 最后兜底。
 */
internal object TextDecode {

    private const val TAG = "novel"

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.isEmpty()) return Decoded("", UTF8)

        bomOf(bytes)?.let { (name, skip) ->
            strict(bytes, name, skip)?.let { return Decoded(normalize(it), name) }
            DebugLog.warn(TAG, "带 $name BOM 但解不开，继续按无 BOM 探针试")
        }

        strict(bytes, UTF8, 0)?.let { return Decoded(normalize(it), UTF8) }
        strict(bytes, GBK, 0)?.let { return Decoded(normalize(it), GBK) }
        strict(bytes, GB18030, 0)?.let { return Decoded(normalize(it), GB18030) }

        // 全都不肯收：宁可给一片能读的正文加一行日志，也不空屏。
        val loose = String(bytes, Charsets.ISO_8859_1)
        DebugLog.warn(TAG, "UTF-8/GBK/GB18030 都解不开，按单字节兜底显示，正文可能有乱码")
        return Decoded(normalize(loose), "BYTE")
    }

    /** BOM 前缀 → (编码名, 要跳过的字节数)。 */
    private fun bomOf(bytes: ByteArray): Pair<String, Int>? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            UTF8 to 3
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> UTF16BE to 2
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> UTF16LE to 2
        else -> null
    }

    /**
     * 严格解码，失败返回 null——这里的 null **是判定手段而不是错误**，所以不打日志：
     * 一个 GBK 文件在 UTF-8 这关必然报错，那是流程正常，不是出了事。
     */
    private fun strict(bytes: ByteArray, name: String, skip: Int): String? {
        if (!Charset.isSupported(name)) return null
        val decoder = Charset.forName(name).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching {
            decoder.decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString()
        }.getOrNull()
    }

    /** Windows 的 \r\n 与老 Mac 的 \r 一律并成 \n：否则排版时每行都多出一个空行。 */
    private fun normalize(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    private const val UTF8 = "UTF-8"
    private const val GBK = "GBK"
    private const val GB18030 = "GB18030"
    private const val UTF16BE = "UTF-16BE"
    private const val UTF16LE = "UTF-16LE"
}
