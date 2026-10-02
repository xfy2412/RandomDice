package com.xfy.randomdice

import com.xfy.randomdice.dice.DIE_FACES
import com.xfy.randomdice.dice.PIP_RADIUS_RATIO
import com.xfy.randomdice.dice.orientationsFor
import com.xfy.randomdice.dice.pipPositions
import com.xfy.randomdice.dice.targetRotationFor
import com.xfy.randomdice.dice.upFaceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 只测几何数学本身（纯 JVM，不需要设备）。
 *
 * 要守住的底线：**动画停在某个姿态时，"朝上的那一面"必须等于记下来的点数**。
 */
class DiceGeometryTest {

    @Test
    fun faceTableIsConsistent() {
        assertEquals(6, DIE_FACES.size)
        assertEquals((1..6).toSet(), DIE_FACES.map { it.value }.toSet())

        DIE_FACES.forEach { face ->
            val length = sqrt(
                face.normal.x * face.normal.x +
                    face.normal.y * face.normal.y +
                    face.normal.z * face.normal.z
            )
            assertEquals("法线必须是单位向量：${face.normal}", 1f, length, 1e-4f)

            face.corners.forEach { corner ->
                val dot = corner.x * face.normal.x + corner.y * face.normal.y + corner.z * face.normal.z
                assertEquals("角 $corner 不落在点数 ${face.value} 的面平面上", 1f, dot, 1e-4f)
                assertTrue("角必须是立方体顶点：$corner", abs(corner.x) == 1f && abs(corner.y) == 1f && abs(corner.z) == 1f)
            }
        }

        // 对面相加为 7
        DIE_FACES.forEach { face ->
            val opposite = DIE_FACES.first {
                it.normal.x == -face.normal.x && it.normal.y == -face.normal.y && it.normal.z == -face.normal.z
            }
            assertEquals("${face.value} 的对面应该是 ${7 - face.value}", 7, face.value + opposite.value)
        }
    }

    @Test
    fun initialOrientationShowsOne() {
        // DiceScreen 的初值：姿态全 0、value = 1，两者必须一致
        assertEquals(1, upFaceValue(0f, 0f, 0f))
    }

    @Test
    fun everyValueHasReachableOrientation() {
        var total = 0
        for (value in 1..6) {
            val orientations = orientationsFor(value)
            assertTrue("点数 $value 没有任何可达姿态（三轴都要给，缺一轴会有面永远朝不上来）", orientations.isNotEmpty())
            orientations.forEach { (x, y, z) ->
                assertEquals("姿态 ($x, $y, $z) 朝上的不是 $value", value, upFaceValue(x, y, z))
            }
            total += orientations.size
        }
        // 4×4×4 个「90° 整数倍」姿态应被六个面恰好分完
        assertEquals(64, total)
    }

    @Test
    fun targetRotationLandsOnTheRequestedValue() {
        for (value in 1..6) {
            val (endX, endY, endZ) = targetRotationFor(value, 0f, 0f, 0f, extraTurns = 2)
            assertTrue("必须往前多转两圈：$endX", endX >= 720f - 1e-3f)
            assertTrue("必须往前多转两圈：$endY", endY >= 720f - 1e-3f)
            assertTrue("必须往前多转两圈：$endZ", endZ >= 720f - 1e-3f)
            assertEquals("动画终点朝上的面不对：$value", value, upFaceValue(endX, endY, endZ))
            // DiceScreen 摇完会把角度归一，归一后也必须一致
            assertEquals(
                "归一化后朝上的面不对：$value",
                value,
                upFaceValue(endX % 360f, endY % 360f, endZ % 360f),
            )
        }
    }

    @Test
    fun targetRotationIsAlwaysForwardFromCurrent() {
        val currents = listOf(0f, 37f, 90f, 123.5f, 270f, 359f)
        for (value in 1..6) {
            for (current in currents) {
                val (endX, endY, endZ) = targetRotationFor(value, current, current, current, extraTurns = 1)
                assertTrue("终点必须大于起点：$endX > $current", endX > current)
                assertTrue("终点必须大于起点：$endY > $current", endY > current)
                assertTrue("终点必须大于起点：$endZ > $current", endZ > current)
                assertEquals(value, upFaceValue(endX, endY, endZ))
            }
        }
    }

    @Test
    fun pipsKeepASafeDistanceFromTheFaceEdge() {
        val expectedCount = mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6)
        val minMargin = 0.10f
        for (value in 1..6) {
            val pips = pipPositions(value)
            assertEquals("点数 $value 的点个数不对", expectedCount.getValue(value), pips.size)
            pips.forEach { (u, v) ->
                assertTrue("点数 $value 在 u=$u 处贴左缘", u - PIP_RADIUS_RATIO >= minMargin)
                assertTrue("点数 $value 在 u=$u 处贴右缘", u + PIP_RADIUS_RATIO <= 1f - minMargin)
                assertTrue("点数 $value 在 v=$v 处贴上缘", v - PIP_RADIUS_RATIO >= minMargin)
                assertTrue("点数 $value 在 v=$v 处贴下缘", v + PIP_RADIUS_RATIO <= 1f - minMargin)
            }
        }
    }

    @Test
    fun pipsDoNotTouchEachOther() {
        for (value in 1..6) {
            val pips = pipPositions(value)
            for (i in pips.indices) {
                for (j in i + 1 until pips.size) {
                    val dx = pips[i].first - pips[j].first
                    val dy = pips[i].second - pips[j].second
                    val distance = sqrt(dx * dx + dy * dy)
                    assertTrue(
                        "点数 $value 的第 ${i + 1} 和第 ${j + 1} 个点挨太近（$distance）",
                        distance >= PIP_RADIUS_RATIO * 2.4f,
                    )
                }
            }
        }
    }
}
