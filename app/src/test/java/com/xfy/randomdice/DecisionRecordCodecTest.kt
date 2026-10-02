package com.xfy.randomdice

import com.xfy.randomdice.data.DecisionRecord
import com.xfy.randomdice.data.decodeRecords
import com.xfy.randomdice.data.encodeRecord
import com.xfy.randomdice.data.encodeRecords
import com.xfy.randomdice.data.parseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 存储格式的往返测试（纯 JVM）。
 *
 * 底线：**用户写进「这次决定了什么」的任何字符，都要能原样读回来**，
 * 而且一条烂数据不能把整个历史带崩。
 */
class DecisionRecordCodecTest {

    @Test
    fun roundTripKeepsEverything() {
        val records = listOf(
            DecisionRecord(1_700_000_000_000L, listOf(4), "中午吃面"),
            DecisionRecord(1_700_000_100_000L, listOf(2, 6, 1, 5, 3), "先做 A 再做 B"),
        )
        assertEquals(records, decodeRecords(encodeRecords(records)))
    }

    @Test
    fun decisionTextWithSpecialCharactersSurvives() {
        val tricky = "第一行\t带制表符\n第二行 带反斜杠 C:\\Users\\xfy\n还有 emoji 🎲 和引号 \"'"
        val record = DecisionRecord(42L, listOf(1, 2, 3), tricky)
        val decoded = decodeRecords(encodeRecords(listOf(record)))
        assertEquals(listOf(record), decoded)
    }

    @Test
    fun encodedTextIsAlwaysOneLinePerRecord() {
        val record = DecisionRecord(7L, listOf(5), "多行\n文本\n也不该多出一行")
        val line = encodeRecord(record)
        assertEquals("编码后必须只有一行", 1, line.lines().size)
        assertEquals(record, parseRecord(line))
    }

    @Test
    fun malformedLinesAreSkipped() {
        val good = encodeRecord(DecisionRecord(1000L, listOf(6), "留得住的"))
        val text = listOf(
            "",
            "这不是记录",
            "abc\t1,2\t时间戳不是数字",
            "123\t1,2", // 只有两段：缺决策文本
            "123\t\t没有点数", // 段数够，但没有点数 —— 脏数据
            good,
        ).joinToString("\n")
        assertEquals(listOf(DecisionRecord(1000L, listOf(6), "留得住的")), decodeRecords(text))
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertEquals(emptyList<DecisionRecord>(), decodeRecords(""))
        assertEquals("", encodeRecords(emptyList()))
    }

    @Test
    fun resultsKeepTheirOrder() {
        val record = DecisionRecord(9L, listOf(3, 1, 4, 1, 5, 9, 2, 6), "顺序不能乱")
        val decoded = decodeRecords(encodeRecords(listOf(record))).single()
        assertEquals(listOf(3, 1, 4, 1, 5, 9, 2, 6), decoded.results)
    }

    @Test
    fun blankLineIsNotARecord() {
        assertNull(parseRecord("   "))
        assertNull(parseRecord(""))
    }
}
