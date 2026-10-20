package com.xiaz.flowvault.core.util

/** 金额一律用带符号「分」存库，避免浮点累加误差；这里只做显示层格式化。 */
object Money {

    /**
     * "-15" / "500" / "12.5" -> 分。只允许最多两位小数；带符号前缀（- + ！）由路由层先剥掉再进来。
     * 解不开就返回 null，让调用方退回"这是一条笔记"，不猜用户想写什么。
     */
    fun fromYuan(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('.')
        if (parts.size > 2) return null
        val yuan = parts[0].toLongOrNull() ?: return null
        val fraction = when (parts.size) {
            1 -> 0L
            else -> when (parts[1].length) {
                0 -> 0L
                1 -> (parts[1] + "0").toLongOrNull() ?: return null
                2 -> parts[1].toLongOrNull() ?: return null
                else -> return null
            }
        }
        val sign = if (yuan < 0) -1L else 1L
        return yuan * 100 + sign * fraction
    }

    /** "+500.00" / "-15.00" / "0.00"，账本里收入支出要一眼分开。 */
    fun signed(cents: Long): String {
        val abs = if (cents == Long.MIN_VALUE) cents else if (cents < 0) -cents else cents
        val head = if (cents < 0) "-" else if (cents > 0) "+" else ""
        return "$head${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }
}
