package com.opensmarthome.speaker.tool

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CompositeToolExecutorTest {

    private lateinit var executor1: ToolExecutor
    private lateinit var executor2: ToolExecutor
    private lateinit var composite: CompositeToolExecutor

    @BeforeEach
    fun setup() {
        executor1 = mockk(relaxed = true)
        executor2 = mockk(relaxed = true)

        coEvery { executor1.availableTools() } returns listOf(
            ToolSchema("tool_a", "Tool A", emptyMap()),
            ToolSchema("tool_b", "Tool B", emptyMap())
        )
        coEvery { executor2.availableTools() } returns listOf(
            ToolSchema("tool_c", "Tool C", emptyMap())
        )

        composite = CompositeToolExecutor(listOf(executor1, executor2))
    }

    @Test
    fun `availableTools aggregates from all executors`() = runTest {
        val tools = composite.availableTools()
        assertThat(tools.map { it.name }).containsExactly("tool_a", "tool_b", "tool_c")
    }

    @Test
    fun `execute routes to correct executor`() = runTest {
        val expectedResult = ToolResult("1", true, "ok")
        coEvery { executor2.execute(any()) } returns expectedResult

        composite.availableTools() // initialize routing
        val result = composite.execute(ToolCall("1", "tool_c", emptyMap()))

        assertThat(result.success).isTrue()
        coVerify { executor2.execute(any()) }
    }

    @Test
    fun `execute returns error for unknown tool`() = runTest {
        composite.availableTools()
        val result = composite.execute(ToolCall("1", "unknown", emptyMap()))

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }
}
