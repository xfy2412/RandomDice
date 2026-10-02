package com.xfy.randomdice.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 「震动开关」的图标：**一个代表手机的圆角方块，配和骰子那枚一样的两条角弧**。
 *
 * 和 [ShakeDiceIcon] 是同一个视觉语言（角上的残影 = 在动），区别是中间没有点：
 * 有点的是骰子（摇一摇），没点的是手机（震动）。
 */
val PhoneShakeIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "PhoneShake",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = ink,
            strokeLineWidth = 1.5f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10f, 4.6f)
            lineTo(14f, 4.6f)
            curveTo(15.1f, 4.6f, 16f, 5.5f, 16f, 6.6f)
            lineTo(16f, 17.4f)
            curveTo(16f, 18.5f, 15.1f, 19.4f, 14f, 19.4f)
            lineTo(10f, 19.4f)
            curveTo(8.9f, 19.4f, 8f, 18.5f, 8f, 17.4f)
            lineTo(8f, 6.6f)
            curveTo(8f, 5.5f, 8.9f, 4.6f, 10f, 4.6f)
            close()
        }
        // 和骰子那枚同款：右上、左下各一条贴着圆角的弧
        path(
            stroke = ink,
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(14f, 1.6f)
            curveTo(16.05f, 1.6f, 17.7f, 3.25f, 17.7f, 5.3f)
            moveTo(10f, 22.4f)
            curveTo(7.95f, 22.4f, 6.3f, 20.75f, 6.3f, 18.7f)
        }
    }.build()
}

/**
 * 「音效」的图标：一只喇叭 + 两道声波。
 *
 * core 那套图标里没有喇叭（要 material-icons-extended），所以照骰子那枚一样自己画。
 */
val SpeakerIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "Speaker",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 喇叭本体：方块 + 喇叭口，实心
        path(fill = ink) {
            moveTo(4f, 9.2f)
            lineTo(7.2f, 9.2f)
            lineTo(11.6f, 5.2f)
            curveTo(12.1f, 4.75f, 13f, 5.1f, 13f, 5.8f)
            lineTo(13f, 18.2f)
            curveTo(13f, 18.9f, 12.1f, 19.25f, 11.6f, 18.8f)
            lineTo(7.2f, 14.8f)
            lineTo(4f, 14.8f)
            close()
        }
        // 两道声波
        path(
            stroke = ink,
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(15.6f, 9.4f)
            curveTo(16.9f, 10.7f, 16.9f, 13.3f, 15.6f, 14.6f)
            moveTo(18.2f, 6.8f)
            curveTo(20.7f, 9.3f, 20.7f, 14.7f, 18.2f, 17.2f)
        }
    }.build()
}
