package com.xiaz.flowvault.ui.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaz.flowvault.FlowVaultApp
import com.xiaz.flowvault.core.backup.BackupFiles
import com.xiaz.flowvault.core.backup.WriteOutcome
import com.xiaz.flowvault.core.db.BackupDao
import com.xiaz.flowvault.core.db.FlashDao
import com.xiaz.flowvault.core.db.NovelDao
import com.xiaz.flowvault.core.db.RestoreOutcome
import com.xiaz.flowvault.core.db.RestorePlan
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.core.novel.NovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一条"文件接不上"的提醒。恢复出来的行只有这一种下场：数据全在，来源文件认不出了。
 * [isNovel] 决定改哪张表的哪一列，界面两类的摆法也一样，所以合成一种。
 */
internal data class NeedsReauth(
    val id: Long,
    val title: String,
    val meta: String,
    val isNovel: Boolean,
)

internal data class BackupState(
    /** 当前这一步在干什么；null 表示闲。忙的时候界面按它显示一行，不自己编话。 */
    val busyLabel: String? = null,
    val note: String? = null,
    /** 非 null 就是"验过了、等他按第二下"。这一步在屏幕上停留多久都由他决定。 */
    val plan: RestorePlan.Ready? = null,
    val needs: List<NeedsReauth> = emptyList(),
    /** 已经点开文件选择器、正在等他选的那一条；null 表示没有在指认。 */
    val reauthFor: NeedsReauth? = null,
) {
    val busy: Boolean get() = busyLabel != null
}

/**
 * 备份与恢复唯一的持有者：导出、读回来验一遍、两步确认、恢复后重新指认文件。
 *
 * 三条口径定在这里：
 * ① **验和写分成两步**，中间把行数摆给他看——整表替换洗掉的就是他现在库里的东西，不可逆；
 * ② 认不认一个文件"能不能读"，只看系统有没有把**持久读权限**记给这个应用，
 *    不去真的 open 一次：误判成"接不上"他会多点一次指认，误判成"接得上"则是一打开就报错，
 *    前者代价小得多；
 * ③ 每一步都是 IO 线程 + 兜底，坏在最坏的情况是这一句失败原因亮出来，库还是原样。
 */
internal class BackupKit(app: Application) : AndroidViewModel(app) {

