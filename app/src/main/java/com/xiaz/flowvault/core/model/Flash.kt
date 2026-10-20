package com.xiaz.flowvault.core.model

/** 一个卡组。[cardCount] 是导入那一刻数出来的张数，之后删卡不回填——界面上要说得出"当初进了多少张"。 */
data class FlashDeck(
    val id: Long,
    val name: String,
    val sourcePath: String?,
    val cardCount: Int,
    val importedAt: Long,
    /** 现在到期的张数，查询时现算，行里没有这一列。 */
    val dueCount: Int,
)

/** 一张卡。盒子=1 是新卡，答对升一格、答错跌回 1，见 [Flash.nextBox]。 */
data class FlashCard(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    val box: Int,
    val lastReviewAt: Long?,
    val dueAt: Long?,
    val correctCount: Int,
    val wrongCount: Int,
)

/** 一次"过路费"。asked/correct 是当场数字，没答对也照实记一行，不美化。 */
data class GateAttempt(
    val id: Long,
    val context: Int,
    val asked: Int,
    val correct: Int,
    val passed: Boolean,
    val grantedSec: Int,
    val createdAt: Long,
)

/** 从文件里解析出来、还没进库的一行。 */
data class FlashCardDraft(
    val front: String,
    val back: String,
)

/**
 * 闪卡的调度口径。间隔按"天"给，不按分钟——这是个离线自用应用，
 * 今天该见的卡今天见到就够了，精确到分钟只会让到期时间看着假。
 */
internal object Flash {

    /** 五盒 Leitner。下标 0 是盒子 1。 */
    private val INTERVAL_DAYS = intArrayOf(1, 3, 7, 14, 30)

    const val BOX_FIRST = 1
    val BOX_LAST: Int = INTERVAL_DAYS.size

    const val DAY_MS = 86_400_000L

    /** 一趟过路费问几张。到期的不满这个数就全问，不能凑数重复问同一张。 */
    const val TOLL_CARDS = 5

    const val CTX_ENTER_BREAK = 0
    const val CTX_DAILY_SPOT = 1

    /** 一个文件最多进多少张，超了就是选错文件（多半把那本 TXT 当成卡组了）。 */
    const val MAX_CARDS = 5000

    /** 卡组文件的字节上限。卡组是几万字级的文本，4M 已经宽得离谱，再大一定是选错了。 */
    const val MAX_SOURCE_BYTES = 4L * 1024 * 1024

    /** 单格超过这么多字就不像卡片了，像整段正文——拒掉，别把一本塞进一张卡。 */
    const val MAX_CELL_CHARS = 2000

    /**
     * 下一次到期时间。答对升一盒、答错跌回盒子 1（重头再来才是错的意义）。
     * 已经顶到最后一盒还答对，间隔不再无限拉长，就按第 5 盒循环。
     */
    fun nextBox(box: Int, correct: Boolean): Int = when {
        !correct -> BOX_FIRST
        box < BOX_LAST -> box + 1
        else -> BOX_LAST
    }

    /** 这个盒子该隔几天再见。盒子号越界时按最后一盒算，不抛。 */
    fun intervalDays(box: Int): Int = INTERVAL_DAYS[(box - 1).coerceIn(0, INTERVAL_DAYS.size - 1)]

    /**
     * 下一次什么时候再见到这张。**答错的不推后**：错的就是没过，没过就该继续在门口等着。
     * 推后了会漏出一个洞——一轮没过之后到期队列空了，再点一次"打开"就免过路费读成了。
     */
    fun nextDueAt(box: Int, correct: Boolean, atMillis: Long): Long =
        if (correct) atMillis + intervalDays(box) * DAY_MS else atMillis

    /**
     * 过关线：答对数要到 asked 的六成，向上取整。全整数算，不碰浮点。
     * 5 张要 3 张对，3 张要 2 张对，1 张要 1 张对，0 张要 0 张对（没卡就免过路费）。
     */
    fun required(asked: Int): Int = if (asked <= 0) 0 else (asked * 6 + 9) / 10

    fun contextLabel(context: Int): String = when (context) {
        CTX_ENTER_BREAK -> "进回血"
        CTX_DAILY_SPOT -> "日常抽查"
        else -> "过路费"
    }

    /** 库里的 box 被别处写坏时界面也别崩，越界一律显示成 1 盒。 */
    fun boxLabel(box: Int): String = "${box.coerceIn(BOX_FIRST, BOX_LAST)} 盒"
}
