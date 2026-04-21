package com.opendash.app.voice.stt.whisper

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * JVM-side sanity tests for the Kotlin wrapper. Native code is exercised
 * only on-device — the JVM test runner has no access to the arm64-v8a
 * `libwhisper_jni.so`, so `System.loadLibrary("whisper_jni")` always
 * fails here. These tests confirm that the failure path is graceful
 * (no crash, no state corruption) and that the public API refuses to
 * hand out a pretend-loaded context.
 */
class WhisperCppBridgeTest {

    @Test
    fun `tryLoadLibrary returns false on JVM host`() {
        // No libwhisper_jni.so on the JVM classpath.
        assertThat(WhisperCppBridge.tryLoadLibrary()).isFalse()
        assertThat(WhisperCppBridge.isAvailable()).isFalse()
    }

    @Test
    fun `loadModel returns false when native lib is missing`() {
        val bridge = WhisperCppBridge()
        val loaded = bridge.loadModel("/nonexistent/path/to/ggml-tiny.bin")
        assertThat(loaded).isFalse()
        assertThat(bridge.isModelLoaded()).isFalse()
        assertThat(bridge.isReady()).isFalse()
    }

    @Test
    fun `transcribe throws IllegalStateException when model is not loaded`() {
        val bridge = WhisperCppBridge()
        val samples = FloatArray(16000) { 0f }

        // kotlin.test's assertFailsWith isn't on this classpath, hand-roll.
        var caught: Throwable? = null
        try {
            bridge.transcribe(samples)
        } catch (t: IllegalStateException) {
            caught = t
        }
        assertThat(caught).isNotNull()
        assertThat(caught?.message).contains("not loaded")
    }

    @Test
    fun `unload is idempotent when model never loaded`() {
        val bridge = WhisperCppBridge()
        // Should not throw — nothing to unload.
        bridge.unload()
        bridge.unload()
        assertThat(bridge.isModelLoaded()).isFalse()
    }
}
