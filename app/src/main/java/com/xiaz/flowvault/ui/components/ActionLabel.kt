package com.xiaz.flowvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** 动作那一圈描边的浓度：比主角卡的边实一点，因为它要的是"现在就能按"。 */
private const val ActionEdgeAlpha = 0.6f

/**
 * 同一行里两枚按钮之间的空。写在组件里而不是每行的 Spacer：全项目四十多个调用点，
 * 漏一行的话描边就在那一行连成一串框，而这种事只在屏幕上看得见。
 */
private val ActionGap = 8.dp

/**
 * 项目里唯一的文字按钮。荣耀/MagicOS 上 Material 组件自带的波纹和按压态层会在屏幕上
 * 碎成一片雪花，而 Material3 的 TextButton 不暴露 indication，没法关，所以自绘并把
 * indication 显式置空。以后要加按钮一律走这里。
 *
 * 两种语义长两种样子，不靠颜色区分靠形状：**动作**（按下去就发生一件事）围一圈主色描边、
 * 字用正文色；**选项**（一排里挑一个）不围边，选中的那一枚沉一层底色。
 * 原来 43 处全是"主色文字"一种长相，能按的和已选的、加书签和切翻页，看着是同一种东西。
 */
@Composable
internal fun ActionLabel(
    label: String,
    modifier: Modifier = Modifier,
    option: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "actionLabelFill",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            option && selected -> MaterialTheme.colorScheme.onSurface
            option -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            // 这一格空在外侧：先让出间距，再落描边和点击区，按压范围不被吃掉。
            .padding(end = ActionGap)
            .clip(shape)
            .background(
                if (fillAlpha <= 0f) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = fillAlpha),
            )
            .background(pressedFill(source))
            .then(
                if (option) Modifier else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = ActionEdgeAlpha),
                    shape,
                ),
            )
            .clickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            // 纵向给到 12：按压高实测 48dp（在 420dpi 的截图上量的，不是拿行高算的）。
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

