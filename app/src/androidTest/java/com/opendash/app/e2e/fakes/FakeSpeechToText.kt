package com.opendash.app.e2e.fakes

import com.opendash.app.voice.stt.SpeechToText
import com.opendash.app.voice.stt.SttResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * Test double for [SpeechToText] that lets a test programmatically push
 * [SttResult]s into the pipeline without needing a real microphone or
 * SpeechRecognizer service.
 *
 * Usage in a test:
 * ```
 * @Inject lateinit var fakeStt: FakeSpeechToText
 * fakeStt.queue(SttResult.Final("set timer for 5 minutes"))
 * voicePipeline.startListening()  // collects from this fake
 * ```
 *
 * Each call to [startListening] returns a fresh Flow backed by a fresh
 * channel so tests can re-enter listening mode multiple times without
 * cross-talk between iterations.
 */
class FakeSpeechToText : SpeechToText {

    private var channel: Channel<SttResult> = Channel(Channel.UNLIMITED)
    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /**
     * Push an [SttResult] that the next (or current) collector will receive.
     * Calls before [startListening] are buffered until a collector subscribes.
     */
    fun queue(result: SttResult) {
        channel.trySend(result)
        // A `Final` or `Error` result terminates the current listening session
        // — close the channel so the collector sees the Flow complete and
        // VoicePipeline transitions out of Listening.
        if (result is SttResult.Final || result is SttResult.Error) {
            channel.close()
        }
    }

    override fun startListening(): Flow<SttResult> {
        // Reset for a new listening session if the previous one already
        // completed. A test may call queue → startListening → queue
        // multiple times within one @Test method.
        if (channel.isClosedForSend) {
            channel = Channel(Channel.UNLIMITED)
        }
        _isListening.value = true
        val current = channel
        return current.consumeAsFlow()
    }

    override fun stopListening() {
        _isListening.value = false
        channel.close()
    }

    fun reset() {
        _isListening.value = false
        if (!channel.isClosedForSend) channel.close()
        channel = Channel(Channel.UNLIMITED)
    }
}
