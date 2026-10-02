package com.xfy.randomdice

import com.xfy.randomdice.dice.rollImpacts
import com.xfy.randomdice.ui.rollHapticWaveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 震动波形的测试（纯 JVM）。
 *
 * 手感没法自动验证，但**波形描述手感的那些性质**可以：
 * 1. **只在该震的时候震**：每一段有声的起点都必须落在某次「角撞地」上；
 * 2. **越转越轻**：幅度随角速度衰减，最后轻到没有；
 * 3. **落定最重**：末尾一记最重的，前面还留了空档；
 * 4. **长度精确**：整条正好等于动画时长 —— 落定才压在骰子停住那一刻。
 */
class RollHapticTest {

    private val durationMs = 1160
    private val impacts = rollImpacts(
        startX = 0f, startY = 0f, startZ = 0f,
        endX = 720f, endY = 720f, endZ = 720f,
        durationXMs = 900, durationYMs = 1040, durationZMs = 1160,
    )

    private fun pulseStarts(wave: com.xfy.randomdice.ui.RollHapticWaveform): List<Long> {
        var cursor = 0L
        val starts = mutableListOf<Long>()
        wave.timingsMs.forEachIndexed { index, timing ->
            if (wave.amplitudes[index] > 0) starts += cursor
            cursor += timing
        }
        return starts
    }

    @Test
    fun waveformLastsExactlyAsLongAsTheAnimation() {
        assertEquals(durationMs.toLong(), rollHapticWaveform(impacts, durationMs).totalMs)
    }

    @Test
    fun timingsAndAmplitudesLineUp() {
        val wave = rollHapticWaveform(impacts, durationMs)
        assertEquals(wave.timingsMs.size, wave.amplitudes.size)
        assertTrue("每段时长都得为正", wave.timingsMs.all { it > 0 })
    }

    @Test
    fun everyPulseHappensOnAnImpact() {
        val wave = rollHapticWaveform(impacts, durationMs)
        val impactTimes = impacts.map { it.atMs }.toSet()
        // 最后一段是落定，不在撞击表里
        val fromImpacts = pulseStarts(wave).dropLast(1)
        assertTrue("撞击应该震出好几下，实际 ${fromImpacts.size}", fromImpacts.size >= 5)
        assertTrue("震动的起点必须都是撞击时刻：$fromImpacts", fromImpacts.all { it in impactTimes })
    }

    @Test
    fun theRollFadesOutBeforeTheSettle() {
        val wave = rollHapticWaveform(impacts, durationMs)
        val rolling = wave.amplitudes.dropLast(1).filter { it > 0 }
        assertTrue(rolling.size >= 3)
        // 转速先升后降，所以声音也是"起转 → 最响 → 弱下去"，峰值在中段
        val peakIndex = rolling.indices.maxByOrNull { rolling[it] }!!
        assertTrue("峰值该在中段，实际第 $peakIndex / ${rolling.size}", peakIndex < rolling.size * 0.7)
        assertTrue("收尾要明显轻", rolling.last() < rolling[peakIndex] / 2)
    }

    @Test
    fun theSettleIsTheStrongestHitOfAll() {
        val wave = rollHapticWaveform(impacts, durationMs)
        assertEquals("最后一段必须是震", true, wave.amplitudes.last() > 0)
        assertEquals("落定要是整条里最重的", wave.amplitudes.max(), wave.amplitudes.last())
        assertEquals(255, wave.amplitudes.last())
        assertEquals("落定前先顿一下", 0, wave.amplitudes[wave.amplitudes.size - 2])
    }

    @Test
    fun theWaveformAlternatesStrictly() {
        // createWaveform 按下标奇偶判断"震/停"：一旦出现两个连续的"震"，
        // 后面整条都会被读反（该震的地方变静音）—— 这是踩过的坑，钉住它。
        val wave = rollHapticWaveform(impacts, durationMs)
        assertEquals("条目数必须是奇数（起于震、止于落定）", 1, wave.timingsMs.size % 2)
        assertTrue(
            "奇数下标都必须是静音段",
            wave.amplitudes.filterIndexed { index, _ -> index % 2 == 1 }.all { it == 0 },
        )
    }

    @Test
    fun brokenInputStillEndsWithASettle() {
        for (candidate in listOf(emptyList(), impacts.take(1))) {
            val wave = rollHapticWaveform(candidate, 0)
            assertTrue("再短也得有落定那一下", wave.amplitudes.last() > 0)
            assertEquals(wave.timingsMs.size, wave.amplitudes.size)
            assertTrue(wave.timingsMs.all { it > 0 })
        }
    }
}
