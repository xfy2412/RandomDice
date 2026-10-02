package com.xfy.randomdice.dice

import kotlin.math.cos
import kotlin.math.sin

/** 骰子局部坐标里的一个点：立方体中心在原点，棱长为 2（各轴取值 -1..1）。 */
data class Vec3(val x: Float, val y: Float, val z: Float)

/**
 * 骰子的一个面。
 *
 * @param value 点数
 * @param normal 朝外的法线（单位轴向量）
 * @param corners 四个角，顺序 A → B → C → D 绕面一圈；
 *                A→B 是点数的 u 轴、A→D 是 v 轴，九宫格摆位就按这套 (u, v) 映射。
 */
class DieFace(
    val value: Int,
    val normal: Vec3,
    val corners: List<Vec3>,
) {
    init {
        require(corners.size == 4) { "一个面需要 4 个角，实际是 ${corners.size}" }
    }
}

private fun v(x: Int, y: Int, z: Int) = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

/**
 * 六个面。对面点数相加为 7（1-6 / 2-5 / 3-4），与真骰子一致。
 * 每行的四个角都落在这个面所在的平面上（`DiceGeometryTest` 会校验）。
 */
val DIE_FACES: List<DieFace> = listOf(
    DieFace(1, Vec3(0f, 1f, 0f), listOf(v(-1, 1, -1), v(1, 1, -1), v(1, 1, 1), v(-1, 1, 1))),
    DieFace(6, Vec3(0f, -1f, 0f), listOf(v(-1, -1, 1), v(1, -1, 1), v(1, -1, -1), v(-1, -1, -1))),
    DieFace(2, Vec3(0f, 0f, 1f), listOf(v(1, -1, 1), v(-1, -1, 1), v(-1, 1, 1), v(1, 1, 1))),
    DieFace(5, Vec3(0f, 0f, -1f), listOf(v(-1, -1, -1), v(1, -1, -1), v(1, 1, -1), v(-1, 1, -1))),
    DieFace(3, Vec3(1f, 0f, 0f), listOf(v(1, -1, 1), v(1, -1, -1), v(1, 1, -1), v(1, 1, 1))),
    DieFace(4, Vec3(-1f, 0f, 0f), listOf(v(-1, -1, -1), v(-1, -1, 1), v(-1, 1, 1), v(-1, 1, -1))),
)

/** 视线方向：等轴测视角相当于摄像机在 (1, 1, 1) 方向上。 */
val VIEW_DIRECTION = Vec3(1f, 1f, 1f)

/**
 * 点数在面上的摆位：面内参数 0..1 的九宫格三档坐标。
 *
 * 取值偏保守 —— 点数必须离面缘留出安全距离，不能贴着边（`DiceGeometryTest` 有用例守着）。
 * 图标生成脚本 `_audit/scratch/gen-dice-icon.js` 镜像了同一组数值，改这里要一起改。
 */
private val PIP_GRID = listOf(0.225f, 0.5f, 0.775f)

/** 点数半径 / 面边长。 */
const val PIP_RADIUS_RATIO = 0.098f

/** 各点数的九宫格单元（列, 行），取值 0/1/2。 */
private val PIP_CELLS: Map<Int, List<Pair<Int, Int>>> = mapOf(
    1 to listOf(1 to 1),
    2 to listOf(0 to 0, 2 to 2),
    3 to listOf(0 to 0, 1 to 1, 2 to 2),
    4 to listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2),
    5 to listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2),
    6 to listOf(0 to 0, 0 to 1, 0 to 2, 2 to 0, 2 to 1, 2 to 2),
)

/** 某个点数的各个点：面内 (u, v) 坐标，0..1，A 角是 (0, 0)。 */
fun pipPositions(value: Int): List<Pair<Float, Float>> =
    PIP_CELLS.getValue(value).map { (column, row) -> PIP_GRID[column] to PIP_GRID[row] }

