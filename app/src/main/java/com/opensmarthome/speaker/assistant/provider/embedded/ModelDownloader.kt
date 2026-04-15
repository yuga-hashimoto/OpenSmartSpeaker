package com.opensmarthome.speaker.assistant.provider.embedded

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

sealed class ModelDownloadState {
    data object NotStarted : ModelDownloadState()
    data object Checking : ModelDownloadState()
    data class Downloading(val progress: Float, val downloadedMb: Int, val totalMb: Int) : ModelDownloadState()
    data object Ready : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

class ModelDownloader(private val context: Context) {

    companion object {
        // Gemma 4 E2B IT GGUF (Q4_K_M quantization) from unsloth on HuggingFace
        // Public repo, no authentication required
        private const val MODEL_REPO = "unsloth/gemma-4-E2B-it-GGUF"
        private const val MODEL_FILE = "gemma-4-E2B-it-Q4_K_M.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/$MODEL_REPO/resolve/main/$MODEL_FILE"
        private const val MODEL_FILENAME = "gemma-4-e2b.gguf"
        private const val MIN_STORAGE_MB = 2000L
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotStarted)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getModelPath(): String? {
        val modelFile = File(modelsDir, MODEL_FILENAME)
        if (modelFile.exists() && modelFile.length() > 1024) return modelFile.absolutePath
        // Also check for any .gguf file
        val anyGguf = modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" && it.length() > 1024 }
        return anyGguf?.absolutePath
    }

    fun isModelDownloaded(): Boolean = getModelPath() != null

    suspend fun ensureModelAvailable() {
        if (isModelDownloaded()) {
            _state.value = ModelDownloadState.Ready
            return
        }

        _state.value = ModelDownloadState.Checking

        if (!hasEnoughStorage()) {
            _state.value = ModelDownloadState.Error(
                "Not enough storage. Need at least ${MIN_STORAGE_MB}MB free."
            )
            return
        }

        downloadModel()
    }

    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, MODEL_FILENAME)
        val tempFile = File(modelsDir, "$MODEL_FILENAME.downloading")

        try {
            _state.value = ModelDownloadState.Downloading(0f, 0, 0)
            Timber.d("Starting model download: $MODEL_URL")

            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "open-smart-speaker/1.0")

            // Follow redirects (HuggingFace uses redirects to CDN)
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Timber.e("Download failed: HTTP $responseCode - $errorBody")
                _state.value = ModelDownloadState.Error("Download failed: HTTP $responseCode")
                return@withContext
            }

            val totalBytes = connection.contentLengthLong
            val totalMb = if (totalBytes > 0) (totalBytes / 1_048_576).toInt() else 0
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(65536) // 64KB buffer for faster download
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        val downloadedMb = (downloadedBytes / 1_048_576).toInt()
                        _state.value = ModelDownloadState.Downloading(progress, downloadedMb, totalMb)
                    }
                }
            }

            if (tempFile.renameTo(targetFile)) {
                Timber.d("Model downloaded: ${targetFile.absolutePath} (${targetFile.length() / 1_048_576}MB)")
                _state.value = ModelDownloadState.Ready
            } else {
                _state.value = ModelDownloadState.Error("Failed to save model file")
            }
        } catch (e: Exception) {
            Timber.e(e, "Model download failed")
            tempFile.delete()
            _state.value = ModelDownloadState.Error("Download failed: ${e.message}")
        }
    }

    private fun hasEnoughStorage(): Boolean {
        val freeSpaceMb = modelsDir.usableSpace / 1_048_576
        return freeSpaceMb >= MIN_STORAGE_MB
    }

    fun deleteModel() {
        modelsDir.listFiles()?.forEach { it.delete() }
        _state.value = ModelDownloadState.NotStarted
    }
}
