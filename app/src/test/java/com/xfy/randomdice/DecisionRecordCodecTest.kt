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
    fun roundTripKeepsTheRulingAndRule() {
        val records = listOf(
            DecisionRecord(
                timestampMillis = 1_700_000_200_000L,
                results = listOf(5),
                decision = "去吃火锅",
                ruling = "执行",
                ruleSummary = "单双定夺 · 看第 1 颗 · 单数=执行",
            ),
            DecisionRecord(
                timestampMillis = 1_700_000_300_000L,
                results = listOf(3, 1, 4),
                decision = "平手",
                ruling = "平手",
                ruleSummary = "三局两胜 · 单数算赢",
            ),
        )
        assertEquals(records, decodeRecords(encodeRecords(records)))
    }

    @Test
    fun roundTripKeepsTheSubject() {
        // 题目和结果是两回事：题目来自下面那格，结果来自行里那格，两个都得留下
        val record = DecisionRecord(
            timestampMillis = 1_700_000_400_000L,
            results = listOf(2, 4, 6),
            decision = "不执行",
            ruling = "不执行",
            ruleSummary = "三局两胜 · 单数算赢",
            subject = "多行\n题目\t带制表符",
        )
        assertEquals(listOf(record), decodeRecords(encodeRecords(listOf(record))))
    }

    @Test
    fun fiveFieldLinesFromBeforeTheSubjectStillRead() {
        // 加「要决定的事」之前写下的五段记录：不迁移也得原样读出来，题目当空串
        val line = "1700000000000\t5\t去吃火锅\t执行\t单双定夺 · 看第 1 颗 · 单数=执行"
        val record = parseRecord(line)
        assertEquals(
            DecisionRecord(
                timestampMillis = 1_700_000_000_000L,
                results = listOf(5),
                decision = "去吃火锅",
                ruling = "执行",
                ruleSummary = "单双定夺 · 看第 1 颗 · 单数=执行",
            ),
            record,
        )
        assertEquals("", record?.subject)
    }

    @Test
    fun oldThreeFieldLinesStillRead() {
        // 加判决之前写的记录只有三段：不迁移也得能看，缺的两段当空串
        val old = "1700000000000\t4\t中午吃面"
        val record = parseRecord(old)
        assertEquals(
            DecisionRecord(1_700_000_000_000L, listOf(4), "中午吃面"),
            record,
        )
        assertEquals("", record?.ruling)
        assertEquals("", record?.ruleSummary)
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
