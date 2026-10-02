package com.xfy.randomdice.audio

import com.xfy.randomdice.dice.RollImpact
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** 采样率：够用且各处一致，别改。 */
const val SOUND_SAMPLE_RATE = 44_100

/** 落定之后的尾巴，留出来让余响自然收掉。 */
private const val TAIL_MS = 260

/** 峰值归一化的目标（≈ -3dBFS）。 */
private const val NORMALIZE_PEAK = 0.707f

/**
 * 撞击音色。
 *
 * ⚠️ 枚举名会写进设置文件（`settings.txt` 最后一段），**改名等于让老设置失效**。
 */
enum class SoundTimbre(val label: String) {
    Off("关闭"),
    Wood("木头"),
    Plastic("塑料"),
    Ceramic("陶瓷"),
    Heavy("厚重"),
    Hybrid("混合"),
}

/** 一个共振模态：频率、相对音量、衰减时长。 */
internal class Resonance(val hz: Float, val gain: Float, val decayMs: Float)

/**
 * 一种材质的合成参数。
 *
 * 撞击 = 一段极短的噪声（敲击瞬间）+ 几个阻尼正弦（骰子自身的共振）；
 * 落定 = 同样的东西压低频率、拉长衰减、加重噪声。
 */
internal class TimbreSpec(
    val noise: Float,
    val noiseDecayMs: Float,
    val modes: List<Resonance>,
    val settleScale: Float,
    val settleDecayScale: Float,
    val settleNoise: Float,
    /** 早期反射 (延迟ms, 增益)：给一点"房间"的感觉。 */
    val room: List<Pair<Float, Float>> = emptyList(),
)

internal val TIMBRE_SPECS: Map<SoundTimbre, TimbreSpec> = mapOf(
    SoundTimbre.Wood to TimbreSpec(
        noise = 0.55f, noiseDecayMs = 4f,
        modes = listOf(Resonance(900f, 1f, 42f), Resonance(1650f, 0.6f, 32f), Resonance(2700f, 0.32f, 20f)),
        settleScale = 0.42f, settleDecayScale = 2.6f, settleNoise = 0.7f,
    ),
    SoundTimbre.Plastic to TimbreSpec(
        noise = 0.5f, noiseDecayMs = 2.5f,
        modes = listOf(Resonance(1950f, 1f, 26f), Resonance(3400f, 0.7f, 18f), Resonance(5600f, 0.4f, 12f)),
        settleScale = 0.4f, settleDecayScale = 2.4f, settleNoise = 0.65f,
    ),
    SoundTimbre.Ceramic to TimbreSpec(
        noise = 0.35f, noiseDecayMs = 3f,
        modes = listOf(Resonance(1100f, 1f, 70f), Resonance(2300f, 0.5f, 44f), Resonance(4100f, 0.25f, 26f)),
        settleScale = 0.45f, settleDecayScale = 2.2f, settleNoise = 0.6f,
        room = listOf(9f to 0.16f, 17f to 0.09f),
    ),
    SoundTimbre.Heavy to TimbreSpec(
        noise = 0.45f, noiseDecayMs = 6f,
        modes = listOf(Resonance(330f, 1f, 80f), Resonance(760f, 0.5f, 52f), Resonance(1500f, 0.25f, 30f)),
        settleScale = 0.5f, settleDecayScale = 3f, settleNoise = 0.8f,
        room = listOf(13f to 0.12f),
    ),
    SoundTimbre.Hybrid to TimbreSpec(
        noise = 0.5f, noiseDecayMs = 4f,
        modes = listOf(Resonance(1100f, 1f, 40f), Resonance(2000f, 0.6f, 28f), Resonance(3200f, 0.3f, 18f)),
        settleScale = 0.22f, settleDecayScale = 4f, settleNoise = 0.9f,
        room = listOf(11f to 0.12f),
    ),
)

