package com.xiaz.flowvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaz.flowvault.core.debug.DebugLog
import com.xiaz.flowvault.core.state.GlobalMode
import com.xiaz.flowvault.core.state.ModeEngine
import com.xiaz.flowvault.ui.components.ContentMaxWidth
import com.xiaz.flowvault.ui.components.ModeSwitcher
import com.xiaz.flowvault.ui.components.WideBreakpoint
import com.xiaz.flowvault.ui.components.WideContentMaxWidth
import com.xiaz.flowvault.ui.debug.DebugPanel
import com.xiaz.flowvault.ui.screen.BreakScreen
import com.xiaz.flowvault.ui.screen.DailyScreen
import com.xiaz.flowvault.ui.screen.FocusScreen
import com.xiaz.flowvault.ui.state.DayBoard
import com.xiaz.flowvault.ui.state.BackupKit
import com.xiaz.flowvault.ui.state.DeckShelf
import com.xiaz.flowvault.ui.state.NovelShelf
import com.xiaz.flowvault.ui.state.PomodoroClock
import kotlinx.coroutines.delay
import com.xiaz.flowvault.ui.theme.FlowVaultTheme
import com.xiaz.flowvault.ui.theme.hairline
import com.xiaz.flowvault.ui.theme.tint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}

/**
 * 色板跟着当前态走：攻坚一套、回血一套、日常一套，换态时整屏一起换。
 * 所以 Theme 必须包在内容外面，而它又要知道 mode——只能在这一层把 mode 先取出来。
 */
@Composable
private fun AppRoot(engine: ModeEngine = viewModel()) {
    val mode by engine.mode.collectAsStateWithLifecycle()
    FlowVaultTheme(dark = isSystemInDarkTheme(), tint = mode.tint()) {
        AppShell(mode, engine)
    }
}

@Composable
private fun AppShell(
    mode: GlobalMode,
    engine: ModeEngine,
    board: DayBoard = viewModel(),
    clock: PomodoroClock = viewModel(),
    shelf: NovelShelf = viewModel(),
    decks: DeckShelf = viewModel(),
    backup: BackupKit = viewModel(),
) {
    val handoff by clock.handoff.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as FlowVaultApp
    val databaseStatus by app.databaseStatus.collectAsStateWithLifecycle()
    var panelOpen by remember { mutableStateOf(false) }

    // 走秒只在前台：退到后台时把那一段结算掉，回来重新对齐锚点，中间那段不算。
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> clock.onForeground()
                Lifecycle.Event.ON_PAUSE -> clock.onBackground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            clock.onBackground()
        }
    }
    LaunchedEffect(clock) {
        while (true) {
            delay(1000)
            clock.tick()
        }
    }
    LaunchedEffect(handoff) {
        handoff?.let {
            engine.enter(it)
            clock.consumeHandoff()
            // 换态这一刻一定是有一趟结算完了，待办上的"走完几趟"刚在库里加过；
            // 看板不重查就要等到下一次动它才更新，那一格会当着他面停在旧数上。
            board.refresh()
        }
    }

    // 用约束宽度而不是 Configuration.screenWidthDp 判宽窄：后者在分屏和荣耀的桌面模式里
    // 报的是整个屏还是应用窗口并不一定，而这两列／一列的切换要的就是"我这一栏真拿到多宽"。
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        val contentCap = if (maxWidth >= WideBreakpoint) WideContentMaxWidth else ContentMaxWidth
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .widthIn(max = contentCap)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "流转秘境",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            panelOpen = !panelOpen
                            DebugLog.info("ui", if (panelOpen) "打开日志浮窗" else "收起日志浮窗")
                        })
                    },
                )
                Spacer(Modifier.weight(1f))
                ModeSwitcher(current = mode, onSelect = engine::enter)
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(hairline())
            )
            Text(
                text = mode.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Box(Modifier.weight(1f)) {
                when (mode) {
                    GlobalMode.FOCUS -> FocusScreen(board, clock)
                    GlobalMode.BREAK -> BreakScreen(board, shelf, clock, decks)
                    GlobalMode.DAILY -> DailyScreen(board, decks, shelf, clock, backup)
                }
            }
        }

        if (panelOpen) {
            DebugPanel(databaseStatus = databaseStatus, onClose = { panelOpen = false })
        }
    }
}
