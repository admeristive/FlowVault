package com.xiaz.flowvault.core.capture

import com.xiaz.flowvault.core.util.Money

/** captures.route 里存的序号；库里只有列没有语义，语义只写在这里。 */
internal object CaptureRoute {
    const val NOTE = 0
    const val LEDGER = 1
    const val TODO = 2
}

/**
 * 一条胶囊的去处。这里只描述"读成了什么"，不落库——分类要不要新建、胶囊要不要绑当前小说，
 * 由界面层决定，保持这个解析是纯函数（无 Android 依赖，随时可脱机断言）。
 */
internal sealed class CapturePlan {

    /**
     * amountCents 带符号，支出为负。word 是金额后的第一个词，拿去当分类候选；
     * note 是剩下的话，可能为空。
     */
    data class Ledger(val amountCents: Long, val word: String?, val note: String?) : CapturePlan()

    /** startMin 是从零点到该时刻的分钟数，读不出时间就是 null（仍然算一条待办）。 */
    data class Todo(val startMin: Int?, val title: String) : CapturePlan()

    data class Note(val text: String) : CapturePlan()
}

/**
 * 全局闪念胶囊的唯一入口。规则就三条，宁可退回笔记也不猜：
 * `-15 午饭` / `+500 工资` 走账本，`!14:00 复习` 走待办，其余原样进笔记。
 */
internal object CaptureRouter {

    fun parse(raw: String): CapturePlan? {
        val whole = normalize(raw)
        if (whole.isEmpty()) return null
        val head = whole.first()
        val body = whole.substring(1)
        return when (head) {
            '-' -> ledger(body, whole, negative = true)
            '+' -> ledger(body, whole, negative = false)
            '!' -> todo(body, whole)
            else -> CapturePlan.Note(whole)
        }
    }

    /** 只归一开头那一个字符：正文里的全角符号是你写的字，不能替你改。 */
    private fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed
        val head = when (trimmed.first()) {
            '！' -> '!'
            '＋' -> '+'
            '－' -> '-'
            else -> trimmed.first()
        }
        return "$head${trimmed.substring(1)}"
    }

    private fun ledger(body: String, whole: String, negative: Boolean): CapturePlan {
        val amountText = body.takeWhile { it.isDigit() || it == '.' }
        val cents = if (amountText.isEmpty()) null else Money.fromYuan(amountText)
        // 解不开、或者正好是 0 元，都不算一笔流水：0 收支只会把账本汇总搅浑，退回笔记让他重写。
        if (cents == null || cents == 0L) return CapturePlan.Note(whole)
        val tail = whole.substring(1 + amountText.length).trim()
        val words = tail.split(BLANKS).filter { it.isNotEmpty() }
        return CapturePlan.Ledger(
            amountCents = if (negative) -cents else cents,
            word = words.firstOrNull(),
            note = if (words.size >= 2) words.drop(1).joinToString(" ") else null,
        )
    }

    private fun todo(body: String, whole: String): CapturePlan {
        val hourText = body.takeWhile { it.isDigit() }
        if (hourText.isEmpty() || hourText.length > 2) return titleOrNote(body, whole)
        val sep = body.getOrNull(hourText.length)
        // 分隔符这里两种冒号都认，因为输入法在数字后面给出的就是全角那一个。
        if (sep != ':' && sep != '：') return titleOrNote(body, whole)
        val rest = body.substring(hourText.length + 1)
        val minuteText = rest.takeWhile { it.isDigit() }
        if (minuteText.length != 2) return titleOrNote(body, whole)
        val hour = hourText.toIntOrNull() ?: return titleOrNote(body, whole)
        val minute = minuteText.toIntOrNull() ?: return titleOrNote(body, whole)
        if (hour > 23 || minute > 59) return titleOrNote(body, whole)
        val title = rest.substring(minuteText.length).trim()
        if (title.isEmpty()) return CapturePlan.Note(whole)
        return CapturePlan.Todo(startMin = hour * 60 + minute, title = title)
    }

    /** `!复习` 这种没时间的光杆命令：内容照抄，时间留空，别把用户写的字丢掉。 */
    private fun titleOrNote(body: String, whole: String): CapturePlan {
        val title = body.trim()
        return if (title.isEmpty()) CapturePlan.Note(whole) else CapturePlan.Todo(null, title)
    }

    /** Java 正则里的 \s 不含全角空格，中文输入法敲出来的那一下必须算分隔，否则分类名会带上空格。 */
    private val BLANKS = Regex("[\\s\\u3000]+")
}
