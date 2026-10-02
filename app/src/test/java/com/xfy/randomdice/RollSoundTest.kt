package com.xfy.randomdice

import com.xfy.randomdice.audio.SOUND_SAMPLE_RATE
import com.xfy.randomdice.audio.SoundTimbre
import com.xfy.randomdice.audio.renderRollSound
import com.xfy.randomdice.dice.rollImpacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 音效合成的测试（纯 JVM）。
 *
 * 我听不到声音，所以只能量：**电平合理、开头立刻有声、五种材质确实不一样**。
 * 第一条"开头立刻有声"是有来历的 —— 试听素材第一版把音量直接等于角速度，
 * 结果前 176ms 几乎是静音的（动画缓动在起步那段的斜率接近 0）。
 */
class RollSoundTest {

    private val impacts = rollImpacts(
        startX = 0f, startY = 0f, startZ = 0f,
        endX = 720f, endY = 810f, endZ = 900f,
        durationXMs = 900, durationYMs = 1040, durationZMs = 1160,
    )

    /** 过零率：当"亮度"的便宜代理（越高越亮）。 */
    private fun zeroCrossingRate(samples: ShortArray): Double {
        var crossings = 0
        for (index in 1 until samples.size) {
            if ((samples[index - 1] < 0) != (samples[index] < 0)) crossings += 1
        }
        return crossings.toDouble() / samples.size
    }

    @Test
    fun offMeansNoSamples() {
        assertEquals(0, renderRollSound(impacts, SoundTimbre.Off, 1160).size)
    }

    @Test
    fun renderedSoundIsLoudEnoughAndNeverClips() {
        val samples = renderRollSound(impacts, SoundTimbre.Plastic, 1160)
        // 时长 = 动画 + 尾巴
        assertTrue("长度不对：${samples.size}", samples.size > SOUND_SAMPLE_RATE)
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue("太轻了：peak=$peak", peak > 20_000)
        assertTrue("削波了：peak=$peak", peak <= 32_767)
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        assertTrue("几乎是静音：rms=$rms", rms > 500)
    }

    @Test
    fun theClatterStartsRightAway() {
        val samples = renderRollSound(impacts, SoundTimbre.Wood, 1160)
        val firstTwentyMs = samples.take(SOUND_SAMPLE_RATE / 50)
        assertTrue(
            "开头 20ms 就得有声音（不能像第一版那样前半秒静音）",
            firstTwentyMs.any { abs(it.toInt()) > 2_000 },
        )
    }

    @Test
    fun everyTimbreSoundsDifferent() {
        val rates = SoundTimbre.entries
            .filter { it != SoundTimbre.Off }
            .associateWith { zeroCrossingRate(renderRollSound(impacts, it, 1160)) }

        assertEquals(
            "五种材质的亮度不该撞车：$rates",
            rates.size,
            rates.values.map { (it * 10_000).roundToInt() }.toSet().size,
        )
        assertTrue(
            "厚重该比塑料暗：heavy=${rates.getValue(SoundTimbre.Heavy)} plastic=${rates.getValue(SoundTimbre.Plastic)}",
            rates.getValue(SoundTimbre.Heavy) < rates.getValue(SoundTimbre.Plastic),
        )
    }

    @Test
    fun theSameRollRendersIdenticallyTwice() {
        // 噪声用固定种子的伪随机：同一材质同一时刻，合成结果必须可复现
        val first = renderRollSound(impacts, SoundTimbre.Ceramic, 1160)
        val second = renderRollSound(impacts, SoundTimbre.Ceramic, 1160)
        assertTrue(first.contentEquals(second))
    }
}
