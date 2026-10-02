package com.xfy.randomdice.dice

import kotlin.math.abs

/**
 * 翻滚过程中的一次「角撞地」：某个角成为最低点的那一瞬间。
 *
 * @param atMs 距离开摇的毫秒数
 * @param speed01 撞击时的角速度，0..1（1 = 起手最快那一下）—— 用来决定这一下有多重
 */
data class RollImpact(val atMs: Long, val speed01: Float)

/** 采样步长：8ms 一帧姿态（125Hz）。再密也没有意义 —— 马达和手感都分不出来。 */
private const val SAMPLE_STEP_MS = 8L

/** 两次撞击至少隔这么久，免得波形碎成几百段（撞得太密时听起来本来就是一片嗡）。 */
private const val MIN_IMPACT_GAP_MS = 8L

/*
 * Compose `FastOutSlowInEasing` 的控制点：CubicBezierEasing(0.4, 0, 0.2, 1)。
 * 自己实现一份是因为这里必须是纯 Kotlin（好单测），而数值又必须和动画一致 ——
 * 不一致的话算出来的撞击时刻就和画面错位了。**动画那边改缓动，这里要跟着改。**
 */
private const val BEZIER_X1 = 0.4f
private const val BEZIER_Y1 = 0f
private const val BEZIER_X2 = 0.2f
private const val BEZIER_Y2 = 1f

private fun bezierCoordinate(t: Float, p1: Float, p2: Float): Float {
    val oneMinusT = 1f - t
    return 3f * oneMinusT * oneMinusT * t * p1 + 3f * oneMinusT * t * t * p2 + t * t * t
}

/** 三次贝塞尔缓动求值：先二分反解出参数 t，再取 y。 */
fun fastOutSlowIn(progress: Float): Float {
    val x = progress.coerceIn(0f, 1f)
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var low = 0f
    var high = 1f
    var t = x
    repeat(24) {
        t = (low + high) / 2f
        if (bezierCoordinate(t, BEZIER_X1, BEZIER_X2) < x) low = t else high = t
    }
    return bezierCoordinate(t, BEZIER_Y1, BEZIER_Y2)
}

/** 某个轴在 [atMs] 时刻的角度：从 [from] 到 [to]，时长 [durationMs]，走的就是动画那条缓动。 */
private fun angleAt(from: Float, to: Float, durationMs: Int, atMs: Long): Float {
    if (durationMs <= 0) return to
    val progress = (atMs.toFloat() / durationMs).coerceIn(0f, 1f)
    return from + (to - from) * fastOutSlowIn(progress)
}

/**
 * 把一次翻滚（三个轴各自的起止角度与时长）**预先**扫成一张撞击时刻表。
 *
 * 骰子是原地自转的，所以"角砸地"= 最低角换人：每换一次记一次撞击，
 * 顺便记下那一刻的角速度（用来定轻重）。因为缓动和起止姿态都是确定的，
 * 这张表可以**离线算完**，再编成一条震动波形一次交给系统 —— 不需要每帧去调马达。
 *
 * 三个轴的时长不同（先收 X、再收 Y、最后 Z），所以每个轴各按自己的进度求值。
 */
fun rollImpacts(
    startX: Float, startY: Float, startZ: Float,
    endX: Float, endY: Float, endZ: Float,
    durationXMs: Int, durationYMs: Int, durationZMs: Int,
    stepMs: Long = SAMPLE_STEP_MS,
): List<RollImpact> {
    val totalMs = maxOf(durationXMs, durationYMs, durationZMs).toLong()

    // 第一遍：把每个采样点的姿态扫成 (时刻, 最低角, 累计转角)
    val times = mutableListOf<Long>()
    val corners = mutableListOf<Int>()
    val angleSums = mutableListOf<Float>()
    var atMs = 0L
    while (atMs <= totalMs) {
        val x = angleAt(startX, endX, durationXMs, atMs)
        val y = angleAt(startY, endY, durationYMs, atMs)
        val z = angleAt(startZ, endZ, durationZMs, atMs)
        times += atMs
        corners += lowestCornerIndex(x, y, z)
        angleSums += abs(x) + abs(y) + abs(z)
        atMs += stepMs
    }

    // 速度取"这一步之后要转多少"（前向差分）—— 这样第一帧的速度就是起手的真实速度，
    // 而不是"从零开始转"造成的 0
    val deltas = FloatArray(times.size) { index ->
        if (index + 1 < times.size) angleSums[index + 1] - angleSums[index] else 0f
    }
    val fastest = deltas.maxOrNull()?.takeIf { it > 0f } ?: 1f

    // 第二遍：最低角换人的时刻就是一次撞击
    val impacts = mutableListOf<RollImpact>()
    var previousCorner = -1
    times.indices.forEach { index ->
        val corner = corners[index]
        if (corner == previousCorner) return@forEach
        val impact = RollImpact(
            atMs = times[index],
            speed01 = (deltas[index] / fastest).coerceIn(0f, 1f),
        )
        // 撞得太密就并进上一次 —— 那种频率下听起来本来就是一片嗡
        val last = impacts.lastOrNull()
        if (last == null || impact.atMs - last.atMs >= MIN_IMPACT_GAP_MS) {
            impacts += impact
            previousCorner = corner
        }
    }
    return impacts
}
