package com.xiaz.flowvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.ui.state.ReaderSurface
import kotlin.math.min

/**
 * 阅读底色与字色。正文颜色在这里给死，不跟 Material 的 colorScheme 走——暗色主题下读书要的是纸，不是界面反色。
 * [hint] 是软熔断那句话的颜色：要看得出，但不能亮成屏幕上的一盏灯。
 */
internal data class ReaderColors(
    val background: Color,
    val foreground: Color,
    val dim: Color,
    val track: Color,
    val hint: Color,
)

internal fun readerColorsOf(surface: ReaderSurface): ReaderColors = when (surface) {
    ReaderSurface.LIGHT -> ReaderColors(Color(0xFFFCFBF7), Color(0xFF1C1B19), Color(0xFF8A867E), Color(0xFFE1DDD2), Color(0xFF8A5A2B))
    ReaderSurface.DARK -> ReaderColors(Color(0xFF131313), Color(0xFFC9C6C0), Color(0xFF6E6B66), Color(0xFF2C2C2C), Color(0xFFB08A45))
    ReaderSurface.EYE -> ReaderColors(Color(0xFFCFE3CB), Color(0xFF28362A), Color(0xFF5D6F60), Color(0xFFB4CDAF), Color(0xFF6B5A20))
}

/**
 * 一次只排版这么多字。整本可能几百万字，全交给排版器会当场吃掉几十兆内存、首帧卡死；
 * 所以每页只取这一窗，翻过界再往后挪。代价是"一页最多这么多字"，正常手机屏一页用不完。
 */
private const val PAGE_PROBE_CHARS = 4000

/** 左边这一窄条是上一页，其余是下一页。 */
private const val BACK_ZONE = 0.3f

/**
 * 翻页模式的正文。关键是**一页装多少字由排版器说了算**：
 * 先按当前字号排这一窗，再从排版结果里找"最后一整行"落在第几个字，那就是下一页的起点。
 * 自己按字数切页必然漏行或压字，换个字号全得崩。
 */
@Composable
internal fun ReaderPage(
    text: String,
    offset: Int,
    style: TextStyle,
    colors: ReaderColors,
    onNext: (Int) -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    var layout by remember(offset) { mutableStateOf(null as TextLayoutResult?) }
    var nextStart by remember(offset, text) { mutableIntStateOf(min(offset + PAGE_PROBE_CHARS, text.length)) }
    val slice = remember(text, offset) {
        text.substring(offset, min(offset + PAGE_PROBE_CHARS, text.length))
    }

    LaunchedEffect(layout, box, slice) {
        val result = layout
        if (result == null || box.height <= 0) return@LaunchedEffect
        if (slice.isEmpty()) {
            nextStart = text.length
            return@LaunchedEffect
        }
        var lastWholeLine = -1
        for (i in 0 until result.lineCount) {
            if (result.getLineBottom(i) <= box.height) lastWholeLine = i else break
        }
        val consumed = if (lastWholeLine + 1 < result.lineCount) {
            result.getLineStart(lastWholeLine + 1)
        } else {
            slice.length
        }
        // 一行都装不下时（字号调到比屏还高）也得强行吃掉一行，否则点下一页纹丝不动。
        nextStart = offset + if (consumed > 0) consumed else maxOf(result.getLineEnd(0), 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .clipToBounds()
            .onSizeChanged { box = it }
            .pointerInput(offset, text, nextStart, box) {
                detectTapGestures { pos ->
                    if (pos.x < box.width * BACK_ZONE) onPrev()
                    else onNext(if (nextStart > offset) nextStart else offset + 1)
                }
            },
    ) {
        BasicText(
            text = slice,
            modifier = Modifier.fillMaxWidth(),
            style = style.copy(color = colors.foreground),
            onTextLayout = { layout = it },
        )
    }
}

/**
 * 滚动模式：一窗字放进竖滚动容器里连续读，滚到底自动接下一窗。
 * 不一次塞全书，理由同翻页模式的首帧。
 */
@Composable
internal fun ReaderScroll(
    text: String,
    offset: Int,
    style: TextStyle,
    colors: ReaderColors,
    onNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberScrollState()
    val slice = remember(text, offset) {
        text.substring(offset, min(offset + PAGE_PROBE_CHARS, text.length))
    }
    // 滚到底自动接下一窗。用 snapshotFlow 而不是把 state.value 当 key：
    // 后者每滚一像素就重起一次这个 effect，等于把滚动过程整条订阅进重组，白烧 CPU。
    // 读到的位置若已超过正文末尾就直接收，不再往下挪——否则最后一页会反复触发续段。
    LaunchedEffect(slice, text.length) {
        if (slice.isEmpty() || offset >= text.length) return@LaunchedEffect
        snapshotFlow { state.value to state.maxValue }.collect { (value, max) ->
            if (max > 0 && value >= max) {
                val next = min(offset + slice.length, text.length)
                state.scrollTo(0)
                onNext(next)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BasicText(
            text = slice,
            modifier = Modifier.fillMaxWidth().verticalScroll(state),
            style = style.copy(color = colors.foreground),
        )
    }
}

/**
 * 页脚：一根进度条加一行读数。进度条是手画的两个 Box，不是 Material 进度条——
 * 压在正文上面的瞬时界面里，任何带状态层/动效的组件都可能在荣耀上碎成雪花。
 * [hint] 非空时右半边给软熔断那句话，进度条照旧读到底。
 */
@Composable
internal fun ReaderProgress(
    text: String,
    offset: Int,
    colors: ReaderColors,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val shown = offset.coerceIn(0, text.length)
    val fraction = if (text.isEmpty()) 0f else shown.toFloat() / text.length
    // 翻页后这一条滑过去而不是咔一下跳。字数和百分比仍按真实值写，不跟着补间跑。
    val slid by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 220),
        label = "readerProgressSlide",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(slid.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(colors.dim),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (text.isEmpty()) "没有正文" else "第 $shown 字 / 共 ${text.length} 字 · ${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = colors.dim,
            )
            Spacer(Modifier.weight(1f))
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.hint,
                )
            }
        }
    }
}