/**
 * 一次姿态的旋转矩阵（预先算好 sin/cos，一帧里复用）。
 *
 * 旋转顺序：**先绕 Z、再绕 X、最后绕 Y**，即 R = Ry · Rx · Rz。
 *
 * ⚠️ 必须是三个轴。只给 X/Y 两个轴的话，±X 两个面（点数 3 和 4）的 y 分量恒为 0，
 * 永远不可能成为"朝上"的那一面 —— 这个坑是被 `DiceGeometryTest` 抓出来的。
 *
 * ⚠️ 渲染和「哪面朝上」必须共用这一个实现：否则会出现画面停在 6、
 * 记下来的却是 3 这种最恶心的 bug。
 */
class DiceRotation(
    rotXDegrees: Float,
    rotYDegrees: Float,
    rotZDegrees: Float,
) {
    private val cosX: Float
    private val sinX: Float
    private val cosY: Float
    private val sinY: Float
    private val cosZ: Float
    private val sinZ: Float

    init {
        val rx = Math.toRadians(rotXDegrees.toDouble())
        val ry = Math.toRadians(rotYDegrees.toDouble())
        val rz = Math.toRadians(rotZDegrees.toDouble())
        cosX = cos(rx).toFloat()
        sinX = sin(rx).toFloat()
        cosY = cos(ry).toFloat()
        sinY = sin(ry).toFloat()
        cosZ = cos(rz).toFloat()
        sinZ = sin(rz).toFloat()
    }

    fun apply(point: Vec3): Vec3 {
        // 绕 Z
        val x1 = point.x * cosZ - point.y * sinZ
        val y1 = point.x * sinZ + point.y * cosZ
        val z1 = point.z
        // 绕 X
        val y2 = y1 * cosX - z1 * sinX
        val z2 = y1 * sinX + z1 * cosX
        // 绕 Y
        val x3 = x1 * cosY + z2 * sinY
        val z3 = -x1 * sinY + z2 * cosY
        return Vec3(x3, y2, z3)
    }
}

/** 当前姿态下「朝上」的那一面点数。用的是和渲染完全相同的旋转。 */
fun upFaceValue(rotXDegrees: Float, rotYDegrees: Float, rotZDegrees: Float): Int {
    val rotation = DiceRotation(rotXDegrees, rotYDegrees, rotZDegrees)
    return DIE_FACES.maxByOrNull { rotation.apply(it.normal).y }!!.value
}

private val QUARTER_TURNS = listOf(0f, 90f, 180f, 270f)

/** 所有能让 [value] 朝上的「90° 整数倍」姿态 (rotX, rotY, rotZ)。 */
fun orientationsFor(value: Int): List<Triple<Float, Float, Float>> =
    QUARTER_TURNS.flatMap { x ->
        QUARTER_TURNS.flatMap { y ->
            QUARTER_TURNS.map { z -> Triple(x, y, z) }
        }
    }.filter { (x, y, z) -> upFaceValue(x, y, z) == value }

/** 从 [from] 沿正方向转到 [to] 的角度差，归一化到 0..360。 */
fun forwardDelta(from: Float, to: Float): Float = ((to - from) % 360f + 360f) % 360f

/**
 * 给 [value] 挑一个落点姿态：在候选里选「继续往前转最近」的那个，
 * 再多转 [extraTurns] 整圈当作翻滚。
 *
 * 返回三个可动画的绝对角度（每个都 ≥ 当前角度，所以看起来永远是往前滚）。
 */
fun targetRotationFor(
    value: Int,
    currentX: Float,
    currentY: Float,
    currentZ: Float,
    extraTurns: Int = 2,
): Triple<Float, Float, Float> {
    val candidates = orientationsFor(value)
    require(candidates.isNotEmpty()) { "点数 $value 没有可达姿态" }
    val (bestX, bestY, bestZ) = candidates.minByOrNull { (x, y, z) ->
        forwardDelta(currentX, x) + forwardDelta(currentY, y) + forwardDelta(currentZ, z)
    }!!
    return Triple(
        currentX + forwardDelta(currentX, bestX) + 360f * extraTurns,
        currentY + forwardDelta(currentY, bestY) + 360f * extraTurns,
        currentZ + forwardDelta(currentZ, bestZ) + 360f * extraTurns,
    )
}
