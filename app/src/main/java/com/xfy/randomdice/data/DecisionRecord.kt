package com.xfy.randomdice.data

/**
 * 一次「用骰子做决定」的记录：什么时候摇的、摇出了什么、当时决定了什么。
 *
 * 这就是全部数据模型 —— 没有引 Room / DataStore / JSON 库，纯 Kotlin，好单测。
 */
data class DecisionRecord(
    val timestampMillis: Long,
    val results: List<Int>,
    val decision: String,
)

/*
 * 存储格式：**一行一条**，字段用制表符分隔
 *
 *     <时间戳毫秒>\t<点数,点数,...>\t<决策文本>
 *
 * 决策文本里的 `\`、制表符、换行都会转义（见 [escape]），所以文本里怎么换行
 * 都不会破坏「一行一条」；解码时坏行直接跳过，不让一条烂数据毁掉整个历史。
 */
private const val FIELD_SEPARATOR = '\t'
private const val RESULT_SEPARATOR = ','

/** 把记录列表编码成文件内容。 */
fun encodeRecords(records: List<DecisionRecord>): String =
    records.joinToString(separator = "\n", postfix = if (records.isEmpty()) "" else "\n") { encodeRecord(it) }

/** 把单条记录编码成一行。 */
fun encodeRecord(record: DecisionRecord): String = buildString {
    append(record.timestampMillis)
    append(FIELD_SEPARATOR)
    append(record.results.joinToString(RESULT_SEPARATOR.toString()))
    append(FIELD_SEPARATOR)
    append(escape(record.decision))
}

/** 解析文件内容；坏行跳过。 */
fun decodeRecords(text: String): List<DecisionRecord> =
    text.lineSequence().mapNotNull { parseRecord(it) }.toList()

/** 解析一行；不是合法记录就返回 null。 */
fun parseRecord(line: String): DecisionRecord? {
    if (line.isBlank()) return null
    val parts = line.split(FIELD_SEPARATOR)
    if (parts.size < 3) return null
    val timestamp = parts[0].toLongOrNull() ?: return null
    val results = parts[1].split(RESULT_SEPARATOR).mapNotNull { it.trim().toIntOrNull() }
    // 一条记录至少得有一次点数 —— 没点数的"记录"是脏数据，直接跳过
    if (results.isEmpty()) return null
    // 文本已被转义，理论上只剩三段；这里把多余段拼回去，尽量不丢内容
    val decision = unescape(parts.drop(2).joinToString(FIELD_SEPARATOR.toString()))
    return DecisionRecord(timestampMillis = timestamp, results = results, decision = decision)
}

private fun escape(text: String): String = buildString {
    for (ch in text) {
        when (ch) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }
}

private fun unescape(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '\\' && i + 1 < text.length) {
            when (val next = text[i + 1]) {
                '\\' -> { append('\\'); i += 2 }
                't' -> { append('\t'); i += 2 }
                'n' -> { append('\n'); i += 2 }
                'r' -> { append('\r'); i += 2 }
                // 未知转义：把后一个字符原样留下，别吞掉用户的内容
                else -> { append(next); i += 2 }
            }
        } else {
            append(ch)
            i++
        }
    }
}
