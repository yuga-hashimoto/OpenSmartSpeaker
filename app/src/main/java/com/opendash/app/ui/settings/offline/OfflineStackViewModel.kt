package com.opendash.app.ui.settings.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.voice.stt.whisper.WhisperCppBridge
import com.opendash.app.voice.stt.whisper.WhisperModelCatalog
import com.opendash.app.voice.stt.whisper.WhisperModelDownloader
import com.opendash.app.voice.tts.piper.PiperCppBridge
import com.opendash.app.voice.tts.piper.PiperVoiceCatalog
import com.opendash.app.voice.tts.piper.PiperVoiceDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * P14.1 + P14.9 diagnostic VM. Surfaces per-component readiness for
 * the offline STT + TTS stacks so power users can see at a glance
 * whether "offline everything" is really ready on their device.
 *
 * Each dimension is derived independently and mapped to a simple
 * [Status] so the Compose layer doesn't duplicate gate logic — if a
 * gate changes here, the UI follows without editing copy.
 */
@HiltViewModel
class OfflineStackViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val whisperDownloader: WhisperModelDownloader,
    private val piperDownloader: PiperVoiceDownloader
) : ViewModel() {

    enum class Status { Ready, Missing }

    data class UiState(
        // STT
        val whisperLibraryLoaded: Status,
        val whisperActiveModelDownloaded: Status,
        val whisperOverall: Status,
        val whisperActiveModelName: String,
        // TTS
        val piperLibraryLoaded: Status,
        val piperActiveVoiceDownloaded: Status,
        val piperOverall: Status,
        val piperActiveVoiceName: String
    )

    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // The downloaders' state flows change on download/delete; combine
        // them so the card updates live while a download lands.
        viewModelScope.launch {
            combine(
                whisperDownloader.state,
                piperDownloader.state,
                preferences.observe(PreferenceKeys.WHISPER_ACTIVE_MODEL_ID),
                preferences.observe(PreferenceKeys.PIPER_ACTIVE_VOICE_ID)
            ) { _, _, _, _ -> computeState() }.collect { _state.value = it }
        }
    }

    /** Re-read bridge availability (cheap System.loadLibrary cache). */
    fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): UiState {
        val whisperLib = if (WhisperCppBridge.isAvailable()) Status.Ready else Status.Missing
        val whisperModel = runCatching {
            kotlinx.coroutines.runBlocking {
                preferences.observe(PreferenceKeys.WHISPER_ACTIVE_MODEL_ID).first()
            }
        }.getOrNull()
        val whisperActive = WhisperModelCatalog.all.firstOrNull { it.id == whisperModel }
            ?: WhisperModelCatalog.default
        val whisperModelStatus =
            if (whisperDownloader.isDownloaded(whisperActive)) Status.Ready else Status.Missing
        val whisperOverall = andStatus(whisperLib, whisperModelStatus)

        val piperLib = if (PiperCppBridge.isAvailable()) Status.Ready else Status.Missing
        val piperVoice = runCatching {
            kotlinx.coroutines.runBlocking {
                preferences.observe(PreferenceKeys.PIPER_ACTIVE_VOICE_ID).first()
            }
        }.getOrNull()
        val piperActive = PiperVoiceCatalog.all.firstOrNull { it.id == piperVoice }
            ?: PiperVoiceCatalog.default
        val piperVoiceStatus =
            if (piperDownloader.isDownloaded(piperActive)) Status.Ready else Status.Missing
        val piperOverall = andStatus(piperLib, piperVoiceStatus)

        return UiState(
            whisperLibraryLoaded = whisperLib,
            whisperActiveModelDownloaded = whisperModelStatus,
            whisperOverall = whisperOverall,
            whisperActiveModelName = whisperActive.displayName,
            piperLibraryLoaded = piperLib,
            piperActiveVoiceDownloaded = piperVoiceStatus,
            piperOverall = piperOverall,
            piperActiveVoiceName = piperActive.displayName
        )
    }

    private fun andStatus(a: Status, b: Status): Status =
        if (a == Status.Ready && b == Status.Ready) Status.Ready else Status.Missing
}
