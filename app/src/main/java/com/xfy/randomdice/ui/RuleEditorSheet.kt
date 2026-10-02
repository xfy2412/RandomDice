package com.xfy.randomdice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.xfy.randomdice.dice.JudgmentBasis
import com.xfy.randomdice.dice.JudgmentPreset
import com.xfy.randomdice.dice.JudgmentRule
import com.xfy.randomdice.dice.MAX_CHOICE_OPTIONS
import com.xfy.randomdice.dice.MAX_TARGET_COUNT
import com.xfy.randomdice.dice.MAX_TARGET_VALUE
import com.xfy.randomdice.dice.MAX_THRESHOLD
import com.xfy.randomdice.dice.MIN_CHOICE_OPTIONS
import com.xfy.randomdice.dice.MIN_TARGET_COUNT
import com.xfy.randomdice.dice.MIN_TARGET_VALUE
import com.xfy.randomdice.dice.MIN_THRESHOLD
import kotlin.math.roundToInt

/**
 * 判决规则编辑：从摇骰子页那行「已经摇了 N 次」点进来。
 *
 * 改一下立刻生效（不用点确定），底下一行「现在」就是这套规则的大白话说明。
 * 以后再加预设，只要在 [JudgmentPreset] 里加一项、这里补一段参数就行。
 *
 * ⚠️ **高度必须与选中的预设无关**：卡片是贴着内容长的，参数区一高一矮，切预设时
 * 整张卡片就会"瞬移"（上沿跳一下，拖上去的位置也一起丢）。所以参数区走 [ParamsSlot]：
 * 所有参数块全都参与测量，只摆放当前这套 —— 高度恒等于最高的那块。
 * 下面那行「现在：…」也要 [Text] 的 `minLines` 兜住，否则文案长短变化照样会跳。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorSheet(
    rule: JudgmentRule,
    onRuleChange: (JudgmentRule) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "判决规则", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "摇完之后骰子直接给一句结论。随便改，改完立刻生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("预设")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JudgmentPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = rule.preset == preset,
                        onClick = { onRuleChange(rule.copy(preset = preset)) },
                        label = { Text(text = preset.label) },
                    )
                }
            }

            // ⚠️ 顺序必须和 JudgmentPreset 的声明顺序一致（按下标摆放当前这套）
            ParamsSlot(activeIndex = rule.preset.ordinal) {
                NoneParams()
                OddEvenParams(rule = rule, onRuleChange = onRuleChange)
                BestOfParams(rule = rule, onRuleChange = onRuleChange)
                PickOneParams(rule = rule, onRuleChange = onRuleChange)
                FortuneParams()
                ThresholdParams(rule = rule, onRuleChange = onRuleChange)
                RollCallParams(rule = rule, onRuleChange = onRuleChange)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "现在：${rule.summary}",
                style = MaterialTheme.typography.bodyMedium,
                minLines = 2,
            )
        }
    }
}

/**
 * 参数区的高度稳定器：**所有孩子都量，只摆当前这个**。
 *
 * 没被摆放的孩子既不显示也收不到点击，所以不会挡住上面那套参数。
 */
@Composable
private fun ParamsSlot(
    activeIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.getOrNull(activeIndex)?.place(0, 0)
        }
    }
}

