package com.xiaz.flowvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaz.flowvault.R
import com.xiaz.flowvault.core.state.GlobalMode

/**
 * 常驻的看板娘：切到哪一态就换哪一个姿势，人一直站在原地。
 *
 * 三张图叠在同一格里，只让透明度动——不用那套按可见性拆掉重建的进出场容器，荣耀的系统层上
 * 它会让整块内容闪一下白。代价是三张图同时驻在内存里（各约 1MB）。
 * 展台自己画：圆底铺这一态的主色薄纱，描边比卡片的更实一点，让她像是"站在台子上"而不是贴在屏上。
 */
@Composable
internal fun MascotStage(
    mode: GlobalMode,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
    ) {
        val pose = when (mode) {
            GlobalMode.FOCUS -> R.drawable.mascot_focus
            GlobalMode.BREAK -> R.drawable.mascot_break
            GlobalMode.DAILY -> R.drawable.mascot_daily
        }
        Pose(R.drawable.mascot_focus, pose, "mascotFocus")
        Pose(R.drawable.mascot_break, pose, "mascotBreak")
        Pose(R.drawable.mascot_daily, pose, "mascotDaily")
    }
}

@Composable
private fun Pose(res: Int, active: Int, label: String) {
    val alpha by animateFloatAsState(
        targetValue = if (res == active) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = label,
    )
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(StageInset).alpha(alpha),
    )
}

// 图里的人本来就贴着画幅边，再往里收一点，头顶和鞋尖才不会撞到圆台。
private val StageInset = 4.dp
