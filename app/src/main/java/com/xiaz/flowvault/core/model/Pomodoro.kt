package com.xiaz.flowvault.core.model

/** pomodoro_sessions.kind 的取值。库里只存数字，语义只写在这里。 */
internal object Pomodoro {
    const val KIND_FOCUS = 0
    const val KIND_BREAK = 1

    /** 到点自动切态时给休息定的默认长度：读一页纸之前先让人喘一口。 */
    const val BREAK_DEFAULT_SEC = 300

    /**
     * 软熔断的硬上限。到点之后**不打断**——翻过一页才收；
     * 但人可能去倒水、去接电话，超时不再有人翻页，就按这个上限自己收，
     * 否则回血态会一直挂着，攻坚态再也回不来。
     */
    const val BREAK_MAX_OVERDUE_SEC = 300

    fun label(kind: Int): String = if (kind == KIND_FOCUS) "专注" else "休息"

    /** 界面上一律走这里，别在渲染路径上各写各的取整。 */
    fun clockText(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)
}
