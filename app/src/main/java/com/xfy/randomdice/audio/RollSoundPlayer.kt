package com.xfy.randomdice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/** logcat 标签。Log 不会被 R8 删掉（实测最终 R8 配置里没有针对 android.util.Log 的 assume* 规则）。 */
private const val TAG = "RollSoundPlayer"

/**
 * 摇骰子的音效播放。
 *
 * 用 `MODE_STATIC` 一次性写完再播 —— 和震动一样"整条一次交出去"，不逐帧喂数据，
 * 也就没有喂不上导致的抖动。上次那条会先释放，同时最多一条。
 */
class RollSoundPlayer {

    private var track: AudioTrack? = null

    /** 播一条已经合成好的 PCM；[samples] 为空就什么都不做。 */
    fun play(samples: ShortArray, sampleRate: Int = SOUND_SAMPLE_RATE) {
        release()
        if (samples.isEmpty()) return

        val built = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // 走媒体音量（USAGE_GAME 和 USAGE_MEDIA 都落在 STREAM_MUSIC）：
                        // 音量键能直接调它，静音/免打扰也不会把它掐掉。
                        // ⚠️ 别改回 USAGE_ASSISTANCE_SONIFICATION：Android 13+ 把那条流别名到
                        // 铃声流，于是"一开免打扰就没声音"——真机踩过，查了半天。
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (error: Exception) {
            // 不能静默：这里失败的现象是「有震动、没声音、零线索」。真机上撞过一次
            // （那次是免打扰静掉了提示音流，不是这里的锅），但下次未必还是那个原因。
            Log.w(TAG, "AudioTrack 建不起来，这次摇动没声音", error)
            null
        } ?: return

        track = built
        try {
            built.write(samples, 0, samples.size)
            built.play()
        } catch (error: Exception) {
            Log.w(TAG, "AudioTrack 播放失败，这次摇动没声音", error)
            release()
        }
    }

    /** 停掉并释放。 */
    fun release() {
        track?.let { playing ->
            runCatching { playing.stop() }
            runCatching { playing.release() }
        }
        track = null
    }
}
