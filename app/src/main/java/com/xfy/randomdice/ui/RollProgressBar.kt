package com.xfy.randomdice.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
 * - 段与段之间**不留缝**（`strokeCap = Butt`、`gapSize = 0`、去掉末端停止点），
 *   边界只用一条背景色的细刻度线"切"出来 —— 所以填充看起来是连续的一条。
 * - 已完成段：满格。
 * - 当前段：正在翻滚时用**原生 indeterminate 形态**（扫动）+ 轨道亮暗呼吸；
 *   落定后由调用方按停留时长把 [currentFill] 从 0 推到 1（步进插值），填满即切换。
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
                val segmentTrackColor = if (isCurrent && rolling) {
                    trackColor.copy(alpha = trackColor.alpha * breath)
                } else {
                    trackColor
                }

                if (isCurrent && rolling) {
                    // 正在摇：原生不确定形态 —— 语义上就是"在动、还没定"
                    LinearProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        color = indicatorColor,
                        trackColor = segmentTrackColor,
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                    )
                } else {
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
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
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
