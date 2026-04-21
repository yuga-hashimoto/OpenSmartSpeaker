package com.opendash.app.voice.tts.piper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 16 piper item 5: download piper voices with Range-resume support,
 * mirroring [com.opendash.app.voice.stt.whisper.WhisperModelDownloader].
 *
 * Each piper voice is a pair of files — an ONNX model + a JSON config —
 * so [download] fetches both sequentially and marks the voice ready only
 * when both land. Failures on either leave the `.downloading` temp
 * files in place so the next call can resume.
 *
 * Storage: `filesDir/piper/<filename>` for both the `.onnx` and
 * `.onnx.json` — matches the expected layout for a future
 * [PiperTtsProvider] that loads `(model, config)` off the same dir.
 */
class PiperVoiceDownloader(
    private val context: Context,
    private val openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) {

    sealed class State {
        data object NotStarted : State()
        data class Downloading(
            val progress: Float,
            val downloadedMb: Int,
            val totalMb: Int,
            val file: String
        ) : State()
        data object Ready : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.NotStarted)
    val state: StateFlow<State> = _state.asStateFlow()

    private val piperDir: File
        get() = File(context.filesDir, "piper").apply { mkdirs() }

    fun modelFile(voice: PiperVoice): File = File(piperDir, voice.modelFilename)
    fun configFile(voice: PiperVoice): File = File(piperDir, voice.configFilename)

    fun isDownloaded(voice: PiperVoice): Boolean =
        modelFile(voice).length() > 1024 && configFile(voice).length() > 0

    fun deleteVoice(voice: PiperVoice): Boolean {
        val modelRemoved = modelFile(voice).let { it.exists() && it.delete() }
        val configRemoved = configFile(voice).let { it.exists() && it.delete() }
        File(piperDir, "${voice.modelFilename}.downloading").delete()
        File(piperDir, "${voice.configFilename}.downloading").delete()
        if (modelRemoved || configRemoved) _state.value = State.NotStarted
        return modelRemoved
    }

    suspend fun download(voice: PiperVoice): State = withContext(Dispatchers.IO) {
        // Fetch config first — it's small (<5 kB) so if the pair is
        // going to fail, it fails cheaply. Only then bother with the
        // ~60 MB model download.
        val configOutcome = downloadOne(voice.configUrl, voice.configFilename, voice.modelSizeMb, label = voice.configFilename)
        if (configOutcome is State.Error) {
            _state.value = configOutcome
            return@withContext configOutcome
        }

        val modelOutcome = downloadOne(voice.modelUrl, voice.modelFilename, voice.modelSizeMb, label = voice.modelFilename)
        if (modelOutcome is State.Error) {
            _state.value = modelOutcome
            return@withContext modelOutcome
        }

        State.Ready.also { _state.value = it }
    }

    private fun downloadOne(
        url: String,
        filename: String,
        totalMbHint: Int,
        label: String
    ): State {
        val target = File(piperDir, filename)
        val temp = File(piperDir, "$filename.downloading")
        val resumeFrom = if (temp.exists()) temp.length() else 0L

        _state.value = State.Downloading(
            progress = 0f,
            downloadedMb = (resumeFrom / BYTES_PER_MB).toInt(),
            totalMb = totalMbHint,
            file = label
        )

        val conn = try {
            openConnection(URL(url))
        } catch (e: Exception) {
            Timber.w(e, "Failed to open connection for $url")
            return State.Error(e.message ?: "open connection failed")
        }

        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "open-dash/1.0")
        if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")
        conn.instanceFollowRedirects = true

        return try {
            val code = conn.responseCode
            val isPartial = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !isPartial) {
                return State.Error("HTTP $code on $label")
            }

            val appendToExisting = isPartial && resumeFrom > 0
            val remaining = conn.contentLengthLong
            val totalBytes = if (appendToExisting && remaining > 0) remaining + resumeFrom else remaining
            val totalMb = if (totalBytes > 0) (totalBytes / BYTES_PER_MB).toInt() else totalMbHint
            var downloaded = if (appendToExisting) resumeFrom else 0L

            conn.inputStream.use { input ->
                FileOutputStream(temp, appendToExisting).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                        _state.value = State.Downloading(
                            progress = progress,
                            downloadedMb = (downloaded / BYTES_PER_MB).toInt(),
                            totalMb = totalMb,
                            file = label
                        )
                    }
                }
            }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                State.Error("Failed to rename ${temp.name} to ${target.name}")
            } else {
                // downloadOne's success isn't terminal — caller wraps with pair outcome.
                State.Ready
            }
        } catch (e: Exception) {
            Timber.w(e, "piper voice file download failed: $label")
            State.Error(e.message ?: "download failed")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    companion object {
        private const val BYTES_PER_MB = 1_048_576L
    }
}
