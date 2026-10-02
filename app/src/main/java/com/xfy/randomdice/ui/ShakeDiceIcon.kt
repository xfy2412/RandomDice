package com.xfy.randomdice.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 「摇一摇」的图标：**一颗正在被摇的骰子** —— 圆角骰身 + 对角三点 + 右上/左下两条贴着圆角的弧。
 *
 * 为什么自己画：
 * - `material-icons-core`（我们唯一的图标依赖）里没有 shake / vibration —— 那套在
 *   `material-icons-extended` 里，几千个图标换一个字形，不值；
 * - core 里能选的两个都不达意：**手机**会被读成"打电话"，**循环箭头**会被读成"点我再摇一次"，
 *   都是假的交互暗示。与其将就，不如照着我们自己的骰子画一颗。
 *
 * 那两条弧是"残影"：和骰身隔开一点、圆度与骰子圆角一致，看着就是两个角在晃。
 *
 * 画法取巧处：三点用「圆头零长度线段」（`strokeLineCap = Round` 画点最省）。
 * 颜色不写死，交给 `Icon` 的 tint（关=灰、开=主色）。
 */
val ShakeDiceIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "ShakeDice",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 骰身：圆角方框（描边）。圆角只收一点 —— 收多了就不像骰子，像块香皂。
        path(
            stroke = ink,
            strokeLineWidth = 1.5f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8.2f, 6.4f)
            lineTo(15.8f, 6.4f)
            curveTo(16.8f, 6.4f, 17.6f, 7.2f, 17.6f, 8.2f)
            lineTo(17.6f, 15.8f)
            curveTo(17.6f, 16.8f, 16.8f, 17.6f, 15.8f, 17.6f)
            lineTo(8.2f, 17.6f)
            curveTo(7.2f, 17.6f, 6.4f, 16.8f, 6.4f, 15.8f)
            lineTo(6.4f, 8.2f)
            curveTo(6.4f, 7.2f, 7.2f, 6.4f, 8.2f, 6.4f)
            close()
        }
        // 对角三点
        path(
            stroke = ink,
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(9.5f, 9.5f)
            lineTo(9.5f, 9.5f)
            moveTo(12f, 12f)
            lineTo(12f, 12f)
            moveTo(14.5f, 14.5f)
            lineTo(14.5f, 14.5f)
        }
        // 两条贴着圆角的弧：右上、左下各一条，和骰身留一点空隙、圆度跟骰子圆角一致 ——
        // 看着就像"骰子的两个角在晃、留了道残影"，比四道直线更"动"。
        // 弧心与骰子圆角的弧心重合（右上 15.8/8.2，左下 8.2/15.8），半径取 4.0，
        // 控制点按 k≈0.5523×R 摆，逼近四分之一圆。
        path(
            stroke = ink,
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // 右上角
            moveTo(15.8f, 4.2f)
            curveTo(18.01f, 4.2f, 19.8f, 5.99f, 19.8f, 8.2f)
            // 左下角
            moveTo(4.2f, 15.8f)
            curveTo(4.2f, 18.01f, 5.99f, 19.8f, 8.2f, 19.8f)
        }
    }.build()
}
