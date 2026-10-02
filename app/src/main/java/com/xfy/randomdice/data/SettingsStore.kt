package com.xfy.randomdice.data

import android.content.Context
import java.io.File

/**
 * 设置的存储：应用私有目录里的一个 UTF-8 文本文件，只有一行。
 *
 * 和 [DecisionStore] 一样刻意保持"薄" —— 编解码在 [DiceSettings] 那边是纯 Kotlin（有单测），
 * 这里只管读写。
 */
class SettingsStore(private val file: File) {

    /** 读；文件不存在或读坏了都给默认设置，不让存储问题把 app 弄崩。 */
    fun load(): DiceSettings = try {
        if (file.exists()) decodeSettings(file.readText(Charsets.UTF_8)) else DiceSettings()
    } catch (_: Exception) {
        DiceSettings()
    }

    /** 全量覆写。 */
    fun save(settings: DiceSettings) {
        file.parentFile?.mkdirs()
        file.writeText(encodeSettings(settings), Charsets.UTF_8)
    }

    companion object {
        private const val FILE_NAME = "settings.txt"

        fun from(context: Context): SettingsStore =
            SettingsStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
