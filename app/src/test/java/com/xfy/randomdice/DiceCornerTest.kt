package com.xfy.randomdice

import com.xfy.randomdice.dice.DIE_CORNERS
import com.xfy.randomdice.dice.DiceRotation
import com.xfy.randomdice.dice.lowestCornerHeight
import com.xfy.randomdice.dice.lowestCornerIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 「哪个角离地最近」的测试（纯 JVM）。
 *
 * 物理同步的震动全靠这条判断：最低角一换人，就算骰子撞了一下地。
 */
class DiceCornerTest {

    private fun touchingCorners(rotX: Float, rotY: Float, rotZ: Float): Int {
        val rotation = DiceRotation(rotX, rotY, rotZ)
        val lowest = lowestCornerHeight(rotX, rotY, rotZ)
        return DIE_CORNERS.count { abs(rotation.apply(it).y - lowest) < 1e-4f }
    }

    @Test
    fun eightDistinctCornersOnTheCube() {
        assertEquals(8, DIE_CORNERS.size)
        // 棱长 2、中心在原点：每个角都满足 |x|+|y|+|z| = 3
        assertTrue(DIE_CORNERS.all { abs(abs(it.x) + abs(it.y) + abs(it.z) - 3f) < 1e-4f })
    }

    @Test
    fun aFlatRestingDieHasFourCornersDown() {
        // 平放（90° 的整数倍）：四个角同时着地，最低高度就是半个棱长
        assertEquals(4, touchingCorners(0f, 0f, 0f))
        assertEquals(-1f, lowestCornerHeight(0f, 0f, 0f), 1e-4f)
        assertEquals(4, touchingCorners(90f, 180f, 270f))
    }

    @Test
    fun aTiltedDieRestsOnASingleCorner() {
        // 一般姿态下是一个角着地（这是"角撞地"能成为事件的前提）
        assertEquals(1, touchingCorners(30f, 40f, 50f))
    }

    @Test
    fun theReportedCornerReallyIsTheLowestOne() {
        val (x, y, z) = Triple(30f, 40f, 50f)
        val rotation = DiceRotation(x, y, z)
        val chosen = DIE_CORNERS[lowestCornerIndex(x, y, z)]
        val chosenY = rotation.apply(chosen).y
        assertEquals(lowestCornerHeight(x, y, z), chosenY, 1e-4f)
        assertTrue(DIE_CORNERS.all { rotation.apply(it).y >= chosenY - 1e-4f })
    }

    @Test
    fun theLowestCornerChangesAsTheDieTurns() {
        // 转起来最低角必须换人，否则一次撞击都不会产生
        val seen = (0 until 360 step 15).map { lowestCornerIndex(it.toFloat(), 0f, 0f) }.toSet()
        assertTrue("绕 X 转一圈至少换过 2 个角，实际 ${seen.size}", seen.size >= 2)
    }
}
