package com.xiaz.flowvault.core.model

/** 一条待办。start_min / end_min 是当天本地分钟数（0-1439），没排时间段就是 null。 */
data class Todo(
    val id: Long,
    val title: String,
    val note: String?,
    val priority: Int,
    val dayKey: String,
    val startMin: Int?,
    val endMin: Int?,
    val status: Int,
    val plannedPomos: Int,
    val donePomos: Int,
    val createdAt: Long,
    val finishedAt: Long?,
) {
    companion object {
        const val STATUS_OPEN = 0
        const val STATUS_DONE = 1
        const val STATUS_DROPPED = 2

        const val PRIORITY_LOW = 0
        const val PRIORITY_NORMAL = 1
        const val PRIORITY_URGENT = 2
    }
}
