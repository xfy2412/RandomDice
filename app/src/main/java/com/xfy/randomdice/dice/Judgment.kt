package com.xfy.randomdice.dice

/** 「几选一」允许的选项个数范围。 */
const val MIN_CHOICE_OPTIONS = 2
const val MAX_CHOICE_OPTIONS = 6

/** 「阈值定夺」的阈值范围。上限对应 12 颗骰子的最大合计（见 DiceScreen 的 MAX_DICE_COUNT）。 */
const val MIN_THRESHOLD = 1
const val MAX_THRESHOLD = 72

/** 「点名定夺」：点名哪个点数（1..6）、至少要几颗。 */
const val MIN_TARGET_VALUE = 1
const val MAX_TARGET_VALUE = 6
const val MIN_TARGET_COUNT = 1
const val MAX_TARGET_COUNT = 6

/**
 * 判决预设：把「摇出来的点数」翻译成一句结论。
 *
 * 加一套新预设 = 四处：
 * 1. 这里加一个枚举项（**位置即下标**，`RuleEditorSheet` 的参数区按下标摆放，顺序要对齐）；
 * 2. [JudgmentRule.outcomes] 里给一组结果项；
 * 3. [judge] 里加一段分支；
 * 4. `RuleEditorSheet` 里补一段参数（没有参数就只写一句说明）。
 */
enum class JudgmentPreset(val label: String) {
    /** 不判：只记点数，结果完全由你自己写。 */
    None("不用规则"),

    /** 单双定夺：看指定那一颗（或合计）是单还是双。 */
    OddEven("单双定夺"),

    /** 三局两胜：一次摇的每一颗算一局，赢的颗数过半就算赢。 */
    BestOf("三局两胜"),

    /** 几选一：按合计点数在一组选项里挑一个。 */
    PickOne("几选一"),

    /** 吉凶：只看第 1 颗 —— 6 大吉、1 凶、其余平。 */
    Fortune("吉凶"),

    /** 阈值定夺：看指定那一颗（或合计）够不够某个数。 */
    Threshold("阈值定夺"),

    /** 点名定夺：点名的点数出现了足够多颗就执行。 */
    RollCall("点名定夺"),
}

/** 判决看哪里。三局两胜是「每一颗各算一局」、吉凶只看第 1 颗，都用不到这一项。 */
enum class JudgmentBasis(val label: String) {
    FirstDie("第 1 颗"),
    Sum("合计"),
}

/**
 * 一套判决规则。
 *
 * 默认值就是最常用的那套：**三局两胜，单数算赢**（摇一颗时它就退化成"单数执行、双数不执行"）。
 */
data class JudgmentRule(
    val preset: JudgmentPreset = JudgmentPreset.BestOf,
    val basis: JudgmentBasis = JudgmentBasis.FirstDie,
    /** 单数是否对应第一个结果项。true = 单数算赢/执行（默认），false = 双数。 */
    val oddIsFirstOutcome: Boolean = true,
    /** [JudgmentPreset.PickOne] 的选项个数。 */
    val optionCount: Int = 3,
    /** [JudgmentPreset.Threshold] 的阈值：够到这个数就算执行。 */
    val threshold: Int = 4,
    /** [JudgmentPreset.RollCall] 点名的点数。 */
    val targetValue: Int = 6,
    /** [JudgmentPreset.RollCall] 至少要几颗。 */
    val targetCount: Int = 1,
) {

    /**
     * 这套规则会给出的结果项，**顺序固定 —— 索引就是语义**。
     * 记录里存的是标签文本，所以这里改名不影响老记录。
     */
    val outcomes: List<String>
        get() = when (preset) {
            JudgmentPreset.None -> emptyList()
            JudgmentPreset.OddEven -> listOf("执行", "不执行")
            JudgmentPreset.BestOf -> listOf("执行", "不执行", "平手")
            JudgmentPreset.PickOne -> List(optionCount.coerceIn(MIN_CHOICE_OPTIONS, MAX_CHOICE_OPTIONS)) {
                ('A' + it).toString()
            }
            JudgmentPreset.Fortune -> listOf("大吉", "平", "凶")
            JudgmentPreset.Threshold -> listOf("执行", "不执行")
            JudgmentPreset.RollCall -> listOf("执行", "不执行")
        }

    /** 摇骰子页那行小字用的短摘要（没规则时为空串）。 */
    val shortSummary: String
        get() = when (preset) {
            JudgmentPreset.None -> ""
            JudgmentPreset.OddEven -> if (oddIsFirstOutcome) "单数→执行" else "双数→执行"
            JudgmentPreset.BestOf -> "三局两胜"
            JudgmentPreset.PickOne -> "${optionCount}选一"
            JudgmentPreset.Fortune -> "吉凶"
            JudgmentPreset.Threshold -> "≥$threshold"
            JudgmentPreset.RollCall -> "点名$targetValue"
        }

    /** 写进记录里的完整规则快照 —— 以后规则改了，老记录也能自己说明白当时是按什么判的。 */
    val summary: String
        get() = when (preset) {
            JudgmentPreset.None -> "不用规则"
            JudgmentPreset.OddEven ->
                "单双定夺 · 看${basis.label} · 单数=" + if (oddIsFirstOutcome) "执行" else "不执行"
            JudgmentPreset.BestOf ->
                "三局两胜 · " + if (oddIsFirstOutcome) "单数算赢" else "双数算赢"
            JudgmentPreset.PickOne -> "几选一 · $optionCount 个选项（合计取模）"
            JudgmentPreset.Fortune -> "吉凶 · 看第 1 颗（6 大吉 / 1 凶 / 其余平）"
            JudgmentPreset.Threshold -> "阈值定夺 · 看${basis.label} · 够 $threshold 就执行"
            JudgmentPreset.RollCall -> "点名定夺 · 至少 $targetCount 颗 $targetValue"
        }
}

