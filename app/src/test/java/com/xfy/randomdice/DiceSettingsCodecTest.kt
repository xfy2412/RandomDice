package com.xfy.randomdice

import com.xfy.randomdice.audio.SoundTimbre
import com.xfy.randomdice.data.DiceSettings
import com.xfy.randomdice.data.decodeSettings
import com.xfy.randomdice.data.encodeSettings
import com.xfy.randomdice.dice.JudgmentBasis
import com.xfy.randomdice.dice.JudgmentPreset
import com.xfy.randomdice.dice.JudgmentRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置的编解码测试（纯 JVM）。
 *
 * 底线：**设置文件坏了也要能起来** —— 任何一段读不出来都退回默认值，绝不抛异常。
 */
class DiceSettingsCodecTest {

    @Test
    fun roundTripKeepsEverything() {
        val settings = DiceSettings(
            totalRolls = 137,
            rule = JudgmentRule(
                preset = JudgmentPreset.PickOne,
                basis = JudgmentBasis.Sum,
                oddIsFirstOutcome = false,
                optionCount = 5,
            ),
        )
        assertEquals(settings, decodeSettings(encodeSettings(settings)))
    }

    @Test
    fun roundTripKeepsTheNewRuleParams() {
        // 阈值 / 点名点数 / 至少几颗 也得跟着设置一起活下来，否则重启就丢
        val settings = DiceSettings(
            totalRolls = 7,
            rule = JudgmentRule(
                preset = JudgmentPreset.RollCall,
                basis = JudgmentBasis.Sum,
                oddIsFirstOutcome = false,
                optionCount = 5,
                threshold = 20,
                targetValue = 3,
                targetCount = 2,
            ),
        )
        assertEquals(settings, decodeSettings(encodeSettings(settings)))
    }

    @Test
    fun settingsFromBeforeTheNewParamsStillRead() {
        // 只有五段的老设置：新参数落到默认值，不能整行作废
        val settings = decodeSettings("25\tBestOf\tSum\t1\t3")
        val defaults = JudgmentRule()
        assertEquals(25, settings.totalRolls)
        assertEquals(JudgmentPreset.BestOf, settings.rule.preset)
        assertEquals(JudgmentBasis.Sum, settings.rule.basis)
        assertEquals(3, settings.rule.optionCount)
        assertEquals(defaults.threshold, settings.rule.threshold)
        assertEquals(defaults.targetValue, settings.rule.targetValue)
        assertEquals(defaults.targetCount, settings.rule.targetCount)
    }

    @Test
    fun defaultSettingsAreTheCommonCase() {
        val settings = decodeSettings(encodeSettings(DiceSettings()))
        assertEquals(0, settings.totalRolls)
        assertEquals(JudgmentPreset.BestOf, settings.rule.preset)
        assertEquals(JudgmentBasis.FirstDie, settings.rule.basis)
        assertEquals(true, settings.rule.oddIsFirstOutcome)
    }

    @Test
    fun emptyFileGivesDefaults() {
        assertEquals(DiceSettings(), decodeSettings(""))
        assertEquals(DiceSettings(), decodeSettings("\n\n"))
    }

    @Test
    fun garbageNeverThrows() {
        val junk = listOf(
            "这不是设置",
            "abc\tdef\tghi\tjkl\tmno",
            "-5\tOddEven",
            "\t\t\t\t",
            "12\t不存在的预设\t不存在的基准\t2\t99",
        )
        for (text in junk) {
            val settings = decodeSettings(text)
            // 只要还认得出来的部分就得留着，认不出来的退回默认
            assertTrue("累计次数不能是负数：$text", settings.totalRolls >= 0)
            assertTrue("选项个数必须落在允许范围内：$text", settings.rule.optionCount in 2..6)
            assertTrue("阈值必须落在允许范围内：$text", settings.rule.threshold in 1..72)
            assertTrue("点名点数必须在 1..6：$text", settings.rule.targetValue in 1..6)
            assertTrue("「至少几颗」必须落在允许范围内：$text", settings.rule.targetCount in 1..6)
        }
    }

    @Test
    fun counterSurvivesEvenIfTheRulePartIsBroken() {
        // 「摇过的次数」是最不该丢的 —— 规则那段烂了也得把数字读回来
        assertEquals(42, decodeSettings("42\t乱码\t乱码\t乱码\t乱码").totalRolls)
    }

    @Test
    fun vibrationAndTimbreRoundTrip() {
        val settings = DiceSettings(
            totalRolls = 1,
            vibrationEnabled = false,
            timbre = SoundTimbre.Ceramic,
        )
        assertEquals(settings, decodeSettings(encodeSettings(settings)))
    }

    @Test
    fun olderSettingsKeepVibrationOnAndDefaultToPlastic() {
        // 老文件（只有 9 段）→ 震动保持默认"开"（不改变既有行为）、音色默认塑料
        val old = decodeSettings("25\tBestOf\tSum\t1\t3\t9\t6\t1\t1")
        assertEquals(true, old.vibrationEnabled)
        assertEquals(SoundTimbre.Plastic, old.timbre)
        // 乱码的音色名也退回默认
        assertEquals(SoundTimbre.Plastic, decodeSettings("25\tBestOf\tSum\t1\t3\t9\t6\t1\t1\t1\t乱码").timbre)
    }

    @Test
    fun shakeSwitchRoundTrips() {
        val on = DiceSettings(totalRolls = 3, shakeEnabled = true)
        assertEquals(on, decodeSettings(encodeSettings(on)))
        assertEquals(false, decodeSettings(encodeSettings(DiceSettings())).shakeEnabled)
    }

    @Test
    fun shakeSwitchDefaultsToOffWhenMissing() {
        // 老设置文件没有这一段 → 关。这是个会自己触发摇骰的开关，缺字段就必须保守。
        assertEquals(false, decodeSettings("25\tBestOf\tSum\t1\t3\t9\t6\t1").shakeEnabled)
        assertEquals(false, decodeSettings("25\tBestOf\tSum\t1\t3\t9\t6\t1\t乱码").shakeEnabled)
        assertEquals(true, decodeSettings("25\tBestOf\tSum\t1\t3\t9\t6\t1\t1").shakeEnabled)
    }

    @Test
    fun optionCountIsClampedIntoRange() {
        // 越界的数字按边界收，不整段丢掉 —— 至少方向是对的
        assertEquals(6, decodeSettings("0\tPickOne\tSum\t1\t999").rule.optionCount)
        assertEquals(2, decodeSettings("0\tPickOne\tSum\t1\t0").rule.optionCount)
        // 根本不是数字才退回默认的 3
        assertEquals(3, decodeSettings("0\tPickOne\tSum\t1\t三个").rule.optionCount)
    }
}
