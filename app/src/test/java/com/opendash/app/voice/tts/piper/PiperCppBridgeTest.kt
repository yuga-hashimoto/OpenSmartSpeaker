package com.opendash.app.voice.tts.piper

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PiperCppBridgeTest {

    @Test
    fun `tryLoadLibrary returns false on JVM host`() {
        assertThat(PiperCppBridge.tryLoadLibrary()).isFalse()
        assertThat(PiperCppBridge.isAvailable()).isFalse()
    }

    @Test
    fun `loadVoice returns false when native lib is missing`() {
        val bridge = PiperCppBridge()
        val ok = bridge.loadVoice(
            modelPath = "/nonexistent/voice.onnx",
            configPath = "/nonexistent/voice.onnx.json"
        )
        assertThat(ok).isFalse()
        assertThat(bridge.isVoiceLoaded()).isFalse()
        assertThat(bridge.sampleRate()).isEqualTo(0)
    }

    @Test
    fun `synthesize throws IllegalStateException when voice not loaded`() {
        val bridge = PiperCppBridge()
        var caught: Throwable? = null
        try {
            bridge.synthesize("hello")
        } catch (t: IllegalStateException) {
            caught = t
        }
        assertThat(caught).isNotNull()
        assertThat(caught?.message).contains("not loaded")
    }

    @Test
    fun `unload is idempotent when voice never loaded`() {
        val bridge = PiperCppBridge()
        bridge.unload()
        bridge.unload()
        assertThat(bridge.isVoiceLoaded()).isFalse()
    }
}
