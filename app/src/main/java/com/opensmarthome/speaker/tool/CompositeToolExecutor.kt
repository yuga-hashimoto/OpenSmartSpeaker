package com.opensmarthome.speaker.tool

import timber.log.Timber

/**
 * Combines multiple ToolExecutors into a single executor.
 * Routes tool calls to the appropriate executor based on tool name.
 */
class CompositeToolExecutor(
    private val executors: List<ToolExecutor>
) : ToolExecutor {

    private val toolToExecutor = mutableMapOf<String, ToolExecutor>()

    override suspend fun availableTools(): List<ToolSchema> {
        val allTools = mutableListOf<ToolSchema>()
        toolToExecutor.clear()

        for (executor in executors) {
            val tools = executor.availableTools()
            for (tool in tools) {
                toolToExecutor[tool.name] = executor
                allTools.add(tool)
            }
        }

        return allTools
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        val executor = toolToExecutor[call.name]
        if (executor == null) {
            // Try refreshing tool list in case it was not yet loaded
            availableTools()
            val retryExecutor = toolToExecutor[call.name]
                ?: return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            return retryExecutor.execute(call)
        }

        Timber.d("Routing tool call '${call.name}' to ${executor.javaClass.simpleName}")
        return executor.execute(call)
    }
}
