package com.xiaz.flowvault.core.model

/**
 * 一条闪念胶囊。库里存了它路由去哪张表、以及"记它的时候正在读哪本书、读到第几个字"——
 * 后半截是回血态和日常态之间唯一那条真实联系，之前一直只写不读。
 * `novelName` 不在 captures 表里，是查的时候顺带 JOIN 出来的，省掉界面再去捞一遍书单。
 */
internal data class Capture(
    val id: Long,
    val rawText: String,
    val route: Int,
    val refTable: String?,
    val refId: Long?,
    val novelId: Long?,
    val novelOffset: Long?,
    val createdAt: Long,
    val dayKey: String,
    val novelName: String?,
) {
    val boundToReading: Boolean get() = novelId != null
}
