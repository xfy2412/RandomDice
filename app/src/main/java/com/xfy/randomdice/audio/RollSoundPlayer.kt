package com.xfy.randomdice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

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
                        // 归到"提示音"这一类：跟着系统提示音音量，也尊重静音/勿扰
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
        } catch (_: Exception) {
            null
        } ?: return

        track = built
        try {
            built.write(samples, 0, samples.size)
            built.play()
        } catch (_: Exception) {
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
