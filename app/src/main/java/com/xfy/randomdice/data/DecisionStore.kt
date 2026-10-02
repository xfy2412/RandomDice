package com.xfy.randomdice.data

import android.content.Context
import java.io.File

/**
 * 决策记录的存储：应用私有目录里的一个 UTF-8 文本文件，一行一条。
 *
 * 刻意保持"薄"：编解码在 [DecisionRecord] 那边是纯 Kotlin（有单测），
 * 这里只负责读写文件，所以不需要 Room / DataStore 这类依赖。
 *
 * ⚠️ 文件很小（几条到几百条），直接在主线程读写；真到几千条再考虑挪线程。
 */
class DecisionStore(private val file: File) {

    /** 读；文件不存在或读坏了都返回空表，不让存储问题把 app 弄崩。 */
    fun load(): List<DecisionRecord> = try {
        if (file.exists()) decodeRecords(file.readText(Charsets.UTF_8)) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** 全量覆写。 */
    fun save(records: List<DecisionRecord>) {
        file.parentFile?.mkdirs()
        file.writeText(encodeRecords(records), Charsets.UTF_8)
    }

    companion object {
        private const val FILE_NAME = "decisions.txt"

        fun from(context: Context): DecisionStore =
            DecisionStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
