package com.xfy.randomdice.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.xfy.randomdice.dice.RollImpact

/**
 * 一次摇动的震动波形：`timings` 与 `amplitudes` 一一对应（[VibrationEffect.createWaveform] 的格式）。
 * 幅度 0 的段就是静音，所以整条波形可以从"静止"开始、也可以中途静下来。
 */
internal class RollHapticWaveform(
    val timingsMs: LongArray,
    val amplitudes: IntArray,
) {
    /** 整条波形的时长 —— 应该正好等于摇动动画的时长，落定那一下才落在骰子停住的瞬间。 */
    val totalMs: Long get() = timingsMs.sum()

    init {
        require(timingsMs.size == amplitudes.size) { "timings 与 amplitudes 必须一一对应" }
        require(timingsMs.all { it > 0 }) { "每一段的时长都必须为正" }
    }
}

/*
 * 波形参数：想改手感就改这几个数。
 *
 * 撞击的**时刻**不再由参数决定 —— 那是几何算出来的（见 dice/RollImpacts.kt）：
 * 转得快时每 8ms 就换一个最低角（撞得密 = 一片嗡），转慢了自然稀疏（一下一下）。
 * 这里只管"每一次撞击长什么样"。
 */
/** 撞击脉冲：转得快时又短又硬；转慢了接触时间长，脉冲跟着拉长。 */
private const val IMPACT_FAST_PULSE_MS = 8L
private const val IMPACT_SLOW_PULSE_MS = 22L

/** 撞击轻重：起手满幅，一路轻到没有（0 = 静音），最后只剩落定那一下。 */
private const val IMPACT_MIN_AMPLITUDE = 50
private const val IMPACT_MAX_AMPLITUDE = 150

/** 落定前至少留这么久的空档，落定那一下再给满幅 —— 有停顿才有"咚"的分量。 */
private const val SETTLE_GAP_MS = 40L
private const val SETTLE_PULSE_MS = 34L
private const val SETTLE_AMPLITUDE = 255

/**
 * 把撞击时刻表编成一条震动波形（纯函数，单测在 `RollHapticTest`）。
 *
 * - **频率来自几何**：撞击时刻是 `rollImpacts` 按真实姿态扫出来的，所以"越转越慢"是算出来的，不是调出来的；
 * - **轻重来自角速度**：动画那条曲线一开始斜率是 0（先起转），所以是"起转 → 中段最猛 → 弱下去"；
 * - **最后一下是落定**：停之前留 [SETTLE_GAP_MS] 的空档，再来一记 [SETTLE_AMPLITUDE]。
 *
 * 整条长度**精确等于** [durationMs]，所以落定正好压在骰子停住那一刻。
 * 幅度为 0 的撞击直接并进静音里（不产生空转的"震"段）。
 */
internal fun rollHapticWaveform(impacts: List<RollImpact>, durationMs: Int): RollHapticWaveform {
    val timings = mutableListOf<Long>()
    val amplitudes = mutableListOf<Int>()
    val settleAtMs = (durationMs - SETTLE_PULSE_MS).coerceAtLeast(0L)
    val lastImpactAtMs = (settleAtMs - SETTLE_GAP_MS).coerceAtLeast(0L)

    // ⚠️ 数组必须"震/停"严格交替：createWaveform 是按**下标奇偶**解释每段的，
    // 一旦出现两个连续的"震"，后面整条都会被读反（该震的地方变静音）。
    // 所以撞到重叠的撞击**并进上一段**（延长 + 取更响的），绝不新开一段。
    var lastOnIndex = -1
    var onEndsAtMs = 0L
    impacts.forEachIndexed { index, impact ->
        if (impact.atMs > lastImpactAtMs) return@forEachIndexed

        val amplitude = amplitudeFor(impact.speed01)
        // 脉冲不能盖住下一次撞击；转得慢的那几下接触时间长，脉冲也长一点
        val nextAtMs = impacts.getOrNull(index + 1)?.atMs ?: lastImpactAtMs
        val pulse = minOf(pulseFor(impact.speed01), (nextAtMs - impact.atMs).coerceAtLeast(1L))

        if (lastOnIndex < 0) {
            // 第一次撞击（扫描保证就在 0 时刻）：第 0 段就是"震"
            timings += pulse
            amplitudes += amplitude
            lastOnIndex = 0
            onEndsAtMs = impact.atMs + pulse
            return@forEachIndexed
        }
        if (impact.atMs > onEndsAtMs) {
            // 有空档：先补"停"，再开一段新的"震"
            timings += impact.atMs - onEndsAtMs
            amplitudes += 0
            timings += pulse
            amplitudes += amplitude
            lastOnIndex = timings.size - 1
            onEndsAtMs = impact.atMs + pulse
        } else {
            // 接着上一段（含正好相接的情况）：并进去，绝不让两个"震"挨着
            timings[lastOnIndex] = timings[lastOnIndex] + pulse
            amplitudes[lastOnIndex] = maxOf(amplitudes[lastOnIndex], amplitude)
            onEndsAtMs += pulse
        }
        // 别越过"最后一次撞击"的边界，否则收尾空档会被吃掉
        if (onEndsAtMs > lastImpactAtMs) onEndsAtMs = lastImpactAtMs
    }

    // 没有撞击（理论上不会发生，扫描至少给一次）：只剩落定那一段，
    // 也不能补前导静音 —— 那会把落定挤到奇数下标上，被当成"停"。
    val tailFromMs = if (lastOnIndex < 0) settleAtMs else onEndsAtMs
    if (settleAtMs > tailFromMs) {
        timings += settleAtMs - tailFromMs
        amplitudes += 0
    }
    timings += SETTLE_PULSE_MS
    amplitudes += SETTLE_AMPLITUDE

    return RollHapticWaveform(timings.toLongArray(), amplitudes.toIntArray())
}

