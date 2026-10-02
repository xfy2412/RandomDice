package com.xfy.randomdice

import com.xfy.randomdice.ui.resolveDecisionAndSubject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「结果」和「要决定的事」归位的测试（纯 JVM）。
 *
 * 一句话的模型：**下面那格 = 要决定的事情（题目）**，**行里那格 = 结果**。
 *
 * 这里守的是一条出过事的底线：**有规则时，结果一律听行里那格** ——
 * 原先写成「下面那格优先」，于是行里写的字会被静默丢掉，存进去的是规则判决，
 * 人却以为自己写的算数。
 */
class DecisionOutcomeTest {

    @Test
    fun withARuleTheRowGivesTheResultAndTheBottomIsTheSubject() {
        val (decision, subject) = resolveDecisionAndSubject(
            rowActive = true,
            rowChoice = "看天气",
            bottomText = "要不要去吃火锅",
        )
        assertEquals("看天气", decision)
        assertEquals("要不要去吃火锅", subject)
    }

    @Test
    fun withARuleAndNoSubjectThereIsNoSubject() {
        val (decision, subject) = resolveDecisionAndSubject(
            rowActive = true,
            rowChoice = "执行",
            bottomText = "",
        )
        assertEquals("执行", decision)
        assertEquals("", subject)
    }

    @Test
    fun withoutARuleTheBottomIsTheResult() {
        // 没配规则时行里那行不显示，只剩下面那格可言
        val (decision, subject) = resolveDecisionAndSubject(
            rowActive = false,
            rowChoice = "",
            bottomText = "去吃火锅",
        )
        assertEquals("去吃火锅", decision)
        assertEquals("", subject)
    }

    @Test
    fun textIsTrimmedSoBlankDoesNotCountAsContent() {
        val (decision, subject) = resolveDecisionAndSubject(
            rowActive = true,
            rowChoice = "  执行  ",
            bottomText = "   ",
        )
        assertEquals("执行", decision)
        assertEquals("", subject)
    }

    @Test
    fun aSubjectIsKeptEvenWhenTheRowHasNothingYet() {
        // 行里那格空着（还没写自定义）但题目写了：题目照样留着，不能丢
        val (decision, subject) = resolveDecisionAndSubject(
            rowActive = true,
            rowChoice = "",
            bottomText = "要不要去",
        )
        assertEquals("", decision)
        assertEquals("要不要去", subject)
    }
}
