package com.xfy.randomdice

import com.xfy.randomdice.dice.JudgmentBasis
import com.xfy.randomdice.dice.JudgmentPreset
import com.xfy.randomdice.dice.JudgmentRule
import com.xfy.randomdice.dice.judge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 判决规则的测试（纯 JVM）。
 *
 * 底线两条：
 * 1. **默认规则 = 三局两胜、单数算赢**（摇一颗时退化成"单数执行、双数不执行"）；
 * 2. 判决出来的标签**一定在这套规则声明的结果项里** —— 界面上点「其他项」才有得对。
 */
class JudgmentTest {

    @Test
    fun defaultRuleIsBestOfWithOddWinning() {
        val rule = JudgmentRule()
        assertEquals(JudgmentPreset.BestOf, rule.preset)
        assertTrue("默认应该是单数算赢", rule.oddIsFirstOutcome)
        // 摇一颗时它退化成单双：单数执行、双数不执行
        assertEquals("执行", rule.judge(listOf(5))?.label)
        assertEquals("不执行", rule.judge(listOf(4))?.label)
    }

    @Test
    fun defaultRuleCountsEveryDieWhenThereAreSeveral() {
        // 三局两胜：3 颗里 2 颗单数就执行
        val rule = JudgmentRule()
        assertEquals("执行", rule.judge(listOf(3, 1, 4))?.label)
        assertEquals("3 颗里 2 颗是单数", rule.judge(listOf(3, 1, 4))?.reason)
        assertEquals("不执行", rule.judge(listOf(3, 2, 4))?.label)
    }

    @Test
    fun oddEvenPresetStillLooksAtTheFirstDie() {
        // 「单双定夺」没被默认值改掉自己的行为
        val rule = JudgmentRule(preset = JudgmentPreset.OddEven)
        assertEquals("执行", rule.judge(listOf(3, 2, 2, 6))?.label)
        assertEquals("第 1 颗 3 · 单数", rule.judge(listOf(3, 2, 2, 6))?.reason)
    }

    @Test
    fun sumBasisJudgesTheTotal() {
        val rule = JudgmentRule(preset = JudgmentPreset.OddEven, basis = JudgmentBasis.Sum)
        val verdict = rule.judge(listOf(3, 4))
        assertEquals("执行", verdict?.label)
        assertEquals("合计 7 · 单数", verdict?.reason)
    }

    @Test
    fun swappingTheOutcomesFlipsTheVerdict() {
        val rule = JudgmentRule(preset = JudgmentPreset.OddEven, oddIsFirstOutcome = false)
        assertEquals("不执行", rule.judge(listOf(5))?.label)
        assertEquals("双数→执行", rule.shortSummary)
    }

    @Test
    fun bestOfThreeNeedsMajority() {
        val rule = JudgmentRule(preset = JudgmentPreset.BestOf)
        val win = rule.judge(listOf(1, 3, 4))
        val lose = rule.judge(listOf(1, 2, 4))
        assertEquals("执行", win?.label)
        assertEquals("3 颗里 2 颗是单数", win?.reason)
        assertEquals("不执行", lose?.label)
        assertEquals("3 颗里 1 颗是单数", lose?.reason)
    }

    @Test
    fun bestOfWithAnEvenCountCanTie() {
        // 偶数颗可能打平 —— 这套规则有第三个结果项，不是二选一
        val rule = JudgmentRule(preset = JudgmentPreset.BestOf)
        assertEquals("平手", rule.judge(listOf(1, 2))?.label)
        assertEquals(listOf("执行", "不执行", "平手"), rule.outcomes)
    }

    @Test
    fun bestOfCanBeWonByEvenNumbers() {
        val rule = JudgmentRule(preset = JudgmentPreset.BestOf, oddIsFirstOutcome = false)
        val verdict = rule.judge(listOf(2, 4, 1))
        assertEquals("执行", verdict?.label)
        assertEquals("3 颗里 2 颗是双数", verdict?.reason)
    }

    @Test
    fun bestOfWithASingleDieDegeneratesToOddEven() {
        val rule = JudgmentRule(preset = JudgmentPreset.BestOf)
        assertEquals("执行", rule.judge(listOf(3))?.label)
        assertEquals("不执行", rule.judge(listOf(2))?.label)
    }

    @Test
    fun pickOneWrapsTheSumAroundTheOptions() {
        val rule = JudgmentRule(preset = JudgmentPreset.PickOne, optionCount = 3)
        val verdict = rule.judge(listOf(6, 6, 2))
        assertEquals("B", verdict?.label)
        assertEquals("合计 14 · 第 2 项", verdict?.reason)
        assertEquals(listOf("A", "B", "C"), rule.outcomes)
    }

    @Test
    fun pickOneAlwaysStaysInRange() {
        val rule = JudgmentRule(preset = JudgmentPreset.PickOne, optionCount = 6)
        // 合计 1（最小）和合计 72（12 颗全 6）都不能越界
        assertTrue(rule.judge(listOf(1))!!.label in rule.outcomes)
        assertTrue(rule.judge(List(12) { 6 })!!.label in rule.outcomes)
        assertEquals("A", rule.judge(listOf(1))?.label)
        assertEquals("F", rule.judge(listOf(6))?.label)
    }

    @Test
    fun noRuleMeansNoJudgment() {
        assertNull(JudgmentRule(preset = JudgmentPreset.None).judge(listOf(5)))
        assertTrue(JudgmentRule(preset = JudgmentPreset.None).outcomes.isEmpty())
        assertEquals("", JudgmentRule(preset = JudgmentPreset.None).shortSummary)
    }

