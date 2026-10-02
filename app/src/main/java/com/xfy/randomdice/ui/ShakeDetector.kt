package com.xfy.randomdice.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.sqrt

/** 重力加速度标准值。不引 Android 常量，下面的判断才是能单测的纯函数。 */
private const val GRAVITY = 9.80665f

/** 加速度模长离重力这么远才算"在摇"。走路、翻手机、放桌上都到不了这个数。 */
internal const val SHAKE_IMPULSE_THRESHOLD = 12f

/** 要连续几个采样点都超阈值才算数 —— 单次磕碰只有一两帧，摇一摇是连着好几帧。 */
private const val SHAKE_IMPULSE_SAMPLES = 2

/** 两次触发之间的最短间隔，免得一次挥动连着开好几轮。 */
private const val SHAKE_COOLDOWN_MS = 1200L

/** 这一帧算不算"在摇"（按模长）。 */
internal fun isShakeImpulse(magnitude: Float, threshold: Float = SHAKE_IMPULSE_THRESHOLD): Boolean =
    abs(magnitude - GRAVITY) > threshold

/** 这一帧算不算"在摇"（按三轴读数）。 */
internal fun isShakeImpulse(x: Float, y: Float, z: Float, threshold: Float = SHAKE_IMPULSE_THRESHOLD): Boolean =
    isShakeImpulse(sqrt(x * x + y * y + z * z), threshold)

/**
 * 摇一摇什么时候算数：**开关打开、没在摇、也没有待记录的决定**。
 *
 * 和摇骰按钮同一套"什么时候不该响应"的规矩 —— 区别是按钮在摇动期间变成「停止」还能按，
 * 摇一摇则是干脆不响应（不然挥一下手机就把这一轮停了）。
 */
internal fun shakeArmed(shakeEnabled: Boolean, sessionRunning: Boolean, promptVisible: Boolean): Boolean =
    shakeEnabled && !sessionRunning && !promptVisible

/**
 * 摇一摇手机就摇骰子。
 *
 * [enabled] 为假时**彻底注销传感器**（不是收着事件不用）—— 关掉的开关就该一点电都不费。
 */
@Composable
fun ShakeToRollEffect(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)

    DisposableEffect(enabled, context) {
        if (!enabled) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // 设备没有加速度计（少见）：安安静静地什么都不做，别让 app 崩
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        var consecutive = 0
        var lastTriggerAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                consecutive = if (isShakeImpulse(values[0], values[1], values[2])) consecutive + 1 else 0
                if (consecutive < SHAKE_IMPULSE_SAMPLES) return

                val now = SystemClock.elapsedRealtime()
                if (now - lastTriggerAt < SHAKE_COOLDOWN_MS) return
                lastTriggerAt = now
                consecutive = 0
                currentOnShake()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
}
