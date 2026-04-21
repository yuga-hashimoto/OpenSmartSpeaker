package com.opendash.app.voice.tts

import com.google.common.truth.Truth.assertThat
import com.opendash.app.voice.tts.piper.PiperCppBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

private class RecordingTts : TextToSpeech {
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    override val currentChunk: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val streamsChunks: Boolean = false
    var spokenCount = 0
    var lastText: String? = null
    var stopCount = 0

    override suspend fun speak(text: String) {
        spokenCount++
        lastText = text
        _isSpeaking.value = true
        _isSpeaking.value = false
    }

    override fun stop() { stopCount++ }
}

private class RecordingPcmPlayer : PcmAudioPlayer {
    var playCount = 0
    var lastSamplesLength = 0
    var lastSampleRate = 0
    var stopCount = 0
    override suspend fun play(samples: ShortArray, sampleRate: Int) {
        playCount++
        lastSamplesLength = samples.size
        lastSampleRate = sampleRate
    }
    override fun stop() { stopCount++ }
}

private class FakePiperBridge(
    private val synthesizeSamples: ShortArray = ShortArray(22_050),
    private val sampleRate: Int = 22_050,
    private val loadSucceeds: Boolean = true,
    private val throwOnSynthesize: Exception? = null
) : PiperCppBridge() {
    var loadCalls = 0
    var loaded = false
    override fun loadVoice(modelPath: String, configPath: String): Boolean {
        loadCalls++
        if (!loadSucceeds) return false
        loaded = true
        return true
    }
    override fun isVoiceLoaded(): Boolean = loaded
    override fun synthesize(text: String, speakerId: Int): ShortArray {
        throwOnSynthesize?.let { throw it }
        check(loaded) { "piper voice not loaded" }
        return synthesizeSamples
    }
}

class PiperTtsProviderTest {

    @TempDir
    lateinit var tempDir: File

    private fun validVoicePaths(): PiperTtsProvider.VoicePaths {
        val model = File(tempDir, "voice.onnx").apply { writeBytes(ByteArray(2048)) }
        val config = File(tempDir, "voice.onnx.json").apply { writeBytes(ByteArray(256)) }
        return PiperTtsProvider.VoicePaths(model, config)
    }

    @Test
    fun `speak falls back when library not available`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = FakePiperBridge(),
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { false }
        )

        piper.speak("hello")

        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(fallback.lastText).isEqualTo("hello")
        assertThat(player.playCount).isEqualTo(0)
        assertThat(piper.isSpeaking.value).isFalse()
    }

    @Test
    fun `speak falls back when voice paths missing`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = FakePiperBridge(),
            voicePathProvider = { null },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello")

        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(0)
    }

    @Test
    fun `speak falls back when voice files do not exist`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val missing = PiperTtsProvider.VoicePaths(
            model = File(tempDir, "missing.onnx"),
            config = File(tempDir, "missing.onnx.json")
        )
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = FakePiperBridge(),
            voicePathProvider = { missing },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello")

        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(0)
    }

    @Test
    fun `speak happy path loads voice, synthesises, plays samples`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val bridge = FakePiperBridge(
            synthesizeSamples = ShortArray(11_025) { 0 },
            sampleRate = 22_050
        )
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = bridge,
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello world")

        assertThat(bridge.loadCalls).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(1)
        assertThat(player.lastSamplesLength).isEqualTo(11_025)
        assertThat(player.lastSampleRate).isEqualTo(22_050)
        assertThat(fallback.spokenCount).isEqualTo(0)
        assertThat(piper.isSpeaking.value).isFalse()
    }

    @Test
    fun `speak reuses loaded voice across calls`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val bridge = FakePiperBridge()
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = bridge,
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello")
        piper.speak("world")

        assertThat(bridge.loadCalls).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(2)
    }

    @Test
    fun `speak falls back when loadVoice fails`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val bridge = FakePiperBridge(loadSucceeds = false)
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = bridge,
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello")

        // loadVoice threw IllegalStateException → caught → fallback
        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(0)
    }

    @Test
    fun `speak falls back when synthesize throws`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val bridge = FakePiperBridge(throwOnSynthesize = RuntimeException("boom"))
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = bridge,
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello")

        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(0)
    }

    @Test
    fun `speak falls back when synthesize returns empty`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val bridge = FakePiperBridge(synthesizeSamples = ShortArray(0))
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = bridge,
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("hello")

        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(player.playCount).isEqualTo(0)
    }

    @Test
    fun `stop delegates to pcmPlayer and fallback`() {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = FakePiperBridge(),
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.stop()

        assertThat(player.stopCount).isEqualTo(1)
        assertThat(fallback.stopCount).isEqualTo(1)
        assertThat(piper.isSpeaking.value).isFalse()
    }

    @Test
    fun `blank text is a no-op`() = runTest {
        val fallback = RecordingTts()
        val player = RecordingPcmPlayer()
        val piper = PiperTtsProvider(
            fallback = fallback,
            bridge = FakePiperBridge(),
            voicePathProvider = { validVoicePaths() },
            pcmPlayer = player,
            availabilityCheck = { true }
        )

        piper.speak("   ")

        assertThat(fallback.spokenCount).isEqualTo(0)
        assertThat(player.playCount).isEqualTo(0)
    }
}