    @Test
    fun noResultsMeansNoJudgment() {
        assertNull(JudgmentRule().judge(emptyList()))
    }

    @Test
    fun everyVerdictComesFromTheDeclaredOutcomes() {
        val samples = listOf(
            listOf(1), listOf(6), listOf(2),
            listOf(1, 2), listOf(3, 3), listOf(6, 1, 1),
            listOf(2, 4, 6, 1, 3, 5), List(12) { 6 }, List(12) { 1 },
        )
        for (preset in JudgmentPreset.entries) {
            if (preset == JudgmentPreset.None) continue
            for (basis in JudgmentBasis.entries) {
                for (oddFirst in listOf(true, false)) {
                    for (count in 2..6) {
                        val rule = JudgmentRule(preset, basis, oddFirst, count)
                        for (results in samples) {
                            val verdict = rule.judge(results)
                            if (verdict == null) {
                                throw AssertionError("$rule + $results 应该有判决")
                            }
                            assertTrue(
                                "$rule + $results 的判决 ${verdict.label} 不在 ${rule.outcomes} 里",
                                verdict.label in rule.outcomes,
                            )
                            assertTrue("判决必须给出依据", verdict.reason.isNotBlank())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun summariesDescribeTheRuleInPlainWords() {
        assertEquals(
            "单双定夺 · 看第 1 颗 · 单数=执行",
            JudgmentRule(preset = JudgmentPreset.OddEven).summary,
        )
        assertEquals(
            "单双定夺 · 看合计 · 单数=不执行",
            JudgmentRule(
                preset = JudgmentPreset.OddEven,
                basis = JudgmentBasis.Sum,
                oddIsFirstOutcome = false,
            ).summary,
        )
        assertEquals("三局两胜 · 单数算赢", JudgmentRule(preset = JudgmentPreset.BestOf).summary)
        assertEquals("几选一 · 4 个选项（合计取模）", JudgmentRule(preset = JudgmentPreset.PickOne, optionCount = 4).summary)
    }

    @Test
    fun fortuneIsReadOffTheFirstDie() {
        val rule = JudgmentRule(preset = JudgmentPreset.Fortune)
        assertEquals(listOf("大吉", "平", "凶"), rule.outcomes)
        assertEquals("大吉", rule.judge(listOf(6))?.label)
        assertEquals("凶", rule.judge(listOf(1))?.label)
        assertEquals("平", rule.judge(listOf(3))?.label)
        assertEquals("第 1 颗 6", rule.judge(listOf(6))?.reason)
        // 只看第 1 颗：后面摇出什么都不影响吉凶
        assertEquals("大吉", rule.judge(listOf(6, 1, 1))?.label)
        assertEquals("凶", rule.judge(listOf(1, 6, 6))?.label)
    }

    @Test
    fun thresholdComparesAgainstTheSum() {
        val rule = JudgmentRule(
            preset = JudgmentPreset.Threshold,
            basis = JudgmentBasis.Sum,
            threshold = 18,
        )
        // 正好够也算执行（边界是闭的）
        assertEquals("执行", rule.judge(listOf(6, 6, 6))?.label)
        assertEquals("合计 18 ≥ 18", rule.judge(listOf(6, 6, 6))?.reason)
        assertEquals("不执行", rule.judge(listOf(6, 6, 5))?.label)
        assertEquals("合计 17 < 18", rule.judge(listOf(6, 6, 5))?.reason)
    }

    @Test
    fun thresholdCanWatchTheFirstDieInstead() {
        val rule = JudgmentRule(
            preset = JudgmentPreset.Threshold,
            basis = JudgmentBasis.FirstDie,
            threshold = 4,
        )
        assertEquals("执行", rule.judge(listOf(4, 1, 1))?.label)
        assertEquals("第 1 颗 4 ≥ 4", rule.judge(listOf(4, 1, 1))?.reason)
        assertEquals("不执行", rule.judge(listOf(3, 6, 6))?.label)
    }

    @Test
    fun rollCallNeedsEnoughOfTheNamedValue() {
        val rule = JudgmentRule(
            preset = JudgmentPreset.RollCall,
            targetValue = 6,
            targetCount = 2,
        )
        assertEquals("执行", rule.judge(listOf(6, 6, 1))?.label)
        assertEquals("3 颗里 2 颗是 6", rule.judge(listOf(6, 6, 1))?.reason)
        assertEquals("不执行", rule.judge(listOf(6, 1, 1))?.label)
        assertEquals("3 颗里 1 颗是 6", rule.judge(listOf(6, 1, 1))?.reason)
    }

    @Test
    fun rollCallCanNameAnyValue() {
        val rule = JudgmentRule(
            preset = JudgmentPreset.RollCall,
            targetValue = 3,
            targetCount = 1,
        )
        assertEquals("执行", rule.judge(listOf(2, 3))?.label)
        assertEquals("不执行", rule.judge(listOf(2, 4))?.label)
    }

    @Test
    fun summariesCoverTheNewPresets() {
        assertEquals(
            "吉凶 · 看第 1 颗（6 大吉 / 1 凶 / 其余平）",
            JudgmentRule(preset = JudgmentPreset.Fortune).summary,
        )
        assertEquals(
            "阈值定夺 · 看合计 · 够 18 就执行",
            JudgmentRule(preset = JudgmentPreset.Threshold, basis = JudgmentBasis.Sum, threshold = 18).summary,
        )
        assertEquals(
            "点名定夺 · 至少 2 颗 6",
            JudgmentRule(preset = JudgmentPreset.RollCall, targetCount = 2).summary,
        )
    }
}
