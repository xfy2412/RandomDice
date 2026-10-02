package com.xfy.randomdice

import com.xfy.randomdice.ui.decisionFieldTargetWidthPx
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行内输入框宽度的测试（纯 JVM）。
 *
 * 守一条底线：**不管文字多长、有没有焦点，这一行都不会被撑出可用宽度** ——
 * 这就是「最大不得使当前行组件超出屏幕」那条要求的可执行版本。
 */
class DecisionChoiceRowWidthTest {

    private val startPadding = 12f
    private val endPadding = 8f
    private val clearButton = 28f
    private val available = 400

    @Test
    fun focusedFieldTakesAllTheRemainingWidth() {
        assertEquals(
            400f,
            decisionFieldTargetWidthPx(true, 60, startPadding, endPadding, clearButton, available),
            0.01f,
        )
    }

    @Test
    fun unfocusedFieldShrinksToTheTextLength() {
        // 文字 60 + 内边距 20 = 80，远小于可用宽度，就按这个来
        assertEquals(
            80f,
            decisionFieldTargetWidthPx(false, 60, startPadding, endPadding, clearButton, available),
            0.01f,
        )
    }

    @Test
    fun longTextNeverPushesTheRowPastTheAvailableWidth() {
        val veryLong = 10_000
        assertEquals(
            400f,
            decisionFieldTargetWidthPx(false, veryLong, startPadding, endPadding, clearButton, available),
            0.01f,
        )
        assertEquals(
            400f,
            decisionFieldTargetWidthPx(true, veryLong, startPadding, endPadding, clearButton, available),
            0.01f,
        )
    }

    @Test
    fun atTheBoundaryTheFieldFitsExactly() {
        // 可用宽度正好等于需要的宽度：不压缩也不越界
        assertEquals(80f, decisionFieldTargetWidthPx(false, 60, startPadding, endPadding, clearButton, 80), 0.01f)
        // 再少 1px 就按可用宽度收（收缩优先于保持文字全宽）
        assertEquals(79f, decisionFieldTargetWidthPx(false, 60, startPadding, endPadding, clearButton, 79), 0.01f)
    }

    @Test
    fun beforeTheFirstMeasureItStillHasAWidth() {
        // 可用宽度还是 0（第一帧）时不能给 0 宽，否则会闪一下空胶囊
        assertEquals(
            80f,
            decisionFieldTargetWidthPx(false, 60, startPadding, endPadding, clearButton, 0),
            0.01f,
        )
        assertEquals(
            108f,
            decisionFieldTargetWidthPx(true, 60, startPadding, endPadding, clearButton, 0),
            0.01f,
        )
    }
}
