package com.xfy.randomdice.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xfy.randomdice.dice.Judgment
import com.xfy.randomdice.dice.JudgmentRule

/**
 * 这一行的高度写死：行内元素怎么伸缩都不改变行高，下面的记录行和按钮就不会上下跳。
 */
private val CHOICE_ROW_HEIGHT = 40.dp

/** 空输入框的 hint —— 原来那颗「自定义」chip 现在就长这样。 */
private const val FIELD_HINT = "自定义"
private val FIELD_START_PADDING = 12.dp
private val FIELD_END_PADDING = 8.dp
private val FIELD_CLEAR_SIZE = 28.dp
private const val FIELD_WIDTH_ANIM_MS = 200

/**
 * 输入框宽度怎么定（纯函数，单测在 [DecisionChoiceRowWidthTest]）：
 *
 * - 拿到焦点 → 撑满这一行剩下的宽度；
 * - 没焦点 → 缩到文字（或 hint）的长度；
 * - 两种情况下都**不会超过可用宽度** —— 所以这一行组件永远撑不出屏幕。
 */
internal fun decisionFieldTargetWidthPx(
    focused: Boolean,
    textWidthPx: Int,
    startPaddingPx: Float,
    endPaddingPx: Float,
    clearButtonPx: Float,
    availableWidthPx: Int,
): Float {
    val desired = textWidthPx + startPaddingPx + endPaddingPx + if (focused) clearButtonPx else 0f
    // 还没量到可用宽度（第一帧）时先按需要宽度摆，下一帧就会被结构上的约束夹住
    if (availableWidthPx <= 0) return desired
    return if (focused) availableWidthPx.toFloat() else minOf(desired, availableWidthPx.toFloat())
}

/**
 * 记录里的「结果」和「要决定的事」怎么定（纯函数，单测在 `DecisionOutcomeTest`）：
 *
 * - **有判决规则**时，结果一律听行里那格（规则判决 / 换过的那一项 / 自己写的那句话），
 *   下面那格「要决定的事」是题目；
 * - **没配规则**时行里那行压根不显示，下面那格写的就是结果。
 *
 * 拆成纯函数是因为这里踩过坑：原先写成「下面那格优先」，于是行里写的字会被**静默丢掉**
 * —— 存进去的是规则判决，人却以为自己写的算数。
 */
internal fun resolveDecisionAndSubject(
    rowActive: Boolean,
    rowChoice: String,
    bottomText: String,
): Pair<String, String> {
    val bottom = bottomText.trim()
    return if (rowActive) rowChoice.trim() to bottom else bottom to ""
}

/**
 * 终局那行「本次决策」：
 *
 * ```
 * 本次决策： [ 执行 ▾ ]  [ 自定义 ]              ← 空着、没焦点：就是 hint 那么宽
 * 本次决策： [ 执行 ▾ ]  [ 自己写一个…       ✕ ]  ← 拿到焦点：伸长，但不超过这行剩下的宽度
 * 本次决策： [ 执行 ▾ ]  [ 看天气 ]              ← 失去焦点：缩到文字长度
 * ```
 *
 * - 左边那颗 chip 是**规则给出的判决**，点开可以换成同一套规则里的其他结果项
 *   （「三局两胜」就有第三个「平手」可选）；换完就退出自定义，两边互斥。
 * - 右边那格输入框：**有字的时候再点一下（获得焦点）就算重新选中自定义** ——
 *   不用重打一遍；右边 ✕ 是不要这句话、改回按规则判。
 * - 输入框只是**看起来**像 chip：它是 BasicTextField 手搭的，和 chip 同高，切换不跳行。
 */
@Composable
fun DecisionChoiceRow(
    rule: JudgmentRule,
    judgment: Judgment,
    chosen: String,
    customSelected: Boolean,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    onSelectCustom: () -> Unit,
    onClearCustom: () -> Unit,
    onChooseOutcome: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    // 输入框那一格永远占住「这行剩下的宽度」，胶囊再在这个上限里伸缩 ——
    // 上限来自布局本身，不是估的，所以结构上就超不出屏幕。
    var availableWidthPx by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CHOICE_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "本次决策：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box {
            FilterChip(
                selected = !customSelected,
                onClick = { menuExpanded = true },
                label = { Text(text = chosen.ifEmpty { judgment.label }) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                // 先把「凭什么」摆出来，翻选项的时候不用回头看骰子
                DropdownMenuItem(
                    text = {
                        Text(
                            text = judgment.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
                rule.outcomes.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(text = item) },
                        onClick = {
                            onChooseOutcome(item)
                            menuExpanded = false
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { availableWidthPx = it.width },
        ) {
            DecisionInputField(
                value = customText,
                selected = customSelected,
                focused = fieldFocused,
                availableWidthPx = availableWidthPx,
                onValueChange = onCustomTextChange,
                onFocusChanged = { isFocused ->
                    fieldFocused = isFocused
                    // 已经有字：再点回来一次就算重新选中自定义，不用重打
                    if (isFocused && customText.isNotEmpty()) onSelectCustom()
                },
                onClear = {
                    onClearCustom()
                    // 清空并把焦点放掉，胶囊缩回 hint 那么宽
                    fieldFocused = false
                },
            )
        }
    }
}

@Composable
private fun DecisionInputField(
    value: String,
    selected: Boolean,
    focused: Boolean,
    availableWidthPx: Int,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium

    // 收起时的宽度 = 文字（空着就是 hint）实测宽度 + 内边距
    val sample = value.ifEmpty { FIELD_HINT }
    val textWidthPx = remember(measurer, sample, textStyle) {
        measurer.measure(text = sample, style = textStyle).size.width
    }

    val targetWidthPx = decisionFieldTargetWidthPx(
        focused = focused,
        textWidthPx = textWidthPx,
        startPaddingPx = with(density) { FIELD_START_PADDING.toPx() },
        endPaddingPx = with(density) { FIELD_END_PADDING.toPx() },
        clearButtonPx = with(density) { FIELD_CLEAR_SIZE.toPx() },
        availableWidthPx = availableWidthPx,
    )
    val width by animateDpAsState(
        targetValue = with(density) { targetWidthPx.toDp() },
        animationSpec = tween(durationMillis = FIELD_WIDTH_ANIM_MS),
        label = "decisionFieldWidth",
    )

    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        shape = RoundedCornerShape(50),
        // 选中时和旁边的 chip 一样是实心，没选中就只剩描边 —— 一眼看出这句算不算数
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
        ),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { onFocusChanged(it.isFocused) },
            singleLine = true,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = FIELD_START_PADDING, end = FIELD_END_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = FIELD_HINT,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        innerTextField()
                    }
                    if (focused && value.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onClear()
                            },
                            modifier = Modifier.size(FIELD_CLEAR_SIZE),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "不要这句，改回按规则判",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
