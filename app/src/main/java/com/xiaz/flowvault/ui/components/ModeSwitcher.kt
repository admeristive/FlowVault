package com.xiaz.flowvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.core.state.GlobalMode
import com.xiaz.flowvault.ui.theme.hairline

/** 三态分段控件。选中那段只换底色和字色，不加高亮描边。 */
@Composable
internal fun ModeSwitcher(
    current: GlobalMode,
    onSelect: (GlobalMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = GlobalMode.entries
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, hairline(), MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEachIndexed { index, mode ->
            val selected = mode == current
            val source = remember(mode) { MutableInteractionSource() }
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .background(cellFill(selected))
                    .background(pressedFill(source))
                    .clickable(interactionSource = source, indication = null) { onSelect(mode) }
                    // 纵向 14：字高 16 + 上下 28 = 44dp 的一格。原来 8 只有 32dp，
                    // 三格挤在右上角，切换这一态是全场点得最多的一下，准头最差。
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (index < modes.lastIndex) {
                Spacer(
                    Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(hairline())
                )
            }
        }
    }
}

/**
 * 选中格的底色。三格同时动：进的那格沉下去、出的那格浮上来，眼睛能跟着手走完这一趟。
 * 字色还是即时换的——补间一个颜色得逐帧算 ARGB，为这点效果不值当再引一层。
 */
@Composable
private fun cellFill(selected: Boolean): Color {
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "modeCellFill",
    )
    return if (alpha <= 0f) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
}
