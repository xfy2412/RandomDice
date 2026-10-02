package com.xfy.randomdice.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** 进度条高度（M3 线性进度条默认就是 4dp）。 */
private val BAR_HEIGHT = 4.dp

/** 刻度线宽度。 */
private val TICK_WIDTH = 1.dp

/** 「正在摇」那一段的呼吸周期。 */
private const val BREATH_DURATION_MS = 900

/** 呼吸时的轨道最暗透明度。 */
private const val BREATH_MIN_ALPHA = 0.45f

/**
 * 本轮进度条：**分段无缝 + 刻度线**。
 *
 * - 段数 = 本轮骰子数（空闲时按待摇数量预览，所以不会"忽现忽隐"）。
 * - 每段用 `strokeCap = Round`（圆角端点）。M3 内部会把端点按半个笔宽内缩
 *   （见 1.3.1 源码 `drawLinearIndicator` 里的 `coerceRange`），
 *   所以相邻两段的圆角**恰好相接、不会互相盖住**；相接处只有一个尖角。
 * - 因此段边界仍用一条背景色的细刻度线"切"出来 —— 让"这是一段"读得出来的是它。
 * - 已完成段：满格。
 * - 当前段：只用一个**确定态**组件，进度值由调用方连续驱动
 *   （翻滚期间保持空、靠轨道亮暗呼吸表示"正在摇"；落定后按停留时长填满）。
 *   ⚠️ 刻意**不用 indeterminate 重载**：不确定态与确定态是两个不同的可组合函数，
 *   一翻标志位 Compose 就会重建节点、扫动动画当场消失 —— 肉眼就是"闪一下"，
 *   而且换任何版本（含 Expressive 波浪条）只要还是两个重载就一样会闪。
 *   只用一个确定态组件、只推一个单调递增的值，就不存在可闪的瞬间。
 * - 未开始段：只有轨道。
 *
 * 渲染全部是 M3 原生组件；自己画的只有那几条刻度线。
 */
@Composable
fun RollProgressBar(
    totalCount: Int,
    completedCount: Int,
    currentFill: Float,
    rolling: Boolean,
    modifier: Modifier = Modifier,
) {
    if (totalCount <= 0) return

    val indicatorColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    // 刻度线用背景色"切"出来，压在填充之上，所以分段处看不出缝
    val tickColor = MaterialTheme.colorScheme.surface

    val transition = rememberInfiniteTransition(label = "progressBreath")
    val breath by transition.animateFloat(
        initialValue = BREATH_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BREATH_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )
    // 0 = 不在摇（轨道回到常态）、1 = 正在摇（轨道跟着呼吸）。用动画过渡，避免突跳
    val rollingFactor by animateFloatAsState(
        targetValue = if (rolling) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "rollingFactor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            // 整条两端圆角；内部各段是平头对接，所以是无缝的
            .clip(RoundedCornerShape(BAR_HEIGHT / 2)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(totalCount) { index ->
                val isCurrent = index == completedCount
                val segmentTrackColor = if (isCurrent) {
                    // 呼吸只属于"正在摇"，但用 rollingFactor 平滑淡入淡出 ——
                    // 否则落定那一刻若正好在呼吸低点，轨道也会跟着闪一下
                    trackColor.copy(alpha = trackColor.alpha * (1f - (1f - breath) * rollingFactor))
                } else {
                    trackColor
                }

                LinearProgressIndicator(
                    progress = {
                        when {
                            index < completedCount -> 1f
                            isCurrent -> currentFill
                            else -> 0f
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = indicatorColor,
                    trackColor = segmentTrackColor,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }

        // 刻度线：标出段边界（画在填充之上，用背景色切出来）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / totalCount
            for (i in 1 until totalCount) {
                val x = step * i
                drawLine(
                    color = tickColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = TICK_WIDTH.toPx(),
                )
            }
        }
    }
}
