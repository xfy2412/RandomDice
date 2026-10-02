package com.xfy.randomdice.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.xfy.randomdice.dice.PIP_RADIUS_RATIO
import com.xfy.randomdice.dice.pipPositions

/**
 * 2D 骰面：圆角方块 + 九宫格点数。
 *
 * 点数的摆位与半径跟 3D 骰子共用 geometry 里的 [pipPositions] / [PIP_RADIUS_RATIO]，
 * 所以下拉面板里的小骰子和中间那颗大骰子是同一套比例（改一处两边一起变）。
 */
@Composable
fun DiceFace2D(
    value: Int,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val pipColor = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 22),
        color = containerColor,
        contentColor = pipColor,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
        ) {
            val radius = size.minDimension * PIP_RADIUS_RATIO
            pipPositions(value).forEach { (u, v) ->
                drawCircle(
                    color = pipColor,
                    radius = radius,
                    center = Offset(x = size.width * u, y = size.height * v),
                )
            }
        }
    }
}
