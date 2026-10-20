package com.xiaz.flowvault.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日分组键。库里时间戳一律 UTC epoch 毫秒，但"今天/本月"这类聚合必须按**本地**日历切，
 * 所以每行冗余存一个 day_key，聚合时直接等值查，不做时区换算。
 */
object DayKey {

    // 必须钉 Locale.US：默认 Locale 在佛历/和历环境下会把年份算成别纪，2026 会变 2569。
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val month = SimpleDateFormat("yyyy-MM", Locale.US)

    fun of(millis: Long): String = day.format(Date(millis))

    fun monthOf(millis: Long): String = month.format(Date(millis))

    fun today(): String = of(System.currentTimeMillis())

    /** 某月的 LIKE 前缀，用于按月聚合：2026-10 -> '2026-10-%'。 */
    fun monthPrefix(monthKey: String): String = "$monthKey-%"
}
