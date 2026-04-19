package com.opendash.app.e2e.fakes

import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.assistant.provider.ProviderCapabilities
import com.opendash.app.tool.ToolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test double for [AssistantProvider] that returns scripted responses
 * instead of running an LLM.
 *
 * The production [com.opendash.app.assistant.router.ConversationRouter]
 * accepts dynamically registered providers via [register], so tests can
 * inject this fake at runtime — no `@TestInstallIn` needed.
 *
 * Usage:
 * ```
 * fakeAssistant.queueResponse(AssistantMessage.Assistant(content = "hi"))
 * conversationRouter.registerProvider(fakeAssistant)
 * conversationRouter.selectProvider(fakeAssistant.id)  // make it active
 * ```
 *
 * Records every `send()` call into [sentMessages] for assertions.
 */
class FakeAssistantProvider(
    override val id: String = "fake-assistant",
    override val displayName: String = "Fake Assistant",
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreaming = false,
        supportsTools = true,
        maxContextTokens = 8192,
        modelName = "fake-1",
        // Mark as local so the Auto policy won't gate this provider on
        // network state — tests run on emulators that may not have
        // internet, and we never want to hit the real network anyway.
        isLocal = true
    )
) : AssistantProvider {

    private val responses = ArrayDeque<AssistantMessage>()
    private val _sentMessages = CopyOnWriteArrayList<List<AssistantMessage>>()
    val sentMessages: List<List<AssistantMessage>> get() = _sentMessages.toList()

    fun queueResponse(message: AssistantMessage) {
        responses.addLast(message)
    }

    fun reset() {
        responses.clear()
        _sentMessages.clear()
    }

    override suspend fun startSession(config: Map<String, String>): AssistantSession =
        AssistantSession(providerId = id)

    override suspend fun endSession(session: AssistantSession) = Unit

    override suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): AssistantMessage {
        _sentMessages.add(messages.toList())
        return responses.removeFirstOrNull()
            // Fall back to a benign assistant message so the pipeline
            // doesn't NPE when a test forgets to queue. The assertion
            // failure surfaces in the test, not as a crash.
            ?: AssistantMessage.Assistant(content = "(no fake response queued)")
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flowOf()

    override suspend fun isAvailable(): Boolean = true
    override suspend fun latencyMs(): Long = 1L
}