/** 角速度 → 幅度：转得越快撞得越重（速度为 0 时轻到没有）。 */
private fun amplitudeFor(speed01: Float): Int =
    (IMPACT_MIN_AMPLITUDE + (IMPACT_MAX_AMPLITUDE - IMPACT_MIN_AMPLITUDE) * speed01.coerceIn(0f, 1f))
        .toInt()
        .coerceIn(IMPACT_MIN_AMPLITUDE, IMPACT_MAX_AMPLITUDE)

/** 角速度 → 脉冲长度：快到慢 = 短到长。 */
private fun pulseFor(speed01: Float): Long =
    (IMPACT_SLOW_PULSE_MS - (IMPACT_SLOW_PULSE_MS - IMPACT_FAST_PULSE_MS) * speed01.coerceIn(0f, 1f))
        .toLong()
        .coerceIn(IMPACT_FAST_PULSE_MS, IMPACT_SLOW_PULSE_MS)

/**
 * 摇骰子的震动。设备没有马达、或系统里关掉了"触感反馈"时**什么都不做**。
 *
 * 用 [Vibrator] 而不是 `View.performHapticFeedback`：后者只能挑系统给的几种既定反馈，
 * 控不了幅度和节奏，也就编不出"撞一次震一下"这条曲线。
 */
class RollVibrator(private val context: Context) {

    private val vibrator: Vibrator? = systemVibrator(context)

    /** 这台设备到底能不能震（没有马达就别白算波形）。 */
    val available: Boolean = vibrator?.hasVibrator() == true

    /**
     * 摇一次：把撞击时刻表编成一条波形交给系统。
     *
     * ⚠️ 整段是防御性的：波形再可疑也只能"不震"，绝不能因为震动把摇骰子搞崩。
     */
    fun playRoll(impacts: List<RollImpact>, durationMs: Int) {
        val device = vibrator ?: return
        if (!available) return
        // ⚠️ 这里**故意不看**系统的 haptic_feedback_enabled：那个值在不少 ROM 上不可信
        // （实测华为 VCE-AL00 报 0，而它自己的桌面照样在震），真按它走就是"永远不震"。
        // 平台自己的开关（应用振动权限 / 免打扰 / 厂商策略）仍然生效。

        try {
            val wave = rollHapticWaveform(impacts, durationMs)
            // 没有幅度控制的马达只能"震/不震"，那就把有声段一律拉满
            val amplitudes = if (device.hasAmplitudeControl()) {
                wave.amplitudes
            } else {
                IntArray(wave.amplitudes.size) { index ->
                    if (wave.amplitudes[index] > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0
                }
            }
            device.vibrate(VibrationEffect.createWaveform(wave.timingsMs, amplitudes, -1))
        } catch (_: Exception) {
            // 系统拒绝这条波形（段数太多之类）：退化成"整条一次震完"，至少不会白摇
            try {
                device.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs.toLong().coerceIn(1L, 5_000L),
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            } catch (_: Exception) {
                // 连一次性的都不给震：认了
            }
        }
    }

    /** 中途停下来时把正在播的震动收掉（不然它会自己震完那 1.4 秒）。 */
    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
            // 取消失败也无所谓：那条波形最多再响一秒
        }
    }
}

private fun systemVibrator(context: Context): Vibrator? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
} catch (_: Exception) {
    null
}
