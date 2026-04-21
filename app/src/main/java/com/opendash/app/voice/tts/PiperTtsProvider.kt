package com.opendash.app.voice.tts

import com.opendash.app.voice.tts.piper.PiperCppBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * On-device neural TTS (VITS) via piper-cpp JNI. Routes active when all
 * three gates are open:
 *
 *  - [availabilityCheck] — `libpiper_jni.so` loaded
 *  - [voicePathProvider] returns a pair of `.onnx` + `.onnx.json` files
 *    that both exist on disk (downloaded via PiperVoiceDownloader)
 *  - [bridge] successfully loads the voice
 *
 * When any gate fails the provider transparently delegates to
 * [fallback] (typically Android system TTS) and logs a warning so the
 * user always hears *something* — better a text-to-speech fallback
 * than silent failure.
 *
 * Output is int16 mono PCM at the voice's native sample rate; we hand
 * it to [pcmPlayer] for playback. AudioTrack is the production player,
 * but the interface exists so JVM tests don't need a real audio
 * subsystem.
 *
 * Ref: rhasspy/piper.
 */
class PiperTtsProvider(
    private val fallback: TextToSpeech,
    private val bridge: PiperCppBridge = PiperCppBridge(),
    private val voicePathProvider: () -> VoicePaths? = { null },
    private val pcmPlayer: PcmAudioPlayer = PcmAudioPlayer.NoOp,
    private val availabilityCheck: () -> Boolean = { PiperCppBridge.isAvailable() }
) : TextToSpeech {

    data class VoicePaths(val model: File, val config: File) {
        fun bothExist(): Boolean =
            model.exists() && model.length() > 1024 &&
                config.exists() && config.length() > 0
    }

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Forward the fallback's chunk flow so the karaoke UI keeps working. */
    override val currentChunk: StateFlow<String> = fallback.currentChunk
    override val streamsChunks: Boolean = fallback.streamsChunks

    override suspend fun speak(text: String) {
        if (text.isBlank()) return

        val paths = voicePathProvider()
        val canUsePiper = availabilityCheck() && paths != null && paths.bothExist()

        if (!canUsePiper) {
            Timber.w(
                "Piper TTS unavailable (lib=${availabilityCheck()}, voice=${paths != null}, files=${paths?.bothExist()}) — falling back to Android TTS"
            )
            _isSpeaking.value = true
            try {
                fallback.speak(text)
            } finally {
                _isSpeaking.value = false
            }
            return
        }

        // Happy path: synthesise via the native bridge then play through
        // the injected PCM player. All heavy lifting runs on the IO
        // dispatcher so we don't pin the caller's coroutine context.
        _isSpeaking.value = true
        try {
            withContext(Dispatchers.IO) {
                ensureVoiceLoaded(paths!!)
                val samples = bridge.synthesize(text, speakerId = 0)
                if (samples.isEmpty()) {
                    Timber.w("Piper synthesize returned 0 samples for '${text.take(40)}'")
                    fallback.speak(text)
                    return@withContext
                }
                val sr = bridge.sampleRate().takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE
                pcmPlayer.play(samples = samples, sampleRate = sr)
            }
        } catch (e: Exception) {
            Timber.w(e, "Piper synthesize / play failed — falling back")
            fallback.speak(text)
        } finally {
            _isSpeaking.value = false
        }
    }

    override fun stop() {
        runCatching { pcmPlayer.stop() }
        fallback.stop()
        _isSpeaking.value = false
    }

    private fun ensureVoiceLoaded(paths: VoicePaths) {
        if (!bridge.isVoiceLoaded()) {
            val loaded = bridge.loadVoice(
                modelPath = paths.model.absolutePath,
                configPath = paths.config.absolutePath
            )
            check(loaded) { "Piper loadVoice failed for ${paths.model.name}" }
        }
    }

    companion object {
        /** Piper's recommended VITS voices ship at 22.05 kHz. */
        const val DEFAULT_SAMPLE_RATE = 22_050
    }
}

/**
 * Plays int16 mono PCM. Production binding is [AudioTrackPcmPlayer];
 * tests inject a fake that records the samples / sample rate without
 * touching AudioTrack.
 */
interface PcmAudioPlayer {
    suspend fun play(samples: ShortArray, sampleRate: Int)
    fun stop()

    /** Default binding used until the full Piper route lights up. */
    object NoOp : PcmAudioPlayer {
        override suspend fun play(samples: ShortArray, sampleRate: Int) {}
        override fun stop() {}
    }
}
