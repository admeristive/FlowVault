package com.xiaz.flowvault.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.core.debug.DebugLog
import com.xiaz.flowvault.core.debug.Level
import com.xiaz.flowvault.core.debug.guarding
import com.xiaz.flowvault.ui.components.ActionLabel
import com.xiaz.flowvault.ui.theme.hairline
import com.xiaz.flowvault.ui.theme.stateColors

/** 顶栏长按标题唤出。异常在这里格式化显示，一整段可直接复制走。 */
@Composable
internal fun DebugPanel(databaseStatus: String, onClose: () -> Unit) {
    val entries by DebugLog.entries.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scrimSource = remember { MutableInteractionSource() }
    val bodySource = remember { MutableInteractionSource() }

    // 两处点击都要显式关掉水波纹：浮窗是 if(panelOpen) 即时销毁的，默认水波纹会在被拆掉的
    // 图层上继续播一帧，荣耀机型上表现为一片噪点（用户 10-14 真机报"点击后出现雪花"）。
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = scrimSource, indication = null, onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 12.dp, vertical = 40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, hairline(), MaterialTheme.shapes.medium)
                .clickable(interactionSource = bodySource, indication = null, onClick = {})
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("调试日志", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                ActionLabel("关闭", onClick = onClose)
            }
            Text(
                text = "数据库：$databaseStatus",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (entries.isEmpty()) {
                    Text("（还没有记录）", style = MaterialTheme.typography.bodySmall)
                }
                entries.asReversed().forEach { e ->
                    val states = stateColors()
                    val color = when (e.level) {
                        Level.ERROR -> states.bad
                        Level.WARN -> states.warn
                        Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "${DebugLog.formatTime(e.atMillis)}  ${e.level.name}  ${e.tag}  ${e.message}",
                        style = MaterialTheme.typography.labelSmall,
                        // 日志这两行是异常栈，等宽按列对齐才看得下去——甲档里唯一一处不照单执行的地方。
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionLabel(
                    label = if (copied) "已复制" else "复制全部",
                ) {
                    copied = guarding("clipboard") {
                        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        manager.setPrimaryClip(ClipData.newPlainText("flowvault", DebugLog.asText()))
                        true
                    } ?: false
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${entries.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
