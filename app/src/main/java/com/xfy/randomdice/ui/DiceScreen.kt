package com.xfy.randomdice.ui

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xfy.randomdice.dice.targetRotationFor
import com.xfy.randomdice.ui.theme.RandomDiceTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** 骰子面数。以后要做 D10 / D20，改这里 + 几何表即可。 */
private const val DIE_SIDES = 6

/** 一次最多能连着摇几个。 */
private const val MAX_DICE_COUNT = 12

/** 一次批量里，每个骰子：翻滚时长（三轴依次收住）→ 停留 → 滑出。 */
private const val ROLL_DURATION_X_MS = 900
private const val ROLL_DURATION_Y_MS = 1040
private const val ROLL_DURATION_Z_MS = 1160
private const val SLIDE_MS = 320

/** 每个骰子摇完之后停留多久（同时也是进度条当前段填满所用的时间）。 */
private val HOLD_AFTER_ROLL = 1.5.seconds

/** 同一时长换算成毫秒，给进度条的填充动画用（时长只有上面这一处真值）。 */
private val HOLD_AFTER_ROLL_MS = HOLD_AFTER_ROLL.inWholeMilliseconds.toInt()

/** 骰子本体尺寸上限；舞台被下拉卡片挤矮时会按可用高度自适应缩小。 */
private val DIE_SIZE_MAX = 200.dp

