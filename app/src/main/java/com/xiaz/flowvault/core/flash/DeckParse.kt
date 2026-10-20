package com.xiaz.flowvault.core.flash

import com.xiaz.flowvault.core.model.Flash
import com.xiaz.flowvault.core.model.FlashCardDraft
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 解析只有两种下场。[DeckParse.Ok.skipped] 是被丢掉的空行和半行张数，
 * 界面要把它念出来——"进了 480 张，另有 3 行不像卡片被我扔了"，不能默默吞。
 */
internal sealed class DeckParse {

    data class Ok(
        val cards: List<FlashCardDraft>,
        val skipped: Int,
        val deckName: String? = null,
    ) : DeckParse()

    data class Refused(val message: String) : DeckParse()
}

/**
 * 卡组文件 → 一张张卡。只认两种形状：CSV（逗号或分号分隔、双引号可以包）和 JSON
 * （顶层对象数组，或 `{"cards":[...]}`）。不接 Anki 的 .apkg（那是 zip 套 sqlite，要再加依赖），也不接 xlsx。
 * 这里**不碰任何 IO**，进来的已经是解好码的字符串，所以整套逻辑能在电脑上离线重放。
 */
internal object DeckParser {

    private val FRONT_KEYS = listOf("front", "question", "q", "正面", "问题", "前", "term", "word")
    private val BACK_KEYS = listOf("back", "answer", "a", "背面", "答案", "后", "definition", "meaning")

    fun parse(fileName: String, raw: String): DeckParse {
        // BOM 不剥掉会跟着第一个字面，正面看着正常、匹配却对不上。
        val text = raw.removePrefix("\uFEFF")
        val head = text.trimStart()
        val asJson = fileName.lowercase().endsWith(".json") || head.startsWith("[") || head.startsWith("{")
        return if (asJson) json(text) else csv(text)
    }

    // ---- CSV ------------------------------------------------------------------

    /** 一行拆成格子：引号里的分隔符不算拆，`"\uFEFF"` 是一个引号本身；引号没闭合返回 null。 */
    private fun cells(line: String, delim: Char): List<String>? {
        val out = ArrayList<String>(4)
        val buf = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        buf.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    buf.append(ch)
                }
            } else when (ch) {
                '"' -> quoted = true
                delim -> {
                    out.add(buf.toString())
                    buf.setLength(0)
                }
                else -> buf.append(ch)
            }
            i++
        }
        if (quoted) return null
        out.add(buf.toString())
        return out
    }

    private fun csv(text: String): DeckParse {
        val body = if (text.endsWith("\n")) text.dropLast(1) else text
        val lines = body.split('\n')
        val delim = firstDelimiter(lines) ?: ','
        val cards = ArrayList<FlashCardDraft>(64)
        var skipped = 0
        for (index in lines.indices) {
            val line = lines[index].trimEnd('\r')
            if (index == 0 && isHeader(line, delim)) continue
            if (line.isBlank()) {
                skipped++
                continue
            }
            val row = cells(line, delim)
                ?: return DeckParse.Refused("第 ${index + 1} 行的引号没闭合，这份文件形状可疑")
            val front = row.getOrNull(0)?.trim().orEmpty()
            val back = row.getOrNull(1)?.trim().orEmpty()
            if (front.isEmpty() || back.isEmpty()) {
                skipped++
                continue
            }
            if (front.length > Flash.MAX_CELL_CHARS || back.length > Flash.MAX_CELL_CHARS) {
                return DeckParse.Refused("第 ${index + 1} 行的某一侧超过 ${Flash.MAX_CELL_CHARS} 字，不像卡片——是不是选错了文件")
            }
            if (cards.size >= Flash.MAX_CARDS) {
                return DeckParse.Refused("超过 ${Flash.MAX_CARDS} 张了，先把它拆成几个文件")
            }
            cards.add(FlashCardDraft(front, back))
        }
        return if (cards.isEmpty()) {
            DeckParse.Refused("一行卡片都没认出来。CSV 每行是「正面,背面」，逗号或分号都行")
        } else {
            DeckParse.Ok(cards, skipped)
        }
    }

    /** 中文环境的表格导出常用分号，写死逗号会把整行当成一张卡的正面。 */
    private fun firstDelimiter(lines: List<String>): Char? {
        for (line in lines) {
            if (line.isBlank()) continue
            return if (line.indexOf(',') < 0 && line.contains(';')) ';' else ','
        }
        return null
    }

    private fun isHeader(line: String, delim: Char): Boolean {
        val row = cells(line, delim) ?: return false
        if (row.size < 2) return false
        return FRONT_KEYS.contains(row[0].trim().lowercase()) && BACK_KEYS.contains(row[1].trim().lowercase())
    }

    // ---- JSON -----------------------------------------------------------------

    private fun json(text: String): DeckParse {
        val array: JSONArray
        var deckName: String? = null
        try {
            val head = text.trim()
            array = when {
                head.startsWith("[") -> JSONArray(head)
                head.startsWith("{") -> {
                    val root = JSONObject(head)
                    deckName = root.optString("name").takeIf { it.isNotEmpty() }
                        ?: root.optString("deck").takeIf { it.isNotEmpty() }
                    root.optJSONArray("cards") ?: root.optJSONArray("data") ?: root.optJSONArray("items")
                        ?: return DeckParse.Refused("JSON 里找不到卡片数组：得是顶层数组，或者带 cards / data / items 之一")
                }
                else -> return DeckParse.Refused("这份既不像 CSV 也不像 JSON")
            }
        } catch (e: JSONException) {
            return DeckParse.Refused("JSON 读不动：${e.message ?: "格式有问题"}")
        }

        val cards = ArrayList<FlashCardDraft>(array.length())
        var skipped = 0
        for (i in 0 until array.length()) {
            val entry = entryOf(array.opt(i))
            if (entry == null) {
                skipped++
                continue
            }
            if (entry[0].length > Flash.MAX_CELL_CHARS || entry[1].length > Flash.MAX_CELL_CHARS) {
                return DeckParse.Refused("第 ${i + 1} 张的某一侧超过 ${Flash.MAX_CELL_CHARS} 字，不像卡片")
            }
            if (cards.size >= Flash.MAX_CARDS) {
                return DeckParse.Refused("超过 ${Flash.MAX_CARDS} 张了，先把它拆成几个文件")
            }
            cards.add(FlashCardDraft(entry[0], entry[1]))
        }
        return if (cards.isEmpty()) {
            DeckParse.Refused("JSON 里没有一张同时带正面和背面的对象")
        } else {
            DeckParse.Ok(cards, skipped, deckName)
        }
    }

    /**
     * 一个数组元素 → 正面/背面两个字符串。认不出来就返回 null，由调用方计入 skipped——
     * 一份 500 张里夹了一行垃圾，不该让整个文件进不来。
     */
    private fun entryOf(node: Any?): Array<String>? = when (node) {
        is JSONArray -> pair(node.optString(0), node.optString(1))
        is JSONObject -> pair(pick(node, FRONT_KEYS), pick(node, BACK_KEYS))
        else -> null
    }

    private fun pair(front: String?, back: String?): Array<String>? {
        val f = front?.trim().orEmpty()
        val b = back?.trim().orEmpty()
        return if (f.isEmpty() || b.isEmpty()) null else arrayOf(f, b)
    }

    private fun pick(obj: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            val value = obj.optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
