package com.xfy.randomdice

import com.xfy.randomdice.dice.targetRotationFor
import com.xfy.randomdice.dice.upFaceValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「中途停止」把姿态掰回去的那一步（纯 JVM）。
 *
 * 停止会掐断翻滚动画，骰子就停在半路 —— 画着一面、记录里却是另一面。
 * 修法是用 `targetRotationFor(value, 当前姿态, extraTurns = 0)` 把它转回去。
 * 这里钉住那条底线：**不管从哪个中断姿态出发，转回去之后显示的必须就是记录里的点数**。
 */
class StopMidRollTest {

    private val interruptedPoses = listOf(
        Triple(0f, 0f, 0f),
        Triple(37f, 128f, 211f),
        Triple(180f, 179f, 90f),
        Triple(359f, 44f, 720f),
        Triple(-13f, 271f, 92f),
    )

    @Test
    fun realigningAlwaysLandsOnTheRecordedValue() {
        for (value in 1..6) {
            for ((x, y, z) in interruptedPoses) {
                val (endX, endY, endZ) = targetRotationFor(
                    value = value,
                    currentX = x,
                    currentY = y,
                    currentZ = z,
                    extraTurns = 0,
                )
                assertEquals(
                    "value=$value 从 ($x, $y, $z) 转回之后显示错了",
                    value,
                    upFaceValue(endX, endY, endZ),
                )
            }
        }
    }

    @Test
    fun realigningDoesNotSpinMoreThanOneTurn() {
        // extraTurns = 0：只转到最近的那个姿态，不该再多转整圈（停下时不该又转一大圈）
        for ((x, y, z) in interruptedPoses) {
            val (endX, endY, endZ) = targetRotationFor(3, x, y, z, extraTurns = 0)
            assertEquals(true, endX - x in 0f..360f)
            assertEquals(true, endY - y in 0f..360f)
            assertEquals(true, endZ - z in 0f..360f)
        }
    }
}
