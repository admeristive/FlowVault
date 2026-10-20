package com.xiaz.flowvault.core.model

/** novels 表一行。safUri 是持久化过的文档 URI，charCount 是解码后的**字符**数，不是字节数。 */
data class Novel(
    val id: Long,
    val displayName: String,
    val safUri: String,
    val fileSize: Long?,
    val charCount: Int,
    val encoding: String,
    val lastCharOffset: Int,
    val lastOpenedAt: Long?,
) {
    /** 读到哪儿了，百分比。没正文可算时给 0，不编数。 */
    fun percent(): Int = percentOf(lastCharOffset, charCount)

    companion object {
        /** 一次导入允许的源文件上限。整本要解码成正文，UTF-16 存着占两倍内存，超过就拒绝。 */
        const val MAX_SOURCE_BYTES = 32L * 1024 * 1024

        fun percentOf(offset: Int, charCount: Int): Int =
            if (charCount <= 0) 0 else offset * 100 / charCount
    }
}

/** 一条字偏移书签。label 是记下时那行开头的十几个字，只为让人认得出那是哪儿。 */
data class NovelBookmark(
    val id: Long,
    val novelId: Long,
    val charOffset: Int,
    val label: String,
    val createdAt: Long,
)

/**
 * 闪念胶囊的绑定目标：正在读哪本、读到第几个字。
 * 回血态输入条每次提交都带上它，胶囊里就留着"这句是看书时想到的"。
 */
data class NovelRef(val novelId: Long, val charOffset: Int)