    private val database = getApplication<FlowVaultApp>().database
    private val backups = BackupDao(database)
    private val novels = NovelDao(database)
    private val decks = FlashDao(database)
    private val files = BackupFiles(app)
    private val source = NovelSource(app)

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val needs = readNeeds()
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(needs = needs)
            }
        }
    }

    /** 拉起"另存为"时给的建议文件名。界面只管把这个名字交给系统，日期归这边算。 */
    fun suggestedName(): String = files.suggestedName(System.currentTimeMillis())

    /** 导出到**他选定的那一个文件**。正文不在里面，这一点写在完成的那句里。 */
    fun export(uri: Uri) {
        _state.value = _state.value.copy(busyLabel = "正在写出去", note = null)
        viewModelScope.launch(Dispatchers.IO) {
            val text = backups.exportText()
            if (text == null) {
                publish { it.copy(busyLabel = null, note = "库里读不出完整的一份，一个字节都没写下去：原因在日志浮窗里") }
                return@launch
            }
            when (val out = files.write(uri, text)) {
                is WriteOutcome.Refused ->
                    publish { it.copy(busyLabel = null, note = out.reason) }
                is WriteOutcome.Saved -> {
                    val head = "写出去了 ${text.length} 个字符，只有库里的数据，小说正文不在里面"
                    val note = if (out.caveat == null) head else "$head。${out.caveat}"
                    publish { it.copy(busyLabel = null, note = note) }
                }
            }
        }
    }

    /** 第一步：读回来、验一遍。验过了才把两组数亮出来，等第二下。 */
    fun stage(uri: Uri) {
        _state.value = _state.value.copy(busyLabel = "在读那份文件", note = null, plan = null)
        viewModelScope.launch(Dispatchers.IO) {
            val raw = files.read(uri)
            if (raw == null) {
                publish { it.copy(busyLabel = null, note = "那份文件没读上来：可能是选错了，也可能是权限已经没了") }
                return@launch
            }
            when (val plan = backups.inspect(raw)) {
                is RestorePlan.Reject ->
                    publish { it.copy(busyLabel = null, note = plan.reason) }
                is RestorePlan.Ready ->
                    publish { it.copy(busyLabel = null, plan = plan) }
            }
        }
    }

    fun discardPlan() {
        _state.value = _state.value.copy(plan = null, note = null)
    }

    /**
     * 第二步：真写。整表替换在一个事务里，中途挂了就是原样。
     * [onDone] 只在写成的时候于主线程跑一次——屏幕那边要拿它去让三个持有者重读。
     */
    fun confirmRestore(onDone: () -> Unit) {
        val plan = _state.value.plan ?: return
        // 先把 plan 摘掉再动手：这样第二下连点两次也只会走一次。
        _state.value = _state.value.copy(busyLabel = "正在整表替换", note = null, plan = null)
        viewModelScope.launch(Dispatchers.IO) {
            when (val out = backups.apply(plan)) {
                is RestoreOutcome.Failed ->
                    publish { it.copy(busyLabel = null, note = out.reason) }
                is RestoreOutcome.Done -> {
                    val needs = readNeeds()
                    val note = "装回来 ${out.rowsWritten} 行，分布在 ${out.perTable.size} 张表"
                    withContext(Dispatchers.Main.immediate) {
                        _state.value = _state.value.copy(busyLabel = null, note = note, needs = needs)
                        onDone()
                    }
                }
            }
        }
    }

    /** 点某一条的"指认"：先记下是哪一条，再让界面拉起文件选择器。 */
    fun pick(item: NeedsReauth) {
        _state.value = _state.value.copy(reauthFor = item)
    }

    fun cancelPick() {
        _state.value = _state.value.copy(reauthFor = null)
    }

    /** 重新指认一个文件：先把持久读权限钉住，再改库里那一行的来源。 */
    fun completeReauth(uri: Uri) {
        val target = _state.value.reauthFor ?: return
        _state.value = _state.value.copy(busyLabel = "正在接上这个文件", note = null, reauthFor = null)
        viewModelScope.launch(Dispatchers.IO) {
            val persisted = source.takeReadPermission(uri)
            val path = uri.toString()
            val changed = if (target.isNovel) novels.repoint(target.id, path) else decks.repointSource(target.id, path)
            val needs = readNeeds()
            if (!changed) {
                publish {
                    it.copy(
                        busyLabel = null,
                        needs = needs,
                        note = "没接上：${target.title}——库里这一行改不到，多半它已经不在这份备份里了",
                    )
                }
                return@launch
            }
            val head = "接上了：${target.title}"
            publish {
                it.copy(
                    busyLabel = null,
                    needs = needs,
                    note = if (persisted) head else "$head（系统没给持久权限，下次要重选）",
                )
            }
        }
    }

    /**
     * 哪些行的来源文件已经认不出来。判定只读系统的持久权限登记表，不碰文件本身。
     * 卡组那边 `source_path` 为空**不算接不上**：卡早就整组进了库，那条路径只是当初的出处。
     */
    private fun readNeeds(): List<NeedsReauth> {
        val allowed = grantedReads()
        val out = ArrayList<NeedsReauth>()
        novels.list().forEach { novel ->
            if (!allowed.contains(novel.safUri)) {
                out.add(NeedsReauth(novel.id, novel.displayName, "${novel.encoding} · ${novel.charCount} 字", true))
            }
        }
        decks.listDecks(System.currentTimeMillis()).forEach { deck ->
            val path = deck.sourcePath
            if (path != null && !allowed.contains(path)) {
                out.add(NeedsReauth(deck.id, deck.name, "${deck.cardCount} 张", false))
            }
        }
        return out
    }

    private fun grantedReads(): Set<String> = guarding("backup") {
        getApplication<Application>().contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }
    } ?: emptySet()

    private fun publish(transform: (BackupState) -> BackupState) =
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _state.value = transform(_state.value)
        }
}
