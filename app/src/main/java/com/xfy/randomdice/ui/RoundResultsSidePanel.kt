package com.xfy.randomdice.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 收起时露在外面的把手宽度。
 *
 * ⚠️ 不能太窄（原来 22dp）：它贴着屏幕左缘，而系统返回手势就吃边缘那 20dp 左右 ——
 * 手指一按在边上就成了"返回"。加厚到 44dp 后，小竖条落在离边缘 ~22dp 的地方，
 * 拖动时手指正好在安全区里。
 */
private val HANDLE_WIDTH = 44.dp

/** 完全抽出时面板占多宽。 */
private val PANEL_WIDTH = 240.dp

/** 拖动换算的下限。 */
private val MIN_DRAG_DIVISOR = 80.dp

/**
 * 横屏时的「本轮点数」—— 竖屏那张卡片是**上下**抽的，这里是**左右**抽的：
 * 把手竖着贴在屏幕左缘（就是原来顶栏那条细胶囊转 90°），往右拖把面板抽出来。
 *
 * 抽出来的是同一份内容：标题 + 本轮的骰子面。
 *
 * 交互和竖屏卡片一致：**可以拖，也可以直接点**。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundResultsSidePanel(
    results: List<Int>,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var fraction by remember { mutableFloatStateOf(0f) }
    var snapJob by remember { mutableStateOf<Job?>(null) }

    val panelWidthPx = with(density) { PANEL_WIDTH.toPx() }
    val handleWidthPx = with(density) { HANDLE_WIDTH.toPx() }
    val travelPx = (panelWidthPx - handleWidthPx).coerceAtLeast(with(density) { MIN_DRAG_DIVISOR.toPx() })
    // Dp * Float 是内置的，别自己写同名扩展 —— 那会变成自我调用
    val width = HANDLE_WIDTH + (PANEL_WIDTH - HANDLE_WIDTH) * fraction

    fun snapTo(target: Float) {
        snapJob?.cancel()
        snapJob = scope.launch {
            animate(
                initialValue = fraction,
                targetValue = target,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            ) { value, _ -> fraction = value }
        }
    }

    val dragState = rememberDraggableState { delta ->
        fraction = (fraction + delta / travelPx).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            // 内容按固定宽度排版，多出来的部分被面板裁掉（和竖屏卡片一个思路）。
            // ⚠️ 必须用 requiredWidth：外层宽度正在 22→240dp 之间动画，
            // Modifier.width 会被那层窄约束夹住，于是文字逐字换行、骰子被压成一条。
            Column(
                modifier = Modifier
                    .requiredWidth(PANEL_WIDTH)
                    // ⚠️ 透明度跟着展开比例走：收起时面板只有把手那么宽（22dp），
                    // 而内容是按 240dp 排的 —— 一旦哪层没裁干净，就把「摇一…」这种半截字
                    // 露在把手那条缝里（就是这么被发现的）。alpha=0 从根上杜绝，抽出来时顺带淡入。
                    .graphicsLayer { alpha = fraction }
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    // 和竖屏卡片对称：那边是"把手 = 卡片底边"，所以内容给底部留出把手的位置；
                    // 这边是"把手 = 面板右缘"，于是留出右边。
                    .padding(start = 16.dp, end = HANDLE_WIDTH + 8.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = if (totalCount > 0) {
                        "本轮点数（${results.size} / $totalCount）"
                    } else {
                        "本轮点数"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (results.isEmpty()) {
                    Text(
                        text = "还没有点数，摇一次就有了",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        results.forEach { value ->
                            DiceFace2D(value = value, modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }

            // 把手：**面板的右缘**（和竖屏那张卡片"把手 = 卡片底边"是一个道理），
            // 所以它跟着面板一起往右走，而不是钉在屏幕左缘不动。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(HANDLE_WIDTH)
                    .fillMaxHeight()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { snapJob?.cancel() },
                        onDragStopped = { velocity ->
                            val target = when {
                                velocity < -600f -> 0f
                                velocity > 600f -> 1f
                                fraction > 0.5f -> 1f
                                else -> 0f
                            }
                            snapTo(target)
                        },
                    )
                    .clickable { snapTo(if (fraction > 0.5f) 0f else 1f) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
