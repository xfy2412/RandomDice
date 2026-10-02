package com.xfy.randomdice.data

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

/**
 * 应用级设置：累计摇过的次数 + 当前的判决规则。
 *
 * 和决策记录一样是**纯文本一行**，没有引 DataStore / JSON —— 字段就这几个，加一层依赖不划算。
 */
data class DiceSettings(
    /** 累计摇过的**颗数**（不是轮数），跨启动累加。 */
    val totalRolls: Int = 0,
    val rule: JudgmentRule = JudgmentRule(),
)

/*
 * 存储格式（一行，制表符分隔）：
 *
 *     <累计颗数>\t<预设>\t<基准>\t<单数是否算第一个结果>\t<选项个数>\t<阈值>\t<点名点数>\t<至少几颗>
 *
 * 后加的字段一律**往行尾追加**，所以老设置文件照样能读（缺的当默认值）。
 * 任何一段读不出来都退回该项的默认值 —— 设置文件坏了不该让 app 起不来。
 */
private const val SETTINGS_SEPARATOR = '\t'

/** 把设置编码成文件内容。 */
fun encodeSettings(settings: DiceSettings): String = buildString {
    append(settings.totalRolls.coerceAtLeast(0))
    append(SETTINGS_SEPARATOR)
    append(settings.rule.preset.name)
    append(SETTINGS_SEPARATOR)
    append(settings.rule.basis.name)
    append(SETTINGS_SEPARATOR)
    append(if (settings.rule.oddIsFirstOutcome) "1" else "0")
    append(SETTINGS_SEPARATOR)
    append(settings.rule.optionCount.coerceIn(MIN_CHOICE_OPTIONS, MAX_CHOICE_OPTIONS))
    append(SETTINGS_SEPARATOR)
    append(settings.rule.threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD))
    append(SETTINGS_SEPARATOR)
    append(settings.rule.targetValue.coerceIn(MIN_TARGET_VALUE, MAX_TARGET_VALUE))
    append(SETTINGS_SEPARATOR)
    append(settings.rule.targetCount.coerceIn(MIN_TARGET_COUNT, MAX_TARGET_COUNT))
    append('\n')
}

/** 解析设置；空文件 / 坏内容一律给默认值。 */
fun decodeSettings(text: String): DiceSettings {
    val line = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return DiceSettings()
    val parts = line.split(SETTINGS_SEPARATOR)
    val defaults = JudgmentRule()
    return DiceSettings(
        totalRolls = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        rule = JudgmentRule(
            preset = parseEnum(parts.getOrNull(1), defaults.preset),
            basis = parseEnum(parts.getOrNull(2), defaults.basis),
            // 缺字段时按默认的「单数=执行」算
            oddIsFirstOutcome = parts.getOrNull(3)?.trim() != "0",
            optionCount = clampedInt(parts.getOrNull(4), MIN_CHOICE_OPTIONS, MAX_CHOICE_OPTIONS, defaults.optionCount),
            threshold = clampedInt(parts.getOrNull(5), MIN_THRESHOLD, MAX_THRESHOLD, defaults.threshold),
            targetValue = clampedInt(parts.getOrNull(6), MIN_TARGET_VALUE, MAX_TARGET_VALUE, defaults.targetValue),
            targetCount = clampedInt(parts.getOrNull(7), MIN_TARGET_COUNT, MAX_TARGET_COUNT, defaults.targetCount),
        ),
    )
}

/** 读一个整数并夹到范围内；读不出来就用 [fallback]。 */
private fun clampedInt(raw: String?, min: Int, max: Int, fallback: Int): Int =
    raw?.trim()?.toIntOrNull()?.coerceIn(min, max) ?: fallback

/** 按枚举名解析（大小写不敏感）；认不出来就用 [fallback]。 */
private inline fun <reified T : Enum<T>> parseEnum(name: String?, fallback: T): T =
    name?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { raw -> enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        ?: fallback