/** 一次判决的结果：[label] 是要展示/记录的结论，[reason] 是「凭什么」（一行小字）。 */
data class Judgment(val label: String, val reason: String)

/**
 * 按规则判决一次结果。
 *
 * 返回 null 表示「这次没有判决」—— 没配规则、或者还没摇出任何点数。
 * 纯函数，没有副作用，单测直接喂点数。
 */
fun JudgmentRule.judge(results: List<Int>): Judgment? {
    if (preset == JudgmentPreset.None || results.isEmpty()) return null
    val items = outcomes

    return when (preset) {
        JudgmentPreset.None -> null

        JudgmentPreset.OddEven -> {
            val value = if (basis == JudgmentBasis.FirstDie) results.first() else results.sum()
            val odd = value % 2 != 0
            val where = if (basis == JudgmentBasis.FirstDie) "第 1 颗 $value" else "合计 $value"
            Judgment(
                label = items[if (odd == oddIsFirstOutcome) 0 else 1],
                reason = "$where · " + if (odd) "单数" else "双数",
            )
        }

        JudgmentPreset.BestOf -> {
            // 每一颗各算一局：赢的颗数过半就执行，输的过半就不执行，否则平手
            val wins = results.count { (it % 2 != 0) == oddIsFirstOutcome }
            val losses = results.size - wins
            val index = when {
                wins * 2 > results.size -> 0
                losses * 2 > results.size -> 1
                else -> 2
            }
            val winning = if (oddIsFirstOutcome) "单数" else "双数"
            Judgment(
                label = items[index],
                reason = "${results.size} 颗里 $wins 颗是$winning",
            )
        }

        JudgmentPreset.PickOne -> {
            val sum = results.sum()
            val index = (sum - 1).mod(items.size)
            Judgment(
                label = items[index],
                reason = "合计 $sum · 第 ${index + 1} 项",
            )
        }

        JudgmentPreset.Fortune -> {
            // 只看第 1 颗：一局的仪式感就来自「点那一颗」
            val value = results.first()
            val index = when (value) {
                6 -> 0
                1 -> 2
                else -> 1
            }
            Judgment(label = items[index], reason = "第 1 颗 $value")
        }

        JudgmentPreset.Threshold -> {
            val value = if (basis == JudgmentBasis.FirstDie) results.first() else results.sum()
            val where = if (basis == JudgmentBasis.FirstDie) "第 1 颗 $value" else "合计 $value"
            Judgment(
                label = items[if (value >= threshold) 0 else 1],
                reason = "$where " + if (value >= threshold) "≥ $threshold" else "< $threshold",
            )
        }

        JudgmentPreset.RollCall -> {
            val hits = results.count { it == targetValue }
            Judgment(
                label = items[if (hits >= targetCount) 0 else 1],
                reason = "${results.size} 颗里 $hits 颗是 $targetValue",
            )
        }
    }
}
