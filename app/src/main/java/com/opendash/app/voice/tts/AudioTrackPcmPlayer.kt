package com.opendash.app.voice.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * Production [PcmAudioPlayer] backed by [AudioTrack]. Streams int16
 * mono PCM to the assistant audio stream so it plays on the same
 * output the Android system TTS uses — no alarms / media mixer quirks.
 *
 * One AudioTrack per play() call: Piper synthesises the entire
 * utterance up-front, and AudioTrack's STATIC mode is simpler than
 * STREAM for complete buffers. Released in finally so we never leak
 * the native handle.
 */
class AudioTrackPcmPlayer : PcmAudioPlayer {

    private val active = AtomicReference<AudioTrack?>(null)

    override suspend fun play(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        val byteLen = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(byteLen)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        // Replace any in-flight track, releasing the old one, before we
        // start. Sequential speak() calls release the prior handle here.
        active.getAndSet(track)?.let { runCatching { it.release() } }

        try {
            track.write(samples, 0, samples.size)
            track.play()
            // Drain synchronously so the caller knows when playback is done.
            // AudioTrack.MODE_STATIC plays once and stops itself; we poll
            // head position because there's no onPlaybackComplete callback
            // for static tracks.
            val totalFrames = samples.size
            while (active.get() === track && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val head = track.playbackHeadPosition
                if (head >= totalFrames) break
                // 10 ms ~ 220 samples at 22050 Hz — enough granularity
                // without busy-waiting.
                Thread.sleep(10L)
            }
        } catch (e: Exception) {
            Timber.w(e, "AudioTrack playback failed")
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
            active.compareAndSet(track, null)
        }
    }

    override fun stop() {
        active.getAndSet(null)?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
    }

    companion object {
        @Suppress("unused")
        private const val STREAM = AudioManager.STREAM_MUSIC
    }
}
