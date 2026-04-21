package com.opendash.app.voice.stt.whisper

/**
 * Pure-Kotlin amplitude-based VAD for [AudioRecordPcmSource].
 *
 * Real VAD (Silero ONNX) is Phase 16 item — pending a heavy ONNX
 * Runtime dep. In the meantime this gives Whisper "stop when the user
 * stops talking" instead of capturing a fixed 15-second buffer every
 * time.
 *
 * Algorithm:
 *
 *  1. Caller feeds chunks of float PCM via [feed].
 *  2. VAD tracks cumulative "heard speech" vs "continuous silence"
 *     windows measured in milliseconds.
 *  3. Until [minSpeechMs] of speech has accumulated we never declare
 *     an endpoint — prevents immediate termination on a silent first
 *     half-second while the user is thinking.
 *  4. Once speech threshold is crossed, any [silenceTrailMs] of
 *     consecutive silence terminates capture.
 *
 * "Silence" is defined as chunk RMS energy below [rmsThreshold]; we
 * compute RMS over the incoming chunk (not a rolling window) because
 * the caller's chunk size is already ~10–50 ms which matches typical
 * VAD frame sizes.
 */
class AmplitudeVad(
    private val sampleRate: Int = 16_000,
    /** Chunk RMS below this is "silence". 0.01 ≈ -40 dBFS — covers room noise on mid-tier tablets. */
    private val rmsThreshold: Float = 0.01f,
    /** Speech time required before endpoint detection kicks in. */
    private val minSpeechMs: Int = 300,
    /** Consecutive silence that terminates capture once speech has been heard. */
    private val silenceTrailMs: Int = 800
) {
    enum class Decision { Listening, EndpointDetected }

    private var speechMs: Int = 0
    private var silenceMs: Int = 0

    /**
     * Feed a chunk of float samples. [len] is the valid prefix length —
     * matches AudioRecord's read() return value. Returns
     * [Decision.EndpointDetected] the first time minSpeech + silenceTrail
     * are both satisfied.
     */
    fun feed(chunk: FloatArray, len: Int): Decision {
        if (len <= 0) return Decision.Listening
        val rms = rms(chunk, len)
        val chunkMs = (len * 1000L / sampleRate).toInt()

        if (rms >= rmsThreshold) {
            // Speech frame — extend speech counter, reset trailing silence.
            speechMs += chunkMs
            silenceMs = 0
        } else {
            // Silent frame — only count toward trailing silence once we've
            // heard enough real speech to call this an end-of-utterance.
            if (speechMs >= minSpeechMs) {
                silenceMs += chunkMs
            }
        }

        return if (speechMs >= minSpeechMs && silenceMs >= silenceTrailMs) {
            Decision.EndpointDetected
        } else {
            Decision.Listening
        }
    }

    /**
     * Reset internal state so the next capture session starts fresh.
     * `AudioRecordPcmSource.capture()` instantiates a new VAD per call,
     * so this is only useful for long-lived test harnesses.
     */
    fun reset() {
        speechMs = 0
        silenceMs = 0
    }

    private fun rms(samples: FloatArray, len: Int): Float {
        if (len <= 0) return 0f
        var sum = 0.0
        for (i in 0 until len) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return kotlin.math.sqrt(sum / len).toFloat()
    }
}
