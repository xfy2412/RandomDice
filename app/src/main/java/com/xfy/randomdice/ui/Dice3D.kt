package com.xfy.randomdice.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.xfy.randomdice.dice.DIE_FACES
import com.xfy.randomdice.dice.DieFace
import com.xfy.randomdice.dice.DiceRotation
import com.xfy.randomdice.dice.PIP_RADIUS_RATIO
import com.xfy.randomdice.dice.Vec3
import com.xfy.randomdice.dice.VIEW_DIRECTION
import com.xfy.randomdice.dice.pipPositions
import kotlin.math.cos

/** 等轴测（30° 轴测）投影常数。 */
private val ISO_COS = cos(Math.toRadians(30.0)).toFloat()
private const val ISO_SIN = 0.5f

/** 往面中心收缩的比例：留出棱边的"倒角"感，不然三个面糊成一整块。 */
private const val FACE_INSET = 0.965f

private class ProjectedFace(
    val face: DieFace,
    val normalY: Float,
    val depth: Float,
    val corners: List<Offset>,
)

/**
 * 3D 骰子：等轴测投影 + 手写旋转，**零第三方依赖**（GLES/Filament 都不需要）。
 *
 * 注意参数是「取角度」的 lambda 而不是 Float —— 状态只在 Canvas 的绘制 lambda 里读，
 * 所以翻滚时只触发重绘、不触发重组。
 */
@Composable
fun Dice3D(
    rotXDegrees: () -> Float,
    rotYDegrees: () -> Float,
    rotZDegrees: () -> Float,
    rolling: Boolean,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (rolling) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 220),
        label = "diceContainer",
    )
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val shadowColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val rotation = DiceRotation(rotXDegrees(), rotYDegrees(), rotZDegrees())
        val center = Offset(size.width / 2f, size.height * 0.46f)
        val scale = size.minDimension * 0.20f

        fun project(point: Vec3): Offset = Offset(
            x = center.x + (point.x - point.z) * ISO_COS * scale,
            y = center.y + ((point.x + point.z) * ISO_SIN - point.y) * scale,
        )

        drawGroundShadow(center, scale, shadowColor)

        // 只画朝向摄像机的面（内积 > 0），远的先画、近的后画
        val visible = DIE_FACES.mapNotNull { face ->
            val rotatedNormal = rotation.apply(face.normal)
            val facing = rotatedNormal.x * VIEW_DIRECTION.x +
                rotatedNormal.y * VIEW_DIRECTION.y +
                rotatedNormal.z * VIEW_DIRECTION.z
            if (facing <= 0.001f) {
                return@mapNotNull null
            }
            val rotatedCorners = face.corners.map { rotation.apply(it) }
            ProjectedFace(
                face = face,
                normalY = rotatedNormal.y,
                depth = rotatedCorners.sumOf { (it.x + it.y + it.z).toDouble() }.toFloat(),
                corners = rotatedCorners.map { project(it) },
            )
        }.sortedBy { it.depth }

        visible.forEach { projected ->
            // 按"朝上程度"给明暗：转到上面最亮、侧面次之、底下最暗，翻滚时是连续变化的
            val lightness = (projected.normalY + 1f) / 2f
            val faceColor = lerp(container, ink, (1f - lightness) * 0.28f)

            val centroid = Offset(
                x = projected.corners.sumOf { it.x.toDouble() }.toFloat() / 4f,
                y = projected.corners.sumOf { it.y.toDouble() }.toFloat() / 4f,
            )
            val corners = projected.corners.map { corner ->
                Offset(
                    x = centroid.x + (corner.x - centroid.x) * FACE_INSET,
                    y = centroid.y + (corner.y - centroid.y) * FACE_INSET,
                )
            }

            val path = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }
            drawPath(path = path, color = faceColor)

            // 点数：面是平行四边形，投影是仿射变换，所以 (u, v) 直接按 A + u·(B-A) + v·(D-A) 落点
            val a = corners[0]
            val b = corners[1]
            val d = corners[3]
            val pipRadius = ((b - a).getDistance() + (d - a).getDistance()) / 2f * PIP_RADIUS_RATIO
            pipPositions(projected.face.value).forEach { (u, v) ->
                drawCircle(
                    color = ink,
                    radius = pipRadius,
                    center = Offset(
                        x = a.x + (b.x - a.x) * u + (d.x - a.x) * v,
                        y = a.y + (b.y - a.y) * u + (d.y - a.y) * v,
                    ),
                )
            }
        }
    }
}

/** 地面上的一团柔影（三层同心椭圆叠出来，Canvas 里画不出真正的高斯模糊）。 */
private fun DrawScope.drawGroundShadow(center: Offset, scale: Float, color: Color) {
    val shadowCenter = Offset(center.x, center.y + scale * 1.95f)
    val radiusX = scale * 1.30f
    val radiusY = scale * 0.34f
    val layers = listOf(
        Triple(1.30f, 0.05f, 0.0f),
        Triple(1.00f, 0.07f, 0.0f),
        Triple(0.62f, 0.09f, 0.0f),
    )
    layers.forEach { (spread, alpha) ->
        drawOval(
            color = color.copy(alpha = alpha),
            topLeft = Offset(
                x = shadowCenter.x - radiusX * spread,
                y = shadowCenter.y - radiusY * spread,
            ),
            size = Size(radiusX * spread * 2f, radiusY * spread * 2f),
        )
    }
}
