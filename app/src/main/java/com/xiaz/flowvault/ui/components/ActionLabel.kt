package com.xiaz.flowvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 按下去铺多厚的一层纱。跟发丝描边取同一个数，两处看起来是一套东西。 */
private const val PressedAlpha = 0.12f

/**
 * 项目里唯一的文字按钮。荣耀/MagicOS 上 Material 组件自带的波纹和按压态层会在屏幕上
 * 碎成一片雪花，而 Material3 的 TextButton 不暴露 indication，没法关，所以自绘并把
 * indication 显式置空。以后要加按钮一律走这里。
 */
@Composable
internal fun ActionLabel(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "actionLabelFill",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        // 选中态不靠颜色喊，靠一层底色站住；字色跟着从 primary 收回正文色，
        // 免得一排按钮里"能点的"和"已经选的"看着是同一种东西。
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (fillAlpha <= 0f) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = fillAlpha),
            )
            .background(pressedFill(source))
            .clickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            // 纵向给到 12：文字行高 16 + 上下 24 = 40dp 的按压高度，
            // 原来是 32dp，手指点着不算难受但准头差。
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

/**
 * 自绘的按压反馈：只让一层薄底色的透明度动起来，不碰缩放、位移、波纹。
 * 松手比按下慢一点——手指离开之后那点余韵是"按到了"的证据。
 * 调用方自己决定铺在哪一层上（写在 background 里）。
 */
@Composable
internal fun pressedFill(source: MutableInteractionSource): Color {
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed) PressedAlpha else 0f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 160),
        label = "pressedAlpha",
    )
    return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
}

