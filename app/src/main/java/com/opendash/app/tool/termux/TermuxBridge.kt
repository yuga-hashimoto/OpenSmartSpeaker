package com.opendash.app.tool.termux

/**
 * Dispatches a shell command to a locally-installed Termux app.
 *
 * Real implementation fires a `com.termux.RUN_COMMAND` service intent and
 * awaits the result via a one-shot BroadcastReceiver bound to a
 * `com.termux.RUN_COMMAND_RESULT_PENDING_INTENT` — see [IntentTermuxBridge].
 *
 * Separated from the implementation so tests inject a [FakeTermuxBridge]
 * without needing an actual Termux install or an Android runtime.
 */
interface TermuxBridge {

    suspend fun exec(request: TermuxRequest): TermuxResult
}

data class TermuxRequest(
    val command: String,
    val arguments: List<String> = emptyList(),
    val workingDir: String? = null,
    val background: Boolean = true,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000
    }
}

sealed interface TermuxResult {
    data class Success(
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    ) : TermuxResult

    data class Failure(val reason: String) : TermuxResult

    data class NotAvailable(val reason: String) : TermuxResult

    data class Timeout(val elapsedMs: Long) : TermuxResult
}
