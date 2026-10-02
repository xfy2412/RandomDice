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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 卡片离左右屏幕边的距离。 */
private val CARD_HORIZONTAL_MARGIN = 16.dp

/** 卡片圆角。 */
private val CARD_SHAPE = RoundedCornerShape(20.dp)

/** 拖动条高度 —— 它就是卡片的底边，也是收起时唯一露在外面的部分。 */
private val HANDLE_HEIGHT = 40.dp

/** 拖动换算的下限，避免内容很少时拖一点点就全开。 */
private val MIN_DRAG_DIVISOR = 60.dp

/**
 * 进度条下面那张「本轮点数」卡片，做成**抽屉**：
 *
 * - 拖动条不是卡片上面的把手，而是**卡片自己的底边**；
 * - 收起时整张卡片缩进顶部栏，只留这条底边在外面（就是那根细胶囊）；
 * - 往下拖，卡片从顶部栏里抽出来，拖动条始终跟在卡片底部往下走；
 * - 卡片高度 = 拖动条 + 「本内容自然高度 × 展开比例」，内容靠裁剪窗口露出来，
 *   所以拖到一半就是"抽出一半"。
 *
 * 交互：**可以拖，也可以直接点**（点一下切换展开/收起 —— 不想拖、拖不动的人也能用）。
 *
 * @param results 本轮已摇出的点数（按顺序）
 * @param totalCount 本轮打算摇几个（0 = 还没开始过一轮）
 * @param maxContentHeight 内容区高度上限；点数很多时超过就在卡片里滚动
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundResultsCard(
    results: List<Int>,
    totalCount: Int,
    maxContentHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var fraction by remember { mutableFloatStateOf(0f) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    // 内容（标题 + 骰子）按自然高度排版实测出来的高度，不受卡片当前高度影响
    var contentHeightPx by remember { mutableIntStateOf(0) }

    val maxContentHeightPx = with(density) { maxContentHeight.toPx() }
    val visibleContentPx = minOf(contentHeightPx.toFloat(), maxContentHeightPx)
    val dragDivisor = maxOf(visibleContentPx.toFloat(), with(density) { MIN_DRAG_DIVISOR.toPx() })

    // 卡片高度 = 底边 + 露出来的内容
    val cardHeight = HANDLE_HEIGHT + with(density) { (visibleContentPx * fraction).toDp() }

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
        if (dragDivisor > 0f) {
            fraction = (fraction + delta / dragDivisor).coerceIn(0f, 1f)
        }
    }

    ElevatedCard(
        modifier = modifier
            .padding(horizontal = CARD_HORIZONTAL_MARGIN)
            .fillMaxWidth()
            .height(cardHeight),
        shape = CARD_SHAPE,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 内容区：高度就是"露出来的那部分"；内容自己按自然高度排版，多出来的被卡片裁掉
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .onSizeChanged { contentHeightPx = it.height }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
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
                                DiceFace2D(value = value, modifier = Modifier.size(44.dp))
                            }
                        }
                    }
                }
            }

            // 拖动条：卡片的底边
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_HEIGHT)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { snapJob?.cancel() },
                        onDragStopped = { velocity ->
                            val target = when {
                                velocity < -600f -> 0f // 往上甩 → 收起
                                velocity > 600f -> 1f // 往下甩 → 抽出
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
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                )
            }
        }
    }
}
