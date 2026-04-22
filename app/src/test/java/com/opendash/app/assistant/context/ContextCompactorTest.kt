package com.opendash.app.assistant.context

import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.model.AssistantMessage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContextCompactorTest {

    @Test
    fun `history below threshold is returned unchanged`() = runTest {
        val compactor = ContextCompactor(maxMessages = 10, keepRecent = 5)
        val history = listOf(
            AssistantMessage.User(content = "hi"),
            AssistantMessage.Assistant(content = "hello")
        )

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isFalse()
        assertThat(result.messages).isEqualTo(history)
    }

    @Test
    fun `history over threshold gets compacted with naive summary`() = runTest {
        val compactor = ContextCompactor(maxMessages = 10, keepRecent = 3)
        val history = (1..15).flatMap {
            listOf(
                AssistantMessage.User(content = "Q$it"),
                AssistantMessage.Assistant(content = "A$it")
            )
        }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        assertThat(result.removedCount).isGreaterThan(0)
        assertThat(result.messages.size).isLessThan(history.size)
        // First message should be a system summary
        val firstSystem = result.messages.firstOrNull { it is AssistantMessage.System }
        assertThat(firstSystem).isNotNull()
        assertThat((firstSystem as AssistantMessage.System).content).contains("summary")
    }

    @Test
    fun `initial system messages are preserved`() = runTest {
        val compactor = ContextCompactor(maxMessages = 10, keepRecent = 3)
        val system = AssistantMessage.System(content = "You are a helpful assistant.")
        val history = listOf(system) + (1..20).map { AssistantMessage.User(content = "msg$it") }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        assertThat(result.messages[0]).isEqualTo(system)
    }

    @Test
    fun `recent messages are preserved in order`() = runTest {
        val compactor = ContextCompactor(maxMessages = 5, keepRecent = 3)
        val history = (1..10).map { AssistantMessage.User(content = "Q$it") }

        val result = compactor.compact(history)

        // Last 3 should be Q8, Q9, Q10
        val last3 = result.messages.takeLast(3).map { (it as AssistantMessage.User).content }
        assertThat(last3).containsExactly("Q8", "Q9", "Q10").inOrder()
    }

    @Test
    fun `uses summarizer when provided`() = runTest {
        val summarizer = mockk<ConversationSummarizer>()
        coEvery { summarizer.summarize(any()) } returns "Smart summary here"

        val compactor = ContextCompactor(maxMessages = 5, keepRecent = 2, summarizer = summarizer)
        val history = (1..10).map { AssistantMessage.User(content = "msg$it") }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        val summaryMsg = result.messages.firstOrNull { it is AssistantMessage.System } as? AssistantMessage.System
        assertThat(summaryMsg?.content).contains("Smart summary here")
    }

    @Test
    fun `falls back to naive summary on summarizer error`() = runTest {
        val summarizer = mockk<ConversationSummarizer>()
        coEvery { summarizer.summarize(any()) } throws RuntimeException("LLM error")

        val compactor = ContextCompactor(maxMessages = 5, keepRecent = 2, summarizer = summarizer)
        val history = (1..10).map { AssistantMessage.User(content = "msg$it") }

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        // Should still compact using naive summary
        val summaryMsg = result.messages.firstOrNull { it is AssistantMessage.System }
        assertThat(summaryMsg).isNotNull()
    }

    @Test
    fun `tool call and its result never split across the boundary`() = runTest {
        val compactor = ContextCompactor(maxMessages = 4, keepRecent = 2)
        val history = listOf(
            AssistantMessage.User(content = "what is the weather?"),
            AssistantMessage.Assistant(
                content = "",
                toolCalls = listOf(
                    com.opendash.app.assistant.model.ToolCallRequest(
                        id = "1", name = "get_weather", arguments = "{}"
                    )
                )
            ),
            AssistantMessage.ToolCallResult(callId = "1", result = "sunny"),
            AssistantMessage.Assistant(content = "It's sunny."),
            AssistantMessage.User(content = "and tomorrow?"),
            AssistantMessage.Assistant(
                content = "",
                toolCalls = listOf(
                    com.opendash.app.assistant.model.ToolCallRequest(
                        id = "2", name = "get_forecast", arguments = "{}"
                    )
                )
            ),
            AssistantMessage.ToolCallResult(callId = "2", result = "rain"),
            AssistantMessage.Assistant(content = "Rain tomorrow.")
        )

        val result = compactor.compact(history)

        assertThat(result.wasCompacted).isTrue()
        // The first retained non-system message must not be an orphaned
        // ToolCallResult whose tool_call was cut into the summary.
        val nonSystem = result.messages.filter { it !is AssistantMessage.System }
        val firstNonSystem = nonSystem.firstOrNull()
        assertThat(firstNonSystem).isNotInstanceOf(AssistantMessage.ToolCallResult::class.java)

        // Each retained ToolCallResult must have a matching tool_call
        // earlier in the retained slice.
        val retainedCallIds = result.messages
            .filterIsInstance<AssistantMessage.Assistant>()
            .flatMap { it.toolCalls.map { req -> req.id } }
        val resultIds = result.messages
            .filterIsInstance<AssistantMessage.ToolCallResult>()
            .map { it.callId }
        for (rid in resultIds) {
            assertThat(retainedCallIds).contains(rid)
        }
    }

    @Test
    fun `boundary snaps forward to the next User turn`() = runTest {
        val compactor = ContextCompactor(maxMessages = 4, keepRecent = 2)
        val u1 = AssistantMessage.User(content = "u1")
        val a1 = AssistantMessage.Assistant(content = "a1")
        val u2 = AssistantMessage.User(content = "u2")
        val a2 = AssistantMessage.Assistant(content = "a2")
        val u3 = AssistantMessage.User(content = "u3")
        val a3 = AssistantMessage.Assistant(content = "a3")
        val history = listOf(u1, a1, u2, a2, u3, a3)

        val result = compactor.compact(history)

        // Retained slice must begin with a User message to give the LLM a
        // clean turn boundary.
        val nonSystem = result.messages.filter { it !is AssistantMessage.System }
        assertThat(nonSystem.first()).isInstanceOf(AssistantMessage.User::class.java)
    }
}
