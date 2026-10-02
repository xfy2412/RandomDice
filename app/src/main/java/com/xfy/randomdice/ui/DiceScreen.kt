package com.xfy.randomdice.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xfy.randomdice.data.DecisionRecord
import com.xfy.randomdice.data.DiceSettings
import com.xfy.randomdice.dice.JudgmentRule
import com.xfy.randomdice.dice.judge
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

/** 摇骰按钮高度。藏起来时要留同样高的占位，见下面按钮那段的说明。 */
private val ROLL_BUTTON_HEIGHT = 56.dp

/**
 * 底部那一格（数量滑杆 ↔ 记录行）的固定高度。
 *
 * 两种内容自然高度不同（滑杆行约 48dp、输入框行 56dp），不固定的话切换时这一格会换高，
 * 把上面的「摇出 X 点」和骰子顶上顶下 —— 固定之后，上面的一切都是常量。
 */
private val BOTTOM_SLOT_HEIGHT = 56.dp

/** 底部那一格当前显示什么。三态共用同一格固定高度，所以切换时上面的元素一律不动。 */
private enum class BottomSlot { Selector, Rolling, Prompt }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceScreen(
    settings: DiceSettings,
    onTotalRollsChange: (Int) -> Unit,
    onRuleChange: (JudgmentRule) -> Unit,
    onOpenDecisions: () -> Unit,
    onSaveDecision: (DecisionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var diceCount by rememberSaveable { mutableIntStateOf(1) }
    var value by rememberSaveable { mutableIntStateOf(1) }
    // 累计摇过多少颗：本地值负责「一摇就变」，同时写回设置，跨启动接着累加
    var totalRolls by rememberSaveable { mutableIntStateOf(settings.totalRolls) }
    var roundResults by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    // 本轮打算摇几个：按下按钮时定下来，之后拖滑杆不影响卡片上的分母
    var roundTotal by rememberSaveable { mutableIntStateOf(0) }
    var sessionRolled by rememberSaveable { mutableIntStateOf(0) }
    var sessionRunning by remember { mutableStateOf(false) }
    var tumbling by remember { mutableStateOf(false) }
    // 进度条「当前段」的填充比例（停留期间从 0 推到 1）
    var currentFill by remember { mutableFloatStateOf(0f) }
    // 底部那行「记录这次的决定」是否露出（完整摇完一轮后出现，可自动缩回）
    var promptVisible by remember { mutableStateOf(false) }
    // 终局那行「本次决策」的临时选择：换过的结果项 / 是否用自己写的 / 自己写的那句话
    var chosenOutcome by rememberSaveable { mutableStateOf<String?>(null) }
    var customSelected by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    // 下面那格：这次**要决定的事情**（题目）。结果在上面的行里，两者互不覆盖，都会进记录。
    var decisionText by rememberSaveable { mutableStateOf("") }
    // 判决规则编辑弹层
    var editorOpen by remember { mutableStateOf(false) }
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

    // 从记录页返回时这一页会被重建：value 是 rememberSaveable 恢复回来的，
    // 但姿态是 remember 的、会归零 —— 不重新对齐就会出现「画着 1 点、写着 4 点」。
    LaunchedEffect(Unit) {
        val (x, y, z) = targetRotationFor(value, 0f, 0f, 0f, extraTurns = 0)
        rotationX.snapTo(x % 360f)
        rotationY.snapTo(y % 360f)
        rotationZ.snapTo(z % 360f)
    }

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

    // 记录行露出时摇骰按钮淡出；位置照留（原因见按钮那段的注释）
    val rollButtonAlpha by animateFloatAsState(
        targetValue = if (promptVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "rollButtonAlpha",
    )

    // 这一轮的判决（没配规则、或还没摇出点数时为 null）
    val judgment = settings.rule.judge(roundResults)
    // 本次掷骰的结论：手动换过的结果项 > 规则判决。
    // 换过的那一项必须仍在这套规则里 —— 规则可能刚被改过（比如「平手」没了）。
    val outcomeChoice = chosenOutcome?.takeIf { it in settings.rule.outcomes } ?: judgment?.label.orEmpty()
    // 选中「自定义」时以自己写的为准；还没写就先按结论算
    val rowChoice = if (customSelected) customText.trim().ifEmpty { outcomeChoice } else outcomeChoice
    // 记录里的「结果」和「要决定的事」：有规则时结果听行里那格、下面那格是题目（谁都不会被丢），
    // 没规则时下面那格写的就是结果。见 resolveDecisionAndSubject 的注释（这里踩过静默丢字的坑）。
    val (decision, subject) = resolveDecisionAndSubject(
        rowActive = judgment != null,
        rowChoice = rowChoice,
        bottomText = decisionText,
    )
    val canSave = decision.isNotEmpty() || subject.isNotEmpty()
    // 记录里的「判决」始终是规则自己给的那句 —— 你改了主意也留个痕
    val ruling = judgment?.label.orEmpty()

    /** 收起记录行，并把「本次决策」的临时选择清干净（下一轮重新按规则判）。 */
    fun closePrompt() {
        promptVisible = false
        decisionText = ""
        customText = ""
        customSelected = false
        chosenOutcome = null
    }

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
        totalRolls += 1
        onTotalRollsChange(totalRolls)
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
        closePrompt() // 再摇一次就把记录行收起来
        sessionJob = scope.launch {
            var completed = false
            try {
                repeat(count) { index ->
                    // 翻滚期间这一段保持空着，由轨道呼吸负责「正在摇」的观感
                    // （不用 indeterminate 形态：不确定态与确定态是两个不同重载，
                    //  一切换 Compose 就重建节点、扫动动画当场消失 —— 那就是那一闪）
                    rollOne()
                    // 「停留」= 按停留时长从 0 填到满：填满的瞬间就是该切换的瞬间，
                    // 于是进度条自己成了「还有多久」的读数（全程单调连续，无阶跃）
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
                completed = true
            } finally {
                tumbling = false
                sessionRunning = false
                // 只有完整摇完一轮才提示记录；中途停止不弹（那不算一次完整的决定）
                if (completed) promptVisible = true
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
                navigationIcon = {
                    IconButton(onClick = onOpenDecisions) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "决策记录",
                        )
                    }
                },
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
            // 摇完（或中途停止）后**保留那一轮的状态** —— 摇满就停在满格；
            // 只有还没摇过、或数量被改动过时，才回到「准备摇 N 个」的空预览。
            val keepsLastRound = !sessionRunning && roundTotal > 0 && diceCount == roundTotal
            RollProgressBar(
                totalCount = if (sessionRunning || keepsLastRound) roundTotal else diceCount,
                completedCount = if (sessionRunning || keepsLastRound) sessionRolled else 0,
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

                Spacer(modifier = Modifier.height(4.dp))

                // 这一行是「摇过多少次」，同时也是判决规则的入口：点一下换规则。
                // 摘要跟在次数后面，免得要点开才知道现在按什么判。
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClickLabel = "设置判决规则") { editorOpen = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            sessionRunning -> "第 ${(sessionRolled + 1).coerceAtMost(diceCount)} 个 / 共 $diceCount 个"
                            totalRolls == 0 -> "让骰子替你决定"
                            else -> "已经摇了 $totalRolls 次"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (settings.rule.shortSummary.isNotEmpty()) {
                        Text(
                            text = " · ${settings.rule.shortSummary}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }

                // 终局那行「本次决策」：只在有规则、且这一轮完整摇完之后出现。
                // 出现时把骰子舞台挤矮一点，骰子跟着缩 —— 沿用抽屉展开时那套自适应，不跳。
                val activeJudgment = judgment
                AnimatedVisibility(
                    visible = promptVisible && activeJudgment != null,
                    enter = fadeIn(tween(durationMillis = 200)) + expandVertically(tween(durationMillis = 220)),
                    exit = fadeOut(tween(durationMillis = 160)) + shrinkVertically(tween(durationMillis = 200)),
                ) {
                    if (activeJudgment != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DecisionChoiceRow(
                                rule = settings.rule,
                                judgment = activeJudgment,
                                chosen = outcomeChoice,
                                customSelected = customSelected,
                                customText = customText,
                                // 一打字就算选中「自定义」—— 字都写了，结论当然听你的
                                onCustomTextChange = {
                                    customText = it
                                    customSelected = true
                                },
                                // 已经有字的时候再点回输入框（获得焦点）= 重新选中自定义，不用重打
                                onSelectCustom = { customSelected = true },
                                // ✕：退出自定义，回到「按规则判」，顺手把那句话清掉
                                onClearCustom = {
                                    customSelected = false
                                    customText = ""
                                },
                                // 换项就退出自定义 —— 两边互斥，否则换了看不出效果
                                onChooseOutcome = {
                                    chosenOutcome = it
                                    customSelected = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底部这一格：三态（数量滑杆 / 摇动中留空 / 记录行）共用**同一格固定高度**，
                // 只在格子内部交叉淡化。
                // 之前「摇动中」走的是另一个 48dp 的占位分支，比这格矮 8dp —— 于是开摇时上面的
                // 「摇出 X 点」被顶下去、记录行出现时又跳回来。三态共用一个高度就不会再发生。
                val bottomSlot = when {
                    sessionRunning -> BottomSlot.Rolling
                    promptVisible -> BottomSlot.Prompt
                    else -> BottomSlot.Selector
                }
                AnimatedContent(
                    targetState = bottomSlot,
                    modifier = Modifier.height(BOTTOM_SLOT_HEIGHT),
                    transitionSpec = {
                        fadeIn(tween(durationMillis = 200)) togetherWith fadeOut(tween(durationMillis = 200))
                    },
                    label = "bottomSlot",
                ) { slot ->
                    when (slot) {
                        BottomSlot.Selector -> CountSelector(
                            diceCount = diceCount,
                            onDiceCountChange = { diceCount = it },
                        )

                        // 摇动中这一格留空，避免误触数量滑杆
                        BottomSlot.Rolling -> Box(modifier = Modifier.fillMaxSize())

                        BottomSlot.Prompt -> DecisionPrompt(
                            value = decisionText,
                            onValueChange = { decisionText = it },
                            canSave = canSave,
                            onSave = {
                                onSaveDecision(
                                    DecisionRecord(
                                        timestampMillis = System.currentTimeMillis(),
                                        results = roundResults,
                                        decision = decision,
                                        ruling = ruling,
                                        // 规则的快照：以后改了规则，这条记录也说得清当时是怎么判的
                                        ruleSummary = if (judgment == null) "" else settings.rule.summary,
                                        subject = subject,
                                    ),
                                )
                                closePrompt()
                            },
                            onIgnore = { closePrompt() },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 记录行露出期间把摇骰按钮藏起来：它一按就开新一轮、会清空本轮结果，
                // 而输入法正好挡在这一带，很容易误触；记完或忽略后它自己淡回来。
                //
                // ⚠️ 用「原地淡出 + 禁用」，不是把它从布局里拿掉：一拿掉，位置就让给了下面的
                // 间距，记录行顺势往下掉、正好落进键盘区域（上一轮之所以没事，正是因为按钮还
                // 占着位置、把记录行顶在键盘上方）。位置不动，就永远不会被键盘吃到。
                Button(
                    onClick = { if (sessionRunning) stopSession() else startSession() },
                    enabled = !promptVisible,
                    // 隐藏期间保持与正常态相同的配色，只靠透明度淡出。
                    // 若用 M3 默认的禁用配色，会瞬间变成"禁用灰"——看起来就是"啪"地一下没了。
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROLL_BUTTON_HEIGHT)
                        .graphicsLayer { alpha = rollButtonAlpha },
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

    // 判决规则编辑：点「已经摇了 N 次」那行打开，改一下立刻生效
    if (editorOpen) {
        RuleEditorSheet(
            rule = settings.rule,
            onRuleChange = onRuleChange,
            onDismiss = { editorOpen = false },
        )
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
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
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
        DiceScreen(
            settings = DiceSettings(),
            onTotalRollsChange = {},
            onRuleChange = {},
            onOpenDecisions = {},
            onSaveDecision = {},
        )
    }
}

@Preview(showBackground = true, name = "暗色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiceScreenDarkPreview() {
    RandomDiceTheme(darkTheme = true, dynamicColor = false) {
        DiceScreen(
            settings = DiceSettings(),
            onTotalRollsChange = {},
            onRuleChange = {},
            onOpenDecisions = {},
            onSaveDecision = {},
        )
    }
}