/** 下拉卡片里「内容区」的高度上限（卡片总高还要加 40dp 的拖动条底边）。 */
private val CARD_CONTENT_MAX_HEIGHT = 180.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceScreen(modifier: Modifier = Modifier) {
    var diceCount by rememberSaveable { mutableIntStateOf(1) }
    var value by rememberSaveable { mutableIntStateOf(1) }
    var rollCount by rememberSaveable { mutableIntStateOf(0) }
    var roundResults by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    // 本轮打算摇几个：按下按钮时定下来，之后拖滑杆不影响卡片上的分母
    var roundTotal by rememberSaveable { mutableIntStateOf(0) }
    var sessionRolled by rememberSaveable { mutableIntStateOf(0) }
    var sessionRunning by remember { mutableStateOf(false) }
    var tumbling by remember { mutableStateOf(false) }
    // 进度条「当前段」的填充比例（停留期间从 0 推到 1）
    var currentFill by remember { mutableFloatStateOf(0f) }
    // 骰子所在区域的实测尺寸：宽度用来算滑动距离，高度用来把骰子缩到放得下
    var stageWidthPx by remember { mutableIntStateOf(0) }
    var stageHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // 骰子姿态（绕 Z → 绕 X → 绕 Y，单位度）。全 0 时朝上的是「1」，
    // 与 value 的初值一致 —— 这条不变量由 DiceGeometryTest 守着。
    val rotationX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }
    val rotationZ = remember { Animatable(0f) }
    // 位移：0 = 居中；-1 = 向左滑出屏幕；+1 = 在右侧屏幕外待入场
    val slideFraction = remember { Animatable(0f) }
    var sessionJob by remember { mutableStateOf<Job?>(null) }

    // 卡片展开会把舞台挤矮，骰子跟着缩小（还没测量时先按上限，避免首帧闪一下）
    val dieSize by animateDpAsState(
        targetValue = if (stageHeightPx == 0) {
            DIE_SIZE_MAX
        } else {
            minOf(DIE_SIZE_MAX, with(density) { stageHeightPx.toDp() })
        },
        animationSpec = tween(durationMillis = 220),
        label = "dieSize",
    )

    /** 摇一次：跑完翻滚动画并把结果记下来。会挂起直到动画结束。 */
    suspend fun rollOne() {
        // 先定结果，再反解出「让这一面朝上」的目标姿态：
        // 画面最后停在哪一面，记录的就是哪一面，不会对不上。
        val result = Random.nextInt(1, DIE_SIDES + 1)
        val (endX, endY, endZ) = targetRotationFor(
            value = result,
            currentX = rotationX.value,
            currentY = rotationY.value,
            currentZ = rotationZ.value,
            extraTurns = 2,
        )

        tumbling = true
        coroutineScope {
            launch {
                rotationX.animateTo(endX, tween(ROLL_DURATION_X_MS, easing = FastOutSlowInEasing))
            }
            launch {
                rotationY.animateTo(endY, tween(ROLL_DURATION_Y_MS, easing = FastOutSlowInEasing))
            }
            launch {
                rotationZ.animateTo(endZ, tween(ROLL_DURATION_Z_MS, easing = FastOutSlowInEasing))
            }
        }
        tumbling = false

        // 归一角度，避免浮点无限增长（整圈是恒等变换，视觉上完全等价）
        rotationX.snapTo(endX % 360f)
        rotationY.snapTo(endY % 360f)
        rotationZ.snapTo(endZ % 360f)

        value = result
        rollCount += 1
        roundResults = roundResults + result
        // sessionRolled（进度条「已完成段数」）不在这里 +1 —— 要等停留填满之后，见 startSession
    }

    fun startSession() {
        if (sessionRunning) return
        val count = diceCount
        sessionRunning = true
        sessionRolled = 0
        roundResults = emptyList()
        roundTotal = count
        sessionJob = scope.launch {
            try {
                repeat(count) { index ->
                    rollOne()
                    // 「停留」= 把当前段按停留时长填满：填满的瞬间就是该切换的瞬间，
                    // 于是进度条自己成了「还有多久」的读数（步进插值，不再有阶跃）
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = HOLD_AFTER_ROLL_MS, easing = LinearEasing),
                    ) { fill, _ -> currentFill = fill }
                    currentFill = 0f
                    sessionRolled = index + 1
                    if (index < count - 1) {
                        // 左滑移出 → 瞬移到右侧屏幕外 → 滑回中间（下一个的翻滚同时开始）
                        slideFraction.animateTo(
                            targetValue = -1f,
                            animationSpec = tween(SLIDE_MS, easing = FastOutLinearInEasing),
                        )
                        slideFraction.snapTo(1f)
                        launch {
                            slideFraction.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(SLIDE_MS, easing = LinearOutSlowInEasing),
                            )
                        }
                    }
                }
            } finally {
                tumbling = false
                sessionRunning = false
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        currentFill = 0f
        // 万一停在滑出途中，把骰子带回中间
        scope.launch {
            slideFraction.animateTo(0f, tween(SLIDE_MS, easing = LinearOutSlowInEasing))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "随机骰子") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 顶部进度条：分段无缝 + 刻度线。
            // 空闲时显示的是「本轮准备摇 N 个」的预览 —— 所以不会有忽现忽隐的跳变
            RollProgressBar(
                totalCount = if (sessionRunning) roundTotal else diceCount,
                completedCount = if (sessionRunning) sessionRolled else 0,
                currentFill = currentFill,
                rolling = tumbling,
            )

            // 下拉条 + 本轮点数卡片（就在进度条下面）
            RoundResultsCard(
                results = roundResults,
                totalCount = roundTotal,
                maxContentHeight = CARD_CONTENT_MAX_HEIGHT,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged {
                            stageWidthPx = it.width
                            stageHeightPx = it.height
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Dice3D(
                        rotXDegrees = { rotationX.value },
                        rotYDegrees = { rotationY.value },
                        rotZDegrees = { rotationZ.value },
                        rolling = tumbling,
                        modifier = Modifier
                            .size(dieSize)
                            .graphicsLayer {
                                // 滑出距离：让骰子中心越过屏幕边缘（半个可用宽度 + 一个骰子尺寸）
                                val travel = stageWidthPx / 2f + dieSize.toPx()
                                translationX = travel * slideFraction.value
                                alpha = (1f - abs(slideFraction.value)).coerceIn(0f, 1f)
                            },
                    )
                }

                Text(
                    text = "摇出 $value 点",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        sessionRunning -> "第 ${(sessionRolled + 1).coerceAtMost(diceCount)} 个 / 共 $diceCount 个"
                        rollCount == 0 -> "让骰子替你决定"
                        else -> "已经摇了 $rollCount 次"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (sessionRunning) {
                    // 会话中把数量选择换成一个占位，避免误触
                    Spacer(modifier = Modifier.height(48.dp))
                } else {
                    CountSelector(
                        diceCount = diceCount,
                        onDiceCountChange = { diceCount = it },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (sessionRunning) stopSession() else startSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        text = when {
                            sessionRunning -> "停止"
                            diceCount == 1 -> "摇一次"
                            else -> "依次摇 $diceCount 次"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** 数量选择：1..MAX_DICE_COUNT 的滑杆（steps 让取值落在整数上）。 */
@Composable
private fun CountSelector(
    diceCount: Int,
    onDiceCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "数量",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = diceCount.toFloat(),
            onValueChange = { onDiceCountChange(it.roundToInt().coerceIn(1, MAX_DICE_COUNT)) },
            valueRange = 1f..MAX_DICE_COUNT.toFloat(),
            steps = MAX_DICE_COUNT - 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        Text(
            text = "$diceCount",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
    }
}

@Preview(showBackground = true, name = "亮色")
@Composable
private fun DiceScreenPreview() {
    RandomDiceTheme(dynamicColor = false) {
        DiceScreen()
    }
}

@Preview(showBackground = true, name = "暗色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiceScreenDarkPreview() {
    RandomDiceTheme(darkTheme = true, dynamicColor = false) {
        DiceScreen()
    }
}
