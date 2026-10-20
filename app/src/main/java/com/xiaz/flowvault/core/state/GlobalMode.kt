package com.xiaz.flowvault.core.state

/** 顶层运行态。三态互斥，手动切换；番茄钟到点的自动触发在阶段 3 接进来。 */
enum class GlobalMode(val label: String, val hint: String) {
    FOCUS("攻坚", "只做眼前这段时间该做的事"),
    BREAK("回血", "读一会儿，到点自己收"),
    DAILY("日常", "今天的时间、钱和念头"),
    ;

    companion object {
        fun fromKey(key: String?): GlobalMode? = entries.firstOrNull { it.name == key }
    }
}