@Composable
private fun NoneParams() {
    ParamBlock {
        Hint("不判决：只记点数和你自己写的结果。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OddEvenParams(rule: JudgmentRule, onRuleChange: (JudgmentRule) -> Unit) {
    ParamBlock {
        SectionLabel("看哪里")
        BasisChips(rule = rule, onRuleChange = onRuleChange)
        SectionLabel("单数算哪一边")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutcomeChip(
                text = "单数=${rule.outcomes.getOrElse(0) { "执行" }}",
                selected = rule.oddIsFirstOutcome,
                onClick = { onRuleChange(rule.copy(oddIsFirstOutcome = true)) },
            )
            OutcomeChip(
                text = "双数=${rule.outcomes.getOrElse(0) { "执行" }}",
                selected = !rule.oddIsFirstOutcome,
                onClick = { onRuleChange(rule.copy(oddIsFirstOutcome = false)) },
            )
        }
        Hint("看第 1 颗就是只看第一次摇出来的那一下；合计是把这一轮的点数加起来。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BestOfParams(rule: JudgmentRule, onRuleChange: (JudgmentRule) -> Unit) {
    ParamBlock {
        SectionLabel("哪边算赢")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutcomeChip(
                text = "单数算赢",
                selected = rule.oddIsFirstOutcome,
                onClick = { onRuleChange(rule.copy(oddIsFirstOutcome = true)) },
            )
            OutcomeChip(
                text = "双数算赢",
                selected = !rule.oddIsFirstOutcome,
                onClick = { onRuleChange(rule.copy(oddIsFirstOutcome = false)) },
            )
        }
        Hint("一次摇的每一颗算一局，赢的颗数过半就执行。想要三局两胜，把数量调成 3 就行；偶数颗可能打平。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickOneParams(rule: JudgmentRule, onRuleChange: (JudgmentRule) -> Unit) {
    ParamBlock {
        SectionLabel("选项个数")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (MIN_CHOICE_OPTIONS..MAX_CHOICE_OPTIONS).forEach { count ->
                FilterChip(
                    selected = rule.optionCount == count,
                    onClick = { onRuleChange(rule.copy(optionCount = count)) },
                    label = { Text(text = "$count") },
                )
            }
        }
        Hint("选项就是 A、B、C…，按合计点数取模落到其中一个上。")
    }
}

@Composable
private fun FortuneParams() {
    ParamBlock {
        SectionLabel("怎么判")
        Hint("只看第 1 颗：摇到 6 是大吉，摇到 1 是凶，2~5 都是平。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThresholdParams(rule: JudgmentRule, onRuleChange: (JudgmentRule) -> Unit) {
    ParamBlock {
        SectionLabel("看哪里")
        BasisChips(rule = rule, onRuleChange = onRuleChange)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "够 ${rule.threshold} 就执行",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(text = "${rule.threshold}", style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = rule.threshold.toFloat(),
            onValueChange = { raw ->
                onRuleChange(
                    rule.copy(
                        threshold = raw.roundToInt().coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
                    ),
                )
            },
            valueRange = MIN_THRESHOLD.toFloat()..MAX_THRESHOLD.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("看第 1 颗时阈值取 1~6 就够了；看合计时是整轮点数之和，摇得越多上限越高。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RollCallParams(rule: JudgmentRule, onRuleChange: (JudgmentRule) -> Unit) {
    ParamBlock {
        SectionLabel("点名哪个点数")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (MIN_TARGET_VALUE..MAX_TARGET_VALUE).forEach { value ->
                FilterChip(
                    selected = rule.targetValue == value,
                    onClick = { onRuleChange(rule.copy(targetValue = value)) },
                    label = { Text(text = "$value") },
                )
            }
        }
        SectionLabel("至少几颗")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (MIN_TARGET_COUNT..MAX_TARGET_COUNT).forEach { count ->
                FilterChip(
                    selected = rule.targetCount == count,
                    onClick = { onRuleChange(rule.copy(targetCount = count)) },
                    label = { Text(text = "$count") },
                )
            }
        }
        Hint("点名的点数出现够多颗就执行。摇的颗数比「至少几颗」还少时，这套规则永远不会执行。")
    }
}

/** 每个参数块的排版都一样，免得各写一份、高矮不一。 */
@Composable
private fun ParamBlock(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun BasisChips(rule: JudgmentRule, onRuleChange: (JudgmentRule) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JudgmentBasis.entries.forEach { basis ->
            FilterChip(
                selected = rule.basis == basis,
                onClick = { onRuleChange(rule.copy(basis = basis)) },
                label = { Text(text = basis.label) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OutcomeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text = text) })
}
