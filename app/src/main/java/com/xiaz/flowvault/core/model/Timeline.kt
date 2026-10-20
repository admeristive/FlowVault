package com.xiaz.flowvault.core.model

/**
 * 时间线的一行。库里存的是英文 module / action 键，中文说法在 ui/components/DayRows 里映射，
 * 以后加模块不用动数据库里的取值。
 */
data class TimelineEvent(
    val id: Long,
    val ts: Long,
    val dayKey: String,
    val module: String,
    val action: String,
    val refTable: String?,
    val refId: Long?,
    val payload: String?,
)
