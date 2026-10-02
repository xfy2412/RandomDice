package com.xfy.randomdice

import com.xfy.randomdice.dice.fastOutSlowIn
import com.xfy.randomdice.dice.rollImpacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 撞击时刻表的测试（纯 JVM）。
 *
 * 这张表是"物理同步震动"的源头：它按真实的姿态扫描算出**每个角成为最低点的瞬间**。
 * 这里守的性质：起手就有、时刻单调、转慢时变稀疏、速度从 1 衰减。
 */
class RollImpactsTest {

    private val impacts = rollImpacts(
        startX = 0f, startY = 0f, startZ = 0f,
        endX = 720f, endY = 720f, endZ = 720f,
        durationXMs = 900, durationYMs = 1040, durationZMs = 1160,
    )

    @Test
    fun easingMirrorsTheAnimationCurve() {
        // Compose 的 FastOutSlowInEasing：前快后慢，中点在 0.5 右边
        assertEquals(0f, fastOutSlowIn(0f), 1e-4f)
        assertEquals(1f, fastOutSlowIn(1f), 1e-4f)
        assertTrue("前快后慢：一半时间要走过一半以上的角度", fastOutSlowIn(0.5f) > 0.5f)
        // 单调不减
        val samples = (0..20).map { fastOutSlowIn(it / 20f) }
        assertEquals(samples.sorted(), samples)
    }

    @Test
    fun theRollStartsWithAnImpactAndTimesNeverGoBackwards() {
        assertTrue(impacts.isNotEmpty())
        assertEquals("开摇那一下就撞地", 0L, impacts.first().atMs)
        assertEquals(impacts.map { it.atMs }.sorted(), impacts.map { it.atMs })
    }

    @Test
    fun aSpinningDieHitsTheGroundAgainAndAgain() {
        assertTrue("转两圈应该撞很多下，实际 ${impacts.size}", impacts.size >= 12)
        assertTrue("最后一撞不能超出动画时长", impacts.last().atMs <= 1160L)
    }

    @Test
    fun impactsThinOutAsTheSpinSlows() {
        val gaps = impacts.zipWithNext { a, b -> b.atMs - a.atMs }
        val quarter = (gaps.size / 4).coerceAtLeast(1)
        val head = gaps.take(quarter).average()
        val tail = gaps.takeLast(quarter).average()
        assertTrue("开头要密得多：开头 $head ms，收尾 $tail ms", head < tail)
    }

    @Test
    fun speedRampsUpThenDecays() {
        // ⚠️ 动画用的 FastOutSlowInEasing 在 t=0 斜率是 0（标准曲线：先加速再减速），
        // 所以骰子其实"先起转、后收住"—— 撞击力度跟着它走，峰值在中段而不是开头。
        val peakIndex = impacts.indices.maxByOrNull { impacts[it].speed01 }!!
        assertTrue("峰值该在前 60%，实际第 $peakIndex / ${impacts.size} 下", peakIndex < impacts.size * 0.6)
        assertTrue("收尾要明显低于峰值", impacts.last().speed01 < impacts[peakIndex].speed01 / 2f)
        assertTrue(impacts.all { it.speed01 in 0f..1f })
    }

    @Test
    fun impactsStayAtLeastOneSampleApart() {
        val gaps = impacts.zipWithNext { a, b -> b.atMs - a.atMs }
        assertTrue("相邻撞击至少隔一个采样步长", gaps.all { it >= 8L })
    }
}
