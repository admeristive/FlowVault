package com.xiaz.flowvault.ui.state

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaz.flowvault.FlowVaultApp
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.db.CaptureDao
import com.xiaz.flowvault.core.db.LedgerDao
import com.xiaz.flowvault.core.db.NovelDao
import com.xiaz.flowvault.core.db.SettingsStore
import com.xiaz.flowvault.core.db.TodoDao
import com.xiaz.flowvault.core.model.Novel
import com.xiaz.flowvault.core.model.NovelBookmark
import com.xiaz.flowvault.core.model.NovelRef
import com.xiaz.flowvault.core.novel.NovelSource
import com.xiaz.flowvault.core.novel.TextDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 阅读底色。护眼不是"绿色就护"，只是把亮度压下来，屏幕暗一点眼睛少受点刺激。 */
internal enum class ReaderSurface(val label: String) {
    LIGHT("亮"),
    DARK("暗"),
    EYE("护眼"),
    ;

    companion object {
        fun of(name: String?): ReaderSurface = entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

/**
 * 字号与行距都走**档位**而不是连续值：档位能让设置里存进来读出去对得上，
 * 也能保证同一本书在两台机器上排出来是同一批页。
 */
internal data class ReaderStyle(
    val fontStep: Int = DEFAULT_FONT_STEP,
    val lineStep: Int = DEFAULT_LINE_STEP,
    val surface: ReaderSurface = ReaderSurface.LIGHT,
    val paginated: Boolean = true,
) {
    val fontSp: Int get() = FONT_SP[fontStep.coerceIn(0, FONT_SP.lastIndex)]
    val lineHeightSp: Int get() = fontSp + LINE_SP[lineStep.coerceIn(0, LINE_SP.lastIndex)]

    fun bumpFont(delta: Int) = copy(fontStep = (fontStep + delta).coerceIn(0, FONT_SP.lastIndex))

    fun bumpLine(delta: Int) = copy(lineStep = (lineStep + delta).coerceIn(0, LINE_SP.lastIndex))

    fun nextSurface() = copy(surface = SURFACES[(SURFACES.indexOf(surface) + 1) % SURFACES.size])

    companion object {
        val FONT_SP = intArrayOf(15, 16, 17, 18, 19, 21, 23, 25, 27, 30)
        val LINE_SP = intArrayOf(2, 4, 6, 8, 10, 12)
        private val SURFACES = ReaderSurface.entries.toList()
        private const val DEFAULT_FONT_STEP = 3
        private const val DEFAULT_LINE_STEP = 1
    }
}

internal data class ShelfState(
    val novels: List<Novel> = emptyList(),
    val captureCounts: Map<Long, Int> = emptyMap(),
    val current: Novel? = null,
    val marks: List<NovelBookmark> = emptyList(),
    val text: String = "",
    val offset: Int = 0,
    val busy: Boolean = false,
    val note: String? = null,
    val style: ReaderStyle = ReaderStyle(),
) {
    /** 正文总共读进来多少字；没在读就是 0，界面上的百分比据此而不是据此猜。 */
    val loadedChars: Int get() = text.length

    /** 这本书里记下过几条胶囊。没查回来之前是空表，界面上就不显示这一段。 */
    fun capturedCount(novelId: Long): Int? = captureCounts[novelId]
}

/**
 * 回血态的小说书架。两处代价写在这里：
 * ① 正文只在**打开那一本书**时整本读进内存，不缓存副本、不落库——卸载不留东西，代价是每次打开要重解一遍；
 * ② 所有文件读写都在 IO 线程且包着兜底，坏在最坏的情况是这一本打不开，界面仍然回得来。
 */
internal class NovelShelf(app: Application) : AndroidViewModel(app) {

    private val database = getApplication<FlowVaultApp>().database
    private val dao = NovelDao(database)
    private val captureDao = CaptureDao(database, TodoDao(database), LedgerDao(database))
    private val source = NovelSource(app)

    private val _state = MutableStateFlow(ShelfState())
    val state: StateFlow<ShelfState> = _state.asStateFlow()

    /** 上一页走这个栈，而不是"减一页字数"：每页排多少字由排版决定，减字数会漏行。 */
    private val history = ArrayDeque<Int>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val novels = dao.list()
            val style = readStyle()
            val counts = captureDao.countByNovel()
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(novels = novels, style = style, captureCounts = counts)
            }
        }
    }

    /** 记完一条胶囊回查一次条数：书架上"记了 N 条"得当场跳，不然看着像没记上。 */
    fun refreshCaptureCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val counts = captureDao.countByNovel()
            publish { it.copy(captureCounts = counts) }
        }
    }

    /** 正在读的书和位置，交给闪念胶囊当绑定。没在读书就是 null，胶囊就正常记一条裸闪念。 */
    fun binding(): NovelRef? = _state.value.current?.let { NovelRef(it.id, _state.value.offset) }

    fun import(uri: Uri) {
        _state.value = _state.value.copy(busy = true, note = null)
        viewModelScope.launch(Dispatchers.IO) {
            val persisted = source.takeReadPermission(uri)
            val bytes = source.readBounded(uri, Novel.MAX_SOURCE_BYTES)
            if (bytes == null) {
                publish { it.copy(busy = false, note = "这份文件读不出来，原因在日志浮窗里") }
                return@launch
            }
            val decoded = guarding("novel") { TextDecode.decode(bytes) }
            if (decoded == null) {
                publish { it.copy(busy = false, note = "这份文件解不出正文，原因在日志浮窗里") }
                return@launch
            }
            val name = source.displayName(uri)
            val saved = dao.put(name, uri.toString(), bytes.size.toLong(), decoded.text.length, decoded.encoding)
            val novels = dao.list()
            // 存上了才敢说"已导入"。库写不进去时书架上根本不会出现这本，话就不能说成成功了。
            val note = if (saved == null) {
                "《$name》正文解开了但没存进书架：库写入失败，原因在日志浮窗里"
            } else {
                val suffix = if (persisted) "" else "（系统没给持久权限，下次要重选）"
                "《$name》${decoded.encoding} · ${decoded.text.length} 字$suffix"
            }
            publish { it.copy(busy = false, novels = novels, note = note) }
        }
    }

    fun open(novel: Novel) {
        _state.value = _state.value.copy(busy = true, note = null)
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = source.readBounded(novel.safUri.toUri(), Novel.MAX_SOURCE_BYTES)
            if (bytes == null) {
                publish {
                    it.copy(
                        busy = false,
                        current = null,
                        note = "《${novel.displayName}》打不开：文件可能挪了或改了名，重新导一次",
                    )
                }
                return@launch
            }
            val decoded = guarding("novel") { TextDecode.decode(bytes) }
            if (decoded == null) {
                publish {
                    it.copy(
                        busy = false,
                        current = null,
                        note = "《${novel.displayName}》解不出正文，原因在日志浮窗里",
                    )
                }
                return@launch
            }
            val marks = dao.marks(novel.id)
            // 重导后正文长度可能变短，存着的偏移可能已经越界。贴回末尾，不许因此崩在排版上。
            val offset = novel.lastCharOffset.coerceIn(0, decoded.text.length)
            history.clear()
            publish {
                it.copy(
                    busy = false,
                    current = novel,
                    text = decoded.text,
                    offset = offset,
                    marks = marks,
                    note = if (decoded.encoding == novel.encoding) null
                    else "编码重判为 ${decoded.encoding}",
                )
            }
        }
    }

    /**
     * 整库换过之后调一次。书架上的行是库里的东西，库一换就得重读；
     * 正在读的那本更要连正文一起放下——屏幕上留着的可能是被洗掉的那一本的旧文字。
     */
    fun reloadAfterRestore() {
        leaveReader()
        viewModelScope.launch(Dispatchers.IO) {
            val novels = dao.list()
            val style = readStyle()
            publish { it.copy(novels = novels, style = style) }
        }
    }

    fun leaveReader() {
        history.clear()
        _state.value = _state.value.copy(current = null, marks = emptyList(), text = "", offset = 0)
    }

    /** 翻页落到新的字符位置。排版那边算出多少就落多少，这里只负责夹住边界。 */
    fun turnTo(target: Int) {
        val now = _state.value
        val novel = now.current ?: return
        val clamped = target.coerceIn(0, now.text.length)
        if (clamped == now.offset) return
        history.addLast(now.offset)
        while (history.size > HISTORY_LIMIT) history.removeFirst()
        _state.value = now.copy(offset = clamped)
        viewModelScope.launch(Dispatchers.IO) { dao.saveProgress(novel.id, clamped) }
    }

    fun turnBack() {
        val now = _state.value
        val novel = now.current ?: return
        val prev = history.removeLastOrNull() ?: return
        _state.value = now.copy(offset = prev.coerceIn(0, now.text.length))
        viewModelScope.launch(Dispatchers.IO) { dao.saveProgress(novel.id, prev) }
    }

    fun addMark() {
        val now = _state.value
        val novel = now.current ?: return
        val offset = now.offset
        val label = labelAt(now.text, offset)
        viewModelScope.launch(Dispatchers.IO) {
            dao.addMark(novel.id, offset, label)
            val marks = dao.marks(novel.id)
            publish { it.copy(marks = marks, note = "书签：第 $offset 字") }
        }
    }

    fun dropMark(id: Long) {
        val novel = _state.value.current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.dropMark(id)
            val marks = dao.marks(novel.id)
            publish { it.copy(marks = marks) }
        }
    }

    fun remove(novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            source.releaseReadPermission(novel.safUri.toUri())
            dao.delete(novel.id)
            val novels = dao.list()
            val reading = _state.value.current?.id == novel.id
            publish {
                if (reading) {
                    it.copy(novels = novels, current = null, marks = emptyList(), text = "", offset = 0)
                } else {
                    it.copy(novels = novels)
                }
            }
        }
    }

    fun font(delta: Int) = restyle { it.bumpFont(delta) }

    fun line(delta: Int) = restyle { it.bumpLine(delta) }

    fun surface() = restyle { it.nextSurface() }

    fun paging(toPaginated: Boolean) = restyle { it.copy(paginated = toPaginated) }

    private fun restyle(transform: (ReaderStyle) -> ReaderStyle) {
        val next = transform(_state.value.style)
        _state.value = _state.value.copy(style = next)
        viewModelScope.launch(Dispatchers.IO) { writeStyle(next) }
    }

    /** 档位串存 settings 的一行 KV；读回来任何一段对不上都退回默认，不拿脏值排版。 */
    private fun readStyle(): ReaderStyle {
        val raw = store()?.get(KEY_STYLE) ?: return ReaderStyle()
        val parts = raw.split('|')
        if (parts.size < 4) return ReaderStyle()
        return ReaderStyle(
            fontStep = parts[0].toIntOrNull() ?: ReaderStyle().fontStep,
            lineStep = parts[1].toIntOrNull() ?: ReaderStyle().lineStep,
            surface = ReaderSurface.of(parts[2]),
            paginated = parts[3] == "1",
        )
    }

    private fun writeStyle(style: ReaderStyle) {
        store()?.put(
            KEY_STYLE,
            "${style.fontStep}|${style.lineStep}|${style.surface.name}|${if (style.paginated) 1 else 0}",
        )
    }

    /** settings 每次现取：库没开成时它是 null，阅读照样能跑，只是设置不留存。 */
    private fun store(): SettingsStore? = getApplication<FlowVaultApp>().settings()

    private fun publish(transform: (ShelfState) -> ShelfState) =
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _state.value = transform(_state.value)
        }

    private companion object {
        const val KEY_STYLE = "reader.style"
        const val HISTORY_LIMIT = 300
        const val LABEL_CHARS = 16

        /** 书签上那行字：从偏移处取十几个字，换行压掉，取不到就退回"第 N 字"。 */
        fun labelAt(text: String, offset: Int): String {
            if (text.isEmpty()) return "开头"
            val from = offset.coerceIn(0, text.lastIndex)
            val slice = text.substring(from, minOf(from + LABEL_CHARS, text.length))
                .replace('\n', ' ')
                .trim()
            return if (slice.isEmpty()) "第 $from 字" else slice
        }
    }
}
