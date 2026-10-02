package com.xfy.randomdice

import com.xfy.randomdice.ui.SHAKE_IMPULSE_THRESHOLD
import com.xfy.randomdice.ui.isShakeImpulse
import com.xfy.randomdice.ui.shakeArmed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 摇一摇的判定（纯 JVM）。
 *
 * 两条底线：
 * 1. **关掉就是关掉**：开关没开、正在摇、或者还有没记的决定 —— 任何一条成立都不许响应；
 * 2. 安静放着、拿起来的小晃不能误触发，真挥起来才响。
 */
class ShakeToRollTest {

    @Test
    fun stillPhoneIsNotAShake() {
        assertFalse("平放着不该算摇", isShakeImpulse(9.81f))
        assertFalse("拿起来的小晃不该算摇", isShakeImpulse(10.5f))
        assertFalse(isShakeImpulse(8.0f))
    }

    @Test
    fun aRealShakeCrossesTheThreshold() {
        assertTrue(isShakeImpulse(9.81f + SHAKE_IMPULSE_THRESHOLD + 0.5f))
        assertTrue("反向甩一样算", isShakeImpulse(-25f))
    }

    @Test
    fun axisReadingsAreCombined() {
        assertTrue(isShakeImpulse(25f, 0f, 0f))
        // 三轴各 5：合成模长 8.66，离重力很近，不算摇
        assertFalse(isShakeImpulse(5f, 5f, 5f))
    }

    @Test
    fun armedOnlyWhenTheSwitchIsOnAndNothingIsPending() {
        assertTrue(shakeArmed(shakeEnabled = true, sessionRunning = false, promptVisible = false))
        assertFalse(
            "默认关闭时不许响应",
            shakeArmed(shakeEnabled = false, sessionRunning = false, promptVisible = false),
        )
        assertFalse(
            "正在摇的时候不许响应",
            shakeArmed(shakeEnabled = true, sessionRunning = true, promptVisible = false),
        )
        assertFalse(
            "还有没记的决定时不许响应",
            shakeArmed(shakeEnabled = true, sessionRunning = false, promptVisible = true),
        )
        assertFalse(shakeArmed(shakeEnabled = true, sessionRunning = true, promptVisible = true))
    }
}
