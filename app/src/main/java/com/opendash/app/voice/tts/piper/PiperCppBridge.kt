package com.opendash.app.voice.tts.piper

import timber.log.Timber

/**
 * JNI bridge to the piper native library (P14.9).
 *
 * Mirrors [com.opendash.app.voice.stt.whisper.WhisperCppBridge]:
 *
 *  - `System.loadLibrary("piper_jni")` is attempted lazily via
 *    [tryLoadLibrary]. Today this always returns false because the
 *    CMake wire-up for piper is deferred (see
 *    `docs/phase-16-native-plan.md` item 2 under the Piper checklist —
 *    piper's transitive deps on piper-phonemize + espeak-ng + ONNX
 *    Runtime are too heavy to add behind a simple guard).
 *  - Once `libpiper_jni.so` ships, [isAvailable] flips to true and
 *    [TtsModule] can route through a real [PiperTtsProvider] swap
 *    instead of the current fallback-to-AndroidTts placeholder.
 *
 * Voice lifecycle: [loadVoice] → [synthesize]* → [unload]. Voices are
 * distributed as paired `.onnx` model + `.onnx.json` config files (see
 * [PiperVoiceCatalog]); both paths are passed to the JNI so piper can
 * resolve sample rate + speaker map.
 *
 * Output contract: [synthesize] returns int16 mono PCM at the voice's
 * native sample rate (22050 Hz for Piper's recommended VITS voices).
 * Callers marshal that into an Android `AudioTrack` or write to a WAV
 * file — the bridge stays dumb.
 */
open class PiperCppBridge {

    private var voiceHandle: Long = 0L
    private var loadedSampleRate: Int = 0

    open fun loadVoice(modelPath: String, configPath: String): Boolean {
        if (!tryLoadLibrary()) {
            Timber.e("Cannot load piper voice: native library not available")
            return false
        }
        return try {
            voiceHandle = nativeLoadVoice(modelPath, configPath)
            if (voiceHandle != 0L) {
                loadedSampleRate = nativeGetSampleRate(voiceHandle)
            }
            voiceHandle != 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to load piper voice: $modelPath")
            false
        }
    }

    /**
     * Synthesize [text] using the loaded voice. Returns int16 mono PCM
     * at [sampleRate] Hz, or an empty array if the bridge isn't ready.
     */
    open fun synthesize(text: String, speakerId: Int = 0): ShortArray {
        check(voiceHandle != 0L) { "piper voice not loaded" }
        return nativeSynthesize(voiceHandle, text, speakerId)
    }

    fun sampleRate(): Int = loadedSampleRate

    fun unload() {
        if (voiceHandle != 0L) {
            nativeUnloadVoice(voiceHandle)
            voiceHandle = 0L
            loadedSampleRate = 0
        }
    }

    open fun isVoiceLoaded(): Boolean = voiceHandle != 0L

    private external fun nativeLoadVoice(modelPath: String, configPath: String): Long
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeSynthesize(handle: Long, text: String, speakerId: Int): ShortArray
    private external fun nativeUnloadVoice(handle: Long)

    companion object {
        @Volatile
        private var libraryLoaded = false

        fun tryLoadLibrary(): Boolean {
            if (libraryLoaded) return true
            return try {
                System.loadLibrary("piper_jni")
                libraryLoaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("piper_jni native library not available: ${e.message}")
                false
            }
        }

        fun isAvailable(): Boolean = tryLoadLibrary()
    }
}
