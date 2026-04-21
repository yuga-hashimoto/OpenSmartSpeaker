package com.opendash.app.voice.tts.piper

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class PiperVoiceDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var downloader: PiperVoiceDownloader

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("piper-dl-").toFile()
        context = mockk()
        every { context.filesDir } returns tempDir
        downloader = PiperVoiceDownloader(context)
    }

    @AfterEach
    fun teardown() {
        runCatching { server.shutdown() }
        tempDir.deleteRecursively()
    }

    private fun voice(modelName: String = "voice.onnx", configName: String = "voice.onnx.json") = PiperVoice(
        id = "test",
        displayName = "Test Voice",
        languageTag = "en-US",
        modelUrl = server.url("/m/$modelName").toString(),
        configUrl = server.url("/c/$configName").toString(),
        modelFilename = modelName,
        configFilename = configName,
        modelSizeMb = 1
    )

    private fun bodyOfSize(bytes: Int): Buffer = Buffer().apply {
        repeat(bytes) { writeByte(it and 0xFF) }
    }

    @Test
    fun `download fetches config then model and reaches Ready`() = runTest {
        val config = bodyOfSize(512)
        val model = bodyOfSize(4096)
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", "512").setBody(config)
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", "4096").setBody(model)
        )

        val v = voice()
        val state = downloader.download(v)

        assertThat(state).isEqualTo(PiperVoiceDownloader.State.Ready)
        assertThat(downloader.modelFile(v).length()).isEqualTo(4096L)
        assertThat(downloader.configFile(v).length()).isEqualTo(512L)
        assertThat(downloader.isDownloaded(v)).isTrue()

        // Config first, then model — MockWebServer preserves order.
        val first = server.takeRequest()
        assertThat(first.path).contains("/c/")
        val second = server.takeRequest()
        assertThat(second.path).contains("/m/")
    }

    @Test
    fun `download stops on config failure without fetching model`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val state = downloader.download(voice())

        assertThat(state).isInstanceOf(PiperVoiceDownloader.State.Error::class.java)
        // Only the config request was attempted.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `download stops on model failure after config success`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", "100").setBody(bodyOfSize(100))
        )
        server.enqueue(MockResponse().setResponseCode(500))

        val v = voice()
        val state = downloader.download(v)

        assertThat(state).isInstanceOf(PiperVoiceDownloader.State.Error::class.java)
        // Config landed; model didn't.
        assertThat(downloader.configFile(v).length()).isEqualTo(100L)
        assertThat(downloader.modelFile(v).exists()).isFalse()
    }

    @Test
    fun `deleteVoice removes both files`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", "100").setBody(bodyOfSize(100))
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", "2048").setBody(bodyOfSize(2048))
        )
        val v = voice()
        downloader.download(v)
        assertThat(downloader.isDownloaded(v)).isTrue()

        assertThat(downloader.deleteVoice(v)).isTrue()
        assertThat(downloader.isDownloaded(v)).isFalse()
        assertThat(downloader.state.value).isEqualTo(PiperVoiceDownloader.State.NotStarted)
    }

    @Test
    fun `config resume sends Range header and appends on 206`() = runTest {
        val v = voice()
        val piperDir = File(tempDir, "piper").apply { mkdirs() }
        File(piperDir, "${v.configFilename}.downloading").writeBytes(ByteArray(300))

        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Length", "200")
                .setHeader("Content-Range", "bytes 300-499/500")
                .setBody(bodyOfSize(200))
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Length", "1024").setBody(bodyOfSize(1024))
        )

        downloader.download(v)

        val configRequest = server.takeRequest()
        assertThat(configRequest.getHeader("Range")).isEqualTo("bytes=300-")
        assertThat(downloader.configFile(v).length()).isEqualTo(500L)
    }
}