/**
 * 合成一次摇动的音效（纯函数，单测在 `RollSoundTest`）。
 *
 * 返回 16bit 单声道 PCM。节奏完全来自 [impacts]（和震动、画面共用同一张表），
 * 音量用 `0.3 + 0.7×角速度` —— **不能直接等于角速度**：动画的缓动在 t=0 斜率是 0，
 * 照搬的话开头一百多毫秒几乎没声（试听素材里就是这么抓出来的）。
 */
fun renderRollSound(
    impacts: List<RollImpact>,
    timbre: SoundTimbre,
    durationMs: Int,
    sampleRate: Int = SOUND_SAMPLE_RATE,
): ShortArray {
    val spec = TIMBRE_SPECS[timbre] ?: return ShortArray(0)
    val totalMs = durationMs + TAIL_MS
    val buffer = FloatArray((totalMs / 1000f * sampleRate).toInt())

    impacts.forEach { impact ->
        addHit(buffer, impact.atMs.toFloat(), 0.3f + 0.7f * impact.speed01, spec, sampleRate, settle = false)
    }
    addHit(buffer, durationMs - 34f, 1f, spec, sampleRate, settle = true)

    applyRoom(buffer, spec, sampleRate)

    // 峰值归一化
    var peak = 0f
    for (sample in buffer) peak = max(peak, abs(sample))
    if (peak > 0f) {
        val scale = NORMALIZE_PEAK / peak
        for (index in buffer.indices) buffer[index] *= scale
    }

    // 末尾淡出，避免"啪"地截断
    val fadeSamples = (0.03f * sampleRate).toInt()
    for (index in buffer.indices.reversed()) {
        val fromEnd = buffer.size - 1 - index
        if (fromEnd >= fadeSamples) break
        buffer[index] *= fromEnd.toFloat() / fadeSamples
    }

    return ShortArray(buffer.size) { index ->
        (buffer[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    }
}

private fun addHit(
    buffer: FloatArray,
    atMs: Float,
    gain: Float,
    spec: TimbreSpec,
    sampleRate: Int,
    settle: Boolean,
) {
    // 固定种子的伪随机：同一时刻同一材质，每次合成结果一致（可测）
    val random = Lcg(seed = (atMs * 1000f).toLong() + if (settle) 7919L else 104_729L)
    val start = (atMs / 1000f * sampleRate).toInt().coerceAtLeast(0)
    val scale = if (settle) spec.settleScale else 1f
    val decayScale = if (settle) spec.settleDecayScale else 1f
    val noiseGain = (if (settle) spec.settleNoise else spec.noise) * gain

    val noiseSamples = ((spec.noiseDecayMs * decayScale / 1000f) * sampleRate).toInt()
    for (offset in 0 until noiseSamples) {
        val index = start + offset
        if (index >= buffer.size) break
        val envelope = exp(-offset / (noiseSamples / 4.5f))
        buffer[index] += random.next() * envelope * noiseGain
    }

    for (mode in spec.modes) {
        val frequency = mode.hz * scale
        val decaySamples = ((mode.decayMs * decayScale / 1000f) * sampleRate).toInt()
        if (decaySamples <= 0) continue
        val omega = (2.0 * PI * frequency / sampleRate).toFloat()
        for (offset in 0 until decaySamples) {
            val index = start + offset
            if (index >= buffer.size) break
            val envelope = exp(-offset / (decaySamples / 5.5f))
            buffer[index] += sin(omega * offset) * envelope * mode.gain * gain * 0.55f
        }
    }
}

private fun applyRoom(buffer: FloatArray, spec: TimbreSpec, sampleRate: Int) {
    if (spec.room.isEmpty()) return
    val dry = buffer.copyOf()
    for ((delayMs, gain) in spec.room) {
        val offset = (delayMs / 1000f * sampleRate).toInt()
        for (index in 0 until buffer.size - offset) {
            buffer[index + offset] += dry[index] * gain
        }
    }
}

/** 线性同余伪随机：要的是"可复现的噪声"，不是统计质量。 */
private class Lcg(seed: Long) {
    private var state: Long = seed

    fun next(): Float {
        state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return state / 0xFFFFFFFF.toFloat() * 2f - 1f
    }
}
